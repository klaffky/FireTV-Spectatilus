package de.klaffky.spectatilus.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

enum class Route { HOME, TAILSCALE }

/** Hält den aktiven Server und entscheidet zwischen Heimnetz- und Tailscale-Adresse. */
object Session {
    lateinit var prefs: Prefs
        private set
    private lateinit var appContext: Context

    var api: JellyfinApi? = null
        private set
    var route: Route? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = Prefs(appContext)
    }

    val hasLogin: Boolean
        get() = prefs.token != null && prefs.userId != null && (prefs.remoteUrl != null || prefs.localUrl != null)

    fun requireApi(): JellyfinApi = api ?: error("Nicht verbunden")

    /**
     * Probiert zuerst die Heimnetz-Adresse (kurzer Timeout), dann die Tailscale-Adresse.
     * Gibt die erreichbare Server-Info zurück oder wirft die letzte Fehlermeldung.
     */
    suspend fun connect(): ServerInfo {
        val candidates = listOfNotNull(
            prefs.localUrl?.takeIf { it.isNotBlank() }?.let { it to Route.HOME },
            prefs.remoteUrl?.takeIf { it.isNotBlank() }?.let { it to Route.TAILSCALE },
        )
        var lastError: Exception? = null
        for ((url, r) in candidates) {
            val client = JellyfinApi(url, prefs.deviceId, prefs.token, prefs.userId)
            try {
                val info = client.publicInfo(timeoutMs = if (r == Route.HOME && candidates.size > 1) 2500 else 10000)
                api = client
                route = r
                return info
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Keine Server-Adresse eingetragen")
    }

    /** Für den Einrichtungs-Assistenten: einzelne Adresse testen. */
    suspend fun probe(url: String): ServerInfo = JellyfinApi(url, prefs.deviceId).publicInfo(timeoutMs = 10000)

    fun useServer(url: String, r: Route) {
        api = JellyfinApi(url, prefs.deviceId, prefs.token, prefs.userId)
        route = r
    }

    fun saveLogin(auth: AuthResult) {
        prefs.token = auth.token
        prefs.userId = auth.userId
        prefs.userName = auth.userName
        api?.token = auth.token
        api?.userId = auth.userId
    }

    suspend fun logout() {
        api?.logout()
        prefs.clearLogin()
        api?.token = null
        api?.userId = null
    }

    // ---------- Tailscale ----------

    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

    /** true, wenn gerade ein VPN (auf dem Fire TV praktisch immer Tailscale) aktiv ist. */
    fun isVpnActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            @Suppress("DEPRECATION")
            cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        } catch (e: Exception) {
            false
        }
    }

    fun isTailscaleInstalled(): Boolean = tailscaleIntent() != null

    fun tailscaleIntent(): Intent? {
        val pm = appContext.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(TAILSCALE_PACKAGE) ?: pm.getLaunchIntentForPackage(TAILSCALE_PACKAGE))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
