package ru.svoe.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val Accent = Color(0xFF8CC0FF)
val TextC = Color(0xFFF4F7FC)
val Muted = Color(0xFFC3CCDF)
val Good = Color(0xFF5BE08F)
val Bad = Color(0xFFFF7A7A)

/** Всё рассчитано на экран 960x540 dp; на других размерах масштабируется. */
val LocalScale = compositionLocalOf { 1f }

/** false — карточки не берут фокус (пока сверху открыт оверлей). */
val LocalInteractive = compositionLocalOf { true }

@Composable
@ReadOnlyComposable
fun d(v: Number): Dp = (v.toFloat() * LocalScale.current).dp

@Composable
@ReadOnlyComposable
fun fs(v: Number): TextUnit = (v.toFloat() * LocalScale.current).sp

// ---------------------------------------------------------------- Текст

@Composable
fun Label(
    text: String,
    size: Number,
    weight: FontWeight = FontWeight.Medium,
    color: Color = TextC,
    maxLines: Int = 2,
    align: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = fs(size), fontWeight = weight, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------- Анимация появления

/** Плавное «выплывание» с задержкой по порядку (каскад). */
@Composable
fun Modifier.enter(index: Int, key: Any? = null): Modifier {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(index * 70L + 60L)
        a.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 48f
        val s = 0.92f + 0.08f * v
        scaleX = s
        scaleY = s
    }
}

// ---------------------------------------------------------------- Liquid glass

/**
 * «Жидкое стекло»: полупрозрачная заливка, блик в углу, яркая кромка-градиент.
 * Настоящего размытия фона нет (на Android 11 его нельзя сделать), поэтому стекло имитируется слоями.
 */
fun Modifier.glassSurface(shape: Shape, glow: Float, selected: Boolean = false, tint: Color = Color.White): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val sel = if (selected) 0.12f else 0f
        drawOutline(
            outline,
            Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.24f + 0.12f * glow + sel), tint.copy(alpha = 0.07f + 0.09f * glow + sel)),
            ),
        )
        // блик
        drawOutline(
            outline,
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.30f + 0.12f * glow), Color.Transparent),
                center = Offset(size.width * 0.12f, 0f),
                radius = size.maxDimension * 0.95f,
            ),
        )
        // нижний отсвет
        drawOutline(
            outline,
            Brush.radialGradient(
                listOf(Accent.copy(alpha = 0.16f + 0.14f * glow), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height),
                radius = size.maxDimension * 0.7f,
            ),
        )
        // кромка
        drawOutline(
            outline,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.90f),
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.70f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width = 1.3.dp.toPx() + 1.3.dp.toPx() * glow),
        )
    }

/** Бегущий блик по сфокусированной карточке. */
@Composable
fun BoxScope.Shine() {
    val tr = rememberInfiniteTransition(label = "shine")
    val x by tr.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1700, delayMillis = 500, easing = LinearEasing)),
        label = "x",
    )
    Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val band = w * 0.45f
        val cx = w * x
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                start = Offset(cx - band, 0f),
                end = Offset(cx + band, size.height * 0.7f),
            ),
        )
    }
}

