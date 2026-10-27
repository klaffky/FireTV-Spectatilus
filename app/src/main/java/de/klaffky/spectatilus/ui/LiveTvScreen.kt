package de.klaffky.spectatilus.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.klaffky.spectatilus.data.Channel
import de.klaffky.spectatilus.data.ChannelList
import de.klaffky.spectatilus.data.LiveSession
import de.klaffky.spectatilus.data.LiveTvStore
import de.klaffky.spectatilus.player.LiveTvActivity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val STALE_MS = 12L * 60 * 60 * 1000

suspend fun loadLive(state: LiveState, force: Boolean) {
    val p = state.playlists.firstOrNull { it.id == state.selected } ?: return
    state.loading = true
    state.error = null
    try {
        val list = LiveTvStore.load(p, force)
        if (state.selected == p.id) state.list = list
        // Alte Zwischenspeicher still im Hintergrund auffrischen
        if (!force && System.currentTimeMillis() - list.updatedAt > STALE_MS) {
            runCatching { LiveTvStore.load(p, true) }.onSuccess { if (state.selected == p.id) state.list = it }
        }
    } catch (e: Exception) {
        state.error = friendlyError(e)
    } finally {
        state.loading = false
    }
}

@Composable
fun LiveTvScreen(app: AppState) {
    val state = app.live
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val firstReq = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        state.playlists = LiveTvStore.playlists()
        if (state.selected == null || state.playlists.none { it.id == state.selected }) {
            state.selected = LiveTvStore.selectedId?.takeIf { id -> state.playlists.any { it.id == id } } ?: state.playlists.firstOrNull()?.id
        }
        if (state.list == null) loadLive(state, force = false)
    }

    fun select(id: String) {
        if (id == state.selected) return
        state.selected = id
        LiveTvStore.selectedId = id
        state.list = null
        state.confirmDelete = false
        scope.launch { loadLive(state, force = false) }
    }

    fun addPlaylist(returnTo: FocusRequester) {
        app.ask(
            TextRequest(
                "M3U-Playlist hinzufügen", "",
                hint = "Adresse der M3U/M3U8-Liste, z. B. http://anbieter.example/get.php?username=…&type=m3u_plus",
                returnTo = returnTo,
            ) { url ->
                if (url.isNotBlank()) {
                    val p = LiveTvStore.add(url)
                    state.playlists = LiveTvStore.playlists()
                    state.selected = p.id
                    LiveTvStore.selectedId = p.id
                    state.list = null
                    scope.launch { loadLive(state, force = true) }
                }
            }
        )
    }

    fun watch(list: ChannelList, c: Channel) {
        LiveSession.list = list
        LiveSession.playlistId = state.selected
        context.startActivity(Intent(context, LiveTvActivity::class.java).putExtra(LiveTvActivity.EXTRA_KEY, c.key))
    }

    ProvideFocusMemory(state.focus) {
        InitialFocus(state.focus, firstReq, ready = true, skip = app.focusTabs)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = state.gridState,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "topbar") { TopBar(app, horizontalPadding = 0.dp) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Live-TV", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(14.dp))
                        val l = state.list
                        if (l != null) {
                            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(l.updatedAt))
                            Text(
                                "${l.channels.size} deutsche Sender · ${l.hidden} ausgeblendet · Stand $time",
                                color = C.TextFaint, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.playlists.forEachIndexed { i, p ->
                            Chip(p.name, p.id == state.selected, onClick = { select(p.id) }, focusKey = "pl/${p.id}", focusRequester = if (i == 0) firstReq else null)
                        }
                        val addReq = remember { FocusRequester() }
                        Chip("+ Playlist", false, onClick = { addPlaylist(addReq) }, focusKey = "pl/add", focusRequester = if (state.playlists.isEmpty()) firstReq else addReq)
                        if (state.selected != null) {
                            Chip(if (state.loading) "Lädt …" else "↻ Aktualisieren", false, onClick = {
                                if (!state.loading) scope.launch { loadLive(state, force = true) }
                            }, focusKey = "pl/refresh")
                            Chip(if (state.confirmDelete) "Wirklich entfernen?" else "Entfernen", state.confirmDelete, onClick = {
                                val id = state.selected
                                if (!state.confirmDelete) {
                                    state.confirmDelete = true
                                } else if (id != null) {
                                    LiveTvStore.remove(id)
                                    state.playlists = LiveTvStore.playlists()
                                    state.selected = state.playlists.firstOrNull()?.id
                                    state.list = null
                                    state.confirmDelete = false
                                    scope.launch { loadLive(state, force = false) }
                                }
                            }, focusKey = "pl/remove")
                        }
                    }
                }
            }

            val list = state.list
            when {
                state.playlists.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Text(
                        "Füge eine M3U-Playlist hinzu. Spectatilus zeigt daraus nur deutsche Sender – immer in derselben Reihenfolge wie früher " +
                            "auf dem Receiver (1 Das Erste, 2 ZDF, 3 RTL … 7 ProSieben). Bundesliga-Sender stehen als Favoriten ganz vorne.",
                        color = C.TextDim, fontSize = 15.sp, modifier = Modifier.padding(top = 20.dp).width(640.dp),
                    )
                }
                state.error != null && list == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                    Text(state.error ?: "", color = C.Error, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
                }
                list == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingDots()
                        Spacer(Modifier.height(12.dp))
                        Text("Playlist wird geladen und sortiert …", color = C.TextDim, fontSize = 13.sp)
                    }
                }
                list.channels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                    Text("In dieser Playlist wurden keine deutschen Sender gefunden.", color = C.TextDim, fontSize = 15.sp, modifier = Modifier.padding(top = 20.dp))
                }
                else -> {
                    val favorites = list.favorites
                    if (favorites.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-fav") { SectionHeader("★ Bundesliga") }
                        items(favorites, key = { "f/" + it.key }) { c -> ChannelCard(list, c) { watch(list, c) } }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-all") { SectionHeader("Alle deutschen Sender") }
                    items(list.regular, key = { "c/" + it.key }) { c -> ChannelCard(list, c) { watch(list, c) } }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
fun ChannelLogo(c: Channel, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        if (c.logo != null) {
            AsyncImage(model = c.logo, contentDescription = c.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            Text(c.name.take(3).uppercase(), color = C.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChannelCard(list: ChannelList, c: Channel, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        focusKey = "ch/${c.key}",
        focusRequester = focusRequester,
        focusedScale = 1.05f,
        modifier = Modifier.fillMaxWidth(),
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().background(if (focused) C.SurfaceHigh else C.Surface).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(44.dp).clip(RoundedCornerShape(6.dp)).background(if (c.favorite) C.Accent else Color.White.copy(alpha = 0.1f))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(list.label(c), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            ChannelLogo(c, Modifier.size(width = 48.dp, height = 30.dp))
            Spacer(Modifier.width(10.dp))
            Text(c.name, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
