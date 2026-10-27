package de.klaffky.spectatilus.player

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.klaffky.spectatilus.data.Channel
import de.klaffky.spectatilus.data.ChannelList
import de.klaffky.spectatilus.data.LiveSession
import de.klaffky.spectatilus.data.LiveTvStore
import de.klaffky.spectatilus.ui.C
import de.klaffky.spectatilus.ui.ChannelCard
import de.klaffky.spectatilus.ui.ChannelLogo
import de.klaffky.spectatilus.ui.SpectatilusTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Vollbild-Fernsehen mit Umschalten wie am alten Receiver. */
@OptIn(UnstableApi::class)
class LiveTvActivity : ComponentActivity() {

    companion object {
        const val EXTRA_KEY = "channelKey"
    }

    private lateinit var list: ChannelList
    private val channels get() = list.channels
    private var player: ExoPlayer? = null

    private var index by mutableStateOf(0)
    private var previousIndex = -1
    private var urlIndex = 0
    private var osdVisible by mutableStateOf(true)
    private var listVisible by mutableStateOf(false)
    private var status by mutableStateOf<String?>(null)
    private var buffering by mutableStateOf(true)
    private var digits by mutableStateOf("")
    private var osdJob: Job? = null
    private var digitJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val l = LiveSession.list
        if (l == null || l.channels.isEmpty()) {
            finish(); return
        }
        list = l
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val key = intent.getStringExtra(EXTRA_KEY)
        index = channels.indexOfFirst { it.key == key }.coerceAtLeast(0)
        player = createPlayer()
        setContent { SpectatilusTheme { LiveUi() } }
        tune(index, rememberPrevious = false)
    }

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                    if (state == Player.STATE_READY) status = null
                }

                override fun onPlayerError(error: PlaybackException) {
                    val c = channels[index]
                    if (urlIndex + 1 < c.urls.size) {
                        urlIndex++ // Ersatz-Stream (z. B. SD statt HD) probieren
                        play()
                    } else {
                        buffering = false
                        status = "${c.name} ist gerade nicht verfügbar"
                    }
                }
            })
        }

    private fun tune(i: Int, rememberPrevious: Boolean = true) {
        val n = channels.size
        val target = ((i % n) + n) % n
        if (rememberPrevious && target != index) previousIndex = index
        index = target
        urlIndex = 0
        play()
        showOsd()
        LiveSession.playlistId?.let { LiveTvStore.setLastChannel(it, channels[target].key) }
    }

    private fun play() {
        val c = channels[index]
        val url = c.urls[urlIndex]
        status = null
        buffering = true
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(c.userAgent ?: "Spectatilus/1.0 (Linux; Android) VLC")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
        val item = MediaItem.Builder().setUri(Uri.parse(url)).apply {
            if (url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player?.run {
            setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(item))
            prepare()
            playWhenReady = true
        }
    }

    private fun showOsd() {
        osdVisible = true
        osdJob?.cancel()
        osdJob = lifecycleScope.launch {
            delay(4500); osdVisible = false
        }
    }

    private fun typeDigit(d: Int) {
        digits = (digits + d).takeLast(3)
        digitJob?.cancel()
        digitJob = lifecycleScope.launch {
            delay(1600)
            val number = digits.toIntOrNull()
            digits = ""
            val target = channels.indexOfFirst { !it.favorite && it.number == number }
            if (target >= 0) tune(target) else showOsd()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (listVisible || event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> tune(index + 1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> tune(index - 1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                if (previousIndex in channels.indices) tune(previousIndex) else showOsd()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> showOsd()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> {
                osdVisible = false; listVisible = true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE ->
                player?.let { it.playWhenReady = !it.playWhenReady }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> typeDigit(event.keyCode - KeyEvent.KEYCODE_0)
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    @Composable
    private fun LiveUi() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        player = this@LiveTvActivity.player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            val c = channels[index]
            AnimatedVisibility(osdVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
                Osd(c)
            }

            if (digits.isNotEmpty()) {
                Text(
                    digits.padEnd(2, '_'), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(36.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            val s = status
            if (s != null) {
                Text(
                    s, color = Color.White, fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else if (buffering) {
                Text("Verbinde …", color = C.TextDim, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                listVisible,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ChannelPanel()
            }
        }
    }

    @Composable
    private fun Osd(c: Channel) {
        Row(
            Modifier.padding(start = 40.dp, top = 30.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.55f))))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (c.favorite) C.Accent else Color.White)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(list.label(c), color = if (c.favorite) Color.White else C.Bg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            ChannelLogo(c, Modifier.size(width = 64.dp, height = 40.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(c.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("▲▼ Sender  ·  ◀ letzter Sender  ·  OK Senderliste", color = C.TextDim, fontSize = 11.sp)
            }
        }
    }

    @Composable
    private fun ChannelPanel() {
        val state = rememberLazyListState()
        val current = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            state.scrollToItem((index - 3).coerceAtLeast(0))
            delay(60)
            runCatching { current.requestFocus() }
        }
        BackHandler { listVisible = false }
        Column(
            Modifier.width(380.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.88f)).padding(top = 28.dp),
        ) {
            Text("Senderliste", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.key }) { i, ch ->
                    ChannelCard(list, ch, focusRequester = if (i == index) current else null) {
                        listVisible = false
                        tune(i)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onStart() {
        super.onStart()
        player?.let { if (it.playbackState != Player.STATE_IDLE) it.playWhenReady = true }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
