package de.klaffky.spectatilus.data

import java.io.BufferedReader
import java.text.Normalizer

// Reine Kotlin-Logik (ohne Android), damit sie sich auch per Unit-Test prüfen lässt.

/**
 * Ein Sender nach dem Sortieren. [number] ist die feste „Programmplatz“-Nummer,
 * die in jeder Playlist gleich bleibt (ProSieben ist immer 7).
 */
data class Channel(
    val key: String,
    val number: Int,
    val name: String,
    val logo: String?,
    val urls: List<String>,
    val favorite: Boolean,
    val group: String?,
    val userAgent: String?,
)

data class ChannelList(val channels: List<Channel>, val hidden: Int, val updatedAt: Long) {
    val favorites get() = channels.filter { it.favorite }
    val regular get() = channels.filter { !it.favorite }

    /** „★1“, „★2“ … für Bundesliga, sonst die feste Nummer. */
    fun label(c: Channel): String = if (c.favorite) "★" + (favorites.indexOf(c) + 1) else c.number.toString()
}

/** Die klassische Senderbelegung wie auf dem alten Receiver. Reihenfolge = Programmplatz. */
object ChannelOrder {
    private class Slot(val name: String, val aliases: List<String>, val prefixes: List<String> = emptyList())

    private val CLASSIC = listOf(
        Slot("Das Erste", listOf("daserste", "ard", "arddaserste", "erstes")),
        Slot("ZDF", listOf("zdf")),
        Slot("RTL", listOf("rtl", "rtltelevision", "rtldeutschland")),
        Slot("SAT.1", listOf("sat1", "sat1deutschland")),
        Slot("VOX", listOf("vox")),
        Slot("RTLZWEI", listOf("rtlzwei", "rtl2", "rtlii")),
        Slot("ProSieben", listOf("prosieben", "pro7", "prosiebendeutschland")),
        Slot("kabel eins", listOf("kabeleins", "kabel1")),
        Slot("3sat", listOf("3sat")),
        Slot("arte", listOf("arte")),
        Slot("ZDFneo", listOf("zdfneo")),
        Slot("ZDFinfo", listOf("zdfinfo")),
        Slot("ONE", listOf("one", "ardone")),
        Slot("tagesschau24", listOf("tagesschau24")),
        Slot("phoenix", listOf("phoenix")),
        Slot("KiKA", listOf("kika")),
        Slot("Super RTL", listOf("superrtl")),
        Slot("NITRO", listOf("nitro", "rtlnitro")),
        Slot("RTLup", listOf("rtlup", "rtlplus")),
        Slot("ntv", listOf("ntv")),
        Slot("WELT", listOf("welt", "n24", "weltn24")),
        Slot("sixx", listOf("sixx")),
        Slot("ProSieben MAXX", listOf("prosiebenmaxx", "pro7maxx")),
        Slot("SAT.1 Gold", listOf("sat1gold")),
        Slot("kabel eins Doku", listOf("kabeleinsdoku", "kabel1doku")),
        Slot("DMAX", listOf("dmax")),
        Slot("TLC", listOf("tlc")),
        Slot("Tele 5", listOf("tele5")),
        Slot("SPORT1", listOf("sport1")),
        Slot("Eurosport 1", listOf("eurosport1", "eurosport")),
        Slot("Comedy Central", listOf("comedycentral")),
        Slot("Disney Channel", listOf("disneychannel")),
        Slot("Nickelodeon", listOf("nickelodeon", "nick")),
        Slot("BR Fernsehen", listOf("br", "brfernsehen", "bayerischesfernsehen"), listOf("brfernsehen", "bayerischesfernsehen")),
        Slot("WDR", listOf("wdr", "wdrfernsehen"), listOf("wdr")),
        Slot("NDR", listOf("ndr", "ndrfernsehen"), listOf("ndr")),
        Slot("MDR", listOf("mdr", "mdrfernsehen"), listOf("mdr")),
        Slot("hr-fernsehen", listOf("hr", "hrfernsehen", "hessischerrundfunk")),
        Slot("SWR", listOf("swr", "swrfernsehen"), listOf("swr")),
        Slot("rbb", listOf("rbb", "rbbfernsehen"), listOf("rbb")),
        Slot("Radio Bremen", listOf("radiobremen", "radiobrementv")),
        Slot("SR Fernsehen", listOf("sr", "srfernsehen")),
        Slot("Sky Sport News", listOf("skysportnews")),
        Slot("Sky One", listOf("skyone")),
        Slot("Sky Atlantic", listOf("skyatlantic")),
        Slot("Sky Cinema Premieren", listOf("skycinemapremieren")),
        Slot("Sky Krimi", listOf("skykrimi")),
        Slot("ServusTV Deutschland", listOf("servustv", "servustvdeutschland")),
        Slot("MTV", listOf("mtv", "mtvgermany")),
        Slot("DELUXE MUSIC", listOf("deluxemusic")),
        Slot("Bibel TV", listOf("bibeltv")),
        Slot("QVC", listOf("qvc")),
        Slot("HSE", listOf("hse", "hse24")),
    )

