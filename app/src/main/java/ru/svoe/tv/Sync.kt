package ru.svoe.tv

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.util.UUID
import kotlin.concurrent.thread

data class Notif(val app: String, val title: String, val text: String, val time: Long)
data class CalEvent(val title: String, val start: Long, val end: Long, val allDay: Boolean)

/**
 * Приём данных с телефона по Bluetooth (RFCOMM).
 * Протокол: одна JSON-строка на сообщение:
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

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var started = false

    @SuppressLint("MissingPermission")
    fun start() {
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
                    adapter.listenUsingRfcommWithServiceRecord("SvoeTV", SERVICE_UUID).use { server ->
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
        main.post { connected.value = true }
        try {
            sock.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { handle(it) }
        } catch (_: Throwable) {
        } finally {
            runCatching { sock.close() }
            main.post { connected.value = false; call.value = null }
        }
    }

    private fun handle(line: String) {
        val j = runCatching { JSONObject(line) }.getOrNull() ?: return
        when (j.optString("t")) {
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
