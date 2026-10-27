package de.klaffky.spectatilus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

data class Playlist(val id: String, val name: String, val url: String)

/** Liest, filtert, sortiert und speichert M3U-Playlists. */
object LiveTvStore {
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("livetv", Context.MODE_PRIVATE)
    private val dir get() = File(context.filesDir, "playlists").apply { mkdirs() }

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    // ---------- Playlists ----------

    fun playlists(): List<Playlist> = JSONArray(prefs.getString("playlists", "[]")).mapObjects {
        Playlist(it.optString("id"), it.optString("name"), it.optString("url"))
    }

    private fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    fun add(url: String): Playlist {
        val clean = url.trim()
        val host = runCatching { android.net.Uri.parse(clean).host }.getOrNull()
        val base = host?.removePrefix("www.") ?: clean.substringAfterLast('/').ifEmpty { "Playlist" }
        val existing = playlists()
        existing.firstOrNull { it.url == clean }?.let { return it }
        var name = base
        var n = 2
        while (existing.any { it.name == name }) name = "$base ${n++}"
        val p = Playlist(UUID.randomUUID().toString().take(8), name, clean)
        savePlaylists(existing + p)
        return p
    }

    fun remove(id: String) {
        savePlaylists(playlists().filter { it.id != id })
        File(dir, "$id.json").delete()
        if (selectedId == id) selectedId = null
    }

    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    fun lastChannel(playlistId: String): String? = prefs.getString("last_$playlistId", null)
    fun setLastChannel(playlistId: String, key: String) = prefs.edit().putString("last_$playlistId", key).apply()

    // ---------- feste Nummern für Sender außerhalb der klassischen Liste ----------

    @Synchronized
    private fun numbersFor(keys: Collection<String>, names: Map<String, String>): Map<String, Int> {
        val stored = JSONObject(prefs.getString("numbers", "{}") ?: "{}")
        val result = HashMap<String, Int>()
        var next = maxOf(ChannelOrder.FIRST_EXTRA, stored.keys().asSequence().map { stored.optInt(it) }.maxOrNull()?.plus(1) ?: 0)
        // Neue Sender alphabetisch einsortieren, damit die Vergabe nachvollziehbar ist
        for (k in keys.sortedBy { names[it]?.lowercase() ?: it }) {
            val n = if (stored.has(k)) stored.optInt(k) else next++.also { stored.put(k, it) }
            result[k] = n
        }
        prefs.edit().putString("numbers", stored.toString()).apply()
        return result
    }

    // ---------- Laden ----------

    private fun open(url: String): BufferedReader {
        if (url.startsWith("/") || url.startsWith("file:", true)) {
            return File(url.removePrefix("file://").removePrefix("file:")).bufferedReader()
        }
        val client = JellyfinApi.http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()
        val resp = client.newCall(Request.Builder().url(url).header("User-Agent", "Spectatilus/1.0 (Linux; Android) VLC").build()).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw ApiException(resp.code, "Playlist konnte nicht geladen werden (HTTP ${resp.code}).")
        }
        val body = resp.body ?: throw IllegalStateException("Leere Antwort")
        return BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
    }

    /** Lädt die Playlist (Cache, sonst Internet), filtert deutsche Sender und sortiert sie fest. */
    suspend fun load(p: Playlist, forceRefresh: Boolean = false): ChannelList = withContext(Dispatchers.IO) {
        val cache = File(dir, "${p.id}.json")
        if (!forceRefresh && cache.exists()) {
            runCatching { return@withContext readCache(cache) }
        }
        val list = open(p.url).use { reader ->
            ChannelBuilder.build(reader) { keys, names -> numbersFor(keys, names) }
        }
        writeCache(cache, list)
        list
    }

    private fun writeCache(file: File, list: ChannelList) {
        val arr = JSONArray()
        list.channels.forEach { c ->
            arr.put(
                JSONObject().put("key", c.key).put("number", c.number).put("name", c.name).put("logo", c.logo)
                    .put("urls", JSONArray(c.urls)).put("fav", c.favorite).put("group", c.group).put("ua", c.userAgent)
            )
        }
        file.writeText(JSONObject().put("updatedAt", list.updatedAt).put("hidden", list.hidden).put("channels", arr).toString())
    }

    private fun readCache(file: File): ChannelList {
        val o = JSONObject(file.readText())
        val channels = o.arr("channels").mapObjects {
            Channel(
                key = it.optString("key"),
                number = it.optInt("number"),
                name = it.optString("name"),
                logo = it.str("logo"),
                urls = it.arr("urls").strings(),
                favorite = it.bool("fav"),
                group = it.str("group"),
                userAgent = it.str("ua"),
            )
        }
        return ChannelList(ChannelBuilder.sort(channels), o.optInt("hidden"), o.optLong("updatedAt"))
    }
}

/** Übergabe der aktuellen Senderliste an den Live-Player (gleicher Prozess). */
object LiveSession {
    var playlistId: String? = null
    var list: ChannelList? = null
}
