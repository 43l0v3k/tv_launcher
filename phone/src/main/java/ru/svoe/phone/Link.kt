package ru.svoe.phone

import androidx.compose.runtime.mutableStateOf
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Текущее соединение с ТВ. send() потокобезопасен, сообщения
 * пишутся из одного фонового потока; без соединения — отбрасываются.
 */
object Link {
    val SERVICE_UUID: UUID = UUID.fromString("5c0e7a10-7a5e-4b0e-9d52-5f0a0b3a0001")

    val connected = mutableStateOf(false)
    val status = mutableStateOf("Служба не запущена")

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "link-io").apply { isDaemon = true }
    }

    @Volatile
    private var out: OutputStream? = null

    fun attach(o: OutputStream) {
        out = o
    }

    fun detach() {
        out = null
        connected.value = false
    }

    fun send(line: String) {
        if (out == null) return
        try {
            io.execute {
                val o = out
                if (o != null) {
                    try {
                        o.write((line + "\n").toByteArray(Charsets.UTF_8))
                        o.flush()
                    } catch (e: Throwable) {
                        // обрыв: закрываем поток, цикл службы переподключится
                        try { o.close() } catch (_: Throwable) {}
                        if (out === o) out = null
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}