/**
 * Основная карточка лаунчера. Без onClick — просто стеклянная панель, с onClick — фокусируется с пульта.
 * При фокусе: пружинное увеличение, свечение, бегущий блик.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(d(18)),
    autoFocus: Boolean = false,
    selected: Boolean = false,
    focusScale: Float = 1.07f,
    tint: Color = Color.White,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) focusScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "scale",
    )
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "glow")
    val fr = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { delay(350); runCatching { fr.requestFocus() } }

    var m = modifier
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(d(2 + 16 * glow), shape, ambientColor = Accent, spotColor = Accent)
        .clip(shape)
        .glassSurface(shape, glow, selected, tint)
    val interactive = LocalInteractive.current
    if (onClick != null) {
        m = m.focusProperties { canFocus = interactive }
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(enabled = interactive, onClick = onClick, onLongClick = onLongClick)
    }
    Box(m, contentAlignment = contentAlignment) {
        content()
        if (focused) Shine()
    }
}

// ---------------------------------------------------------------- Иконки (рисуем сами, без библиотеки иконок)

@Composable
fun Ico(kind: String, size: Dp, color: Color = TextC, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun poly(pts: List<Pair<Float, Float>>, fill: Boolean = false, close: Boolean = false) {
            val path = Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
            if (close) path.close()
            drawPath(path, color, style = if (fill) Fill else st)
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 2f) =
            drawLine(color, p(x1, y1), p(x2, y2), w * u, StrokeCap.Round)
        fun cloud(dy: Float) {
            drawCircle(color, 4f * u, p(8f, 15f + dy))
            drawCircle(color, 5.5f * u, p(13f, 12f + dy))
            drawCircle(color, 4f * u, p(18f, 15f + dy))
            drawRoundRect(color, p(8f, 14f + dy), Size(10f * u, 5f * u), CornerRadius(2f * u))
        }
        when (kind) {
            "search" -> { drawCircle(color, 6.5f * u, p(10f, 10f), style = st); line(15f, 15f, 21f, 21f) }
            "mic" -> {
                drawRoundRect(color, p(9f, 3f), Size(6f * u, 11f * u), CornerRadius(3f * u), style = st)
                drawArc(color, 0f, 180f, false, p(6f, 6f), Size(12f * u, 12f * u), style = st)
                line(12f, 18f, 12f, 21f)
            }
            "tv" -> {
                drawRoundRect(color, p(2f, 4f), Size(20f * u, 13f * u), CornerRadius(2f * u), style = st)
                line(8f, 21f, 16f, 21f)
            }
            "phone" -> {
                drawRoundRect(color, p(7f, 2f), Size(10f * u, 20f * u), CornerRadius(2.5f * u), style = st)
                drawCircle(color, 0.9f * u, p(12f, 18.5f))
            }
            "grid" -> for (x in listOf(3f, 13f)) for (y in listOf(3f, 13f))
                drawRoundRect(color, p(x, y), Size(8f * u, 8f * u), CornerRadius(2f * u))
            "wifi" -> {
                for (r in listOf(4f, 8f, 12f)) drawArc(color, -135f, 90f, false, p(12f - r, 19f - r), Size(2f * r * u, 2f * r * u), style = st)
                drawCircle(color, 1.5f * u, p(12f, 19f))
            }
            "bt" -> poly(listOf(7f to 7f, 17f to 17f, 12f to 22f, 12f to 2f, 17f to 7f, 7f to 17f))
            "sound" -> {
                poly(listOf(3f to 9f, 7f to 9f, 12f to 5f, 12f to 19f, 7f to 15f, 3f to 15f), fill = true, close = true)
                drawArc(color, -50f, 100f, false, p(7f, 7f), Size(10f * u, 10f * u), style = st)
                drawArc(color, -50f, 100f, false, p(3f, 3f), Size(18f * u, 18f * u), style = st)
            }
            "lock" -> {
                drawRoundRect(color, p(5f, 11f), Size(14f * u, 10f * u), CornerRadius(2f * u))
                drawArc(color, 180f, 180f, false, p(8f, 4f), Size(8f * u, 8f * u), style = st)
                line(8f, 8f, 8f, 11f); line(16f, 8f, 16f, 11f)
            }
            "gear" -> {
                drawCircle(color, 4f * u, p(12f, 12f), style = st)
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4f
                    line(12f + 7f * cos(a), 12f + 7f * sin(a), 12f + 10f * cos(a), 12f + 10f * sin(a), 2.4f)
                }
            }
            "plus" -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            "check" -> poly(listOf(5f to 13f, 10f to 18f, 19f to 7f))
            "back" -> {
                poly(listOf(9f to 5f, 21f to 5f, 21f to 19f, 9f to 19f, 3f to 12f), close = true)
                line(13f, 9f, 17f, 15f); line(17f, 9f, 13f, 15f)
            }
            "play" -> poly(listOf(7f to 4f, 20f to 12f, 7f to 20f), fill = true, close = true)
            "pause" -> {
                drawRect(color, p(6f, 4f), Size(4f * u, 16f * u)); drawRect(color, p(14f, 4f), Size(4f * u, 16f * u))
            }
            "next" -> {
                poly(listOf(5f to 5f, 15f to 12f, 5f to 19f), fill = true, close = true)
                drawRect(color, p(17f, 5f), Size(2.5f * u, 14f * u))
            }
            "prev" -> {
                poly(listOf(19f to 5f, 9f to 12f, 19f to 19f), fill = true, close = true)
                drawRect(color, p(4.5f, 5f), Size(2.5f * u, 14f * u))
            }
            "sun" -> {
                drawCircle(color, 4.5f * u, p(12f, 12f))
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4f
                    line(12f + 7f * cos(a), 12f + 7f * sin(a), 12f + 10f * cos(a), 12f + 10f * sin(a))
                }
            }
            "cloud" -> cloud(-2f)
            "rain" -> {
                cloud(-3f)
                line(8f, 19f, 7f, 22f); line(12f, 19f, 11f, 22f); line(16f, 19f, 15f, 22f)
            }
            "snow" -> {
                cloud(-3f)
                drawCircle(color, 1.2f * u, p(8f, 20.5f)); drawCircle(color, 1.2f * u, p(12f, 21.5f)); drawCircle(color, 1.2f * u, p(16f, 20.5f))
            }
        }
    }
}

/** Значок источника данных: телевизор или телефон. */
@Composable
fun SourceBadge(kind: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(d(20)).background(Color(0x40FFFFFF), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) { Ico(kind, d(12), Color(0xE6FFFFFF)) }
}

fun weatherIcon(code: Int): String = when (code) {
    0 -> "sun"
    1, 2, 3, 45, 48 -> "cloud"
    in 71..77, 85, 86 -> "snow"
    -1 -> "cloud"
    else -> "rain"
}

// ---------------------------------------------------------------- Обои: солнце и горы

private class Pal(val sky: List<Color>, val sun: Color, val m1: Color, val m2: Color, val m3: Color, val stars: Boolean)

