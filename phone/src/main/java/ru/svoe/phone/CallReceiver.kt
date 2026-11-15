package ru.svoe.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import org.json.JSONObject

/** Входящие звонки -> ТВ. */
class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // номер может отсутствовать на новых Android
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    val who = who(ctx, number)
                    Link.send(JSONObject().put("t", "call").put("state", "ring").put("who", who).toString())
                }
                TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Link.send(JSONObject().put("t", "call").put("state", "end").toString())
                }
                else -> {}
            }
        } catch (e: Throwable) {
        }
    }

    private fun who(ctx: Context, number: String?): String {
        if (number.isNullOrBlank()) return "Неизвестный номер"
        var name: String? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        } catch (e: Throwable) {
        }
        val n = name
        return if (!n.isNullOrBlank()) n else number
    }
}
