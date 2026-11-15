package ru.svoe.tv

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ссылка на APK приложения для телефона (его собирает тот же GitHub Actions). */
const val PHONE_APK_URL = "https://github.com/43l0v3k/tv_launcher/releases/download/latest/svoe-phone.apk"

private fun requestDiscoverable(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ---------------------------------------------------------------- Первая настройка

@Composable
fun Onboarding(prefs: Prefs, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var region by remember { mutableStateOf(prefs.region) }
    var lockSet by remember { mutableStateOf(prefs.hasPin) }
    var pinDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = step > 0 && !pinDialog) { step-- }

    val greets = listOf("Здравствуйте!", "Hello!", "Привіт!", "¡Hola!")
    var gi by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2300)
            gi = (gi + 1) % greets.size
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = d(56), vertical = d(36)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(d(18)),
        ) {
            Box(Modifier.height(d(76)).enter(0), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = gi,
                    transitionSpec = {
                        (slideInVertically(tween(450)) { it / 2 } + fadeIn(tween(450))) togetherWith
                            (slideOutVertically(tween(350)) { -it / 2 } + fadeOut(tween(300)))
                    },
                    label = "greet",
                ) { i -> Label(greets[i], 52, FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center) }
            }

            // индикатор шагов
            Row(horizontalArrangement = Arrangement.spacedBy(d(8)), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 4) {
                    val w by animateDpAsState(if (i == step) d(28) else d(8), spring(dampingRatio = 0.6f, stiffness = 400f), label = "dot")
                    Box(Modifier.size(w, d(8)).background(if (i <= step) TextC else Color(0x44FFFFFF), RoundedCornerShape(50)))
                }
            }

            GlassCard(Modifier.fillMaxWidth(0.88f).weight(1f).enter(1), shape = RoundedCornerShape(d(30))) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val fwd = targetState > initialState
                        (slideInHorizontally(tween(420)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(380))) togetherWith
                            (slideOutHorizontally(tween(300)) { if (fwd) -it / 3 else it / 3 } + fadeOut(tween(220)))
                    },
                    label = "step",
                ) { s ->
                    Row(Modifier.fillMaxSize().padding(d(28)), horizontalArrangement = Arrangement.spacedBy(d(28))) {
                        when (s) {
                            0 -> {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d(10))) {
                                    Label(tr("lang_title"), 24, FontWeight.SemiBold)
                                    LANGS.forEach { (code, name) ->
                                        GlassCard(
                                            Modifier.fillMaxWidth().height(d(46)),
                                            onClick = { L.lang = code; prefs.lang = code },
                                            selected = L.lang == code,
                                            autoFocus = L.lang == code,
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Row(Modifier.padding(horizontal = d(16)).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Label(name, 18, maxLines = 1, modifier = Modifier.weight(1f))
                                                if (L.lang == code) Ico("check", d(20), Good)
                                            }
                                        }
                                    }
                                }
                                NextColumn(Modifier.weight(1f)) { Btn(tr("next"), Modifier.width(d(170)), Accent) { step = 1 } }
                            }
                            1 -> {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d(10))) {
                                    Label(tr("region_title"), 24, FontWeight.SemiBold)
                                    listOf("ru" to "reg_ru", "ua" to "reg_ua", "us" to "reg_us").forEach { (code, key) ->
                                        GlassCard(
                                            Modifier.fillMaxWidth().height(d(46)),
                                            onClick = {
                                                region = code
                                                prefs.region = code
                                                // стартовый город для погоды (для России остаётся, как был)
                                                when (code) {
                                                    "ua" -> { prefs.city = "Київ"; prefs.lat = 50.4501; prefs.lon = 30.5234 }
                                                    "us" -> { prefs.city = "New York"; prefs.lat = 40.7128; prefs.lon = -74.006 }
                                                }
                                            },
                                            selected = region == code,
                                            autoFocus = region == code,
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Row(Modifier.padding(horizontal = d(16)).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Label(tr(key), 18, maxLines = 1, modifier = Modifier.weight(1f))
                                                if (region == code) Ico("check", d(20), Good)
                                            }
                                        }
                                    }
                                }
                                NextColumn(Modifier.weight(1f)) { Btn(tr("next"), Modifier.width(d(170)), Accent) { step = 2 } }
                            }
                            2 -> {
                                LaunchedEffect(Unit) { if (!Sync.connected.value) requestDiscoverable(ctx) }
                                val code = prefs.pairCode
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d(12))) {
                                    Label(tr("pair_title"), 24, FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(d(26))) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d(6))) {
                                            QrImage(PHONE_APK_URL, Modifier.size(d(124)))
                                            Label(tr("pair_download"), 13, color = Muted, maxLines = 1)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d(6))) {
                                            QrImage("svoetv://pair?code=$code", Modifier.size(d(124)))
                                            Label(tr("pair_link"), 13, color = Muted, maxLines = 1)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(10))) {
                                        Label("${tr("pair_code")}:", 15, color = Muted)
                                        Label(code.chunked(3).joinToString(" "), 24, FontWeight.Bold, maxLines = 1)
                                    }
                                }
                                NextColumn(Modifier.weight(1f)) {
                                    if (Sync.connected.value) {
                                        Label("✓ ${tr("pair_ok")}", 15, color = Good, maxLines = 1)
                                        Btn(tr("next"), Modifier.width(d(170)), Accent, autoFocus = true) { step = 3 }
                                    } else {
                                        Btn(tr("skip"), Modifier.width(d(170)), autoFocus = true) { step = 3 }
                                    }
                                }
                            }
                            else -> {
                                Column(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Label(tr("done"), 40, FontWeight.SemiBold, maxLines = 1)
                                    Spacer(Modifier.height(d(22)))
                                    Btn(tr("go_home"), Modifier.width(d(360)), Accent, autoFocus = true) {
                                        prefs.onboarded = true
                                        onFinish()
                                    }
                                    Spacer(Modifier.height(d(12)))
                                    GlassCard(Modifier.width(d(360)).height(d(44)), onClick = { pinDialog = true }, selected = lockSet) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(8))) {
                                            Ico("lock", d(18))
                                            Label(tr("setup_lock"), 16, FontWeight.SemiBold, maxLines = 1)
                                            if (lockSet) Ico("check", d(18), Good)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pinDialog) Overlay({ pinDialog = false }) {
            PinSetup(prefs) { lockSet = true; pinDialog = false }
        }
    }
}

