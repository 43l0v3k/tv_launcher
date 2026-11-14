package ru.svoe.tv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF7DB2FF)
private val Muted = Color(0xFFA9B4C8)
private val TextColor = Color(0xFFF2F5FA)
private val Panel = Color(0xFF1C2233)
private val Glass = Color(0x22FFFFFF)
private val GlassFocus = Color(0x55FFFFFF)

data class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Bitmap?)

class MainActivity : ComponentActivity() {
    val tick = mutableIntStateOf(0)

    private val btPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { Sync.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT) else Sync.start()
        setContent { Root(this, prefs) }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++ // обновить список приложений после установки/удаления
    }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    var intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    var list = pm.queryIntentActivities(intent, 0)
    if (list.isEmpty()) {
        intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        list = pm.queryIntentActivities(intent, 0)
    }
    return list
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val pkg = it.activityInfo.packageName
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                ?: Intent(Intent.ACTION_MAIN).setClassName(pkg, it.activityInfo.name)
            AppItem(
                label = it.loadLabel(pm).toString(),
                pkg = pkg,
                intent = launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                icon = runCatching { it.loadIcon(pm).toBitmap(192, 192) }.getOrNull(),
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun move(list: List<String>, pkg: String, d: Int): List<String> {
    val i = list.indexOf(pkg)
    val j = i + d
    if (i < 0 || j < 0 || j >= list.size) return list
    val m = list.toMutableList()
    m.removeAt(i)
    m.add(j, pkg)
    return m
}

fun Context.open(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun greeting(now: Date): String {
    val h = SimpleDateFormat("H", Locale.US).format(now).toInt()
    return when (h) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

@Composable
fun Root(act: MainActivity, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    val tick = act.tick.intValue
    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(act) } }

    var pinned by remember { mutableStateOf(prefs.pinned) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var playing by remember { mutableStateOf<Int?>(null) }
    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var plDialog by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    LaunchedEffect(Unit) {
        channels = Playlist.readCached(act)
        val url = prefs.playlistUrl
        if (url.isNotBlank()) runCatching { channels = Playlist.download(act, url) }
    }

    BackHandler { /* на главном экране «Назад» ничего не делает */ }

    val pinnedApps = pinned.mapNotNull { p -> apps.find { it.pkg == p } }

    val system = listOf(
        "Настройки" to Settings.ACTION_SETTINGS,
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Экран" to Settings.ACTION_DISPLAY_SETTINGS,
        "Звук" to Settings.ACTION_SOUND_SETTINGS,
        "Приложения" to Settings.ACTION_APPLICATION_SETTINGS,
        "Домашний экран" to Settings.ACTION_HOME_SETTINGS,
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0A0F1F), Color(0xFF1B1242), Color(0xFF0B2A3F)))
        )
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Шапка
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Label(SimpleDateFormat("HH:mm", Locale("ru")).format(now), 60, FontWeight.Light)
                        Label(
                            SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(now)
                                .replaceFirstChar { it.uppercase() },
                            18, color = Muted,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Label(greeting(now), 22, FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    PhoneStatus()
                }
            }

            // Главный экран (закреплённые)
            item {
                Section("Главный экран", "удерживай приложение в списке ниже, чтобы закрепить") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(12.dp)) {
                        if (pinnedApps.isEmpty()) {
                            item {
                                FocusCard(360.dp, 160.dp, onClick = {}) {
                                    Label("Пока пусто. Удерживай приложение из «Все приложения», чтобы закрепить", 16, color = Muted, center = true)
                                }
                            }
                        }
                        items(pinnedApps, key = { it.pkg }) { app ->
                            AppTile(app, big = true, onLaunch = { act.open(app.intent) }, onMenu = { menuApp = app })
                        }
                    }
                }
            }

            // Календарь с телефона
            item {
                Section("Календарь", "события с телефона") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(12.dp)) {
                        if (Sync.events.isEmpty()) {
                            item {
                                FocusCard(360.dp, 100.dp, onClick = {}) {
                                    Label(
                                        if (Sync.connected.value) "Ближайших событий нет"
                                        else "Подключи телефон по Bluetooth, события появятся здесь",
                                        15, color = Muted, center = true,
                                    )
                                }
                            }
                        }
                        items(Sync.events.toList()) { ev -> EventCard(ev) }
                    }
                }
            }

            // Уведомления с телефона
            if (Sync.notifs.isNotEmpty()) {
                item {
                    Section("Сообщения", "нажми OK, чтобы скрыть") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(12.dp)) {
                            items(Sync.notifs.toList()) { n -> NotifCard(n) { Sync.notifs.remove(n) } }
                        }
                    }
                }
            }

            // Все приложения
            item {
                Section("Все приложения") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(12.dp)) {
                        items(apps, key = { it.pkg }) { app ->
                            AppTile(app, big = false, onLaunch = { act.open(app.intent) }, onMenu = { menuApp = app })
                        }
                    }
                }
            }

            // IPTV
            item {
                Section("Прямой эфир", "IPTV") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(12.dp)) {
                        item { FocusCard(170.dp, 90.dp, onClick = { plDialog = true }) { Label("＋ Плейлист", 17) } }
                        itemsIndexed(channels) { i, ch ->
                            FocusCard(170.dp, 90.dp, onClick = { playing = i }) {
                                Label("${i + 1}  ${ch.name}", 15, center = true)
                            }
                        }
                    }
                }
            }

            // Система
            item {
                Section("Система") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(12.dp)) {
                        items(system) { (title, action) ->
                            FocusCard(180.dp, 80.dp, onClick = { act.open(Intent(action)) }) { Label(title, 17) }
                        }
                    }
                }
            }
        }

        // Всплывающее уведомление с телефона
        val t = Sync.toast.value
        if (t != null) {
            LaunchedEffect(t) {
                delay(8000)
                if (Sync.toast.value === t) Sync.toast.value = null
            }
            Box(Modifier.align(Alignment.TopEnd).padding(32.dp)) { NotifCard(t, popup = true) { Sync.toast.value = null } }
        }

        // Входящий звонок
        val caller = Sync.call.value
        if (caller != null) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 28.dp)) {
                Column(
                    Modifier.background(Color(0xEE12351F), RoundedCornerShape(20.dp))
                        .border(BorderStroke(2.dp, Color(0xFF4CD27A)), RoundedCornerShape(20.dp))
                        .padding(horizontal = 36.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Label("Входящий звонок", 16, color = Color(0xFF9BE8B6))
                    Label(caller, 30, FontWeight.SemiBold)
                }
            }
        }

        // Плеер IPTV
        playing?.let { start ->
            BackHandler { playing = null }
            PlayerScreen(channels, start)
        }
    }

    // Меню приложения (долгое нажатие)
    menuApp?.let { app ->
        Dialog(onDismissRequest = { menuApp = null }) {
            Column(
                Modifier.background(Panel, RoundedCornerShape(20.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Label(app.label, 22, FontWeight.SemiBold)
                val isPinned = app.pkg in pinned
                MenuBtn(if (isPinned) "Убрать с главного экрана" else "Закрепить на главном экране", autoFocus = true) {
                    pinned = if (isPinned) pinned - app.pkg else pinned + app.pkg
                    prefs.pinned = pinned
                    menuApp = null
                }
                if (isPinned) {
                    MenuBtn("Сдвинуть влево") { pinned = move(pinned, app.pkg, -1); prefs.pinned = pinned }
                    MenuBtn("Сдвинуть вправо") { pinned = move(pinned, app.pkg, 1); prefs.pinned = pinned }
                }
                MenuBtn("О приложении") {
                    act.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                    menuApp = null
                }
                MenuBtn("Удалить") {
                    act.open(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                    menuApp = null
                }
            }
        }
    }

    // Диалог плейлиста
    if (plDialog) {
        var url by remember { mutableStateOf(prefs.playlistUrl) }
        var status by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { plDialog = false }) {
            Column(
                Modifier.background(Panel, RoundedCornerShape(20.dp)).padding(24.dp).widthIn(min = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Label("Ссылка на плейлист (M3U)", 22, FontWeight.SemiBold)
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextColor, fontSize = 17.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF0E1320), RoundedCornerShape(10.dp)).padding(14.dp),
                )
                if (status.isNotEmpty()) Label(status, 14, color = Muted)
                MenuBtn("Загрузить") {
                    status = "Загрузка…"
                    scope.launch {
                        runCatching { Playlist.download(act, url.trim()) }
                            .onSuccess {
                                if (it.isEmpty()) status = "В плейлисте нет каналов"
                                else { channels = it; prefs.playlistUrl = url.trim(); plDialog = false }
                            }
                            .onFailure { status = "Ошибка: ${it.message}" }
                    }
                }
                MenuBtn("Отмена") { plDialog = false }
            }
        }
    }
}