    /** Nicht-klassische Sender bekommen ab hier dauerhaft ihre Nummer. */
    const val FIRST_EXTRA = 100

    private val aliasToSlot: Map<String, Int> = buildMap {
        CLASSIC.forEachIndexed { i, s -> s.aliases.forEach { put(it, i) } }
    }

    fun classicSlot(key: String): Int? {
        aliasToSlot[key]?.let { return it }
        CLASSIC.forEachIndexed { i, s -> if (s.prefixes.any { key.startsWith(it) }) return i }
        return null
    }

    fun classicName(slot: Int) = CLASSIC[slot].name

    // ---------- Namen bereinigen ----------

    private val COUNTRY = "de|ger|germany|german|deutsch|deutschland"
    private val prefixRe = Regex("""^\s*[\[(|]*\s*(?:vip\s+)?(?:$COUNTRY)\s*(?:hd|fhd|uhd)?\s*[\])|:\-–—▎•»]+\s*""", RegexOption.IGNORE_CASE)
    private val prefixWordRe = Regex("""^(?:de|ger)\s+(?=\S)""", RegexOption.IGNORE_CASE)
    private val suffixRe = Regex("""[\s\[(|]+(?:$COUNTRY)[\])|]*\s*$""", RegexOption.IGNORE_CASE)
    private val qualityRe = Regex("""(?<![a-z0-9])(?:uhd|fhd|hd\+?|sd|4k|8k|hevc|h\.?265|h\.?264|1080[pi]?|720p|2160p|50fps|60fps|full\s*hd|ultra\s*hd|backup|raw|ᴴᴰ|ᵁᴴᴰ|ᶠᴴᴰ)(?![a-z0-9])""", RegexOption.IGNORE_CASE)

    /** Anzeigename ohne „DE:“-Präfix und Qualitätskürzel. */
    fun cleanName(raw: String): String {
        var s = raw.trim()
        repeat(2) { s = s.replace(prefixRe, "").replace(prefixWordRe, "") }
        s = s.replace(suffixRe, "")
        s = s.replace(qualityRe, " ").replace(Regex("""[\[(]\s*[\])]"""), " ")
        return s.replace(Regex("""\s{2,}"""), " ").trim(' ', '-', '|', ':', '•').ifEmpty { raw.trim() }
    }

    /** Vergleichsschlüssel: klein, ohne Umlaute/Satzzeichen („SAT.1 HD“ → „sat1“). */
    fun keyOf(cleanName: String): String {
        val t = cleanName.lowercase()
            .replace("+", "plus")
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        val noAccents = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.replace(Regex("[^a-z0-9]"), "")
    }

    fun qualityScore(raw: String): Int {
        val s = raw.lowercase()
        return when {
            Regex("""(?<![a-z])fhd|1080|full\s*hd""").containsMatchIn(s) -> 5
            Regex("""(?<![a-z])hd(?![a-z])|720p""").containsMatchIn(s) -> 4
            Regex("""uhd|4k|2160""").containsMatchIn(s) -> 3
            Regex("""(?<![a-z])sd(?![a-z])""").containsMatchIn(s) -> 1
            else -> 2
        }
    }

    // ---------- Deutschland-Filter ----------

    private val deMarker = Regex("""(?:^|[\s\[(|:/,_.-])(?:$COUNTRY)(?:$|[\s\])|:/,_.-])""", RegexOption.IGNORE_CASE)
    private val foreignMarker = Regex(
        """(?:^|[\s\[(|:/_-])(?:at|ch|uk|gb|us|usa|fr|it|es|nl|be|pl|tr|ru|pt|dk|se|no|fi|cz|sk|hu|ro|bg|gr|hr|rs|al|ar|in|pk|ex-?yu|austria|schweiz|swiss|österreich|oesterreich|turkey|türk|arabic|france|italia|spain|polska|nederland)(?:$|[\s\])|:/_-])""",
        RegexOption.IGNORE_CASE,
    )

    fun isBundesliga(name: String) = name.contains("bundesliga", ignoreCase = true)

    /**
     * Deutsch ist ein Sender, wenn Playlist-Gruppe, Name, tvg-country oder tvg-id auf DE hinweisen,
     * oder wenn es ein bekannter deutscher Sender ohne Auslands-Kennung ist.
     */
    fun isGerman(name: String, group: String?, tvgId: String?, country: String?, key: String): Boolean {
        if (country != null && country.split(',', ';', ' ').any { it.trim().equals("DE", true) }) return true
        if (tvgId != null && Regex("""\.de(?:@|$)""", RegexOption.IGNORE_CASE).containsMatchIn(tvgId)) return true
        val namePrefix = name.take(14)
        val hasDe = deMarker.containsMatchIn(group ?: "") || deMarker.containsMatchIn(namePrefix)
        val hasForeign = foreignMarker.containsMatchIn(group ?: "") || foreignMarker.containsMatchIn(namePrefix)
        if (hasDe) return true
        if (hasForeign) return false
        if (isBundesliga(name)) return true
        return classicSlot(key) != null
    }
}


