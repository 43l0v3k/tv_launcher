package ru.svoe.tv

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import org.json.JSONObject

/**
 * Управление ТВ с привязанного телефона.
 *   {"t":"rc","k":"up|down|left|right|ok|back|menu|home|vol_up|vol_down|mute|play|next|prev"}
 *   {"t":"open","url":"https://..."}
 * Кнопки навигации работают, пока открыт сам лаунчер; громкость, музыка, «домой» и ссылки работают всегда.
 */
object Remote {
    @Volatile private var app: Context? = null
    @Volatile private var prefs: Prefs? = null
    @Volatile var activity: MainActivity? = null

    fun init(ctx: Context, p: Prefs) {
        app = ctx.applicationContext
        prefs = p
    }

    private fun audio(): AudioManager? = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Вызывается из главного потока. */
    fun handle(j: JSONObject) {
        if (prefs?.remoteOn != true) return
        when (j.optString("t")) {
            "rc" -> key(j.optString("k"))
            "open" -> openUrl(j.optString("url"))
        }
    }

    private fun key(k: String) {
        val am = audio()
        when (k) {
            "up" -> nav(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> nav(KeyEvent.KEYCODE_DPAD_DOWN)
            "left" -> nav(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> nav(KeyEvent.KEYCODE_DPAD_RIGHT)
            "ok" -> nav(KeyEvent.KEYCODE_DPAD_CENTER)
            "back" -> nav(KeyEvent.KEYCODE_BACK)
            "menu" -> nav(KeyEvent.KEYCODE_MENU)
            "home" -> goHome()
            "vol_up" -> am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "vol_down" -> am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "play" -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    /** Нажатие кнопки пульта внутри лаунчера. */
    private fun nav(code: Int) {
        val a = activity ?: return
        val t = SystemClock.uptimeMillis()
        runCatching {
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            a.dispatchKeyEvent(KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0))
        }
    }

    private fun media(code: Int) {
        val am = audio() ?: return
        val t = SystemClock.uptimeMillis()
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0))
        }
    }

    private fun goHome() {
        val c = app ?: return
        runCatching {
            c.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(c.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openUrl(url: String) {
        val c = app ?: return
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return
        runCatching {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
