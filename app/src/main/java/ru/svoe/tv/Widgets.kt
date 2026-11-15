package ru.svoe.tv

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Net {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "SvoeTV/0.3")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

object WeatherData {
    var temp by mutableStateOf<Int?>(null)
    var wind by mutableStateOf<Int?>(null)
    var code by mutableIntStateOf(-1)
    var city by mutableStateOf("")
}

/** Погода: open-meteo.com, ключ не нужен. */
suspend fun loadWeather(prefs: Prefs) = withContext(Dispatchers.IO) {
    runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${prefs.lat}&longitude=${prefs.lon}" +
            "&current=temperature_2m,weather_code,wind_speed_10m&wind_speed_unit=ms"
        val c = JSONObject(Net.get(url)).getJSONObject("current")
        WeatherData.temp = Math.round(c.getDouble("temperature_2m")).toInt()
        WeatherData.wind = Math.round(c.getDouble("wind_speed_10m")).toInt()
        WeatherData.code = c.getInt("weather_code")
        WeatherData.city = prefs.city
    }
}

/** Ищет город по названию, возвращает (название, широта, долгота). */
suspend fun geocode(name: String): Triple<String, Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        val j = JSONObject(Net.get("https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(name)}&count=1&language=ru"))
        val r = j.getJSONArray("results").getJSONObject(0)
        Triple(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }.getOrNull()
}

object FinData {
    var imoex by mutableStateOf(emptyList<Float>())
    var btc by mutableStateOf(emptyList<Float>())
    var usd by mutableStateOf<Float?>(null)
    var usdPrev by mutableStateOf<Float?>(null)
    var gbp by mutableStateOf<Float?>(null)
    var gbpPrev by mutableStateOf<Float?>(null)
}

/** Курсы ЦБ, индекс Мосбиржи, биткоин. Каждый источник независим: упал один, остальные работают. */
suspend fun loadFinance() = withContext(Dispatchers.IO) {
    runCatching {
        val v = JSONObject(Net.get("https://www.cbr-xml-daily.ru/daily_json.js")).getJSONObject("Valute")
        fun rate(code: String, prev: Boolean): Float {
            val o = v.getJSONObject(code)
            return (o.getDouble(if (prev) "Previous" else "Value") / o.getInt("Nominal")).toFloat()
        }
        FinData.usd = rate("USD", false); FinData.usdPrev = rate("USD", true)
        FinData.gbp = rate("GBP", false); FinData.gbpPrev = rate("GBP", true)
    }
    runCatching {
        val from = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 45L * 86_400_000))
        val j = JSONObject(Net.get("https://iss.moex.com/iss/engines/stock/markets/index/securities/IMOEX/candles.json?interval=24&from=$from&iss.meta=off"))
        val c = j.getJSONObject("candles")
        val cols = c.getJSONArray("columns")
        var ci = 1
        for (i in 0 until cols.length()) if (cols.getString(i) == "close") ci = i
        val data = c.getJSONArray("data")
        val out = ArrayList<Float>()
        for (i in 0 until data.length()) out += data.getJSONArray(i).getDouble(ci).toFloat()
        if (out.size >= 2) FinData.imoex = out.takeLast(30)
    }
    runCatching {
        val out = ArrayList<Float>()
        runCatching {
            val j = JSONObject(Net.get("https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=30&interval=daily"))
            val a = j.getJSONArray("prices")
            for (i in 0 until a.length()) out += a.getJSONArray(i).getDouble(1).toFloat()
        }
        if (out.size < 2) {
            out.clear()
            val a = org.json.JSONArray(Net.get("https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1d&limit=30"))
            for (i in 0 until a.length()) out += a.getJSONArray(i).getString(4).toFloat()
        }
        if (out.size >= 2) FinData.btc = out
    }
}
