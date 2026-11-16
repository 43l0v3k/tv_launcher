package ru.svoe.tv

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Что лежит в закрепе: приложение или встроенная плитка. */
data class Tl(val id: String, val label: String, val icon: Bitmap?, val kind: String?, val app: AppItem?)

private val KNOWN = listOf("кинопоиск", "kinopoisk", "incy", "youtube", "vk", "rutube")

@Composable
fun Home(act: MainActivity, prefs: Prefs) {
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    val tick = act.tick.intValue
    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(act) } }

    var pinned by remember { mutableStateOf(prefs.pinned) }
    var recents by remember { mutableStateOf(prefs.recents) }
    var widgets by remember { mutableStateOf(prefs.widgets) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }

    var overlay by remember { mutableStateOf<String?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Int?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(false) }
    var focusedPin by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        channels = Playlist.readCached(act)
        val url = prefs.playlistUrl
        if (url.isNotBlank()) runCatching { channels = Playlist.download(act, url) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            loadWeather(prefs)
            loadFinance()
            delay(15 * 60_000L)
        }
    }
    // Первый запуск: закрепляем то, что обычно стоит на ТВ
    LaunchedEffect(apps) {
        if (apps.isNotEmpty() && !prefs.autopinned) {
            if (pinned.isEmpty()) {
                val found = KNOWN.mapNotNull { k -> apps.firstOrNull { it.label.lowercase().contains(k) }?.pkg }.distinct()
                pinned = found + "builtin:iptv"
                prefs.pinned = pinned
            }
            prefs.autopinned = true
        }
    }
    LaunchedEffect(act.menuTick.intValue) {
        if (act.menuTick.intValue > 0) {
            if (overlay == "panel") overlay = null else if (overlay == null) overlay = "panel"
        }
    }

    fun resolve(id: String): Tl? = when (id) {
        "builtin:iptv" -> Tl(id, tr("iptv"), null, "tv", null)
        "builtin:settings" -> Tl(id, tr("qs_settings"), null, "gear", null)
        else -> apps.firstOrNull { it.pkg == id }?.let { Tl(id, it.label, it.icon, null, it) }
    }

    fun openApp(a: AppItem) {
        prefs.pushRecent(a.pkg)
        recents = prefs.recents
        act.open(a.intent)
    }

    fun run(t: Tl) {
        when (t.id) {
            "builtin:iptv" -> overlay = "iptv"
            "builtin:settings" -> overlay = "settings"
            else -> t.app?.let { openApp(it) }
        }
    }

    val interactive = overlay == null && menuId == null && playing == null && !pinDialog

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalInteractive provides interactive) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = d(36), vertical = d(22)),
                verticalArrangement = Arrangement.spacedBy(d(14)),
            ) {
                // ---------------- шапка
                Row(
                    Modifier.fillMaxWidth().height(d(92)),
                    horizontalArrangement = Arrangement.spacedBy(d(14)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label("Своё ТВ", 28, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.width(d(112)))
                    PhoneCard(Modifier.width(d(200)).fillMaxHeight().enter(0))
                    NotifSlot(Modifier.weight(1f).fillMaxHeight().enter(1))
                    WeatherCard(Modifier.width(d(150)).fillMaxHeight().enter(2)) { overlay = "city" }
                    ClockBlock(Modifier.enter(3)) { overlay = "panel" }
                }

                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d(22))) {
                    // ---------------- левая часть
                    Column(Modifier.weight(1.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(d(8))) {
                        Label(tr("pinned"), 14, color = Muted)
                        LazyRow(
                            Modifier.fillMaxWidth().height(d(136)),
                            horizontalArrangement = Arrangement.spacedBy(d(14)),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = d(8), vertical = d(6)),
                        ) {
                            val items = pinned.mapNotNull { resolve(it) }
                            itemsIndexed(items, key = { _, t -> t.id }) { i, t ->
                                PinTile(
                                    t, focusedPin == t.id, i == 0, Modifier.enter(4 + i), { run(t) }, { menuId = t.id },
                                ) { f -> if (f) focusedPin = t.id else if (focusedPin == t.id) focusedPin = null }
                            }
                            item(key = "add") {
                                GlassCard(Modifier.size(d(78)).enter(9), onClick = { overlay = "apps" }) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Ico("grid", d(28))
                                        Spacer(Modifier.height(d(4)))
                                        Label(tr("all_label"), 11, maxLines = 1)
                                    }
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth().height(d(50)).enter(10), horizontalArrangement = Arrangement.spacedBy(d(10))) {
                            GlassCard(
                                Modifier.weight(1f).fillMaxHeight(),
                                onClick = { voice = false; overlay = "search" },
                                shape = RoundedCornerShape(d(26)),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Row(Modifier.padding(horizontal = d(18)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(12))) {
                                    Ico("search", d(22), Muted)
                                    Label(tr("search"), 18, color = Muted, maxLines = 1)
                                }
                            }
                            GlassCard(
                                Modifier.fillMaxHeight().aspectRatio(1f),
                                onClick = { voice = true; overlay = "search" },
                                shape = CircleShape,
                            ) { Ico("mic", d(24)) }
                        }

                        Label(tr("recent"), 14, color = Muted)
                        val rec = recents.mapNotNull { resolve(it) }.take(3)
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d(14))) {
                            if (rec.isEmpty()) {
                                GlassCard(Modifier.weight(1f).fillMaxHeight().enter(11)) {
                                    Label(tr("recent_empty"), 14, color = Muted, align = TextAlign.Center, modifier = Modifier.padding(d(16)))
                                }
                            } else {
                                rec.forEachIndexed { i, t ->
                                    RecentCard(t, Modifier.weight(1f).fillMaxHeight().enter(11 + i)) { run(t) }
                                }
                                repeat(3 - rec.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    // ---------------- виджеты
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(d(8))) {
                        Label(tr("widgets"), 14, color = Muted)
                        val cards = widgets.filter { it in listOf("fin", "fx", "media", "cal") } + "add"
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(d(10))) {
                            cards.chunked(2).forEachIndexed { r, row ->
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d(10))) {
                                    row.forEachIndexed { c, kind ->
                                        val m = Modifier.weight(1f).fillMaxHeight().enter(8 + r * 2 + c)
                                        when (kind) {
                                            "fin" -> FinCard(m)
                                            "fx" -> FxCard(m)
                                            "media" -> MediaCard(m, act)
                                            "cal" -> CalCard(m)
                                            else -> GlassCard(m, onClick = { overlay = "widgets" }) { Ico("plus", d(28)) }
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(6))) {
                            SourceBadge("tv")
                            Label(tr("from_tv"), 10, color = Muted, maxLines = 1)
                            Spacer(Modifier.width(d(6)))
                            SourceBadge("phone")
                            Label(tr("from_phone"), 10, color = Muted, maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---------------- оверлеи
        when (overlay) {
            "apps" -> CompositionLocalProvider(LocalInteractive provides (menuId == null)) {
                AppsOverlay(
                    apps, pinned,
                    onClose = { overlay = null },
                    onLaunch = { openApp(it); overlay = null },
                    onMenu = { menuId = it.pkg },
                )
            }
            "search" -> SearchOverlay(
                apps, channels, prefs.region, voice,
                onClose = { overlay = null },
                onApp = { openApp(it); overlay = null },
                onChannel = { playing = it },
            )
            "iptv" -> IptvOverlay(channels, { overlay = null }, { playing = it }, { overlay = "playlist" })
            "playlist" -> PlaylistDialog(prefs, { overlay = "iptv" }) { channels = it }
            "city" -> CityDialog(prefs) { overlay = null }
            "widgets" -> WidgetsDialog(widgets, { widgets = it; prefs.widgets = it }, { overlay = null })
            "panel" -> ControlPanel(
                act, { overlay = null },
                { if (prefs.hasPin) act.locked.value = true else pinDialog = true },
                { overlay = "settings" },
            )
            "settings" -> SettingsScreen(
                act, prefs, widgets,
                { widgets = it; prefs.widgets = it },
                { channels = it },
            ) { overlay = null }
        }

        val mid = menuId
        val mt = if (mid != null) resolve(mid) else null
        if (mid != null && mt != null) {
            val isPinned = mid in pinned
            AppMenu(
                mt, isPinned,
                onClose = { menuId = null },
                onPinToggle = {
                    pinned = if (isPinned) pinned - mid else pinned + mid
                    prefs.pinned = pinned
                    menuId = null
                },
                onMove = { pinned = move(pinned, mid, it); prefs.pinned = pinned },
            )
        }

        if (pinDialog) Overlay({ pinDialog = false }) { PinSetup(prefs) { pinDialog = false } }

        playing?.let { PlayerScreen(channels, it) { playing = null } }
    }
}

// ---------------------------------------------------------------- Плитки

@Composable
fun TileIcon(t: Tl, size: androidx.compose.ui.unit.Dp) {
    if (t.icon != null) Image(t.icon.asImageBitmap(), null, Modifier.size(size))
    else Ico(t.kind ?: "grid", size)
}

@Composable
fun PinTile(
    t: Tl,
    big: Boolean,
    autoFocus: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLong: () -> Unit,
    onFocus: (Boolean) -> Unit,
) {
    // Большая плитка = та, на которую навёлся сейчас
    val k by animateFloatAsState(if (big) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 380f), label = "pin")
    GlassCard(
        modifier.size(d(78 + 40 * k)),
        onClick = onClick,
        onLongClick = onLong,
        autoFocus = autoFocus,
        focusScale = 1.03f,
        onFocus = onFocus,
    ) {
        Column(Modifier.padding(d(6)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            TileIcon(t, d(36 + 22 * k))
            Spacer(Modifier.height(d(4 + 4 * k)))
            Label(t.label, 9 + 6 * k, if (big) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, align = TextAlign.Center)
        }
    }
}

@Composable
fun RecentCard(t: Tl, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d(6))) {
            TileIcon(t, d(52))
            Label(t.label, 15, FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(4))) {
                Ico("play", d(11), Accent)
                Label(tr("open"), 11, color = Accent, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- Шапка

@Composable
fun Battery(level: Int) {
    val col = if (level <= 20) Bad else Good
    Canvas(Modifier.size(d(26), d(12))) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color.White.copy(alpha = 0.8f), Offset.Zero, Size(w * 0.9f, h), CornerRadius(h * 0.25f), style = Stroke(1.2f * density))
        drawRoundRect(col, Offset(w * 0.06f, h * 0.16f), Size((w * 0.9f - w * 0.12f) * level.coerceIn(0, 100) / 100f, h * 0.68f), CornerRadius(h * 0.15f))
        drawRect(Color.White.copy(alpha = 0.8f), Offset(w * 0.92f, h * 0.3f), Size(w * 0.08f, h * 0.4f))
    }
}

@Composable
fun PhoneCard(modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize().padding(d(12)), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(tr("phone_link"), 11, color = Muted, maxLines = 1, modifier = Modifier.weight(1f))
                SourceBadge("phone")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(10))) {
                val on = Sync.connected.value
                Ico("phone", d(34), if (on) TextC else Muted)
                Column {
                    if (on) {
                        Label(Sync.phoneName.value.ifBlank { "Phone" }, 15, FontWeight.SemiBold, maxLines = 1)
                        val b = Sync.battery.intValue
                        if (b >= 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(6))) {
                            Battery(b)
                            Label("$b%", 13, maxLines = 1)
                        }
                    } else {
                        Label(tr("phone_none"), 12, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun NotifSlot(modifier: Modifier) {
    val call = Sync.call.value
    val toast = Sync.toast.value
    val prog = remember { Animatable(1f) }
    LaunchedEffect(toast) {
        if (toast != null) {
            prog.snapTo(1f)
            prog.animateTo(0f, tween(10_000, easing = LinearEasing))
            if (Sync.toast.value === toast) Sync.toast.value = null
        }
    }
    val now = System.currentTimeMillis()
    val ev = Sync.events.firstOrNull { it.end > now || it.allDay }
    val key = when {
        call != null -> "call"
        toast != null -> "n${toast.time}"
        ev != null -> "e"
        else -> "none"
    }
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(tween(450)) { -it / 2 } + fadeIn(tween(450))) togetherWith
                (slideOutVertically(tween(300)) { it / 2 } + fadeOut(tween(250)))
        },
        label = "notif",
    ) { k ->
        val c = Sync.call.value
        val t = Sync.toast.value
        when {
            k == "call" && c != null -> CallCard(c)
            k.startsWith("n") && t != null -> GlassCard(Modifier.fillMaxSize(), onClick = { Sync.toast.value = null }) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = d(14), vertical = d(10)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d(12)),
                ) {
                    Box(Modifier.size(d(46)).background(Color(0x40FFFFFF), RoundedCornerShape(d(12))), contentAlignment = Alignment.Center) {
                        Label(t.app.take(1).uppercase().ifEmpty { "?" }, 22, FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Label("${t.app} • ${t.title}", 13, color = Accent, maxLines = 1)
                        Label(t.text, 19, FontWeight.SemiBold, maxLines = 2)
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(prog.value.coerceIn(0.001f, 1f)).height(d(3)).background(Accent))
                SourceBadge("phone", Modifier.align(Alignment.TopEnd).padding(d(8)))
            }
            k == "e" && ev != null -> GlassCard(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = d(16)), verticalArrangement = Arrangement.spacedBy(d(3))) {
                    Label(
                        if (ev.allDay) tr("w_cal") else SimpleDateFormat("EEE, HH:mm", Locale(L.lang)).format(Date(ev.start)),
                        12, color = Accent, maxLines = 1,
                    )
                    Label(ev.title.ifBlank { "—" }, 19, FontWeight.SemiBold, maxLines = 2)
                }
                SourceBadge("phone", Modifier.align(Alignment.TopEnd).padding(d(8)))
            }
            else -> GlassCard(Modifier.fillMaxSize()) { Label(tr("no_notifs"), 15, color = Muted, align = TextAlign.Center) }
        }
    }
}

