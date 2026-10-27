package de.klaffky.spectatilus.player

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.klaffky.spectatilus.data.Item
import de.klaffky.spectatilus.data.JellyfinApi
import de.klaffky.spectatilus.data.Session
import de.klaffky.spectatilus.data.DeviceProfile
import de.klaffky.spectatilus.data.arr
import de.klaffky.spectatilus.data.bool
import de.klaffky.spectatilus.data.int
import de.klaffky.spectatilus.data.mapObjects
import de.klaffky.spectatilus.data.str
import de.klaffky.spectatilus.ui.friendlyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ITEM = "itemId"
        const val EXTRA_START_MS = "startMs"

        // Eigener Scope, damit die „Gestoppt“-Meldung auch nach dem Schließen noch rausgeht
        private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var header: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var messageView: TextView

    private var player: ExoPlayer? = null
    private var progressJob: Job? = null

    private var current: Item? = null
    private var mediaSourceId: String? = null
    private var playSessionId: String? = null
    private var playMethod = "DirectPlay"
    private var triedTranscode = false
    private var startMs = 0L
    private var reportedStart = false

    private val api: JellyfinApi? get() = Session.api

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildLayout()

        val itemId = intent.getStringExtra(EXTRA_ITEM)
        if (itemId == null || api == null) {
            finish(); return
        }
        startMs = intent.getLongExtra(EXTRA_START_MS, 0L)
        load(itemId, startMs)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun buildLayout() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 4000
            controllerAutoShow = true
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowSubtitleButton(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            keepScreenOn = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(40))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0x00000000))
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); paint.isFakeBoldText = true
        }
        subtitleView = TextView(this).apply {
            setTextColor(0xFFB9A8FF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        header.addView(titleView)
        header.addView(subtitleView)
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        loading = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(loading, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))

        messageView = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); gravity = Gravity.CENTER
            setPadding(dp(80), 0, dp(80), 0); visibility = View.GONE
        }
        root.addView(messageView, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            header.visibility = visibility
        })
        setContentView(root)
    }

    private fun showMessage(text: String?) {
        messageView.text = text ?: ""
        messageView.visibility = if (text == null) View.GONE else View.VISIBLE
        if (text != null) loading.visibility = View.GONE
    }

    /** Serien/Staffeln in eine konkrete Episode auflösen. */
    private suspend fun resolvePlayable(item: Item): Item {
        val api = api ?: return item
        return when (item.type) {
            "Series" -> api.nextUp(1, item.id).firstOrNull()
                ?: api.episodes(item.id, limit = 1).firstOrNull()
                ?: item
            "Season" -> {
                val seriesId = item.seriesId ?: return item
                val episodes = api.episodes(seriesId, seasonId = item.id)
                episodes.firstOrNull { !it.played } ?: episodes.firstOrNull() ?: item
            }
            else -> item
        }
    }

    private fun load(itemId: String, positionMs: Long, forceTranscode: Boolean = false) {
        val api = api ?: return
        loading.visibility = View.VISIBLE
        showMessage(null)
        lifecycleScope.launch {
            try {
                var item = resolvePlayable(api.item(itemId))
                if (item.id != itemId) item = api.item(item.id)
                val start = if (item.id != itemId) item.positionMs else positionMs
                current = item
                titleView.text = item.seriesName ?: item.name
                subtitleView.text = if (item.type == "Episode") listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else listOfNotNull(item.year?.toString()).joinToString()

                val prefs = Session.prefs
                val info = api.playbackInfo(item.id, DeviceProfile.build(prefs.maxBitrate), prefs.maxBitrate, allowDirect = !forceTranscode)
                val source = info.arr("MediaSources")?.optJSONObject(0) ?: throw IllegalStateException("Keine abspielbare Quelle gefunden")
                playSessionId = info.str("PlaySessionId")
                mediaSourceId = source.str("Id") ?: item.id

                val transcodingUrl = source.str("TranscodingUrl")
                val direct = !forceTranscode && (source.bool("SupportsDirectPlay") || (source.bool("SupportsDirectStream") && transcodingUrl == null))
                val url: String
                val mime: String?
                if (direct || transcodingUrl == null) {
                    playMethod = "DirectPlay"
                    url = api.withApiKey("/Videos/${item.id}/stream?static=true&mediaSourceId=${mediaSourceId}&PlaySessionId=${playSessionId ?: ""}")
                    mime = null
                } else {
                    playMethod = "Transcode"
                    url = api.withApiKey(transcodingUrl)
                    mime = MimeTypes.APPLICATION_M3U8
                }

                val subtitles = source.arr("MediaStreams").mapObjects { it }
                    .filter { it.str("Type") == "Subtitle" && it.str("DeliveryMethod") == "External" && it.str("DeliveryUrl") != null }
                    .mapNotNull { s -> externalSubtitle(api, s) }

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMediaId(item.id)
                    .apply { if (mime != null) setMimeType(mime) }
                    .setSubtitleConfigurations(subtitles)
                    .build()

                val p = player ?: createPlayer().also { player = it; playerView.player = it }
                p.setMediaItem(mediaItem, start.coerceAtLeast(0))
                p.prepare()
                p.playWhenReady = true
                reportedStart = false
                startProgressLoop()
            } catch (e: Exception) {
                showMessage("Wiedergabe nicht möglich:\n${friendlyError(e)}")
            }
        }
    }

    private fun externalSubtitle(api: JellyfinApi, s: JSONObject): MediaItem.SubtitleConfiguration? {
        val codec = (s.str("Codec") ?: "").lowercase()
        val mime = when (codec) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> return null
        }
        val lang = s.str("Language")
        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(api.withApiKey(s.str("DeliveryUrl") ?: return null)))
            .setMimeType(mime)
            .setLanguage(lang)
            .setLabel(s.str("DisplayTitle") ?: lang ?: "Untertitel")
            .setSelectionFlags(if (s.bool("IsForced")) C.SELECTION_FLAG_FORCED else 0)
            .setId("ext-${s.int("Index") ?: 0}")
            .build()
    }

    private fun createPlayer(): ExoPlayer {
        val prefs = Session.prefs
        val trackSelector = DefaultTrackSelector(this).apply {
            val builder = buildUponParameters()
            if (prefs.audioLanguage.isNotEmpty()) builder.setPreferredAudioLanguage(prefs.audioLanguage)
            if (prefs.subtitleLanguage.isNotEmpty()) {
                builder.setPreferredTextLanguage(prefs.subtitleLanguage)
            } else {
                builder.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            }
            setParameters(builder)
        }
        val headers = mapOf("Authorization" to (api?.authHeader ?: ""))
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        loading.visibility = if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) View.VISIBLE else View.GONE
                        if (state == Player.STATE_READY && !reportedStart) {
                            reportedStart = true
                            report("start")
                        }
                        if (state == Player.STATE_ENDED) onEnded()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (reportedStart) report("progress")
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val item = current
                        // Direktwiedergabe gescheitert (z. B. Codec) → einmal mit Server-Umwandlung probieren
                        if (!triedTranscode && playMethod == "DirectPlay" && item != null) {
                            triedTranscode = true
                            val pos = p.currentPosition
                            load(item.id, pos, forceTranscode = true)
                        } else {
                            showMessage("Wiedergabefehler: ${error.errorCodeName}\n${error.message ?: ""}")
                        }
                    }
                })
            }
    }

    private fun positionTicks(): Long = (player?.currentPosition ?: 0L) * 10_000

    private fun report(event: String) {
        val item = current ?: return
        val body = JSONObject()
            .put("ItemId", item.id)
            .put("MediaSourceId", mediaSourceId)
            .put("PlaySessionId", playSessionId)
            .put("PositionTicks", positionTicks())
            .put("IsPaused", player?.isPlaying != true)
            .put("PlayMethod", playMethod)
            .put("CanSeek", true)
        val api = api ?: return
        reportScope.launch { api.reportPlayback(event, body) }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                if (reportedStart) report("progress")
            }
        }
    }

    private fun onEnded() {
        val item = current ?: return
        report("stop")
        reportedStart = false
        if (item.type == "Episode" && item.seriesId != null && Session.prefs.autoplayNext) {
            lifecycleScope.launch {
                val next = runCatching {
                    api?.episodes(item.seriesId, startItemId = item.id, limit = 2)?.getOrNull(1)
                }.getOrNull()
                if (next != null) {
                    showMessage("Als Nächstes: ${listOfNotNull(next.episodeLabel, next.name).joinToString(" · ")}")
                    delay(2500)
                    triedTranscode = false
                    load(next.id, 0)
                } else finish()
            }
        } else {
            finish()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player
        // Links/Rechts bei ausgeblendeter Steuerung: direkt spulen (wie bei tvOS)
        if (p != null && event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    p.seekBack(); playerView.showController(); return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    p.seekForward(); playerView.showController(); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        if (reportedStart) {
            report("stop")
            reportedStart = false
        }
        player?.pause()
    }

    override fun onDestroy() {
        progressJob?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }
}