private fun palette(hour: Int): Pal = when (hour) {
    in 5..10 -> Pal(listOf(Color(0xFF2B3A7A), Color(0xFFD9698F), Color(0xFFFFB56B)), Color(0xFFFFE9A8), Color(0xFF6C4F8E), Color(0xFF44336E), Color(0xFF241C4A), false)
    in 11..17 -> Pal(listOf(Color(0xFF1B63D1), Color(0xFF58A9F2), Color(0xFFB9E3FF)), Color(0xFFFFF6C2), Color(0xFF4F7FB8), Color(0xFF365E94), Color(0xFF223F6E), false)
    in 18..21 -> Pal(listOf(Color(0xFF1B1646), Color(0xFFB5476B), Color(0xFFFF9A5C)), Color(0xFFFFD08A), Color(0xFF7A3E7E), Color(0xFF4A2A68), Color(0xFF22143F), false)
    else -> Pal(listOf(Color(0xFF050A1F), Color(0xFF151A4A), Color(0xFF2B2B6B)), Color(0xFFE6ECFF), Color(0xFF24285E), Color(0xFF181B47), Color(0xFF0C0E2B), true)
}

private val R1 = listOf(0f to .60f, .16f to .52f, .30f to .64f, .50f to .46f, .68f to .62f, .84f to .54f, 1f to .64f)
private val R2 = listOf(0f to .72f, .12f to .65f, .30f to .78f, .50f to .62f, .63f to .70f, .80f to .80f, .92f to .73f, 1f to .79f)
private val R3 = listOf(0f to .86f, .20f to .80f, .42f to .90f, .70f to .82f, 1f to .92f)

@Composable
fun Wallpaper(hour: Int, modifier: Modifier = Modifier) {
    val pal = palette(hour)
    val tr = rememberInfiniteTransition(label = "wp")
    val pulse by tr.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val drift by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(110_000, easing = LinearEasing)), label = "drift")
    val stars = remember { List(70) { Triple(Random(it).nextFloat(), Random(it + 100).nextFloat() * 0.55f, 0.5f + Random(it + 200).nextFloat()) } }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(pal.sky))

        if (pal.stars) for ((sx, sy, sr) in stars) {
            drawCircle(Color.White.copy(alpha = 0.35f + 0.35f * ((pulse - 0.92f) / 0.16f) * sr.coerceAtMost(1f)), sr * 1.6f, Offset(sx * w, sy * h))
        }

        // солнце (ночью — луна) с лучами
        val c = Offset(w * 0.21f, h * 0.27f)
        val r = h * 0.085f
        drawCircle(
            Brush.radialGradient(listOf(pal.sun.copy(alpha = 0.55f), Color.Transparent), c, r * 5.5f * pulse),
            r * 5.5f * pulse, c,
        )
        drawCircle(pal.sun, r, c)
        if (!pal.stars) for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f + drift * 2f * PI.toFloat() * 0.6f
            val r1 = r * 1.35f
            val r2 = r * (1.9f + 0.3f * pulse)
            drawLine(pal.sun.copy(alpha = 0.75f), c + Offset(r1 * cos(a), r1 * sin(a)), c + Offset(r2 * cos(a), r2 * sin(a)), r * 0.09f, StrokeCap.Round)
        }

        // облака
        for (k in 0 until 3) {
            val x = ((drift * (1.2f + k * 0.2f) + k * 0.37f) % 1.3f - 0.15f) * w
            val y = h * (0.14f + 0.1f * k)
            val cw = w * (0.10f + 0.03f * k)
            val col = Color.White.copy(alpha = if (pal.stars) 0.04f else 0.20f)
            drawRoundRect(col, Offset(x, y), Size(cw, cw * 0.22f), CornerRadius(cw * 0.11f))
            drawRoundRect(col, Offset(x + cw * 0.2f, y - cw * 0.1f), Size(cw * 0.5f, cw * 0.2f), CornerRadius(cw * 0.1f))
        }

        fun ridge(pts: List<Pair<Float, Float>>, color: Color) {
            val p = Path()
            p.moveTo(0f, h)
            pts.forEach { (x, y) -> p.lineTo(x * w, y * h) }
            p.lineTo(w, h)
            p.close()
            drawPath(p, color)
        }
        ridge(R1, pal.m1)
        ridge(R2, pal.m2)
        ridge(R3, pal.m3)
    }
}

// ---------------------------------------------------------------- Спарклайн и QR

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = Accent) {
    val prog = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { prog.snapTo(0f); prog.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mn = values.minOrNull() ?: 0f
        val mx = values.maxOrNull() ?: 1f
        val rng = if (mx - mn > 0f) mx - mn else 1f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * 0.08f + size.height * 0.84f * (1f - (v - mn) / rng)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        clipRect(right = size.width * prog.value) {
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val m = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1)) }.getOrNull()
    }
    Canvas(modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(6.dp)) {
        val bm = m ?: return@Canvas
        val n = bm.width
        val cell = size.width / n
        for (y in 0 until bm.height) for (x in 0 until n) {
            if (bm.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}
