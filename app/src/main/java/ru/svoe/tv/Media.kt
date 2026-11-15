package ru.svoe.tv

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat

/** Пустой сервис: нужен только как «разрешение на уведомления», без него Android не отдаёт список медиасессий. */
class MediaListenerService : NotificationListenerService()

/** Что сейчас играет на ТВ (любое приложение с медиасессией) + управление. */
object Media {
    val hasAccess = mutableStateOf(false)
    val title = mutableStateOf("")
    val artist = mutableStateOf("")
    val playing = mutableStateOf(false)
    val duration = mutableLongStateOf(0L)
    private val basePos = mutableLongStateOf(0L)
    private var baseTime = 0L
    private var speed = 1f

    private var ctrl: MediaController? = null
    private var started = false

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = applyState(state)
        override fun onMetadataChanged(metadata: MediaMetadata?) = applyMeta(metadata)
        override fun onSessionDestroyed() = detach()
    }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        hasAccess.value = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        if (!hasAccess.value) return
        try {
            val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(app, MediaListenerService::class.java)
            if (!started) {
                msm.addOnActiveSessionsChangedListener(
                    MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }, cn,
                )
                started = true
            }
            pick(msm.getActiveSessions(cn))
        } catch (_: SecurityException) {
            hasAccess.value = false
        } catch (_: Throwable) {
        }
    }

    private fun pick(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (c?.sessionToken != ctrl?.sessionToken) {
            detach()
            ctrl = c
            c?.registerCallback(cb)
        }
        applyMeta(c?.metadata)
        applyState(c?.playbackState)
    }

    private fun detach() {
        runCatching { ctrl?.unregisterCallback(cb) }
        ctrl = null
        title.value = ""; artist.value = ""; playing.value = false; duration.longValue = 0L
    }

    private fun applyMeta(m: MediaMetadata?) {
        title.value = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist.value = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        duration.longValue = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    }

    private fun applyState(s: PlaybackState?) {
        playing.value = s?.state == PlaybackState.STATE_PLAYING
        basePos.longValue = s?.position ?: 0L
        baseTime = s?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        speed = s?.playbackSpeed ?: 1f
    }

    /** Текущая позиция в мс (ориентировочная, между обновлениями экстраполируется). */
    fun position(): Long {
        val p = basePos.longValue
        return if (playing.value) p + ((SystemClock.elapsedRealtime() - baseTime) * speed).toLong() else p
    }

    fun toggle() { ctrl?.transportControls?.let { if (playing.value) it.pause() else it.play() } }
    fun next() { ctrl?.transportControls?.skipToNext() }
    fun prev() { ctrl?.transportControls?.skipToPrevious() }
}
