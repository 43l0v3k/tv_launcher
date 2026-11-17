package ru.svoe.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Оверлей

/** Затемнение + плавное появление контента. «Назад» закрывает. */
@Composable
fun Overlay(
    onClose: () -> Unit,
    align: Alignment = Alignment.Center,
    scrim: Float = 0.6f,
    origin: TransformOrigin = TransformOrigin.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 320f)) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = a.value.coerceIn(0f, 1f) }
                .background(Color(0xFF050814).copy(alpha = scrim)),
        )
        Box(Modifier.fillMaxSize().padding(d(26)), contentAlignment = align) {
            Box(
                Modifier.graphicsLayer {
                    val s = 0.9f + 0.1f * a.value
                    scaleX = s
                    scaleY = s
                    alpha = a.value.coerceIn(0f, 1f)
                    transformOrigin = origin
                },
            ) { content() }
        }
    }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, tint: Color = Color.White, autoFocus: Boolean = false, onClick: () -> Unit) {
    GlassCard(modifier.height(d(44)).widthIn(min = d(120)), onClick = onClick, tint = tint, autoFocus = autoFocus) {
        Label(text, 16, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = d(16)))
    }
}

@Composable
fun TextBox(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    focus: FocusRequester? = null,
    ime: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
) {
    Box(modifier.background(Color(0x40000000), RoundedCornerShape(d(12))).padding(horizontal = d(14), vertical = d(11))) {
        if (value.isEmpty() && hint.isNotEmpty()) Label(hint, 18, color = Muted, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TextC, fontSize = fs(18)),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ime),
            keyboardActions = KeyboardActions(onDone = { onAction() }, onSearch = { onAction() }, onGo = { onAction() }),
            modifier = (if (focus != null) Modifier.focusRequester(focus) else Modifier).fillMaxWidth(),
        )
    }
}

@Composable
fun PanelTitle(text: String, sub: String? = null) {
    Label(text, 22, FontWeight.SemiBold, maxLines = 1)
    if (sub != null) Label(sub, 12, color = Muted, maxLines = 1)
}

// ---------------------------------------------------------------- Все приложения