@Composable
fun PhoneStatus() {
    val on = Sync.connected.value
    Row(
        Modifier.background(Glass, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(if (on) Color(0xFF4CD27A) else Color(0xFF8A93A6), CircleShape))
        Spacer(Modifier.width(10.dp))
        Label(if (on) "Телефон подключён" else "Телефон не подключён", 14, color = if (on) TextColor else Muted)
    }
}

@Composable
fun EventCard(ev: CalEvent) {
    val time = if (ev.allDay) "Весь день"
    else SimpleDateFormat("EEE, HH:mm", Locale("ru")).format(Date(ev.start))
    FocusCard(300.dp, 100.dp, onClick = {}) {
        Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.Start) {
            Label(time, 14, color = Accent)
            Spacer(Modifier.height(4.dp))
            Label(ev.title.ifBlank { "Без названия" }, 17, FontWeight.SemiBold)
        }
    }
}

@Composable
fun NotifCard(n: Notif, popup: Boolean = false, onClick: () -> Unit) {
    FocusCard(if (popup) 400.dp else 340.dp, 112.dp, onClick = onClick) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Label(n.app, 13, color = Accent, maxLines = 1)
            Label(n.title, 17, FontWeight.SemiBold, maxLines = 1)
            Label(n.text, 14, color = Muted, maxLines = 2)
        }
    }
}

