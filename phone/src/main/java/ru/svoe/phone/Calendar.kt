package ru.svoe.phone

import android.content.Context
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject

/** События календаря на ближайшие 7 дней (максимум 10). */
object Cal {
    private class Ev(val title: String, val start: Long, val end: Long, val allDay: Boolean)

    fun build(ctx: Context): JSONObject {
        val list = ArrayList<Ev>()
        try {
            val now = System.currentTimeMillis()
            val cursor = CalendarContract.Instances.query(
                ctx.contentResolver,
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.STATUS
                ),
                now,
                now + 7L * 24 * 3600 * 1000
            )
            cursor?.use {
                while (it.moveToNext()) {
                    if (it.getInt(4) == CalendarContract.Events.STATUS_CANCELED) continue
                    val title = it.getString(0)
                    list += Ev(
                        if (title.isNullOrBlank()) "(без названия)" else title,
                        it.getLong(1),
                        it.getLong(2),
                        it.getInt(3) != 0
                    )
                }
            }
        } catch (e: Throwable) {
            // нет разрешения READ_CALENDAR или провайдер недоступен — шлём пустой список
        }
        list.sortBy { it.start }
        val arr = JSONArray()
        for (e in list.take(10)) {
            arr.put(
                JSONObject().put("title", e.title).put("start", e.start)
                    .put("end", e.end).put("allDay", e.allDay)
            )
        }
        return JSONObject().put("t", "cal").put("events", arr)
    }
}
