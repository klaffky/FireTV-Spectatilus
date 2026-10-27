package de.klaffky.spectatilus.data

import android.media.MediaCodecList
import org.json.JSONArray
import org.json.JSONObject

/**
 * Beschreibt dem Jellyfin-Server, was der Fire TV selbst abspielen kann.
 * Alles andere wird vom Server live in H.264/AAC (HLS) umgewandelt.
 */
object DeviceProfile {

    private val decoders: Set<String> by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder }
                .flatMap { it.supportedTypes.toList() }
                .map { it.lowercase() }
                .toSet()
        } catch (e: Exception) {
            setOf("video/avc", "audio/mp4a-latm")
        }
    }

    private fun has(mime: String) = mime in decoders

    val videoCodecs: List<String>
        get() = buildList {
            add("h264")
            if (has("video/hevc")) add("hevc")
            if (has("video/x-vnd.on2.vp9")) add("vp9")
            if (has("video/av01")) add("av1")
            if (has("video/mp4v-es")) add("mpeg4")
            if (has("video/mpeg2")) add("mpeg2video")
            if (has("video/x-vnd.on2.vp8")) add("vp8")
        }

    val audioCodecs: List<String>
        get() = buildList {
            add("aac")
            add("mp3")
            add("mp2")
            if (has("audio/ac3")) add("ac3")
            if (has("audio/eac3")) add("eac3")
            if (has("audio/eac3-joc")) add("eac3")
            if (has("audio/true-hd")) add("truehd")
            if (has("audio/vnd.dts")) add("dts")
            if (has("audio/vnd.dts.hd")) add("dca")
            add("flac")
            add("opus")
            add("vorbis")
            add("pcm_s16le")
            add("pcm_s24le")
        }.distinct()

    fun build(maxBitrate: Int): JSONObject {
        val bitrate = if (maxBitrate > 0) maxBitrate else 120_000_000
        val video = videoCodecs.joinToString(",")
        val audio = audioCodecs.joinToString(",")
        val transcodeAudio = listOf("aac", "ac3", "eac3").filter { it in audioCodecs }.joinToString(",")

        fun sub(format: String, method: String) = JSONObject().put("Format", format).put("Method", method)

        return JSONObject()
            .put("Name", JellyfinApi.CLIENT)
            .put("MaxStreamingBitrate", bitrate)
            .put("MaxStaticBitrate", bitrate)
            .put("MusicStreamingTranscodingBitrate", 320_000)
            .put(
                "DirectPlayProfiles", JSONArray()
                    .put(
                        JSONObject().put("Type", "Video").put("Container", "mp4,m4v,mkv,webm,mov,ts,mpegts")
                            .put("VideoCodec", video).put("AudioCodec", audio)
                    )
                    .put(JSONObject().put("Type", "Audio").put("Container", "mp3,aac,m4a,flac,ogg,opus,wav"))
            )
            .put(
                "TranscodingProfiles", JSONArray()
                    .put(
                        JSONObject().put("Type", "Video").put("Container", "ts").put("Protocol", "hls")
                            .put("Context", "Streaming").put("VideoCodec", if ("hevc" in video) "h264,hevc" else "h264")
                            .put("AudioCodec", transcodeAudio).put("MaxAudioChannels", "6")
                            .put("MinSegments", 1).put("BreakOnNonKeyFrames", true)
                    )
                    .put(
                        JSONObject().put("Type", "Audio").put("Container", "mp3").put("Protocol", "http")
                            .put("Context", "Streaming").put("AudioCodec", "mp3")
                    )
            )
            .put("ContainerProfiles", JSONArray())
            .put("CodecProfiles", JSONArray())
            .put(
                "SubtitleProfiles", JSONArray()
                    .put(sub("srt", "External")).put(sub("subrip", "External"))
                    .put(sub("vtt", "External")).put(sub("webvtt", "External"))
                    .put(sub("ass", "External")).put(sub("ssa", "External"))
                    .put(sub("srt", "Embed")).put(sub("subrip", "Embed"))
                    .put(sub("ass", "Embed")).put(sub("ssa", "Embed"))
                    .put(sub("pgssub", "Embed")).put(sub("dvdsub", "Encode"))
                    .put(sub("dvbsub", "Encode")).put(sub("pgssub", "Encode"))
            )
    }
}
