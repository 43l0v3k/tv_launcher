package ru.svoe.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
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

    /** Обои: auto | morning | day | evening | night */
    var wallpaper: String
        get() = p.getString("wallpaper", "auto") ?: "auto"
        set(v) { p.edit().putString("wallpaper", v).apply() }

    fun pairLink(): String {
        val n = try { android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.name } catch (_: Throwable) { null } ?: ""
        return "svoetv://pair?code=$pairCode&name=" + java.net.URLEncoder.encode(n, "UTF-8")
    }
    fun resetPairCode() { p.edit().remove("pair_code").apply() }

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

    class PlaylistError(msg: String) : Exception(msg)

    /** Превращает «страничные» ссылки (GitHub, Gist, Pastebin, Drive, Dropbox) в прямые. */
    fun normalize(input: String): String {
        var u = input.trim().replace(" ", "")
        if (u.isEmpty()) return u
        if (!u.contains("://")) u = "https://$u"
        Regex("^https?://(www\\.)?github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(u)?.let {
            return "https://raw.githubusercontent.com/${it.groupValues[2]}/${it.groupValues[3]}/${it.groupValues[4]}"
        }
        Regex("^https?://gist\\.github\\.com/([^/]+)/([0-9a-f]+)/?$").find(u)?.let {
            return "https://gist.githubusercontent.com/${it.groupValues[1]}/${it.groupValues[2]}/raw"
        }
        Regex("^https?://(www\\.)?pastebin\\.com/(?!raw/)([A-Za-z0-9]+)/?$").find(u)?.let {
            return "https://pastebin.com/raw/${it.groupValues[2]}"
        }
        Regex("^https?://drive\\.google\\.com/file/d/([^/]+)").find(u)?.let {
            return "https://drive.google.com/uc?export=download&id=${it.groupValues[1]}"
        }
        if (u.contains("dropbox.com")) u = u.replace("dl=0", "dl=1")
        return u
    }

    /** Скачивает плейлист, кэширует на диск и возвращает каналы. Бросает понятную ошибку. */
    suspend fun download(ctx: Context, input: String): List<Channel> = withContext(Dispatchers.IO) {
        val start = normalize(input)
        if (start.isEmpty()) throw PlaylistError("Вставь ссылку на плейлист")
        var url = start
        var bytes: ByteArray? = null
        var ctype = ""
        for (hop in 0 until 8) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000; readTimeout = 25_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "gzip")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw PlaylistError("Редирект без адреса")
                url = URL(URL(url), loc).toString()
                c.disconnect()
                continue
            }
            if (code != 200) throw PlaylistError("Сервер ответил: HTTP $code")
            ctype = (c.contentType ?: "").lowercase()
            val raw = c.inputStream.use { it.readBytes() }
            bytes = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte())
                java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() } else raw
            break
        }
        val data = bytes ?: throw PlaylistError("Слишком много перенаправлений")
        var text = String(data, Charsets.UTF_8)
        if (text.count { it == '\uFFFD' } > 5) text = String(data, charset("windows-1251"))
        text = text.removePrefix("\uFEFF")
        val head = text.trimStart().take(300).lowercase()
        if (head.startsWith("<!doctype html") || head.startsWith("<html") || ctype.contains("text/html"))
            throw PlaylistError("Это веб-страница, а не плейлист. Нужна прямая ссылка на файл .m3u / .m3u8")
        if (ctype.startsWith("video/") || ctype.startsWith("audio/") || ctype.contains("mpegurl") && !text.contains("#EXTINF") && !text.contains("#EXT-X-STREAM-INF"))
            return@withContext listOf(Channel("Поток", url)).also { }
        val list = parse(text, url)
        if (list.isNotEmpty()) file(ctx).writeText(text)
        list
    }

    fun parse(text: String, base: String = ""): List<Channel> {
        // один HLS-поток (m3u8 с сегментами) — это один канал, а не список
        if (text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-TARGETDURATION")) {
            return if (base.isNotEmpty()) listOf(Channel("Поток", base)) else emptyList()
        }
        // PLS
        if (text.trimStart().startsWith("[playlist]", ignoreCase = true)) {
            val files = Regex("(?im)^File(\\d+)=(.+)$").findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim() }
            val titles = Regex("(?im)^Title(\\d+)=(.+)$").findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim() }
            return files.map { (k, v) -> Channel(titles[k]?.ifBlank { null } ?: v, resolve(v, base)) }
        }
        val out = mutableListOf<Channel>()
        var name: String? = null
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXTINF", ignoreCase = true) -> name = extName(l)
                l.startsWith("#") || l.isEmpty() -> {}
                l.contains("://") || base.isNotEmpty() -> {
                    out += Channel(name?.ifBlank { null } ?: l.substringAfterLast('/').substringBefore('?').ifBlank { l }, resolve(l, base))
                    name = null
                }
            }
        }
        return out
    }

    private fun resolve(u: String, base: String): String =
        if (u.contains("://") || base.isEmpty()) u else runCatching { URL(URL(base), u).toString() }.getOrDefault(u)

    /** Имя канала: текст после последней запятой ВНЕ кавычек; если пусто — tvg-name. */
    private fun extName(l: String): String {
        var q = false
        var cut = -1
        for (i in l.indices) {
            if (l[i] == '"') q = !q
            else if (l[i] == ',' && !q) cut = i
        }
        val n = if (cut >= 0) l.substring(cut + 1).trim() else ""
        if (n.isNotEmpty()) return n
        return Regex("tvg-name=\"([^\"]*)\"").find(l)?.groupValues?.get(1)?.trim().orEmpty()
    }
}
