package de.klaffky.spectatilus.data

import android.content.Context
import java.util.UUID

/** Dauerhafte Einstellungen: Server-Adressen, Anmeldung und Wiedergabe-Optionen. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("spectatilus", Context.MODE_PRIVATE)

    private fun str(key: String) = sp.getString(key, null)
    private fun put(key: String, value: String?) = sp.edit().putString(key, value).apply()

    /** Adresse über Tailscale (unterwegs), z. B. http://100.64.1.2:8096 */
    var remoteUrl: String?
        get() = str("remoteUrl")
        set(v) = put("remoteUrl", v)

    /** Optionale Adresse im Heimnetz, wird beim Start zuerst probiert. */
    var localUrl: String?
        get() = str("localUrl")
        set(v) = put("localUrl", v)

    var serverName: String?
        get() = str("serverName")
        set(v) = put("serverName", v)

    var userId: String?
        get() = str("userId")
        set(v) = put("userId", v)

    var userName: String?
        get() = str("userName")
        set(v) = put("userName", v)

    var token: String?
        get() = str("token")
        set(v) = put("token", v)

    /** Maximale Streaming-Bitrate in bit/s, 0 = Original (Direktwiedergabe bevorzugt). */
    var maxBitrate: Int
        get() = sp.getInt("maxBitrate", 0)
        set(v) = sp.edit().putInt("maxBitrate", v).apply()

    /** Bevorzugte Audiosprache (ISO 639-1), leer = Standardspur der Datei. */
    var audioLanguage: String
        get() = sp.getString("audioLanguage", "de") ?: "de"
        set(v) = put("audioLanguage", v)

    /** Untertitel-Sprache, leer = aus. */
    var subtitleLanguage: String
        get() = sp.getString("subtitleLanguage", "") ?: ""
        set(v) = put("subtitleLanguage", v)

    var autoplayNext: Boolean
        get() = sp.getBoolean("autoplayNext", true)
        set(v) = sp.edit().putBoolean("autoplayNext", v).apply()

    val deviceId: String
        get() = str("deviceId") ?: UUID.randomUUID().toString().also { put("deviceId", it) }

    fun clearLogin() {
        sp.edit().remove("token").remove("userId").remove("userName").apply()
    }
}
