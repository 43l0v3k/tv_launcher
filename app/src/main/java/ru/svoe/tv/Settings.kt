package ru.svoe.tv

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

/** Глобальные настройки оформления, которые читает корень приложения. */
object Look {
    var wallpaper by mutableStateOf("auto")
    var redo by mutableIntStateOf(0)
    var saver by mutableStateOf(false)
    var busy by mutableStateOf(false)
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth().height(d(44)),
        onClick = onClick,
        selected = on,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(horizontal = d(16)).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(text, 16, maxLines = 1, modifier = Modifier.weight(1f))
            if (on) Ico("check", d(18), Good)
        }
    }
}

/** Все настройки «Своего ТВ» в одном месте: слева разделы, справа содержимое выбранного. */
@Composable
fun SettingsScreen(
    act: MainActivity,
    prefs: Prefs,
    widgets: List<String>,
    onWidgets: (List<String>) -> Unit,
    onChannels: (List<Channel>) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf("lang") }
    var sub by remember { mutableStateOf<String?>(null) }
    var hasPin by remember { mutableStateOf(prefs.hasPin) }
    var code by remember { mutableStateOf(prefs.pairCode) }
    var region by remember { mutableStateOf(prefs.region) }
    var city by remember { mutableStateOf(prefs.city) }
    var ssMode by remember { mutableStateOf(prefs.ssMode) }
    var ssMin by remember { mutableStateOf(prefs.ssMinutes) }
    var ssMsg by remember { mutableStateOf("") }
    var remoteOn by remember { mutableStateOf(prefs.remoteOn) }
    val ver = remember { runCatching { act.packageManager.getPackageInfo(act.packageName, 0).versionName }.getOrNull() ?: "" }

    val cats = listOf(
        "lang" to "lang_title",
        "region" to "region_title",
        "phone" to "set_phone",
        "remote" to "set_remote",
        "weather" to "weather",
        "iptv" to "iptv",
        "widgets" to "widgets",
        "saver" to "set_saver",
        "look" to "set_look",
        "lock" to "qs_lock",
        "system" to "set_system",
        "about" to "set_about",
    )

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalInteractive provides (sub == null)) {
            Overlay(onClose) {
                GlassCard(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = RoundedCornerShape(d(28))) {
                    Row(Modifier.fillMaxSize().padding(d(20)), horizontalArrangement = Arrangement.spacedBy(d(20))) {
                        Column(
                            Modifier.width(d(210)).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(d(8)),
                        ) {
                            Label(tr("qs_settings"), 22, FontWeight.SemiBold, maxLines = 1)
                            Spacer(Modifier.height(d(4)))
                            cats.forEachIndexed { i, (id, key) ->
                                GlassCard(
                                    Modifier.fillMaxWidth().height(d(42)),
                                    onClick = { cat = id },
                                    selected = cat == id,
                                    autoFocus = i == 0,
                                    onFocus = { if (it) cat = id },
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Label(tr(key), 15, maxLines = 1, modifier = Modifier.padding(horizontal = d(14)))
                                }
                            }
                        }

                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(d(10)),
                        ) {
                            when (cat) {
                                "lang" -> {
                                    PanelTitle(tr("lang_title"))
                                    LANGS.forEach { (c, n) -> Choice(n, L.lang == c) { L.lang = c; prefs.lang = c } }
                                }

                                "region" -> {
                                    PanelTitle(tr("region_title"))
                                    listOf("ru" to "reg_ru", "ua" to "reg_ua", "us" to "reg_us").forEach { (c, k) ->
                                        Choice(tr(k), region == c) {
                                            region = c
                                            prefs.region = c
                                            when (c) {
                                                "ua" -> { prefs.city = "Київ"; prefs.lat = 50.4501; prefs.lon = 30.5234 }
                                                "us" -> { prefs.city = "New York"; prefs.lat = 40.7128; prefs.lon = -74.006 }
                                            }
                                            city = prefs.city
                                            scope.launch { loadWeather(prefs) }
                                        }
                                    }
                                }

                                "phone" -> {
                                    PanelTitle(tr("set_phone"))
                                    val on = Sync.connected.value
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(10))) {
                                        Ico("phone", d(24), if (on) Good else Muted)
                                        Label(
                                            if (on) "${tr("pair_ok")}: ${Sync.phoneName.value}" else tr("phone_none"),
                                            15, maxLines = 1,
                                        )
                                    }
                                    Label(tr("phone_relink_hint"), 12, color = Muted)
                                    Row(horizontalArrangement = Arrangement.spacedBy(d(26))) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d(6))) {
                                            QrImage(PHONE_APK_URL, Modifier.size(d(110)))
                                            Label(tr("pair_download"), 12, color = Muted, maxLines = 1)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d(6))) {
                                            QrImage(prefs.pairLink(), Modifier.size(d(110)))
                                            Label(tr("pair_link"), 12, color = Muted, maxLines = 1)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(10))) {
                                        Label("${tr("pair_code")}:", 15, color = Muted)
                                        Label(code.chunked(3).joinToString(" "), 22, FontWeight.Bold, maxLines = 1)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(d(10))) {
                                        Btn(tr("phone_visible")) { requestDiscoverable(act) }
                                        Btn(tr("phone_newcode")) {
                                            prefs.resetPairCode()
                                            code = prefs.pairCode
                                            Sync.start(prefs.pairCode)
                                        }
                                    }
                                }

                                "remote" -> {
                                    PanelTitle(tr("set_remote"))
                                    Label(tr("remote_hint"), 13, color = Muted)
                                    Choice(tr("remote_on"), remoteOn) { remoteOn = !remoteOn; prefs.remoteOn = remoteOn }
                                }

                                "weather" -> {
                                    PanelTitle(tr("weather"))
                                    Label("${tr("city_title")}: $city", 16, maxLines = 1)
                                    Btn(tr("city_title")) { sub = "city" }
                                }

                                "iptv" -> {
                                    PanelTitle(tr("iptv"))
                                    Label(prefs.playlistUrl.ifBlank { "—" }, 12, color = Muted, maxLines = 2)
                                    Btn(tr("playlist")) { sub = "playlist" }
                                }

                                "widgets" -> {
                                    PanelTitle(tr("widgets"))
                                    Btn(tr("widgets_pick")) { sub = "widgets" }
                                }

                                "saver" -> {
                                    PanelTitle(tr("set_saver"), tr("ss_sub"))
                                    listOf(
                                        "off" to "ss_off",
                                        "auto" to "ss_auto",
                                        "day" to "ss_day",
                                        "evening" to "ss_evening",
                                        "night" to "ss_night",
                                    ).forEach { (id, k) ->
                                        Choice(tr(k), ssMode == id) { ssMode = id; prefs.ssMode = id }
                                    }
                                    if (ssMode != "off") {
                                        Label(tr("ss_time"), 14, FontWeight.SemiBold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(d(8))) {
                                            listOf(1, 2, 5, 10, 15, 30).forEach { m ->
                                                GlassCard(
                                                    Modifier.weight(1f).height(d(42)),
                                                    onClick = { ssMin = m; prefs.ssMinutes = m },
                                                    selected = ssMin == m,
                                                ) { Label("$m ${tr("ss_min")}", 14, maxLines = 1) }
                                            }
                                        }
                                        Btn(tr("ss_preview")) { Look.saver = true; onClose() }
                                    }
                                    Spacer(Modifier.height(d(6)))
                                    Label(tr("ss_system"), 14, FontWeight.SemiBold)
                                    Label(tr("ss_system_hint"), 12, color = Muted)
                                    Btn(tr("ss_system_btn")) { ssMsg = tr("ss_res_" + SystemSaver.install(act, ssMin)) }
                                    if (ssMsg.isNotEmpty()) Label(ssMsg, 13, color = Good)
                                    Label(SystemSaver.ADB, 11, color = Muted)
                                }

                                "look" -> {
                                    PanelTitle(tr("set_look"))
                                    listOf(
                                        "auto" to "wp_auto",
                                        "morning" to "wp_morning",
                                        "day" to "wp_day",
                                        "evening" to "wp_evening",
                                        "night" to "wp_night",
                                    ).forEach { (id, k) ->
                                        Choice(tr(k), Look.wallpaper == id) { Look.wallpaper = id; prefs.wallpaper = id }
                                    }
                                }

                                "lock" -> {
                                    PanelTitle(tr("qs_lock"))
                                    Label(if (hasPin) tr("lock_on") else tr("lock_off"), 15, color = if (hasPin) Good else Muted)
                                    Btn(if (hasPin) tr("lock_change") else tr("lock_enable")) { sub = "pin" }
                                    if (hasPin) {
                                        Btn(tr("lock_now")) { onClose(); act.locked.value = true }
                                        Btn(tr("lock_remove"), tint = Bad) { prefs.clearPin(); hasPin = false }
                                    }
                                }

                                "system" -> {
                                    PanelTitle(tr("set_system"))
                                    listOf(
                                        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
                                        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
                                        tr("qs_display") to Settings.ACTION_DISPLAY_SETTINGS,
                                        tr("qs_sound") to Settings.ACTION_SOUND_SETTINGS,
                                        tr("sys_apps") to Settings.ACTION_APPLICATION_SETTINGS,
                                        tr("sys_home") to Settings.ACTION_HOME_SETTINGS,
                                        tr("sys_all") to Settings.ACTION_SETTINGS,
                                    ).forEach { (name, action) ->
                                        Btn(name, Modifier.fillMaxWidth()) { act.open(Intent(action)) }
                                    }
                                }

                                "about" -> {
                                    PanelTitle("Своё ТВ", if (ver.isNotEmpty()) "v$ver" else null)
                                    Btn(tr("redo_setup")) {
                                        prefs.onboarded = false
                                        Look.redo++
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }
        }

        when (sub) {
            "city" -> CityDialog(prefs) { sub = null; city = prefs.city }
            "playlist" -> PlaylistDialog(prefs, { sub = null }) { onChannels(it) }
            "widgets" -> WidgetsDialog(widgets, onWidgets) { sub = null }
            "pin" -> Overlay({ sub = null }) { PinSetup(prefs) { sub = null; hasPin = prefs.hasPin } }
        }
    }
}
