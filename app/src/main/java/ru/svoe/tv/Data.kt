package ru.svoe.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Все настройки лаунчера. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("svoe", Context.MODE_PRIVATE)

    private fun list(key: String, def: String = ""): List<String> =
        (p.getString(key, def) ?: def).split("|").filter { it.isNotEmpty() }

    private fun put(key: String, v: List<String>) {
        p.edit().putString(key, v.joinToString("|")).apply()
    }

    /** id закреплённых: пакет приложения или builtin:iptv / builtin:settings */
    var pinned: List<String>
        get() = list("pinned")
        set(v) = put("pinned", v)

    var recents: List<String>
        get() = list("recents")
        set(v) = put("recents", v)

    fun pushRecent(id: String) {
        recents = (listOf(id) + recents.filter { it != id }).take(8)
    }

    /** Какие виджеты включены: fin, fx, media, cal */
    var widgets: List<String>
        get() = list("widgets", "fin|fx|media")
        set(v) = put("widgets", v)

    var playlistUrl: String
        get() = p.getString("playlist_url", "") ?: ""
        set(v) { p.edit().putString("playlist_url", v).apply() }

    /** Стартовый набор закрепов уже выставлялся (чтобы не возвращать его после ручной очистки) */
    var autopinned: Boolean
        get() = p.getBoolean("autopinned", false)
        set(v) { p.edit().putBoolean("autopinned", v).apply() }

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }

    var lang: String
        get() = p.getString("lang", "ru") ?: "ru"
        set(v) { p.edit().putString("lang", v).apply() }

    var region: String
        get() = p.getString("region", "ru") ?: "ru"
        set(v) { p.edit().putString("region", v).apply() }

    // Город для погоды (по умолчанию Краснодар)
    var city: String
        get() = p.getString("city", "Краснодар") ?: "Краснодар"
        set(v) { p.edit().putString("city", v).apply() }

    var lat: Double
        get() = p.getFloat("lat", 45.0355f).toDouble()
        set(v) { p.edit().putFloat("lat", v.toFloat()).apply() }

    var lon: Double
        get() = p.getFloat("lon", 38.9753f).toDouble()
        set(v) { p.edit().putFloat("lon", v.toFloat()).apply() }

    // Пароль экрана блокировки: хранится только хеш с солью
    val hasPin: Boolean get() = p.getString("pin", null) != null
    val pinLen: Int get() = p.getInt("pin_len", 4)

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString().take(8)
        p.edit().putString("pin", "$salt:${hash(salt, pin)}").putInt("pin_len", pin.length).apply()
    }

    fun checkPin(pin: String): Boolean {
        val s = p.getString("pin", null) ?: return true
        val parts = s.split(":")
        return parts.size == 2 && hash(parts[0], pin) == parts[1]
    }

    fun clearPin() {
        p.edit().remove("pin").remove("pin_len").apply()
    }

    /** Код привязки телефона (показывается на ТВ, вводится/сканируется на телефоне) */
    val pairCode: String
        get() {
            var c = p.getString("pair_code", null)
            if (c == null) {
                c = (100000 + SecureRandom().nextInt(900000)).toString()
                p.edit().putString("pair_code", c).apply()
            }
            return c
        }
}

data class Channel(val name: String, val url: String)

object Playlist {
    private fun file(ctx: Context) = File(ctx.filesDir, "playlist.m3u")

    suspend fun readCached(ctx: Context): List<Channel> = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (f.exists()) parse(f.readText()) else emptyList()
    }

    /** Скачивает плейлист, кэширует на диск и возвращает каналы. Бросает исключение при ошибке. */
    suspend fun download(ctx: Context, url: String): List<Channel> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection().apply { connectTimeout = 10_000; readTimeout = 20_000 }
        val text = conn.getInputStream().bufferedReader().use { it.readText() }
        val list = parse(text)
        if (list.isNotEmpty()) file(ctx).writeText(text)
        list
    }

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name: String? = null
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXTINF") -> name = l.substringAfterLast(",").trim()
                l.isNotEmpty() && !l.startsWith("#") -> { out += Channel(name?.ifBlank { null } ?: l, l); name = null }
            }
        }
        return out
    }
}
