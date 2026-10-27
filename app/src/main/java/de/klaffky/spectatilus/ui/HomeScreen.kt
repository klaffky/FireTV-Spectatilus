package de.klaffky.spectatilus.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.klaffky.spectatilus.data.ApiException
import de.klaffky.spectatilus.data.Item
import de.klaffky.spectatilus.data.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HIDDEN_LIBRARIES = setOf("music", "livetv", "playlists", "books", "photos")

suspend fun loadHome(state: HomeState) = coroutineScope {
    val api = Session.requireApi()
    val views = api.views()
    val resumeJob = async { runCatching { api.resume() }.getOrDefault(emptyList()) }
    val nextJob = async { runCatching { api.nextUp() }.getOrDefault(emptyList()) }
    val libs = views.filter { it.collectionType !in HIDDEN_LIBRARIES }
    val latestJobs = libs.filter { it.collectionType == "movies" || it.collectionType == "tvshows" || it.collectionType == null || it.collectionType == "homevideos" }
        .map { lib -> async { lib to runCatching { api.latest(lib.id) }.getOrDefault(emptyList()) } }
    val resume = resumeJob.await()
    val next = nextJob.await().filter { n -> resume.none { it.seriesId != null && it.seriesId == n.seriesId } }
    val latest = latestJobs.awaitAll()

    val shelves = mutableListOf<Shelf>()
    if (resume.isNotEmpty()) shelves += Shelf("resume", "Weiter ansehen", resume, landscape = true)
    if (next.isNotEmpty()) shelves += Shelf("next", "Als Nächstes", next, landscape = true)
    latest.forEach { (lib, items) ->
        if (items.isNotEmpty()) shelves += Shelf("latest-${lib.id}", "Neu in ${lib.name}", items, landscape = false)
    }

    // Hero: ein Mix aus Fortsetzen und Neuheiten – nur Titel mit Hintergrundbild
    val heroCandidates = (resume.take(2) + latest.flatMap { it.second.take(4) })
        .filter { it.backdropTags.isNotEmpty() || it.parentBackdropTags.isNotEmpty() }
        .distinctBy { it.seriesId ?: it.id }
        .take(7)

    state.hero = heroCandidates
    state.shelves = shelves
    state.libraries = libs
    state.error = null
    state.loaded = true
    state.lastRefresh = System.currentTimeMillis()
}

/** Ganzflächiger Hintergrund mit Verläufen – wechselt sanft mit dem fokussierten Titel. */
@Composable
fun Backdrop(item: Item?, dim: Float = 0f) {
    val api = Session.api
    val url = item?.let { api?.backdropUrl(it, 1280) }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = url, animationSpec = tween(700), label = "backdrop") { u ->
            if (u != null) {
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A1340), C.Bg))))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.Bg.copy(alpha = 0.95f), 0.55f to C.Bg.copy(alpha = 0.45f), 1f to C.Bg.copy(alpha = 0.1f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to C.Bg)))
        if (dim > 0f) Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = dim)))
    }
}