@Composable
fun PlayerScreen(channels: List<Channel>, start: Int) {
    val ctx = LocalContext.current
    var idx by remember { mutableIntStateOf(start) }
    var showName by remember { mutableStateOf(true) }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(idx) {
        player.setMediaItem(MediaItem.fromUri(channels[idx].url))
        player.prepare()
        player.playWhenReady = true
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
            factory = { c -> PlayerView(c).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
        if (showName) {
            Box(Modifier.padding(32.dp).background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(14.dp)) {
                Label("${idx + 1}. ${channels[idx].name}", 24, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AppTile(app: AppItem, big: Boolean, onLaunch: () -> Unit, onMenu: () -> Unit) {
    val w = if (big) 200.dp else 150.dp
    val h = if (big) 164.dp else 130.dp
    val ic = if (big) 88.dp else 60.dp
    FocusCard(w, h, onClick = onLaunch, onLongClick = onMenu) {
        Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(ic)) }
            Spacer(Modifier.height(8.dp))
            Label(app.label, if (big) 17 else 14, maxLines = 1, center = true)
        }
    }
}

@Composable
fun MenuBtn(text: String, autoFocus: Boolean = false, onClick: () -> Unit) {
    FocusCard(340.dp, 54.dp, onClick = onClick, autoFocus = autoFocus) { Label(text, 17, maxLines = 1) }
}

@Composable
fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Label(title, 24, FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.width(12.dp))
                Label(subtitle, 14, color = Muted)
            }
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    w: Dp,
    h: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.1f else 1f, animationSpec = tween(160), label = "scale")
    val fr = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(w, h)
            .scale(s)
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .shadow(if (focused) 18.dp else 0.dp, shape, ambientColor = Accent, spotColor = Accent)
            .background(if (focused) GlassFocus else Glass, shape)
            .border(if (focused) BorderStroke(2.dp, Color.White) else BorderStroke(1.dp, Glass), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Label(
    text: String,
    size: Int,
    weight: FontWeight = FontWeight.Medium,
    color: Color = TextColor,
    maxLines: Int = 2,
    center: Boolean = false,
) {
    BasicText(
        text,
        style = TextStyle(
            color = color, fontSize = size.sp, fontWeight = weight,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
