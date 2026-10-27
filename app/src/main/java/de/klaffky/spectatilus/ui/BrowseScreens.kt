package de.klaffky.spectatilus.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klaffky.spectatilus.BuildConfig
import de.klaffky.spectatilus.data.Route
import de.klaffky.spectatilus.data.Session
import kotlinx.coroutines.launch

private val SORTS = listOf(
    Triple("A–Z", "SortName", "Ascending"),
    Triple("Neu hinzugefügt", "DateCreated,SortName", "Descending"),
    Triple("Erscheinungsjahr", "PremiereDate,ProductionYear,SortName", "Descending"),
    Triple("Bewertung", "CommunityRating,SortName", "Descending"),
)

suspend fun loadMore(s: LibraryState) {
    if (s.loading) return
    if (s.total >= 0 && s.items.size >= s.total) return
    s.loading = true
    try {
        val (_, by, order) = SORTS[s.sort]
        val page = Session.requireApi().items(
            types = s.types,
            parentId = s.parentId,
            sortBy = by,
            sortOrder = order,
            start = s.items.size,
            limit = 96,
            filters = if (s.unwatchedOnly) "IsUnplayed" else null,
            extra = s.extra,
            recursive = s.recursive,
        )
        s.items.addAll(page.items.filter { new -> s.items.none { it.id == new.id } })
        s.total = page.total
        s.error = null
    } catch (e: Exception) {
        s.error = friendlyError(e)
    } finally {
        s.loading = false
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, focusKey: String? = null, focusRequester: FocusRequester? = null) {
    FocusBox(onClick = onClick, shape = RoundedCornerShape(50), border = false, focusedScale = 1.06f, focusKey = focusKey, focusRequester = focusRequester) { focused ->
        Text(
            text,
            color = if (focused) C.Bg else if (selected) C.Text else C.TextDim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .background(if (focused) Color.White else if (selected) C.Accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** Raster-Ansicht für Filme, Serien oder eine einzelne Bibliothek/Sammlung. */
@Composable
fun LibraryScreen(app: AppState, state: LibraryState, title: String, showTopBar: Boolean) {
    val scope = rememberCoroutineScope()
    val firstChip = remember { FocusRequester() }

    LaunchedEffect(state, Refresh.stamp) {
        if (state.total < 0) loadMore(state)
    }

    fun changeSort(newSort: Int? = null, unwatched: Boolean? = null) {
        newSort?.let { state.sort = it }
        unwatched?.let { state.unwatchedOnly = it }
        state.reset()
        scope.launch { loadMore(state) }
    }

    ProvideFocusMemory(state.focus) {
        InitialFocus(state.focus, firstChip, ready = true, skip = app.focusTabs)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = state.gridState,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showTopBar) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "topbar") {
                    TopBar(app, horizontalPadding = 0.dp)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(Modifier.padding(top = if (showTopBar) 8.dp else 36.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(title, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(14.dp))
                        if (state.total >= 0) Text("${state.total} Titel", color = C.TextFaint, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SORTS.forEachIndexed { i, (label, _, _) ->
                            Chip(
                                label, state.sort == i, onClick = { changeSort(newSort = i) }, focusKey = "sort/$i",
                                focusRequester = if (i == 0) firstChip else null,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Chip(if (state.unwatchedOnly) "✓ Nur ungesehen" else "Nur ungesehen", state.unwatchedOnly, onClick = { changeSort(unwatched = !state.unwatchedOnly) }, focusKey = "unwatched")
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                PosterCard(
                    item,
                    onClick = { openItem(app, item) },
                    width = null,
                    focusKey = "grid/${item.id}",
                    onFocused = {
                        if (index >= state.items.size - 18) scope.launch { loadMore(state) }
                    },
                )
            }
            if (state.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                    Text(state.error ?: "", color = C.Error, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
                }
            } else if (state.loading && state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { LoadingDots() }
                }
            } else if (state.total == 0) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Text("Hier ist noch nichts.", color = C.TextDim, fontSize = 15.sp, modifier = Modifier.padding(top = 30.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Suche

@Composable
fun SearchScreen(app: AppState) {
    val state = app.search
    val scope = rememberCoroutineScope()
    val field = remember { FocusRequester() }

    fun runSearch(q: String) {
        state.query = q
        if (q.isBlank()) {
            state.results = emptyList(); return
        }
        state.searching = true
        scope.launch {
            try {
                state.results = Session.requireApi().items(
                    types = "Movie,Series,Episode,BoxSet", search = q, sortBy = "SortName", limit = 72,
                ).items
            } catch (e: Exception) {
                app.toast = friendlyError(e)
            } finally {
                state.searching = false
            }
        }
    }

    ProvideFocusMemory(state.focus) {
        InitialFocus(state.focus, field, ready = true, skip = app.focusTabs)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = state.gridState,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "topbar") {
                TopBar(app, horizontalPadding = 0.dp)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "field") {
                Column(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    FieldButton(
                        label = "Suche",
                        value = state.query,
                        placeholder = "Filme, Serien, Episoden …  (OK drücken zum Tippen oder Mikrofon-Taste)",
                        focusRequester = field,
                        onClick = { req -> app.ask(TextRequest("Suchen", state.query, returnTo = req) { runSearch(it) }) },
                        modifier = Modifier.width(620.dp),
                    )
                    if (state.searching) {
                        Spacer(Modifier.height(16.dp)); LoadingDots()
                    } else if (state.query.isNotBlank() && state.results.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Keine Treffer für „${state.query}“.", color = C.TextDim, fontSize = 14.sp)
                    }
                }
            }
            itemsIndexed(state.results, key = { _, it -> it.id }) { _, item ->
                PosterCard(item, onClick = { openItem(app, item) }, width = null, focusKey = "s/${item.id}")
            }
        }
    }
}

// ---------------------------------------------------------------- Einstellungen

private val BITRATES = listOf(
    0 to "Original (Direktwiedergabe)",
    40_000_000 to "40 Mbit/s – 4K",
    20_000_000 to "20 Mbit/s – 1080p hoch",
    10_000_000 to "10 Mbit/s – 1080p",
    6_000_000 to "6 Mbit/s – 720p (unterwegs)",
    3_000_000 to "3 Mbit/s – mobil",
    1_500_000 to "1,5 Mbit/s – sehr langsam",
)
private val AUDIO = listOf("de" to "Deutsch", "en" to "Englisch", "" to "Originalspur der Datei")
private val SUBS = listOf("" to "Aus", "de" to "Deutsch", "en" to "Englisch")

@Composable
fun SettingsScreen(app: AppState) {
    val prefs = Session.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var bitrate by remember { mutableStateOf(prefs.maxBitrate) }
    var audio by remember { mutableStateOf(prefs.audioLanguage) }
    var subs by remember { mutableStateOf(prefs.subtitleLanguage) }
    var autoplay by remember { mutableStateOf(prefs.autoplayNext) }

    fun <T> cycle(list: List<Pair<T, String>>, current: T): T {
        val i = list.indexOfFirst { it.first == current }
        return list[(i + 1) % list.size].first
    }

    ProvideFocusMemory(app.settingsFocus) {
        InitialFocus(app.settingsFocus, first, ready = true, skip = app.focusTabs)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item { TopBar(app) }
            item {
                Row(Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Wiedergabe")
                        SettingRow("Streaming-Qualität", BITRATES.firstOrNull { it.first == bitrate }?.second ?: "Original", focusRequester = first, key = "bitrate") {
                            bitrate = cycle(BITRATES, bitrate); prefs.maxBitrate = bitrate
                        }
                        SettingRow("Bevorzugte Audiosprache", AUDIO.firstOrNull { it.first == audio }?.second ?: "Deutsch", key = "audio") {
                            audio = cycle(AUDIO, audio); prefs.audioLanguage = audio
                        }
                        SettingRow("Untertitel", SUBS.firstOrNull { it.first == subs }?.second ?: "Aus", key = "subs") {
                            subs = cycle(SUBS, subs); prefs.subtitleLanguage = subs
                        }
                        SettingRow("Nächste Folge automatisch", if (autoplay) "An" else "Aus", key = "autoplay") {
                            autoplay = !autoplay; prefs.autoplayNext = autoplay
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Tipp: Unterwegs über Tailscale 6–10 Mbit/s wählen. Dann wandelt der Server große Dateien passend um und das Bild ruckelt nicht.",
                            color = C.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Server & Verbindung")
                        SettingRow("Server", prefs.serverName ?: "–", key = "server") {}
                        SettingRow(
                            "Verbunden über",
                            when (Session.route) {
                                Route.TAILSCALE -> "Tailscale"
                                Route.HOME -> "Heimnetz"
                                null -> "–"
                            },
                            key = "route",
                        ) {}
                        SettingRow("Tailscale-Adresse", prefs.remoteUrl ?: "–", key = "remote") { app.push(Screen.Connect(firstRun = false)) }
                        SettingRow("Heimnetz-Adresse", prefs.localUrl ?: "–", key = "local") { app.push(Screen.Connect(firstRun = false)) }
                        SettingRow("Tailscale-App öffnen", if (Session.isVpnActive()) "VPN aktiv" else "", key = "ts") { openTailscale(context, app) }
                        SettingRow("Abmelden", prefs.userName ?: "", key = "logout") {
                            scope.launch {
                                Session.logout()
                                app.clearContent()
                                app.reset(Screen.Login(prefs.serverName ?: "Jellyfin"))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("FireTV Spectatilus ${BuildConfig.VERSION_NAME}", color = C.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = C.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
private fun SettingRow(label: String, value: String, key: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        focusKey = "set/$key",
        focusRequester = focusRequester,
        focusedScale = 1.03f,
        border = false,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().background(if (focused) Color.White else Color.White.copy(alpha = 0.06f)).padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (focused) C.Bg else C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, color = if (focused) C.Bg.copy(alpha = 0.7f) else C.TextDim, fontSize = 13.sp, maxLines = 1)
        }
    }
}
