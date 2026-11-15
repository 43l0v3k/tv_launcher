package ru.svoe.tv

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

data class Notif(val app: String, val title: String, val text: String, val time: Long)
data class CalEvent(val title: String, val start: Long, val end: Long, val allDay: Boolean)

/**
 * Приём данных с телефона по Bluetooth (RFCOMM).
 * Одна JSON-строка на сообщение. Первым обязательно приходит hello с кодом привязки:
 *   {"t":"hello","code":"123456","name":"Samsung A56","battery":55}
 *   {"t":"batt","level":55}
 *   {"t":"notif","app":"Telegram","title":"Имя","text":"Привет"}
 *   {"t":"call","state":"ring","who":"Мама"}  /  {"t":"call","state":"end"}
 *   {"t":"cal","events":[{"title":"..","start":ms,"end":ms,"allDay":false}]}
 */
object Sync {
    val SERVICE_UUID: UUID = UUID.fromString("5c0e7a10-7a5e-4b0e-9d52-5f0a0b3a0001")

    val notifs = mutableStateListOf<Notif>()
    val toast = mutableStateOf<Notif?>(null)
    val call = mutableStateOf<String?>(null)
    val events = mutableStateListOf<CalEvent>()
    val connected = mutableStateOf(false)
    val phoneName = mutableStateOf("")
    val battery = mutableIntStateOf(-1)

    @Volatile private var code: String = ""
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var started = false

    @SuppressLint("MissingPermission")
    fun start(pairCode: String) {
        code = pairCode
        if (started) return
        started = true
        thread(isDaemon = true, name = "bt-server") {
            while (true) {
                try {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                    if (adapter == null || !adapter.isEnabled) {
                        Thread.sleep(5000)
                        continue
                    }
                    // Без системного диалога сопряжения: защита — код привязки в hello
                    adapter.listenUsingInsecureRfcommWithServiceRecord("SvoeTV", SERVICE_UUID).use { server ->
                        val sock = server.accept()
                        serve(sock)
                    }
                } catch (e: Throwable) {
                    Thread.sleep(3000)
                }
            }
        }
    }

    private fun serve(sock: BluetoothSocket) {
        var authed = false
        try {
            sock.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                val j = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                if (!authed) {
                    if (j.optString("t") == "hello" && code.isNotEmpty() && j.optString("code") == code) {
                        authed = true
                        val name = j.optString("name")
                        val batt = j.optInt("battery", -1)
                        main.post {
                            connected.value = true
                            phoneName.value = name
                            battery.intValue = batt
                        }
                    } else {
                        throw IOException("bad code")
                    }
                } else {
                    handle(j)
                }
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { sock.close() }
            main.post { connected.value = false; call.value = null }
        }
    }

    private fun handle(j: JSONObject) {
        when (j.optString("t")) {
            "batt" -> {
                val lvl = j.optInt("level", -1)
                main.post { battery.intValue = lvl }
            }
            "notif" -> {
                val n = Notif(j.optString("app"), j.optString("title"), j.optString("text"), System.currentTimeMillis())
                main.post {
                    notifs.add(0, n)
                    while (notifs.size > 30) notifs.removeAt(notifs.size - 1)
                    toast.value = n
                }
            }
            "call" -> {
                val ring = j.optString("state") == "ring"
                val who = j.optString("who").ifBlank { "Неизвестный номер" }
                main.post { call.value = if (ring) who else null }
            }
            "cal" -> {
                val arr = j.optJSONArray("events")
                val list = mutableListOf<CalEvent>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list += CalEvent(o.optString("title"), o.optLong("start"), o.optLong("end"), o.optBoolean("allDay"))
                    }
                }
                main.post { events.clear(); events.addAll(list) }
            }
        }
    }
}
