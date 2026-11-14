package ru.svoe.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** Настройки лаунчера: закреплённые приложения и ссылка на плейлист. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("svoe", Context.MODE_PRIVATE)

    var pinned: List<String>
        get() = (p.getString("pinned", "") ?: "").split("|").filter { it.isNotEmpty() }
        set(v) { p.edit().putString("pinned", v.joinToString("|")).apply() }

    var playlistUrl: String
        get() = p.getString("playlist_url", "") ?: ""
        set(v) { p.edit().putString("playlist_url", v).apply() }
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
