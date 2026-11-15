package ru.svoe.phone

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

private val BG = Color(0xFF0F1115)
private val CARD = Color(0xFF1C1F26)
private val DIM = Color(0xFF9AA0A6)
private val ACCENT = Color(0xFF3D7BEA)
private val GOOD = Color(0xFF4CAF50)

@Composable
private fun Txt(text: String, color: Color = Color.White, size: TextUnit = 16.sp, bold: Boolean = false) {
    BasicText(
        text,
        style = TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    )
}

@Composable
private fun Btn(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ACCENT)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Txt(text, bold = true) }
}

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private class Dev(val name: String, val mac: String, val bonded: Boolean)

    private val devices = mutableStateListOf<Dev>()
    private var status by mutableStateOf("")
    private var discovering by mutableStateOf(false)
    private var tvName by mutableStateOf("")
    private var tvMac by mutableStateOf("")
    private var code by mutableStateOf("")
    private var notifAccess by mutableStateOf(false)
    private var missing by mutableStateOf<List<String>>(emptyList())

    private var pendingName: String? = null   // ждём разрешений, чтобы искать ТВ по имени
    private var searchName: String? = null    // идёт поиск ТВ с этим именем
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopDiscovery() }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        val n = pendingName
        if (n != null) resolvePair(n)
        startServiceIfReady(false)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try {
                when (i.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = if (Build.VERSION.SDK_INT >= 33)
                            i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (d != null) onFound(d)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (discovering) stopDiscovery()
                }
            } catch (_: Throwable) {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tvMac = Prefs.mac(this)
        tvName = Prefs.name(this)
        code = Prefs.code(this)
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        setContent { Ui() }
        refresh()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        startServiceIfReady(false)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        handler.removeCallbacks(stopRunnable)
        try { adapter()?.cancelDiscovery() } catch (_: Throwable) {}
        super.onDestroy()
    }

    // ---------- разрешения и состояние ----------

    private fun neededPerms(): List<String> {
        val l = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 31) {
            l += Manifest.permission.BLUETOOTH_CONNECT
            l += Manifest.permission.BLUETOOTH_SCAN
        } else {
            l += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.POST_NOTIFICATIONS
        return l
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun btOk(): Boolean =
        Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun scanOk(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) granted(Manifest.permission.BLUETOOTH_SCAN)
        else granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun refresh() {
        missing = neededPerms().filter { !granted(it) }
        notifAccess = try {
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        } catch (_: Throwable) {
            false
        }
        loadBonded()
    }

    private fun adapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    private fun loadBonded() {
        devices.removeAll { it.bonded }
        if (!btOk()) return
        try {
            val bonded = adapter()?.bondedDevices ?: return
            val list = bonded.map { Dev(it.name ?: it.address, it.address, true) }
            devices.addAll(0, list)
        } catch (_: Throwable) {
        }
    }

    private fun startServiceIfReady(reset: Boolean) {
        if (tvMac.isBlank() || code.length != 6 || !btOk()) return
        try {
            val i = Intent(this, SyncService::class.java).putExtra(SyncService.EXTRA_RESET, reset)
            ContextCompat.startForegroundService(this, i)
        } catch (e: Throwable) {
            status = "Не удалось запустить службу: ${e.message}"
        }
    }

    // ---------- выбор ТВ ----------

    private fun select(d: Dev) {
        stopDiscovery()
        searchName = null
        Prefs.setTv(this, d.mac, d.name)
        tvMac = d.mac
        tvName = d.name
        status = "Выбран ТВ: ${d.name}"
        startServiceIfReady(true)
    }

    private fun onCode(v: String) {
        code = v
        Prefs.setCode(this, v)
        if (v.length == 6) startServiceIfReady(true)
    }

    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme != "svoetv" || data.host != "pair") return
        val name = data.getQueryParameter("name").orEmpty()
        val c = data.getQueryParameter("code").orEmpty().filter { it.isDigit() }.take(6)
        if (c.isNotEmpty()) onCodeNoStart(c)
        if (name.isBlank()) {
            status = "В ссылке нет имени ТВ — выбери его из списка"
            return
        }
        Prefs.setName(this, name)
        tvName = name
        status = "Ищу ТВ «$name»…"
        pendingName = name
        val need = neededPerms().filter { !granted(it) }
        if (need.isNotEmpty()) {
            permLauncher.launch(need.toTypedArray())
        } else {
            resolvePair(name)
        }
    }

    private fun onCodeNoStart(c: String) {
        code = c
        Prefs.setCode(this, c)
    }

    private fun resolvePair(name: String) {
        pendingName = null
        if (!btOk()) {
            status = "Нет разрешения Bluetooth — выбери ТВ вручную после выдачи разрешений"
            return
        }
        refresh()
        val b = devices.firstOrNull { it.bonded && it.name.equals(name, ignoreCase = true) }
        if (b != null) {
            select(b)
            return
        }
        searchName = name
        startDiscovery()
        if (!discovering) searchName = null
    }

    // ---------- поиск устройств ----------

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (!scanOk()) {
            status = "Нет разрешения на поиск Bluetooth"
            permLauncher.launch(neededPerms().filter { !granted(it) }.toTypedArray())
            return
        }
        val ad = adapter()
        if (ad == null) {
            status = "Bluetooth недоступен"
            return
        }
        if (!ad.isEnabled) {
            status = "Включи Bluetooth"
            return
        }
        try {
            if (ad.isDiscovering) ad.cancelDiscovery()
            discovering = ad.startDiscovery()
            if (discovering) {
                status = if (searchName != null) "Ищу ТВ «$searchName»…" else "Ищу устройства…"
                handler.removeCallbacks(stopRunnable)
                handler.postDelayed(stopRunnable, 15000)
            } else {
                status = "Не удалось начать поиск"
            }
        } catch (_: SecurityException) {
            status = "Нет разрешения на поиск Bluetooth"
        } catch (e: Throwable) {
            status = "Ошибка поиска: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscovery() {
        handler.removeCallbacks(stopRunnable)
        try { adapter()?.cancelDiscovery() } catch (_: Throwable) {}
        val wasDiscovering = discovering
        discovering = false
        val n = searchName
        if (wasDiscovering && n != null) {
            searchName = null
            status = "ТВ «$n» не найден. Выбери его из списка или повтори поиск"
        } else if (wasDiscovering) {
            status = "Поиск завершён"
        }
    }

    @SuppressLint("MissingPermission")
    private fun onFound(d: BluetoothDevice) {
        val name = try { d.name } catch (_: Throwable) { null }
        val mac = d.address ?: return
        if (devices.none { it.mac == mac }) devices.add(Dev(name ?: mac, mac, false))
        val s = searchName
        if (s != null && name != null && name.equals(s, ignoreCase = true)) {
            select(Dev(name, mac, false))
        }
    }

    // ---------- QR и подключение ----------

    private fun scanQr() {
        try {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener { b ->
                    val raw = b.rawValue.orEmpty()
                    if (raw.startsWith("svoetv://")) handleIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(raw)))
                    else status = "Это не QR от Своё ТВ"
                }
                .addOnFailureListener { status = "Сканер недоступен: ${it.message}" }
        } catch (e: Throwable) {
            status = "Сканер недоступен: ${e.message}"
        }
    }

    private fun connectNow() {
        if (code.length != 6) { status = "Введи 6-значный код с ТВ или отсканируй QR"; return }
        val need = neededPerms().filter { !granted(it) }
        if (need.isNotEmpty()) { pendingName = tvName.ifBlank { null }; permLauncher.launch(need.toTypedArray()); return }
        when {
            tvMac.isNotBlank() -> { status = "Подключаюсь…"; startServiceIfReady(true) }
            tvName.isNotBlank() -> resolvePair(tvName)
            else -> { searchName = null; startDiscovery(); status = "Выбери свой ТВ из списка ниже" }
        }
    }

    // ---------- UI ----------

    @Composable
    private fun Ui() {
        Column(
            Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Txt("Своё ТВ: телефон", size = 24.sp, bold = true)

            val on = Link.connected.value
            Txt(if (on) "● Связь с ТВ есть" else "○ Нет связи с ТВ", color = if (on) GOOD else DIM, bold = true)
            Txt(Link.status.value, color = DIM, size = 14.sp)
            if (status.isNotBlank()) Txt(status, size = 14.sp)

            Txt("ТВ: " + if (tvMac.isBlank()) "не выбран" else "$tvName ($tvMac)")

            Btn("Сканировать QR с ТВ") { scanQr() }
            Btn("Подключиться") { connectNow() }

            Txt("Код с экрана ТВ (6 цифр)", color = DIM, size = 14.sp)
            BasicTextField(
                value = code,
                onValueChange = { v -> onCode(v.filter { it.isDigit() }.take(6)) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD).padding(14.dp)
            )

            if (missing.isNotEmpty()) {
                Txt("Не хватает разрешений: ${missing.size}", color = DIM, size = 14.sp)
                Btn("Выдать разрешения") { permLauncher.launch(missing.toTypedArray()) }
            }
            if (!notifAccess) {
                Txt("Нет доступа к уведомлениям", color = DIM, size = 14.sp)
            }
            Btn(if (notifAccess) "Доступ к уведомлениям: настройки" else "Выдать доступ к уведомлениям") {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Throwable) {
                    status = "Не удалось открыть настройки"
                }
            }

            Btn(if (discovering) "Остановить поиск" else "Искать устройства (15 с)") {
                if (discovering) {
                    stopDiscovery()
                } else {
                    searchName = null
                    refresh()
                    startDiscovery()
                }
            }
            Btn("Переподключиться") {
                if (tvMac.isBlank() || code.length != 6) status = "Сначала выбери ТВ и введи код"
                else startServiceIfReady(true)
            }

            Txt("Устройства", color = DIM, size = 14.sp)
            if (devices.isEmpty()) Txt("Список пуст", color = DIM, size = 14.sp)
            for (d in devices.toList()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (d.mac == tvMac) ACCENT else CARD)
                        .clickable { select(d) }.padding(12.dp)
                ) {
                    Txt(d.name + (if (d.bonded) " (сопряжено)" else "") + "\n" + d.mac, size = 15.sp)
                }
            }
        }
    }
}
