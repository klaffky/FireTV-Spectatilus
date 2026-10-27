package de.klaffky.spectatilus.data

import org.json.JSONArray
import org.json.JSONObject

// --- kleine Helfer für org.json (JSON-null soll zu Kotlin-null werden) ---

fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
fun JSONObject.double(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
fun JSONObject.bool(key: String): Boolean = optBoolean(key, false)
fun JSONObject.obj(key: String): JSONObject? = if (has(key) && !isNull(key)) optJSONObject(key) else null
fun JSONObject.arr(key: String): JSONArray? = if (has(key) && !isNull(key)) optJSONArray(key) else null

inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out += transform(it) }
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
}

data class ServerInfo(val id: String, val name: String, val version: String)

data class PublicUser(val id: String, val name: String, val imageTag: String?)

data class AuthResult(val token: String, val userId: String, val userName: String)

data class Person(val id: String, val name: String, val role: String?, val type: String?, val imageTag: String?)

data class Item(
    val id: String,
    val name: String,
    val type: String,
    val year: Int?,
    val officialRating: String?,
    val communityRating: Double?,
    val criticRating: Int?,
    val runTimeTicks: Long?,
    val overview: String?,
    val tagline: String?,
    val genres: List<String>,
    val primaryTag: String?,
    val primaryAspect: Double?,
    val logoTag: String?,
    val thumbTag: String?,
    val backdropTags: List<String>,
    val parentBackdropItemId: String?,
    val parentBackdropTags: List<String>,
    val parentLogoItemId: String?,
    val parentLogoTag: String?,
    val parentThumbItemId: String?,
    val parentThumbTag: String?,
    val seriesId: String?,
    val seriesName: String?,
    val seriesPrimaryTag: String?,
    val seasonId: String?,
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val collectionType: String?,
    val positionTicks: Long,
    val played: Boolean,
    val favorite: Boolean,
    val playedPercentage: Double?,
    val unplayedCount: Int?,
    val childCount: Int?,
    val people: List<Person>,
    val badges: List<String>,
) {
    val isFolderLike get() = type == "Series" || type == "Season" || type == "BoxSet" || type == "CollectionFolder" || type == "Folder"
    val isPlayable get() = type == "Movie" || type == "Episode" || type == "Video" || type == "MusicVideo"
    val positionMs get() = positionTicks / 10_000
    val runtimeMs get() = (runTimeTicks ?: 0) / 10_000

    /** Fortschritt 0..1 für den Balken unter „Weiter ansehen“. */
    val progress: Float
        get() = when {
            playedPercentage != null && playedPercentage > 0 -> (playedPercentage / 100).toFloat()
            runTimeTicks != null && runTimeTicks > 0 && positionTicks > 0 -> positionTicks.toFloat() / runTimeTicks
            else -> 0f
        }.coerceIn(0f, 1f)

    /** „S1 · E3“ für Episoden */
    val episodeLabel: String?
        get() = if (type == "Episode" && indexNumber != null) {
            (parentIndexNumber?.let { "S$it · " } ?: "") + "E$indexNumber"
        } else null

    companion object {
        fun parse(o: JSONObject): Item {
            val imageTags = o.obj("ImageTags")
            val userData = o.obj("UserData")
            return Item(
                id = o.optString("Id"),
                name = o.str("Name") ?: "",
                type = o.str("Type") ?: "",
                year = o.int("ProductionYear"),
                officialRating = o.str("OfficialRating"),
                communityRating = o.double("CommunityRating"),
                criticRating = o.int("CriticRating"),
                runTimeTicks = o.long("RunTimeTicks"),
                overview = o.str("Overview"),
                tagline = o.arr("Taglines").strings().firstOrNull(),
                genres = o.arr("Genres").strings(),
                primaryTag = imageTags?.str("Primary"),
                primaryAspect = o.double("PrimaryImageAspectRatio"),
                logoTag = imageTags?.str("Logo"),
                thumbTag = imageTags?.str("Thumb"),
                backdropTags = o.arr("BackdropImageTags").strings(),
                parentBackdropItemId = o.str("ParentBackdropItemId"),
                parentBackdropTags = o.arr("ParentBackdropImageTags").strings(),
                parentLogoItemId = o.str("ParentLogoItemId"),
                parentLogoTag = o.str("ParentLogoImageTag"),
                parentThumbItemId = o.str("ParentThumbItemId"),
                parentThumbTag = o.str("ParentThumbImageTag"),
                seriesId = o.str("SeriesId"),
                seriesName = o.str("SeriesName"),
                seriesPrimaryTag = o.str("SeriesPrimaryImageTag"),
                seasonId = o.str("SeasonId"),
                indexNumber = o.int("IndexNumber"),
                parentIndexNumber = o.int("ParentIndexNumber"),
                collectionType = o.str("CollectionType"),
                positionTicks = userData?.long("PlaybackPositionTicks") ?: 0L,
                played = userData?.bool("Played") ?: false,
                favorite = userData?.bool("IsFavorite") ?: false,
                playedPercentage = userData?.double("PlayedPercentage"),
                unplayedCount = userData?.int("UnplayedItemCount"),
                childCount = o.int("ChildCount"),
                people = o.arr("People").mapObjects {
                    Person(it.optString("Id"), it.str("Name") ?: "", it.str("Role"), it.str("Type"), it.str("PrimaryImageTag"))
                },
                badges = badgesFor(o),
            )
        }

        /** Qualitäts-Abzeichen wie bei Spectati: 4K, Dolby Vision, HDR10, Atmos … */
        private fun badgesFor(o: JSONObject): List<String> {
            val streams = o.arr("MediaStreams")
                ?: o.arr("MediaSources")?.optJSONObject(0)?.arr("MediaStreams")
                ?: return emptyList()
            val list = streams.mapObjects { it }
            val video = list.firstOrNull { it.str("Type") == "Video" }
            val audio = list.filter { it.str("Type") == "Audio" }.let { a -> a.firstOrNull { it.bool("IsDefault") } ?: a.firstOrNull() }
            val out = mutableListOf<String>()
            if (video != null) {
                val w = video.int("Width") ?: 0
                out += when {
                    w >= 3200 -> "4K"
                    w >= 1800 -> "HD 1080p"
                    w >= 1200 -> "HD 720p"
                    w > 0 -> "SD"
                    else -> ""
                }
                val range = video.str("VideoRangeType") ?: ""
                when {
                    range.startsWith("DOVI") -> out += "Dolby Vision"
                    range == "HDR10Plus" -> out += "HDR10+"
                    range == "HDR10" -> out += "HDR10"
                    range == "HLG" -> out += "HLG"
                }
            }
            if (audio != null) {
                val text = ((audio.str("Profile") ?: "") + " " + (audio.str("DisplayTitle") ?: "") + " " + (audio.str("Title") ?: "")).lowercase()
                val codec = audio.str("Codec")?.lowercase() ?: ""
                when {
                    "atmos" in text -> out += "Dolby Atmos"
                    codec == "truehd" -> out += "Dolby TrueHD"
                    codec == "eac3" -> out += "Dolby Digital+"
                    codec == "ac3" -> out += "Dolby Digital"
                    codec.startsWith("dts") -> out += if ("dts:x" in text || "dts-x" in text) "DTS:X" else "DTS"
                }
                when (audio.int("Channels")) {
                    6 -> out += "5.1"
                    8 -> out += "7.1"
                }
            }
            if (list.any { it.str("Type") == "Subtitle" }) out += "UT"
            return out.filter { it.isNotEmpty() }
        }
    }
}

data class Page(val items: List<Item>, val total: Int)

fun formatRuntime(ticks: Long?): String? {
    if (ticks == null || ticks <= 0) return null
    val minutes = ticks / 10_000_000 / 60
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h Std. $m Min."
        h > 0 -> "$h Std."
        else -> "$m Min."
    }
}

fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
