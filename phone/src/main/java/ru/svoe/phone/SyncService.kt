package ru.svoe.phone

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Держит RFCOMM-соединение с ТВ, переподключается каждые 5 секунд. */
class SyncService : Service() {
    companion object {
        const val EXTRA_RESET = "reset"
        private const val CHANNEL = "link"
        private const val NOTIF_ID = 1
    }

    @Volatile private var running = false
    @Volatile private var sock: BluetoothSocket? = null
    private var sched: ScheduledExecutorService? = null
    private var battRegistered = false
    @Volatile private var lastLevel = -1

    private val battReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try {
                val lvl = percent(i)
                if (lvl >= 0 && lvl != lastLevel) sendBatt(lvl)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            goForeground()
        } catch (e: Throwable) {
            Link.status.value = "Нет разрешений для службы связи"
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_RESET, false) == true) closeSock()
        if (!running) {
            running = true
            try {
                ContextCompat.registerReceiver(
                    this, battReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                battRegistered = true
            } catch (_: Throwable) {
            }
            thread(isDaemon = true, name = "tv-link") { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        if (battRegistered) {
            try { unregisterReceiver(battReceiver) } catch (_: Throwable) {}
            battRegistered = false
        }
        closeSock()
        Link.detach()
        Link.status.value = "Служба остановлена"
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Связь с ТВ", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Связь с ТВ")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun closeSock() {
        try { sock?.close() } catch (_: Throwable) {}
    }

    private fun loop() {
        while (running) {
            try {
                val mac = Prefs.mac(this)
                val code = Prefs.code(this)
                val ad = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                when {
                    mac.isBlank() || code.length != 6 -> Link.status.value = "ТВ или код не заданы"
                    ad == null || !ad.isEnabled -> Link.status.value = "Bluetooth выключен"
                    else -> {
                        connectOnce(ad, mac, code)
                        Link.status.value = "Связь потеряна, повтор через 5 с"
                    }
                }
            } catch (e: Throwable) {
                Link.status.value = "ТВ недоступно, повтор через 5 с"
            }
            Link.detach()
            try {
                Thread.sleep(5000)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectOnce(ad: BluetoothAdapter, mac: String, code: String) {
        Link.status.value = "Подключаюсь…"
        try { ad.cancelDiscovery() } catch (_: Throwable) {}
        val s = ad.getRemoteDevice(mac).createInsecureRfcommSocketToServiceRecord(Link.SERVICE_UUID)
        sock = s
        try {
            s.connect()
            val out = s.outputStream
            val bat = battery()
            // hello обязан быть первым сообщением
            val hello = JSONObject().put("t", "hello").put("code", code)
                .put("name", Build.MODEL).put("battery", bat)
            out.write((hello.toString() + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            Link.attach(out)
            Link.connected.value = true
            Link.status.value = "Подключено"
            lastLevel = bat
            startSched()
            // ТВ ничего не шлёт; блокируемся до закрытия сокета
            val inp = s.inputStream
            val buf = ByteArray(256)
            while (inp.read(buf) >= 0) { /* ждём */ }
        } finally {
            stopSched()
            Link.detach()
            try { s.close() } catch (_: Throwable) {}
            sock = null
        }
    }

    private fun startSched() {
        stopSched()
        val ex = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "tv-sched").apply { isDaemon = true }
        }
        sched = ex
        ex.scheduleWithFixedDelay({
            try { sendBatt(battery()) } catch (_: Throwable) {}
        }, 0, 60, TimeUnit.SECONDS)
        ex.scheduleWithFixedDelay({
            try { NotifListener.push() } catch (_: Throwable) {}
        }, 2, 20, TimeUnit.SECONDS)
        ex.scheduleWithFixedDelay({
            try { Link.send(Cal.build(this).toString()) } catch (_: Throwable) {}
        }, 1, 15 * 60L, TimeUnit.SECONDS)
    }

    private fun stopSched() {
        try { sched?.shutdownNow() } catch (_: Throwable) {}
        sched = null
    }

    private fun sendBatt(level: Int) {
        if (level < 0) return
        lastLevel = level
        Link.send(JSONObject().put("t", "batt").put("level", level).toString())
    }

    private fun battery(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return if (i == null) -1 else percent(i)
    }

    private fun percent(i: Intent): Int {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }
}
