package de.klaffky.spectatilus.data

import android.net.Uri
import android.os.Build
import de.klaffky.spectatilus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Schlanker Jellyfin-Client. Spricht zuerst die neuen Endpunkte (Jellyfin 10.9+) an
 * und fällt bei 404 auf die älteren /Users/{id}/…-Routen zurück.
 */
class JellyfinApi(
    baseUrl: String,
    private val deviceId: String,
    var token: String? = null,
    var userId: String? = null,
) {
    val baseUrl: String = baseUrl.trimEnd('/')

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        const val CLIENT = "Fire TV Spectatilus"
        val DEVICE = "Fire TV (${Build.MODEL})"

        /** Fügt fehlendes Schema und den Standard-Port 8096 hinzu. */
        fun normalizeUrl(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.isEmpty()) return s
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                val hostPart = s.substringBefore('/')
                val https = hostPart.endsWith(".ts.net", true)
                s = (if (https) "https://" else "http://") + s
                if (!https && !hostPart.contains(':')) {
                    s = "http://$hostPart:8096" + s.removePrefix("http://$hostPart")
                }
            }
            return s
        }
    }

    val authHeader: String
        get() = buildString {
            append("MediaBrowser Client=\"$CLIENT\", Device=\"$DEVICE\", DeviceId=\"$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\"")
            token?.let { append(", Token=\"$it\"") }
        }

    private suspend fun call(method: String, path: String, body: Any? = null, timeoutMs: Long? = null): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", authHeader)
                .header("X-Emby-Authorization", authHeader)
                .header("Accept", "application/json")
            val payload = (body?.toString() ?: "").toRequestBody(JSON)
            when (method) {
                "POST" -> builder.post(payload)
                "DELETE" -> builder.delete()
                else -> builder.get()
            }
            val client = if (timeoutMs != null) {
                http.newBuilder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
            } else http
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw ApiException(resp.code, "HTTP ${resp.code}: ${text.take(200)}")
                text
            }
        }

    /** Neuer Endpunkt, bei 404 der alte. */
    private suspend fun callCompat(method: String, path: String, legacy: String, body: Any? = null): String =
        try {
            call(method, path, body)
        } catch (e: ApiException) {
            if (e.code == 404 || e.code == 405) call(method, legacy, body) else throw e
        }

    private fun enc(s: String) = Uri.encode(s)
    private val uid get() = userId ?: ""

    private val listFields = "PrimaryImageAspectRatio,Overview,Genres,ParentId,ChildCount"
    private val imageParams = "EnableImageTypes=Primary,Backdrop,Thumb,Logo&ImageTypeLimit=1"

    private fun parseItems(text: String): Page {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            return Page(arr.mapObjects { Item.parse(it) }, arr.length())
        }
        val o = JSONObject(trimmed)
        val items = o.arr("Items").mapObjects { Item.parse(it) }
        return Page(items, o.int("TotalRecordCount") ?: items.size)
    }

    // ---------- Server & Anmeldung ----------

    suspend fun publicInfo(timeoutMs: Long? = null): ServerInfo {
        val o = JSONObject(call("GET", "/System/Info/Public", timeoutMs = timeoutMs))
        return ServerInfo(o.optString("Id"), o.str("ServerName") ?: "Jellyfin", o.str("Version") ?: "")
    }

    suspend fun publicUsers(): List<PublicUser> = try {
        JSONArray(call("GET", "/Users/Public")).mapObjects {
            PublicUser(it.optString("Id"), it.str("Name") ?: "", it.str("PrimaryImageTag"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseAuth(text: String): AuthResult {
        val o = JSONObject(text)
        val user = o.obj("User") ?: JSONObject()
        return AuthResult(o.optString("AccessToken"), user.optString("Id"), user.str("Name") ?: "")
    }

    suspend fun authenticate(username: String, password: String): AuthResult {
        val body = JSONObject().put("Username", username).put("Pw", password)
        return parseAuth(call("POST", "/Users/AuthenticateByName", body))
    }

    suspend fun quickConnectEnabled(): Boolean = try {
        call("GET", "/QuickConnect/Enabled").trim().equals("true", true)
    } catch (e: Exception) {
        false
    }

    /** Liefert (Secret, Code) */
    suspend fun quickConnectInitiate(): Pair<String, String> {
        val text = try {
            call("POST", "/QuickConnect/Initiate")
        } catch (e: ApiException) {
            call("GET", "/QuickConnect/Initiate")
        }
        val o = JSONObject(text)
        return o.optString("Secret") to o.optString("Code")
    }

    suspend fun quickConnectAuthorized(secret: String): Boolean =
        JSONObject(call("GET", "/QuickConnect/Connect?secret=${enc(secret)}")).bool("Authenticated")

    suspend fun authenticateQuickConnect(secret: String): AuthResult =
        parseAuth(call("POST", "/Users/AuthenticateWithQuickConnect", JSONObject().put("Secret", secret)))

    suspend fun logout() {
        runCatching { call("POST", "/Sessions/Logout") }
    }

    // ---------- Bibliothek ----------

    suspend fun views(): List<Item> =
        parseItems(callCompat("GET", "/UserViews?userId=$uid", "/Users/$uid/Views")).items

    suspend fun resume(limit: Int = 24): List<Item> {
        val q = "Limit=$limit&MediaTypes=Video&Fields=$listFields&$imageParams&EnableTotalRecordCount=false"
        return parseItems(callCompat("GET", "/UserItems/Resume?userId=$uid&$q", "/Users/$uid/Items/Resume?$q")).items
    }

    suspend fun nextUp(limit: Int = 24, seriesId: String? = null): List<Item> {
        val series = seriesId?.let { "&SeriesId=$it" } ?: ""
        return parseItems(call("GET", "/Shows/NextUp?userId=$uid&Limit=$limit$series&Fields=$listFields&$imageParams&EnableTotalRecordCount=false")).items
    }

    suspend fun latest(parentId: String, limit: Int = 24): List<Item> {
        val q = "ParentId=$parentId&Limit=$limit&Fields=$listFields&$imageParams&GroupItems=true"
        return parseItems(callCompat("GET", "/Items/Latest?userId=$uid&$q", "/Users/$uid/Items/Latest?$q")).items
    }

    suspend fun items(
        types: String? = null,
        parentId: String? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        start: Int = 0,
        limit: Int = 100,
        search: String? = null,
        filters: String? = null,
        extra: String = "",
        recursive: Boolean = true,
    ): Page {
        val q = buildString {
            append("Recursive=$recursive&Fields=$listFields&$imageParams")
            append("&SortBy=$sortBy&SortOrder=$sortOrder&StartIndex=$start&Limit=$limit")
            types?.let { append("&IncludeItemTypes=$it") }
            parentId?.let { append("&ParentId=$it") }
            search?.let { append("&SearchTerm=${enc(it)}") }
            filters?.let { append("&Filters=$it") }
            append(extra)
        }
        return parseItems(callCompat("GET", "/Items?userId=$uid&$q", "/Users/$uid/Items?$q"))
    }

    suspend fun item(id: String): Item {
        val text = callCompat("GET", "/Items/$id?userId=$uid", "/Users/$uid/Items/$id")
        return Item.parse(JSONObject(text))
    }

    suspend fun seasons(seriesId: String): List<Item> =
        parseItems(call("GET", "/Shows/$seriesId/Seasons?userId=$uid&Fields=$listFields&$imageParams")).items

    suspend fun episodes(seriesId: String, seasonId: String? = null, startItemId: String? = null, limit: Int? = null): List<Item> {
        val q = buildString {
            append("userId=$uid&Fields=Overview,PrimaryImageAspectRatio,MediaStreams&$imageParams")
            seasonId?.let { append("&SeasonId=$it") }
            startItemId?.let { append("&StartItemId=$it") }
            limit?.let { append("&Limit=$it") }
        }
        return parseItems(call("GET", "/Shows/$seriesId/Episodes?$q")).items
    }

    suspend fun similar(id: String, limit: Int = 16): List<Item> = try {
        parseItems(call("GET", "/Items/$id/Similar?userId=$uid&Limit=$limit&Fields=$listFields")).items
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun setPlayed(id: String, played: Boolean) {
        val m = if (played) "POST" else "DELETE"
        callCompat(m, "/UserPlayedItems/$id?userId=$uid", "/Users/$uid/PlayedItems/$id")
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val m = if (favorite) "POST" else "DELETE"
        callCompat(m, "/UserFavoriteItems/$id?userId=$uid", "/Users/$uid/FavoriteItems/$id")
    }

    // ---------- Wiedergabe ----------

    suspend fun playbackInfo(itemId: String, profile: JSONObject, maxBitrate: Int, allowDirect: Boolean): JSONObject {
        val body = JSONObject()
            .put("UserId", uid)
            .put("DeviceProfile", profile)
            .put("EnableDirectPlay", allowDirect)
            .put("EnableDirectStream", allowDirect)
            .put("EnableTranscoding", true)
            .put("AllowVideoStreamCopy", allowDirect)
            .put("AllowAudioStreamCopy", allowDirect)
            .put("AutoOpenLiveStream", true)
        if (maxBitrate > 0) body.put("MaxStreamingBitrate", maxBitrate)
        val q = "userId=$uid" + if (maxBitrate > 0) "&maxStreamingBitrate=$maxBitrate" else ""
        return JSONObject(call("POST", "/Items/$itemId/PlaybackInfo?$q", body))
    }

    suspend fun reportPlayback(event: String, body: JSONObject) {
        val path = when (event) {
            "start" -> "/Sessions/Playing"
            "stop" -> "/Sessions/Playing/Stopped"
            else -> "/Sessions/Playing/Progress"
        }
        runCatching { call("POST", path, body) }
    }

    /** Hängt das Token an, damit auch der Player (ohne eigene Header) Zugriff hat. */
    fun withApiKey(pathOrUrl: String): String {
        val full = if (pathOrUrl.startsWith("http")) pathOrUrl else baseUrl + pathOrUrl
        if (full.contains("api_key=", true) || full.contains("ApiKey=", true)) return full
        val sep = if (full.contains('?')) "&" else "?"
        return "$full${sep}api_key=${token ?: ""}"
    }

    // ---------- Bilder ----------

    fun imageUrl(itemId: String, type: String, tag: String?, maxWidth: Int? = null, maxHeight: Int? = null, index: Int? = null): String {
        val idx = index?.let { "/$it" } ?: ""
        val params = buildList {
            maxWidth?.let { add("maxWidth=$it") }
            maxHeight?.let { add("maxHeight=$it") }
            tag?.let { add("tag=$it") }
            add("quality=90")
        }.joinToString("&")
        return "$baseUrl/Items/$itemId/Images/$type$idx?$params"
    }

    fun userImageUrl(userId: String, tag: String?): String =
        "$baseUrl/Users/$userId/Images/Primary?maxHeight=240&quality=90" + (tag?.let { "&tag=$it" } ?: "")

    fun posterUrl(item: Item, height: Int = 450): String? = when {
        item.type == "Episode" && item.seriesId != null && item.seriesPrimaryTag != null ->
            imageUrl(item.seriesId, "Primary", item.seriesPrimaryTag, maxHeight = height)
        item.primaryTag != null -> imageUrl(item.id, "Primary", item.primaryTag, maxHeight = height)
        else -> null
    }

    /** Querformat-Bild für „Weiter ansehen“ / Episoden. */
    fun landscapeUrl(item: Item, width: Int = 640): String? = when {
        item.type == "Episode" && item.primaryTag != null -> imageUrl(item.id, "Primary", item.primaryTag, maxWidth = width)
        item.thumbTag != null -> imageUrl(item.id, "Thumb", item.thumbTag, maxWidth = width)
        item.parentThumbItemId != null && item.parentThumbTag != null ->
            imageUrl(item.parentThumbItemId, "Thumb", item.parentThumbTag, maxWidth = width)
        else -> backdropUrl(item, width) ?: posterUrl(item)
    }

    fun backdropUrl(item: Item, width: Int = 1920): String? = when {
        item.backdropTags.isNotEmpty() -> imageUrl(item.id, "Backdrop", item.backdropTags.first(), maxWidth = width, index = 0)
        item.parentBackdropItemId != null && item.parentBackdropTags.isNotEmpty() ->
            imageUrl(item.parentBackdropItemId, "Backdrop", item.parentBackdropTags.first(), maxWidth = width, index = 0)
        else -> null
    }

    fun logoUrl(item: Item, width: Int = 600): String? = when {
        item.logoTag != null -> imageUrl(item.id, "Logo", item.logoTag, maxWidth = width)
        item.parentLogoItemId != null && item.parentLogoTag != null ->
            imageUrl(item.parentLogoItemId, "Logo", item.parentLogoTag, maxWidth = width)
        else -> null
    }

    fun personUrl(p: Person): String? = p.imageTag?.let { imageUrl(p.id, "Primary", it, maxHeight = 300) }
}
