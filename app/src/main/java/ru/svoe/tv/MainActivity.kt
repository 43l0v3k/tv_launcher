package ru.svoe.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF1B212B)
private val CardFocus = Color(0xFF252D3A)
private val Accent = Color(0xFF4DA3FF)
private val Muted = Color(0xFF8B97A8)
private val TextColor = Color(0xFFE8EDF4)

data class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Bitmap?)
data class Tile(val title: String, val onClick: () -> Unit)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler { /* лаунчер: Назад ничего не делает */ }
            Home(loadApps(this), this)
        }
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
                icon = runCatching { it.loadIcon(pm).toBitmap(160, 160) }.getOrNull(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun Home(apps: List<AppItem>, ctx: Context) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }

    val channels = listOf("Первый", "Россия 1", "СТС", "Карусель", "5 канал", "Канал 6", "Канал 7", "Канал 8")
    val system = listOf(
        Tile("Настройки") { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        Tile("Wi-Fi") { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        Tile("Bluetooth") { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        Tile("Экран") { ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        Tile("Приложения") { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
    )

    Column(
        Modifier.fillMaxSize().background(Bg).padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(SimpleDateFormat("HH:mm", Locale("ru")).format(now), 34, FontWeight.Light)
            Spacer(Modifier.width(24.dp))
            Label(SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(now), 16, color = Muted)
            Spacer(Modifier.weight(1f))
            Label("Своё ТВ", 26, FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
        }

        Section("Приложения") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
                itemsIndexed(apps) { _, app ->
                    FocusCard(220.dp, 124.dp, { ctx.startActivity(app.intent) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(56.dp)) }
                            Spacer(Modifier.height(6.dp))
                            Label(app.label, 15, maxLines = 1)
                        }
                    }
                }
            }
        }

        Section("Прямой эфир • IPTV") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
                itemsIndexed(channels) { i, name ->
                    FocusCard(150.dp, 80.dp, { /* TODO: плеер IPTV */ }) {
                        Label("${i + 1}  $name", 16)
                    }
                }
            }
        }

        Section("Система") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
                itemsIndexed(system) { _, t ->
                    FocusCard(170.dp, 80.dp, t.onClick) { Label(t.title, 16) }
                }
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Label(title, 18, color = Muted)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
fun FocusCard(w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.08f else 1f, label = "scale")
    Box(
        Modifier
            .size(w, h)
            .scale(s)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) CardFocus else Card, RoundedCornerShape(12.dp))
            .border(if (focused) BorderStroke(3.dp, Accent) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Label(text: String, size: Int, weight: FontWeight = FontWeight.Medium, color: Color = TextColor, maxLines: Int = 2) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight), maxLines = maxLines)
}
