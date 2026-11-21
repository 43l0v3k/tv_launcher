package ru.svoe.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.dreams.DreamService
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Час для обоев по режиму заставки. */
fun saverHour(mode: String, now: Int): Int = when (mode) {
    "day" -> 13
    "evening" -> 19
    "night" -> 23
    else -> now
}

/** Сама заставка: обои выбранного времени суток и большие часы. Больше ничего. */
@Composable
fun SaverContent(mode: String, prefs: Prefs) {
    val hour by produceState(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        while (true) {
            value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(60_000)
        }
    }
    // Погода обновляется, пока идёт заставка (в системной заставке лаунчер может быть не открыт)
    LaunchedEffect(Unit) {
        while (true) {
            loadWeather(prefs)
            delay(15 * 60_000L)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val scale = min(maxWidth.value / 960f, maxHeight.value / 540f).coerceIn(0.5f, 4f)
        CompositionLocalProvider(LocalScale provides scale) {
            Wallpaper(saverHour(mode, hour))
            SaverClock()
            SaverWeather()
        }
    }
}

/** Погода справа внизу: сейчас, как ощущается, совет и прогноз на три дня. */
@Composable
private fun SaverWeather() {
    val temp = WeatherData.temp
    if (temp == null) return
    val now = System.currentTimeMillis()
    val step = (now / 180_000L % 4).toInt()
    val ox by animateDpAsState(d(listOf(0, -40, -10, -60)[step]), tween(4000), label = "wx")
    val oy by animateDpAsState(d(listOf(0, 20, -30, -10)[step]), tween(4000), label = "wy")
    val advice = weatherAdvice()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = d(56), bottom = d(48)).offset(ox, oy),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d(14))) {
                Ico(weatherIcon(WeatherData.code), d(56), Color(0xFFFFE08A))
                Label("$temp°", 72, FontWeight.Light, maxLines = 1)
            }
            Label(WeatherData.city, 20, color = Muted, maxLines = 1, align = TextAlign.End)
            val f = WeatherData.feels
            if (f != null) Label("${tr("feels")} $f°", 16, color = Muted, maxLines = 1, align = TextAlign.End)
            if (advice.isNotEmpty()) {
                Spacer(Modifier.height(d(8)))
                Label(advice, 18, FontWeight.SemiBold, color = Color(0xFFFFE08A), maxLines = 2, align = TextAlign.End)
            }
            val days = WeatherData.days.drop(1).take(3)
            if (days.isNotEmpty()) {
                Spacer(Modifier.height(d(14)))
                Row(horizontalArrangement = Arrangement.spacedBy(d(22))) {
                    days.forEach { dd ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Label(SimpleDateFormat("EEE", Locale(L.lang)).format(Date(dd.time)), 14, color = Muted, maxLines = 1)
                            Ico(weatherIcon(dd.code), d(26), TextC)
                            Label("${dd.max}° / ${dd.min}°", 14, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaverClock() {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.US).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale(L.lang)).format(now)
    // раз в несколько минут часы чуть сдвигаются, чтобы не выгорал экран
    val step = (now.time / 180_000L % 4).toInt()
    val ox by animateDpAsState(d(listOf(0, 60, 20, 90)[step]), tween(4000), label = "ox")
    val oy by animateDpAsState(d(listOf(0, -30, -70, -20)[step]), tween(4000), label = "oy")
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = d(56), bottom = d(48)).offset(ox, oy)) {
            Label(time, 120, FontWeight.Light, maxLines = 1)
            Label(date, 22, color = Muted, maxLines = 1)
        }
    }
}

/** Системная заставка Android TV («дремота»). Запускается самой системой, даже когда открыто стороннее приложение. */
class SaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lc = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lc
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        ssc.performAttach()
        ssc.performRestore(null)
        lc.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val prefs = Prefs(this)
        L.lang = prefs.lang
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        val mode = prefs.ssMode.let { if (it == "off") "night" else it }
        setContentView(ComposeView(this).apply { setContent { SaverContent(mode, prefs) } })
        lc.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        lc.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}

/** Делает «Своё ТВ» системной заставкой (заменяет старую). Нужно разрешение WRITE_SECURE_SETTINGS, выдаётся один раз по ADB. */
object SystemSaver {
    const val COMPONENT = "ru.svoe.tv/ru.svoe.tv.SaverService"
    const val ADB = "pm grant ru.svoe.tv android.permission.WRITE_SECURE_SETTINGS"

    /** "ok", "ok_timeout" — всё записано; "no_perm" — открыли системные настройки заставки. */
    fun install(ctx: Context, minutes: Int): String {
        return try {
            Settings.Secure.putString(ctx.contentResolver, "screensaver_components", COMPONENT)
            Settings.Secure.putInt(ctx.contentResolver, "screensaver_enabled", 1)
            val t = runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000) }.isSuccess
            if (t) "ok_timeout" else "ok"
        } catch (_: SecurityException) {
            openSystem(ctx)
            "no_perm"
        }
    }

    fun openSystem(ctx: Context) {
        for (a in listOf(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
