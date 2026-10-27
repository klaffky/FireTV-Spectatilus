package de.klaffky.spectatilus.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.klaffky.spectatilus.data.Item
import de.klaffky.spectatilus.data.Session
import de.klaffky.spectatilus.data.formatClock
import de.klaffky.spectatilus.data.formatRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private suspend fun loadDetails(s: Screen.Details) = coroutineScope {
    val api = Session.requireApi()
    val item = api.item(s.itemId)
    val similarJob = async { if (item.type == "Movie" || item.type == "Series") api.similar(item.id) else emptyList() }
    if (item.type == "Series") {
        val seasonsJob = async { runCatching { api.seasons(item.id) }.getOrDefault(emptyList()) }
        val nextJob = async { runCatching { api.nextUp(1, item.id).firstOrNull() }.getOrNull() }
        val seasons = seasonsJob.await()
        val next = nextJob.await()
        val season = s.selectedSeason?.takeIf { id -> seasons.any { it.id == id } }
            ?: next?.seasonId?.takeIf { id -> seasons.any { it.id == id } }
            ?: seasons.firstOrNull { (it.indexNumber ?: 0) > 0 }?.id
            ?: seasons.firstOrNull()?.id
        s.seasons = seasons
        s.nextUp = next
        s.selectedSeason = season
        s.episodes = runCatching { api.episodes(item.id, season) }.getOrDefault(emptyList())
    }
    s.item = item
    s.similar = similarJob.await()
    s.error = null
    s.loadedAt = System.currentTimeMillis()
}