@Composable
fun CallCard(who: String) {
    val inf = rememberInfiniteTransition(label = "ring")
    val s = inf.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s",
    )
    GlassCard(Modifier.fillMaxSize(), tint = Good) {
        Row(Modifier.padding(horizontal = d(18)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(16))) {
            Box(Modifier.size(d(46)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(d(36)).graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = 2f - s.value }.background(Good.copy(alpha = 0.45f), CircleShape))
                Ico("phone", d(26), Color.White)
            }
            Column {
                Label(tr("incoming"), 12, color = Color(0xFFB8F5CF), maxLines = 1)
                Label(who, 24, FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun WeatherCard(modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(d(12)), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(tr("weather"), 11, color = Muted, maxLines = 1, modifier = Modifier.weight(1f))
                SourceBadge("tv")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(8))) {
                Ico(weatherIcon(WeatherData.code), d(34), Color(0xFFFFE08A))
                Label(WeatherData.temp?.let { "$it°" } ?: "—", 30, FontWeight.Light, maxLines = 1)
            }
            val w = WeatherData.wind
            Label(
                (if (w != null) "${tr("wind")} $w ${tr("ms")} · " else "") + WeatherData.city,
                10, color = Muted, maxLines = 1,
            )
        }
    }
}

@Composable
fun ClockBlock(modifier: Modifier, onPanel: () -> Unit) {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.US).format(now)
    val date = SimpleDateFormat("d MMMM", Locale(L.lang)).format(now)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(12))) {
        GlassCard(Modifier.size(d(44)), onClick = onPanel, shape = CircleShape) { Ico("grid", d(20)) }
        Column {
            AnimatedContent(
                targetState = time,
                transitionSpec = {
                    (slideInVertically(tween(350)) { it } + fadeIn(tween(350))) togetherWith
                        (slideOutVertically(tween(350)) { -it } + fadeOut(tween(250)))
                },
                label = "time",
            ) { t -> Label(t, 40, FontWeight.Light, maxLines = 1) }
            Label(date, 14, color = Muted, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Виджеты

private fun grouped(v: Float, dec: Int = 0): String =
    String.format(Locale.US, "%,.${dec}f", v).replace(',', ' ')

@Composable
fun FinRow(name: String, values: List<Float>, prefix: String, modifier: Modifier) {
    val last = values.lastOrNull()
    val up = last != null && values.isNotEmpty() && last >= values.first()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(8))) {
        Column(Modifier.width(d(64))) {
            Label(name, 10, color = Muted, maxLines = 1)
            Label(if (last != null) prefix + grouped(last) else "—", 13, FontWeight.SemiBold, maxLines = 1)
        }
        Sparkline(values, Modifier.weight(1f).fillMaxHeight(), if (up) Good else Bad)
    }
}

@Composable
fun FinCard(modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = d(12), vertical = d(10)), verticalArrangement = Arrangement.spacedBy(d(4))) {
            FinRow("IMOEX", FinData.imoex, "", Modifier.weight(1f).fillMaxWidth())
            FinRow("BTC", FinData.btc, "$", Modifier.weight(1f).fillMaxWidth())
        }
        SourceBadge("tv", Modifier.align(Alignment.TopEnd).padding(d(6)))
    }
}

