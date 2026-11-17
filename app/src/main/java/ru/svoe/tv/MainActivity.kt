package ru.svoe.tv

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import java.util.Calendar
import kotlin.math.min

data class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Bitmap?)

class MainActivity : ComponentActivity() {
    val tick = mutableIntStateOf(0)
    val menuTick = mutableIntStateOf(0)
    val locked = mutableStateOf(false)
    private lateinit var prefs: Prefs

    /** Когда пользователь последний раз что-то нажимал. */
    @Volatile var lastInput = SystemClock.elapsedRealtime()
    private var swallowUp = false

    private fun wake(): Boolean {
        lastInput = SystemClock.elapsedRealtime()
        if (Look.saver) { Look.saver = false; return true }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (wake()) { swallowUp = true; return true }
        } else if (event.action == KeyEvent.ACTION_UP && swallowUp) {
            swallowUp = false
            return true
        }
        lastInput = SystemClock.elapsedRealtime()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (wake()) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInput = SystemClock.elapsedRealtime()
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (prefs.hasPin && prefs.onboarded) locked.value = true
        }
    }

    private val btPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Sync.start(prefs.pairCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.lang = prefs.lang
        Look.wallpaper = prefs.wallpaper
        locked.value = prefs.hasPin && prefs.onboarded
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))

        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            btPerm.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
        } else {
            Sync.start(prefs.pairCode)
        }
        setContent { Root(this, prefs) }
    }

    override fun onResume() {
        super.onResume()
        Look.saver = false
        lastInput = SystemClock.elapsedRealtime()
        tick.intValue++ // обновить список приложений после установки/удаления
        Media.start(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            menuTick.intValue++
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
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

fun move(list: List<String>, id: String, d: Int): List<String> {
    val i = list.indexOf(id)
    val j = i + d
    if (i < 0 || j < 0 || j >= list.size) return list
    val m = list.toMutableList()
    m.removeAt(i)
    m.add(j, id)
    return m
}

fun Context.open(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun Root(act: MainActivity, prefs: Prefs) {
    var onboarded by remember { mutableStateOf(prefs.onboarded) }
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(60_000)
        }
    }
    LaunchedEffect(Look.redo) { if (Look.redo > 0) onboarded = false }
    LaunchedEffect(onboarded) {
        while (true) {
            delay(1000)
            val idleMs = SystemClock.elapsedRealtime() - act.lastInput
            if (onboarded && !Look.busy && prefs.ssMode != "off" && idleMs > prefs.ssMinutes * 60_000L) Look.saver = true
        }
    }
    val locked = act.locked.value
    val wpHour = when (Look.wallpaper) {
        "morning" -> 8
        "day" -> 13
        "evening" -> 19
        "night" -> 23
        else -> hour
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val scale = min(maxWidth.value / 960f, maxHeight.value / 540f).coerceIn(0.5f, 4f)
        CompositionLocalProvider(LocalScale provides scale) {
            Wallpaper(wpHour)
            val stage = when {
                !onboarded -> 0
                locked -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(550, delayMillis = 120)) + scaleIn(tween(550, delayMillis = 120), initialScale = 0.93f)) togetherWith
                        (fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.06f))
                },
                label = "stage",
            ) { s ->
                when (s) {
                    0 -> Onboarding(prefs) { onboarded = true }
                    1 -> LockScreen(prefs) { act.locked.value = false }
                    else -> Home(act, prefs)
                }
            }
            AnimatedVisibility(Look.saver, enter = fadeIn(tween(900)), exit = fadeOut(tween(300))) {
                SaverContent(prefs.ssMode)
            }
        }
    }
}
