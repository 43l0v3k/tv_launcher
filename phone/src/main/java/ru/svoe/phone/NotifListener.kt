package ru.svoe.phone

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/** Пересылает уведомления телефона на ТВ. */
class NotifListener : NotificationListenerService() {
    private var lastKey = ""
    private var lastAt = 0L

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