@Composable
fun AppsOverlay(
    apps: List<AppItem>,
    pinned: List<String>,
    onClose: () -> Unit,
    onLaunch: (AppItem) -> Unit,
    onMenu: (AppItem) -> Unit,
) {
    Overlay(onClose) {
        GlassCard(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f), shape = RoundedCornerShape(d(28))) {
            Column(Modifier.fillMaxSize().padding(d(22))) {
                PanelTitle(tr("all_apps"), tr("pick_to_pin"))
                Spacer(Modifier.height(d(8)))
                LazyVerticalGrid(
                    GridCells.Adaptive(d(104)),
                    horizontalArrangement = Arrangement.spacedBy(d(12)),
                    verticalArrangement = Arrangement.spacedBy(d(12)),
                    contentPadding = PaddingValues(d(8)),
                ) {
                    itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                        GlassCard(
                            Modifier.height(d(98)),
                            onClick = { onLaunch(app) },
                            onLongClick = { onMenu(app) },
                            selected = app.pkg in pinned,
                            autoFocus = i == 0,
                        ) {
                            Column(Modifier.padding(d(6)), horizontalAlignment = Alignment.CenterHorizontally) {
                                app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(d(46))) }
                                Spacer(Modifier.height(d(6)))
                                Label(app.label, 11, maxLines = 1, align = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Меню приложения

@Composable
fun AppMenu(
    t: Tl,
    isPinned: Boolean,
    onClose: () -> Unit,
    onPinToggle: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    Overlay(onClose) {
        GlassCard(Modifier.width(d(360)), shape = RoundedCornerShape(d(24))) {
            Column(Modifier.padding(d(20)), verticalArrangement = Arrangement.spacedBy(d(10))) {
                Label(t.label, 20, FontWeight.SemiBold, maxLines = 1)
                Btn(if (isPinned) tr("unpin") else tr("pin_app"), Modifier.fillMaxWidth(), autoFocus = true) { onPinToggle() }
                if (isPinned) {
                    Row(horizontalArrangement = Arrangement.spacedBy(d(10))) {
                        Btn("←", Modifier.weight(1f)) { onMove(-1) }
                        Btn("→", Modifier.weight(1f)) { onMove(1) }
                    }
                }
                if (t.app != null) {
                    Btn(tr("app_info"), Modifier.fillMaxWidth()) {
                        ctx.open(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${t.app.pkg}")))
                        onClose()
                    }
                    Btn(tr("uninstall"), Modifier.fillMaxWidth(), tint = Bad) {
                        ctx.open(Intent(Intent.ACTION_DELETE, Uri.parse("package:${t.app.pkg}")))
                        onClose()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Поиск

@Composable
fun SearchOverlay(
    apps: List<AppItem>,
    channels: List<Channel>,
    region: String,
    autoVoice: Boolean,
    onClose: () -> Unit,
    onApp: (AppItem) -> Unit,
    onChannel: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (t != null) q = t
        }
    }
    fun runVoice() {
        try {
            voice.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
            )
        } catch (_: Throwable) {
            note = tr("voice_na")
        }
    }
    LaunchedEffect(Unit) {
        if (autoVoice) runVoice() else runCatching { fr.requestFocus() }
    }

    val ql = q.trim().lowercase()
    val appRes = if (ql.isEmpty()) emptyList() else apps.filter { it.label.lowercase().contains(ql) }.take(5)
    val chRes = if (ql.isEmpty()) emptyList() else channels.withIndex().filter { it.value.name.lowercase().contains(ql) }.take(3)

    fun web() {
        val base = if (region == "us") "https://www.google.com/search?q=" else "https://yandex.ru/search/?text="
        ctx.open(Intent(Intent.ACTION_VIEW, Uri.parse(base + Uri.encode(q.trim()))))
        onClose()
    }

    fun first() {
        when {
            appRes.isNotEmpty() -> onApp(appRes[0])
            chRes.isNotEmpty() -> onChannel(chRes[0].index)
            q.isNotBlank() -> web()
        }
    }

    Overlay(onClose) {
        GlassCard(Modifier.width(d(640)).height(d(430)), shape = RoundedCornerShape(d(28))) {
            Column(Modifier.fillMaxSize().padding(d(20)), verticalArrangement = Arrangement.spacedBy(d(12))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(12))) {
                    Ico("search", d(26), Muted)
                    TextBox(
                        q, { q = it }, Modifier.weight(1f), tr("search"), fr, ImeAction.Search,
                    ) { first() }
                    GlassCard(Modifier.size(d(44)), onClick = { runVoice() }, shape = CircleShape) { Ico("mic", d(22)) }
                }
                if (note.isNotEmpty()) Label(note, 13, color = Bad)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d(8)), contentPadding = PaddingValues(d(6))) {
                    items(appRes.size) { i ->
                        val a = appRes[i]
                        GlassCard(Modifier.fillMaxWidth().height(d(46)), onClick = { onApp(a) }, contentAlignment = Alignment.CenterStart) {
                            Row(Modifier.padding(horizontal = d(14)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(12))) {
                                a.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(d(28))) }
                                Label(a.label, 16, maxLines = 1)
                            }
                        }
                    }
                    items(chRes.size) { i ->
                        val c = chRes[i]
                        GlassCard(Modifier.fillMaxWidth().height(d(46)), onClick = { onChannel(c.index) }, contentAlignment = Alignment.CenterStart) {
                            Row(Modifier.padding(horizontal = d(14)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(12))) {
                                Ico("tv", d(26))
                                Label("${c.index + 1}  ${c.value.name}", 16, maxLines = 1)
                            }
                        }
                    }
                    if (q.isNotBlank()) {
                        item {
                            GlassCard(Modifier.fillMaxWidth().height(d(46)), onClick = { web() }, contentAlignment = Alignment.CenterStart) {
                                Row(Modifier.padding(horizontal = d(14)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(12))) {
                                    Ico("search", d(24), Accent)
                                    Label("${tr("web_search")}: ${q.trim()}", 16, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- IPTV

@Composable
fun IptvOverlay(channels: List<Channel>, onClose: () -> Unit, onPlay: (Int) -> Unit, onPlaylist: () -> Unit) {
    Overlay(onClose) {
        GlassCard(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f), shape = RoundedCornerShape(d(28))) {
            Column(Modifier.fillMaxSize().padding(d(22))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(tr("iptv"), 22, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Btn("＋ ${tr("playlist")}", autoFocus = channels.isEmpty()) { onPlaylist() }
                }
                Spacer(Modifier.height(d(8)))
                LazyVerticalGrid(
                    GridCells.Adaptive(d(150)),
                    horizontalArrangement = Arrangement.spacedBy(d(12)),
                    verticalArrangement = Arrangement.spacedBy(d(12)),
                    contentPadding = PaddingValues(d(8)),
                ) {
                    itemsIndexed(channels) { i, ch ->
                        GlassCard(Modifier.height(d(64)), onClick = { onPlay(i) }, autoFocus = i == 0) {
                            Label("${i + 1}  ${ch.name}", 14, align = TextAlign.Center, modifier = Modifier.padding(horizontal = d(8)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDialog(prefs: Prefs, onClose: () -> Unit, onLoaded: (List<Channel>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(prefs.playlistUrl) }
    var status by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(300); runCatching { fr.requestFocus() } }
    Overlay(onClose) {
        GlassCard(Modifier.width(d(580)), shape = RoundedCornerShape(d(24))) {
            Column(Modifier.padding(d(22)), verticalArrangement = Arrangement.spacedBy(d(12))) {
                Label(tr("playlist_url"), 20, FontWeight.SemiBold)
                TextBox(url, { url = it }, Modifier.fillMaxWidth(), "https://…", fr)
                if (status.isNotEmpty()) Label(status, 13, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(d(10))) {
                    Btn(tr("load"), tint = Accent) {
                        status = tr("loading")
                        scope.launch {
                            runCatching { Playlist.download(ctx, url.trim()) }
                                .onSuccess {
                                    if (it.isEmpty()) status = tr("no_channels")
                                    else { prefs.playlistUrl = url.trim(); onLoaded(it); onClose() }
                                }
                                .onFailure {
                                    status = if (it is Playlist.PlaylistError) it.message ?: ""
                                    else "${tr("pl_fail")}: ${it.javaClass.simpleName}"
                                }
                        }
                    }
                    Btn(tr("pl_example")) { url = "https://iptv-org.github.io/iptv/languages/rus.m3u" }
                    Btn(tr("cancel")) { onClose() }
                }
                Label(tr("pl_hint"), 11, color = Muted)
            }
        }
    }
}

@Composable
fun PlayerScreen(channels: List<Channel>, start: Int, onClose: () -> Unit) {
    if (channels.isEmpty()) return
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    var idx by remember { mutableIntStateOf(start.coerceIn(0, channels.size - 1)) }
    var showName by remember { mutableStateOf(true) }
    var buffering by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val vlc = remember { LibVLC(ctx.applicationContext, arrayListOf("--network-caching=1500", "--http-reconnect")) }
    val player = remember { MediaPlayer(vlc) }
    DisposableEffect(Unit) {
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Buffering -> buffering = e.buffering.toInt()
                MediaPlayer.Event.Playing -> { failed = false; buffering = 100 }
                MediaPlayer.Event.EncounteredError -> failed = true
            }
        }
        onDispose {
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { vlc.release() }
        }
    }

    LaunchedEffect(idx) {
        failed = false
        buffering = 0
        runCatching {
            val m = Media(vlc, Uri.parse(channels[idx].url))
            m.addOption(":network-caching=1500")
            player.media = m
            m.release()
            player.play()
        }.onFailure { failed = true }
        showName = true
        delay(3000)
        showName = false
    }

    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { idx = (idx + 1) % channels.size; true }
                    Key.DirectionDown, Key.ChannelDown -> { idx = (idx - 1 + channels.size) % channels.size; true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { c -> VLCVideoLayout(c).also { player.attachViews(it, null, false, false) } },
            modifier = Modifier.fillMaxSize(),
        )
        if (showName) {
            Box(Modifier.padding(d(32)).background(Color(0xAA000000), RoundedCornerShape(d(10))).padding(d(14))) {
                Label("${idx + 1}. ${channels[idx].name}", 24, FontWeight.SemiBold)
            }
        }
        if (failed) {
            Box(Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(d(10))).padding(d(18))) {
                Label(tr("pl_open_fail"), 20, FontWeight.SemiBold)
            }
        } else if (buffering in 0..99) {
            Box(Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(d(10))).padding(d(14))) {
                Label("${tr("loading")} $buffering%", 18)
            }
        }
    }
}

private val ALL_WIDGETS = listOf("fin" to "w_fin", "fx" to "w_fx", "media" to "w_media", "cal" to "w_cal")

@Composable
fun CityDialog(prefs: Prefs, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf(prefs.city) }
    var status by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(300); runCatching { fr.requestFocus() } }
    fun save() {
        scope.launch {
            val r = geocode(q.trim())
            if (r == null) status = tr("city_fail")
            else {
                prefs.city = r.first; prefs.lat = r.second; prefs.lon = r.third
                loadWeather(prefs)
                onClose()
            }
        }
    }
    Overlay(onClose) {
        GlassCard(Modifier.width(d(480)), shape = RoundedCornerShape(d(24))) {
            Column(Modifier.padding(d(22)), verticalArrangement = Arrangement.spacedBy(d(12))) {
                Label(tr("city_title"), 20, FontWeight.SemiBold)
                TextBox(q, { q = it }, Modifier.fillMaxWidth(), "", fr, ImeAction.Done) { save() }
                if (status.isNotEmpty()) Label(status, 13, color = Bad)
                Row(horizontalArrangement = Arrangement.spacedBy(d(10))) {
                    Btn(tr("save"), tint = Accent) { save() }
                    Btn(tr("cancel")) { onClose() }
                }
            }
        }
    }
}

@Composable
fun WidgetsDialog(enabled: List<String>, onChange: (List<String>) -> Unit, onClose: () -> Unit) {
    Overlay(onClose) {
        GlassCard(Modifier.width(d(460)), shape = RoundedCornerShape(d(24))) {
            Column(Modifier.padding(d(22)), verticalArrangement = Arrangement.spacedBy(d(10))) {
                Label(tr("widgets_pick"), 20, FontWeight.SemiBold)
                ALL_WIDGETS.forEachIndexed { i, (id, key) ->
                    val on = id in enabled
                    GlassCard(
                        Modifier.fillMaxWidth().height(d(46)),
                        onClick = { onChange(ALL_WIDGETS.map { it.first }.filter { w -> if (w == id) !on else w in enabled }) },
                        selected = on,
                        autoFocus = i == 0,
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(Modifier.padding(horizontal = d(16)).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Label(tr(key), 16, maxLines = 1, modifier = Modifier.weight(1f))
                            if (on) Ico("check", d(20), Good)
                        }
                    }
                }
                Btn(tr("back"), Modifier.fillMaxWidth()) { onClose() }
            }
        }
    }
}

// ---------------------------------------------------------------- Панель быстрых настроек

@Composable
fun VolumeSlider() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    fun set(v: Int) {
        vol = v.coerceIn(0, max)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0) }
    }
    val frac by animateFloatAsState(vol / max.toFloat(), spring(dampingRatio = 0.8f, stiffness = 500f), label = "vol")
    GlassCard(
        Modifier.fillMaxWidth().height(d(46)).onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                set(vol + if (e.key == Key.DirectionRight) 1 else -1)
                true
            } else false
        },
        onClick = { set(if (vol > 0) 0 else max / 2) },
    ) {
        Row(Modifier.padding(horizontal = d(14)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(10))) {
            Ico("sound", d(20))
            Box(Modifier.weight(1f).height(d(8)).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.001f, 1f)).fillMaxHeight().background(TextC, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
fun ControlPanel(act: MainActivity, onClose: () -> Unit, onLock: () -> Unit, onSettings: () -> Unit) {
    fun sys(action: String): () -> Unit = { act.open(Intent(action)); onClose() }
    val tiles: List<Triple<String, String, () -> Unit>> = listOf(
        Triple("wifi", "Wi-Fi", sys(android.provider.Settings.ACTION_WIFI_SETTINGS)),
        Triple("bt", "Bluetooth", sys(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)),
        Triple("tv", tr("qs_display"), sys(android.provider.Settings.ACTION_DISPLAY_SETTINGS)),
        Triple("sound", tr("qs_sound"), sys(android.provider.Settings.ACTION_SOUND_SETTINGS)),
        Triple("gear", tr("qs_settings"), { onClose(); onSettings() }),
        Triple("lock", tr("qs_lock"), { onClose(); onLock() }),
    )
    Overlay(onClose, align = Alignment.TopEnd, scrim = 0.35f, origin = TransformOrigin(1f, 0f)) {
        GlassCard(Modifier.width(d(270)), shape = RoundedCornerShape(d(28))) {
            Column(Modifier.padding(d(16)), verticalArrangement = Arrangement.spacedBy(d(10))) {
                tiles.chunked(2).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(d(10))) {
                        row.forEachIndexed { c, (ic, name, run) ->
                            GlassCard(Modifier.weight(1f).height(d(72)), onClick = run, autoFocus = r == 0 && c == 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d(6))) {
                                    Ico(ic, d(26))
                                    Label(name, 12, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                VolumeSlider()
            }
        }
    }
}