@Composable
fun HomeScreen(app: AppState) {
    val state = app.home
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val heroPlay = remember { FocusRequester() }
    var heroFocused by remember { mutableStateOf(false) }
    var focusedItem by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(Refresh.stamp) {
        if (!state.loaded || state.lastRefresh < Refresh.stamp) {
            try {
                loadHome(state)
            } catch (e: Exception) {
                if (!state.loaded) state.error = friendlyError(e)
                if (e is ApiException && e.code == 401) state.error = friendlyError(e)
            }
        }
    }

    // Hero rotiert automatisch, solange er nicht fokussiert ist
    LaunchedEffect(heroFocused, state.hero) {
        if (heroFocused || state.hero.size < 2) return@LaunchedEffect
        while (true) {
            delay(9000)
            if (state.listState.firstVisibleItemIndex <= 1) state.heroIndex = (state.heroIndex + 1) % state.hero.size
        }
    }

    // Hintergrund folgt dem Fokus, leicht verzögert (schont den Fire TV beim schnellen Scrollen)
    LaunchedEffect(focusedItem) {
        val f = focusedItem ?: return@LaunchedEffect
        delay(350)
        state.featured = f
    }

    val heroItem = state.hero.getOrNull(state.heroIndex % state.hero.size.coerceAtLeast(1))
    val scrolled = state.listState.firstVisibleItemIndex >= 2
    val dim by animateFloatAsState(if (scrolled) 0.45f else 0f, label = "dim")

    Box(Modifier.fillMaxSize()) {
        Backdrop(if (scrolled) state.featured ?: heroItem else heroItem, dim)

        when {
            state.error != null && !state.loaded -> CenterMessage("Start konnte nicht geladen werden", state.error) {
                PillButton("Erneut versuchen", onClick = {
                    state.error = null
                    scope.launch { runCatching { loadHome(state) }.onFailure { state.error = friendlyError(it) } }
                }, primary = true)
                PillButton("Tailscale öffnen", onClick = { openTailscale(context, app) })
                PillButton("Neu anmelden", onClick = {
                    scope.launch { Session.logout() }
                    app.reset(Screen.Login(Session.prefs.serverName ?: "Jellyfin"))
                })
            }
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingDots() }
            else -> ProvideFocusMemory(state.focus) {
                InitialFocus(state.focus, heroPlay, ready = state.loaded, skip = app.focusTabs)
                LazyColumn(state = state.listState, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
                    item(key = "topbar") { TopBar(app) }
                    item(key = "hero") {
                        if (heroItem != null) {
                            Hero(
                                item = heroItem,
                                count = state.hero.size,
                                index = state.heroIndex % state.hero.size,
                                playRequester = heroPlay,
                                onFocusChange = { heroFocused = it; if (it) focusedItem = heroItem },
                                onPlay = { startPlayback(context, heroItem.id, heroItem.positionMs) },
                                onInfo = { openItem(app, heroItem) },
                                onStep = { d -> state.heroIndex = (state.heroIndex + d + state.hero.size) % state.hero.size },
                            )
                        } else {
                            WelcomeHero(heroPlay) { app.tab = 1; app.focusTabs = true }
                        }
                    }
                    items(state.shelves, key = { it.key }) { shelf ->
                        ShelfRow(
                            title = shelf.title,
                            items = shelf.items,
                            landscape = shelf.landscape,
                            rowKey = shelf.key,
                            state = state.rowState(shelf.key),
                            onClick = { item ->
                                if (shelf.key == "resume" || shelf.key == "next") startPlayback(context, item.id, item.positionMs)
                                else openItem(app, item)
                            },
                            onFocused = { focusedItem = it },
                        )
                    }
                    if (state.libraries.isNotEmpty()) {
                        item(key = "libraries") {
                            LibraryRow(state, state.libraries) { lib ->
                                val types = when (lib.collectionType) {
                                    "movies" -> "Movie"
                                    "tvshows" -> "Series"
                                    "boxsets" -> "BoxSet"
                                    else -> null
                                }
                                app.push(Screen.Library(lib.name, lib.id, types, recursive = types != null))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InitialFocus(memory: FocusMemory, requester: FocusRequester, ready: Boolean, skip: Boolean = false) {
    LaunchedEffect(ready) {
        if (!ready || skip) return@LaunchedEffect
        if (memory.restorePending) {
            delay(400)
            if (!memory.restorePending) return@LaunchedEffect
            memory.restorePending = false
        } else if (memory.lastKey != null) {
            return@LaunchedEffect
        } else {
            delay(60)
        }
        runCatching { requester.requestFocus() }
    }
}

@Composable
private fun Hero(
    item: Item,
    count: Int,
    index: Int,
    playRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onStep: (Int) -> Unit,
) {
    val api = Session.api
    var playFocused by remember { mutableStateOf(false) }
    var infoFocused by remember { mutableStateOf(false) }
    LaunchedEffect(playFocused, infoFocused) { onFocusChange(playFocused || infoFocused) }

    Column(
        Modifier.fillMaxWidth().height(318.dp).padding(start = 48.dp, end = 48.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Crossfade(targetState = item, animationSpec = tween(500), label = "heroText") { it ->
            Column {
                val logo = api?.logoUrl(it, 600)
                if (logo != null) {
                    AsyncImage(
                        model = logo, contentDescription = it.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.width(320.dp).heightIn(max = 96.dp),
                    )
                } else {
                    Text(it.seriesName ?: it.name, color = C.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp))
                }
                Spacer(Modifier.height(10.dp))
                if (it.type == "Episode") {
                    Text(listOfNotNull(it.episodeLabel, it.name).joinToString(" · "), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                }
                MetaLine(it)
                Spacer(Modifier.height(8.dp))
                Text(it.overview ?: "", color = C.TextDim, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp, modifier = Modifier.width(470.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionLeft && playFocused -> { onStep(-1); true }
                    e.key == Key.DirectionRight && infoFocused -> { onStep(1); true }
                    else -> false
                }
            },
        ) {
            Box(Modifier.onFocusChanged { playFocused = it.hasFocus }) {
                PillButton(
                    if (item.positionTicks > 0) "Fortsetzen" else "Abspielen",
                    onClick = onPlay, primary = true, icon = "▶", focusKey = "hero/play", focusRequester = playRequester,
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.onFocusChanged { infoFocused = it.hasFocus }) {
                PillButton("Mehr Infos", onClick = onInfo, icon = "ⓘ", focusKey = "hero/info")
            }
            Spacer(Modifier.weight(1f))
            if (count > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(count) { i ->
                        Box(Modifier.size(if (i == index) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(if (i == index) Color.White else Color.White.copy(alpha = 0.35f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeHero(requester: FocusRequester, onBrowse: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 48.dp), verticalArrangement = Arrangement.Bottom) {
        Text("Willkommen zurück", color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Deine Mediathek ist bereit.", color = C.TextDim, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        PillButton("Filme durchsuchen", onClick = onBrowse, primary = true, focusRequester = requester, focusKey = "hero/play")
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun LibraryRow(state: HomeState, libs: List<Item>, onClick: (Item) -> Unit) {
    val api = Session.api
    Column(Modifier.fillMaxWidth()) {
        Text("Bibliotheken", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 48.dp, bottom = 2.dp))
        androidx.compose.foundation.lazy.LazyRow(
            state = state.rowState("libraries"),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(libs, key = { it.id }) { lib ->
                FocusBox(onClick = { onClick(lib) }, focusKey = "lib/${lib.id}", modifier = Modifier.width(236.dp).height(133.dp)) {
                    val url = lib.primaryTag?.let { api?.imageUrl(lib.id, "Primary", it, maxWidth = 640) }
                    if (url != null) {
                        AsyncImage(model = url, contentDescription = lib.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(C.Accent.copy(alpha = 0.7f), Color(0xFF1C1640)))))
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                    Text(lib.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                }
            }
        }
    }
}