@Composable
private fun NextColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(d(12), Alignment.Bottom),
    ) { content() }
}

// ---------------------------------------------------------------- Клавиатура пароля

private fun digitOf(k: Key): Char? = when (k) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/**
 * Цифровая панель из наброска: 1-9, «стереть», 0, «галочка».
 * onSubmit возвращает текст ошибки или null, если всё хорошо.
 * autoLen > 0 — отправлять сразу, как набрано столько цифр.
 */
@Composable
fun PinPad(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    autoLen: Int = 0,
    minLen: Int = 4,
    maxLen: Int = 8,
    onSubmit: (String) -> String?,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (pin.length < minLen) return
        val err = onSubmit(pin)
        if (err != null) {
            error = err
            pin = ""
            scope.launch { for (v in listOf(18f, -16f, 12f, -8f, 0f)) shake.animateTo(v, tween(60)) }
        } else {
            pin = ""
            error = null
        }
    }

    fun add(c: Char) {
        if (pin.length < maxLen) {
            pin += c
            error = null
            if (autoLen > 0 && pin.length == autoLen) submit()
        }
    }

    fun back() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "<", "0", "ok")
    Column(
        modifier
            .graphicsLayer { translationX = shake.value }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else {
                    val c = digitOf(e.key)
                    when {
                        c != null -> { add(c); true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { back(); true }
                        else -> false
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d(10)),
    ) {
        Label(title, 20, FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
        Row(Modifier.height(d(18)), horizontalArrangement = Arrangement.spacedBy(d(10)), verticalAlignment = Alignment.CenterVertically) {
            repeat(maxOf(minLen, pin.length)) { i ->
                Box(Modifier.size(d(13)).background(if (i < pin.length) TextC else Color(0x44FFFFFF), CircleShape))
            }
        }
        Label(error ?: hint, 11, color = if (error != null) Bad else Muted, align = TextAlign.Center, modifier = Modifier.height(d(30)))
        for (r in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(d(10))) {
                for (c in 0 until 3) {
                    val k = keys[r * 3 + c]
                    GlassCard(
                        Modifier.size(d(54)),
                        onClick = {
                            when (k) {
                                "<" -> back()
                                "ok" -> submit()
                                else -> add(k[0])
                            }
                        },
                        shape = CircleShape,
                        autoFocus = k == "5",
                    ) {
                        when (k) {
                            "<" -> Ico("back", d(22))
                            "ok" -> Ico("check", d(22), Good)
                            else -> Label(k, 22, FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Создание пароля: ввести, потом повторить. */
@Composable
fun PinSetup(prefs: Prefs, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    GlassCard(Modifier.width(d(280)), shape = RoundedCornerShape(d(26))) {
        key(first == null) {
            PinPad(
                title = if (first == null) tr("set_pin") else tr("repeat_pin"),
                hint = if (msg.isNotEmpty()) msg else tr("pin_hint"),
                modifier = Modifier.padding(d(20)),
            ) { pin ->
                val f = first
                if (f == null) {
                    first = pin
                    msg = ""
                } else if (f == pin) {
                    prefs.setPin(pin)
                    onDone()
                } else {
                    first = null
                    msg = tr("pin_mismatch")
                }
                null
            }
        }
    }
}

// ---------------------------------------------------------------- Экран блокировки

@Composable
fun LockScreen(prefs: Prefs, onUnlock: () -> Unit) {
    BackHandler { }
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize().padding(d(44))) {
        GlassCard(Modifier.align(Alignment.BottomStart).enter(1), shape = RoundedCornerShape(d(26))) {
            Column(Modifier.padding(horizontal = d(30), vertical = d(18))) {
                Label(SimpleDateFormat("HH:mm", Locale.US).format(now), 72, FontWeight.Light, maxLines = 1)
                Label(SimpleDateFormat("d MMMM", Locale(L.lang)).format(now), 22, color = Muted, maxLines = 1)
            }
        }
        GlassCard(Modifier.align(Alignment.TopEnd).width(d(260)).enter(0), shape = RoundedCornerShape(d(26))) {
            PinPad(
                title = tr("enter_pin"),
                hint = "",
                modifier = Modifier.padding(d(20)),
                autoLen = prefs.pinLen,
            ) { pin ->
                if (prefs.checkPin(pin)) {
                    onUnlock()
                    null
                } else tr("pin_wrong")
            }
        }
    }
}
