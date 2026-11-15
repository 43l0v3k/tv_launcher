package ru.svoe.phone

import android.content.Context

/** Сохранённый ТВ: MAC, имя и код привязки. */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("svoe_phone", Context.MODE_PRIVATE)

    fun mac(c: Context): String = p(c).getString("mac", "") ?: ""
    fun name(c: Context): String = p(c).getString("name", "") ?: ""
    fun code(c: Context): String = p(c).getString("code", "") ?: ""

    fun setTv(c: Context, mac: String, name: String) {
        p(c).edit().putString("mac", mac).putString("name", name).apply()
    }

    fun setName(c: Context, name: String) {
        p(c).edit().putString("name", name).apply()
    }

    fun setCode(c: Context, code: String) {
        p(c).edit().putString("code", code).apply()
    }
}
