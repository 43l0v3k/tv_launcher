package ru.svoe.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Большой экран «Сейчас играет»: обложка, название, перемотка и кнопки управления. */
@Composable
fun MusicOverlay(onClose: () -> Unit) {
    var pos by remember { mutableLongStateOf(Media.position()) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = Media.position()
            delay(500)
        }
    }
    val title = Media.title.value
    val artist = Media.artist.value
    val dur = Media.duration.longValue
    val cover = Media.cover.value

    Overlay(onClose) {
        GlassCard(Modifier.fillMaxWidth(0.8f).height(d(330)), shape = RoundedCornerShape(d(28))) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight().padding(d(28)),
                horizontalArrangement = Arrangement.spacedBy(d(28)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(d(250)).clip(RoundedCornerShape(d(18))).background(Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) Image(cover.asImageBitmap(), null, Modifier.size(d(250)))
                    else Ico("sound", d(80), Muted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d(10))) {
                    Label(title.ifEmpty { tr("media_none") }, 28, FontWeight.SemiBold, maxLines = 2)
                    Label(artist.ifEmpty { " " }, 18, color = Muted, maxLines = 1)
                    Spacer(Modifier.height(d(6)))
                    Box(Modifier.fillMaxWidth().height(d(6)).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
                        val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0.001f, 1f) else 0.001f
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(TextC, RoundedCornerShape(50)))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Label(clock(pos), 13, color = Muted, maxLines = 1, modifier = Modifier.weight(1f))
                        if (dur > 0) Label(clock(dur), 13, color = Muted, maxLines = 1)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(d(12), Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassCard(Modifier.size(d(50)), onClick = { Media.prev() }, shape = CircleShape, focusScale = 1.15f) { Ico("prev", d(22)) }
                        GlassCard(Modifier.width(d(64)).height(d(44)), onClick = { Media.seekBy(-10_000); pos = Media.position() }, focusScale = 1.1f) {
                            Label("−10", 15, FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
                        }
                        GlassCard(Modifier.size(d(62)), onClick = { Media.toggle() }, shape = CircleShape, autoFocus = true, focusScale = 1.15f) {
                            Ico(if (Media.playing.value) "pause" else "play", d(26))
                        }
                        GlassCard(Modifier.width(d(64)).height(d(44)), onClick = { Media.seekBy(10_000); pos = Media.position() }, focusScale = 1.1f) {
                            Label("+10", 15, FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
                        }
                        GlassCard(Modifier.size(d(50)), onClick = { Media.next() }, shape = CircleShape, focusScale = 1.15f) { Ico("next", d(22)) }
                    }
                }
            }
        }
    }
}