class M3uEntry(val name: String, val url: String, val logo: String?, val group: String?, val tvgId: String?, val country: String?, val ua: String?)

private val attrRe = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

/** Zerlegt eine M3U/M3U8-Playlist zeilenweise (auch sehr große Listen). */
fun parseM3u(reader: BufferedReader, onEntry: (M3uEntry) -> Unit) {
    var info: String? = null
    var group: String? = null
    var ua: String? = null
    reader.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("#EXTINF", true) -> {
                info = line; group = null; ua = null
            }
            line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim()
            line.startsWith("#EXTVLCOPT:", true) -> {
                val opt = line.substringAfter(':')
                if (opt.startsWith("http-user-agent=", true)) ua = opt.substringAfter('=').trim()
            }
            line.startsWith("#") -> Unit
            else -> {
                val inf = info ?: return@forEach
                val attrs = attrRe.findAll(inf).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                // Der Name steht nach dem letzten Komma außerhalb der Anführungszeichen
                var inQuote = false
                var comma = -1
                inf.forEachIndexed { i, c -> if (c == '"') inQuote = !inQuote else if (c == ',' && !inQuote) comma = i }
                val name = (if (comma >= 0) inf.substring(comma + 1) else attrs["tvg-name"] ?: "").trim()
                    .ifEmpty { attrs["tvg-name"] ?: "" }
                if (name.isNotEmpty()) {
                    onEntry(
                        M3uEntry(
                            name = name,
                            url = line,
                            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                            group = attrs["group-title"] ?: group,
                            tvgId = attrs["tvg-id"],
                            country = attrs["tvg-country"],
                            ua = ua ?: attrs["user-agent"] ?: attrs["http-user-agent"],
                        )
                    )
                }
                info = null
            }
        }
    }
}


object ChannelBuilder {
    /** Filtert deutsche Sender, fasst HD/SD-Varianten zusammen und vergibt die festen Nummern. */
    fun build(reader: BufferedReader, extraNumbers: (keys: Collection<String>, names: Map<String, String>) -> Map<String, Int>): ChannelList {
        val groups = LinkedHashMap<String, MutableList<Pair<Int, M3uEntry>>>()
        val cleanNames = HashMap<String, String>()
        var hidden = 0
        parseM3u(reader) { raw ->
            val clean = ChannelOrder.cleanName(raw.name)
            val key = ChannelOrder.keyOf(clean)
            if (key.isEmpty() || !ChannelOrder.isGerman(raw.name, raw.group, raw.tvgId, raw.country, key)) {
                hidden++
            } else {
                groups.getOrPut(key) { mutableListOf() } += ChannelOrder.qualityScore(raw.name) to raw
                cleanNames.putIfAbsent(key, clean)
            }
        }
        if (groups.isEmpty() && hidden == 0) throw IllegalStateException("Keine Sender in der Playlist gefunden. Ist das eine M3U-Datei?")

        val numbers = extraNumbers(groups.keys.filter { ChannelOrder.classicSlot(it) == null }, cleanNames)
        val channels = groups.map { (key, variants) ->
            val sorted = variants.sortedByDescending { it.first }.map { it.second }
            val best = sorted.first()
            val slot = ChannelOrder.classicSlot(key)
            Channel(
                key = key,
                number = slot?.plus(1) ?: numbers.getValue(key),
                name = slot?.let { ChannelOrder.classicName(it) } ?: cleanNames.getValue(key),
                logo = sorted.firstNotNullOfOrNull { it.logo },
                urls = sorted.map { it.url }.distinct().take(5),
                favorite = sorted.any { ChannelOrder.isBundesliga(it.name) },
                group = best.group,
                userAgent = best.ua,
            )
        }
        return ChannelList(sort(channels), hidden, System.currentTimeMillis())
    }

    val natural = Comparator<String> { a, b ->
        val re = Regex("""\d+|\D+""")
        val pa = re.findAll(a.lowercase()).map { it.value }.toList()
        val pb = re.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(pa.size, pb.size)) {
            val x = pa[i]; val y = pb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) (x.toLongOrNull() ?: 0).compareTo(y.toLongOrNull() ?: 0) else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        pa.size.compareTo(pb.size)
    }

    /** Bundesliga ganz vorne, danach streng nach Programmplatz. */
    fun sort(list: List<Channel>): List<Channel> =
        list.filter { it.favorite }.sortedWith(compareBy(natural) { it.name }) +
            list.filter { !it.favorite }.sortedBy { it.number }

}