@Composable
fun FxCard(modifier: Modifier) {
    fun row(sym: String, v: Float?, prev: Float?): Triple<String, String, Color> {
        val txt = if (v != null) String.format(Locale.US, "%.2f ₽", v) else "—"
        val delta = if (v != null && prev != null) v - prev else 0f
        val change = if (delta > 0.004f) "▲ " + String.format(Locale.US, "%.2f", delta)
        else if (delta < -0.004f) "▼ " + String.format(Locale.US, "%.2f", -delta) else ""
        return Triple("$sym  $txt", change, if (delta >= 0f) Good else Bad)
    }
    val usd = row("$", FinData.usd, FinData.usdPrev)
    val gbp = row("£", FinData.gbp, FinData.gbpPrev)
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = d(12), vertical = d(10)), verticalArrangement = Arrangement.SpaceEvenly) {
            for (r in listOf(usd, gbp)) {
                Column {
                    Label(r.first, 15, FontWeight.SemiBold, maxLines = 1)
                    Label(r.second.ifEmpty { " " }, 10, color = r.third, maxLines = 1)
                }
            }
        }
        SourceBadge("tv", Modifier.align(Alignment.TopEnd).padding(d(6)))
    }
}

@Composable
fun MediaCard(modifier: Modifier, act: MainActivity) {
    val has = Media.hasAccess.value
    // играет телефон, а ТВ молчит — показываем телефон
    val phoneSrc = Sync.pmTitle.value.isNotEmpty() && !Media.playing.value &&
        (Sync.pmPlaying.value || Media.title.value.isEmpty())
    val mTitle = if (phoneSrc) Sync.pmTitle.value else Media.title.value
    val mArtist = if (phoneSrc) Sync.pmArtist.value else Media.artist.value
    val mDur = if (phoneSrc) Sync.pmDur.longValue else Media.duration.longValue
    val mPlaying = if (phoneSrc) Sync.pmPlaying.value else Media.playing.value
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phoneSrc, mPlaying, mTitle) {
        while (true) {
            pos = if (phoneSrc) {
                val p = Sync.pmPos.longValue
                if (Sync.pmPlaying.value) p + (android.os.SystemClock.elapsedRealtime() - Sync.pmAt) else p
            } else Media.position()
            delay(1000)
        }
    }
    GlassCard(
        modifier,
        onClick = if (!has && !phoneSrc) ({ act.open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) else null,
    ) {
        if (!has && !phoneSrc) {
            Label(tr("media_grant"), 12, color = Muted, align = TextAlign.Center, modifier = Modifier.padding(d(12)))
        } else if (mTitle.isEmpty()) {
            Label(tr("media_none"), 13, color = Muted, align = TextAlign.Center)
        } else {
            Column(Modifier.fillMaxSize().padding(d(12)), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Label(mTitle, 14, FontWeight.SemiBold, maxLines = 1)
                    Label(mArtist.ifEmpty { " " }, 11, color = Muted, maxLines = 1)
                }
                val dur = mDur
                Box(Modifier.fillMaxWidth().height(d(4)).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
                    val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0.001f, 1f) else 0.001f
                    Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(TextC, RoundedCornerShape(50)))
                }
                if (phoneSrc) {
                    Label(if (mPlaying) tr("media_phone") else tr("media_phone_paused"), 11, color = Muted, maxLines = 1)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    GlassCard(Modifier.size(d(30)), onClick = { Media.prev() }, shape = CircleShape, focusScale = 1.15f) { Ico("prev", d(14)) }
                    GlassCard(Modifier.size(d(34)), onClick = { Media.toggle() }, shape = CircleShape, focusScale = 1.15f) {
                        Ico(if (Media.playing.value) "pause" else "play", d(16))
                    }
                    GlassCard(Modifier.size(d(30)), onClick = { Media.next() }, shape = CircleShape, focusScale = 1.15f) { Ico("next", d(14)) }
                }
            }
        }
        SourceBadge(if (phoneSrc) "phone" else "tv", Modifier.align(Alignment.TopEnd).padding(d(6)))
    }
}

@Composable
fun CalCard(modifier: Modifier) {
    val now = System.currentTimeMillis()
    val list = Sync.events.filter { it.end > now || it.allDay }.take(3)
    GlassCard(modifier) {
        if (list.isEmpty()) {
            Label(tr("no_events"), 13, color = Muted, align = TextAlign.Center)
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = d(12), vertical = d(10)), verticalArrangement = Arrangement.SpaceEvenly) {
                list.forEach { e ->
                    Column {
                        Label(if (e.allDay) "—" else SimpleDateFormat("EEE HH:mm", Locale(L.lang)).format(Date(e.start)), 10, color = Accent, maxLines = 1)
                        Label(e.title.ifBlank { "—" }, 12, FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        SourceBadge("phone", Modifier.align(Alignment.TopEnd).padding(d(6)))
    }
}