@Composable
fun DetailsScreen(app: AppState, screen: Screen.Details) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playRequester = remember { FocusRequester() }

    LaunchedEffect(screen, Refresh.stamp) {
        if (screen.item == null || screen.loadedAt < Refresh.stamp) {
            try {
                loadDetails(screen)
            } catch (e: Exception) {
                screen.error = friendlyError(e)
            }
        }
    }

    val item = screen.item
    if (item == null) {
        val err = screen.error
        if (err != null) CenterMessage("Konnte nicht geladen werden", err) {
            PillButton("Zurück", onClick = { app.pop() }, primary = true)
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingDots() }
        return
    }

    fun play(target: Item, fromStart: Boolean = false) = startPlayback(context, target.id, if (fromStart) 0 else target.positionMs)

    fun selectSeason(id: String) {
        if (id == screen.selectedSeason) return
        screen.selectedSeason = id
        scope.launch {
            screen.episodes = runCatching { Session.requireApi().episodes(item.id, id) }.getOrDefault(emptyList())
            screen.episodeState.scrollToItem(0)
        }
    }

    val scrolled = screen.listState.firstVisibleItemIndex > 0
    val dim by animateFloatAsState(if (scrolled) 0.5f else 0f, label = "dim")
    val api = Session.api

    Box(Modifier.fillMaxSize()) {
        Backdrop(item, dim)
        ProvideFocusMemory(screen.focus) {
            InitialFocus(screen.focus, playRequester, ready = true)
            LazyColumn(state = screen.listState, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    Column(
                        Modifier.fillMaxWidth().height(if (item.type == "Series") 410.dp else 470.dp).padding(start = 48.dp, end = 48.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (item.type == "Episode" && item.seriesName != null) {
                            Text(item.seriesName, color = C.AccentSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                        }
                        val logo = api?.logoUrl(item, 700)
                        if (logo != null && item.type != "Episode") {
                            AsyncImage(
                                model = logo, contentDescription = item.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                                modifier = Modifier.width(360.dp).heightIn(max = 110.dp),
                            )
                        } else {
                            Text(
                                listOfNotNull(item.episodeLabel, item.name).joinToString(" · "),
                                color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(620.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        MetaLine(item)
                        if (item.genres.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(item.genres.take(4).joinToString(" · "), color = C.TextFaint, fontSize = 13.sp)
                        }
                        item.tagline?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = C.Text, fontSize = 14.sp, fontStyle = FontStyle.Italic, modifier = Modifier.width(560.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(item.overview ?: "", color = C.TextDim, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp))
                        Spacer(Modifier.height(18.dp))
                        ActionRow(app, screen, item, playRequester, ::play)
                    }
                }

                if (item.type == "Series" && screen.seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        LazyRow(
                            state = screen.seasonState,
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(screen.seasons, key = { it.id }) { season ->
                                Chip(season.name, season.id == screen.selectedSeason, onClick = { selectSeason(season.id) }, focusKey = "season/${season.id}")
                            }
                        }
                    }
                    item(key = "episodes") {
                        LazyRow(
                            state = screen.episodeState,
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(screen.episodes, key = { it.id }) { ep ->
                                Column(Modifier.width(250.dp)) {
                                    LandscapeCard(
                                        ep,
                                        onClick = { play(ep) },
                                        width = 250.dp,
                                        title = listOfNotNull(ep.indexNumber?.let { "Folge $it" }, ep.name).joinToString(" · "),
                                        subtitle = listOfNotNull(formatRuntime(ep.runTimeTicks), if (ep.positionTicks > 0 && !ep.played) "noch ${formatClock(ep.runtimeMs - ep.positionMs)}" else null).joinToString(" · "),
                                        focusKey = "ep/${ep.id}",
                                    )
                                    ep.overview?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(it, color = C.TextFaint, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                val cast = item.people.filter { it.type == "Actor" || it.type == "Director" }.take(20)
                if (cast.isNotEmpty()) {
                    item(key = "cast") {
                        Column {
                            Text("Besetzung & Crew", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 48.dp, top = 10.dp))
                            LazyRow(
                                state = screen.castState,
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                items(cast, key = { it.id + (it.role ?: "") + (it.type ?: "") }) { p ->
                                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        FocusBox(
                                            onClick = { app.push(Screen.Library(p.name, null, "Movie,Series", extra = "&PersonIds=${p.id}")) },
                                            shape = CircleShape, focusKey = "p/${p.id}/${p.type}", focusedScale = 1.1f,
                                            modifier = Modifier.size(84.dp),
                                        ) {
                                            val url = api?.personUrl(p)
                                            if (url != null) AsyncImage(model = url, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(C.SurfaceHigh, Color(0xFF2A2350)))), contentAlignment = Alignment.Center) {
                                                Text(p.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), color = C.TextDim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(p.name, color = C.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                        Text(if (p.type == "Director") "Regie" else p.role ?: "", color = C.TextFaint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }

                if (screen.similar.isNotEmpty()) {
                    item(key = "similar") {
                        ShelfRow("Das könnte dir auch gefallen", screen.similar, landscape = false, rowKey = "similar", state = screen.similarState, onClick = { openItem(app, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(app: AppState, screen: Screen.Details, item: Item, playRequester: FocusRequester, play: (Item, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch { runCatching { loadDetails(screen) } }
        Refresh.stamp = System.currentTimeMillis()
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            item.type == "Series" -> {
                val target = screen.nextUp ?: screen.episodes.firstOrNull()
                val label = when {
                    target == null -> "Abspielen"
                    target.positionTicks > 0 -> "Fortsetzen ${target.episodeLabel ?: ""}"
                    else -> "${target.episodeLabel ?: "Folge"} abspielen"
                }
                PillButton(label.trim(), onClick = { target?.let { play(it, false) } }, primary = true, icon = "▶", focusKey = "act/play", focusRequester = playRequester)
            }
            item.isPlayable -> {
                if (item.positionTicks > 0 && !item.played) {
                    PillButton("Fortsetzen ab ${formatClock(item.positionMs)}", onClick = { play(item, false) }, primary = true, icon = "▶", focusKey = "act/play", focusRequester = playRequester)
                    PillButton("Von vorne", onClick = { play(item, true) }, icon = "↺", focusKey = "act/restart")
                } else {
                    PillButton("Abspielen", onClick = { play(item, true) }, primary = true, icon = "▶", focusKey = "act/play", focusRequester = playRequester)
                }
            }
            else -> PillButton("Öffnen", onClick = { openItem(app, item) }, primary = true, focusRequester = playRequester)
        }
        PillButton(
            if (item.played) "Gesehen" else "Als gesehen markieren",
            icon = if (item.played) "✓" else "○",
            focusKey = "act/played",
            onClick = {
                scope.launch {
                    runCatching { Session.requireApi().setPlayed(item.id, !item.played) }
                        .onSuccess { refresh() }
                        .onFailure { app.toast = friendlyError(it) }
                }
            },
        )
        PillButton(
            if (item.favorite) "Favorit" else "Merken",
            icon = if (item.favorite) "♥" else "♡",
            focusKey = "act/fav",
            onClick = {
                scope.launch {
                    runCatching { Session.requireApi().setFavorite(item.id, !item.favorite) }
                        .onSuccess { refresh() }
                        .onFailure { app.toast = friendlyError(it) }
                }
            },
        )
        if (item.type == "Episode" && item.seriesId != null) {
            PillButton("Zur Serie", onClick = { app.push(Screen.Details(item.seriesId)) }, focusKey = "act/series")
        }
    }
}
