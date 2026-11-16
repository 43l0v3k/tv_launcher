package ru.svoe.phone

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/** Пересылает уведомления телефона и текущую музыку на ТВ. */
class NotifListener : NotificationListenerService() {
    private var lastKey = ""
    private var lastAt = 0L

    companion object {
        @Volatile private var inst: NotifListener? = null
        /** Отправить текущее состояние музыки (например, сразу после подключения к ТВ). */
        fun push() { inst?.sendMedia(true) }
    }

    private var ctrl: MediaController? = null
    private var lastMedia = ""
    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { sendMedia(false) }
        override fun onMetadataChanged(metadata: MediaMetadata?) { sendMedia(false) }
        override fun onSessionDestroyed() { attach(null) }
    }

    override fun onListenerConnected() {
        inst = this
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(this, NotifListener::class.java)
            msm.addOnActiveSessionsChangedListener({ attach(it) }, cn)
            attach(msm.getActiveSessions(cn))
        } catch (_: Throwable) {
        }
    }

    override fun onListenerDisconnected() {
        if (inst === this) inst = null
        try { ctrl?.unregisterCallback(cb) } catch (_: Throwable) {}
        ctrl = null
    }

    private fun attach(list: List<MediaController>?) {
        try {
            val c = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
            if (c?.sessionToken != ctrl?.sessionToken) {
                try { ctrl?.unregisterCallback(cb) } catch (_: Throwable) {}
                ctrl = c
                c?.registerCallback(cb)
            }
            sendMedia(false)
        } catch (_: Throwable) {
        }
    }

    private fun sendMedia(force: Boolean) {
        try {
            val c = ctrl
            val m = c?.metadata
            val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = (m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
            val st = c?.playbackState
            val playing = st?.state == PlaybackState.STATE_PLAYING
            val key = "$title|$artist|$playing"
            if (!force && key == lastMedia) return
            lastMedia = key
            Link.send(
                JSONObject().put("t", "media").put("title", title).put("artist", artist)
                    .put("playing", playing).put("dur", m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
                    .put("pos", st?.position ?: 0L).toString()
            )
        } catch (_: Throwable) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return
            val n = sbn.notification ?: return
            val pkg = sbn.packageName ?: return
            if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
            if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (text.isBlank()) text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) return

            // дубликаты подряд в течение 2 секунд
            val key = "$pkg|$title|$text"
            val now = System.currentTimeMillis()
            if (key == lastKey && now - lastAt < 2000) return
            lastKey = key
            lastAt = now

            val json = JSONObject().put("t", "notif").put("app", label(pkg))
                .put("title", title).put("text", text)
            Link.send(json.toString())
        } catch (e: Throwable) {
        }
    }

    private fun label(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) {
        pkg
    }
}
