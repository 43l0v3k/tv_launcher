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
        c.setRequestProperty("User-Agent", "SvoeTV/1.0")
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
    var feels by mutableStateOf<Int?>(null)
    var rainChance by mutableIntStateOf(-1)
    /** Прогноз на ближайшие дни */
    var days by mutableStateOf(emptyList<DayForecast>())
}

data class DayForecast(val time: Long, val min: Int, val max: Int, val code: Int)

/** Короткий совет по погоде для заставки и главного экрана. Пустая строка — совет не нужен. */
fun weatherAdvice(): String {
    val t = WeatherData.temp ?: return ""
    val rain = WeatherData.rainChance
    val code = WeatherData.code
    val wet = rain >= 50 || code in 51..67 || code in 80..82
    val snow = code in 71..77 || code in 85..86
    return when {
        code in 95..99 -> tr("adv_storm")
        snow -> tr("adv_snow")
        wet -> tr("adv_umbrella")
        t <= -10 -> tr("adv_frost")
        t <= 5 -> tr("adv_cold")
        t >= 30 -> tr("adv_hot")
        else -> ""
    }
}

/** Погода: open-meteo.com, ключ не нужен. */
suspend fun loadWeather(prefs: Prefs) = withContext(Dispatchers.IO) {
    runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${prefs.lat}&longitude=${prefs.lon}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&wind_speed_unit=ms" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=4&timezone=auto"
        val root = JSONObject(Net.get(url))
        val c = root.getJSONObject("current")
        WeatherData.temp = Math.round(c.getDouble("temperature_2m")).toInt()
        WeatherData.wind = Math.round(c.getDouble("wind_speed_10m")).toInt()
        WeatherData.code = c.getInt("weather_code")
        WeatherData.feels = runCatching { Math.round(c.getDouble("apparent_temperature")).toInt() }.getOrNull()
        WeatherData.city = prefs.city
        runCatching {
            val d = root.getJSONObject("daily")
            val codes = d.getJSONArray("weather_code")
            val mx = d.getJSONArray("temperature_2m_max")
            val mn = d.getJSONArray("temperature_2m_min")
            val pr = d.getJSONArray("precipitation_probability_max")
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val list = ArrayList<DayForecast>()
            for (i in 0 until codes.length()) {
                val ts = fmt.parse(d.getJSONArray("time").getString(i))?.time ?: 0L
                list += DayForecast(ts, Math.round(mn.getDouble(i)).toInt(), Math.round(mx.getDouble(i)).toInt(), codes.getInt(i))
            }
            WeatherData.days = list
            WeatherData.rainChance = if (pr.length() > 0 && !pr.isNull(0)) pr.getInt(0) else -1
        }
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
