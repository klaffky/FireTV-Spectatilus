package de.klaffky.spectatilus.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klaffky.spectatilus.data.ApiException
import de.klaffky.spectatilus.data.Item
import de.klaffky.spectatilus.data.Route
import de.klaffky.spectatilus.data.Session
import de.klaffky.spectatilus.player.PlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Signal „Player wurde geschlossen → Fortschritt neu laden“. */
object Refresh {
    var playerOpened = false
    var stamp by mutableStateOf(0L)
}

fun friendlyError(e: Throwable): String = when (e) {
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        "Server nicht erreichbar. Ist Tailscale auf dem Fire TV verbunden und läuft Jellyfin?"
    is ApiException -> when (e.code) {
        401 -> "Anmeldung abgelaufen – bitte neu anmelden."
        403 -> "Kein Zugriff (403)."
        404 -> "Nicht gefunden (404). Ist die Adresse korrekt?"
        else -> e.message ?: "Serverfehler"
    }
    is javax.net.ssl.SSLException -> "TLS-Fehler. Für Tailscale-IP-Adressen „http://“ statt „https://“ verwenden."
    else -> e.message ?: e.javaClass.simpleName
}

fun startPlayback(context: Context, itemId: String, startMs: Long) {
    Refresh.playerOpened = true
    context.startActivity(
        Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_ITEM, itemId)
            .putExtra(PlayerActivity.EXTRA_START_MS, startMs)
    )
}

fun openTailscale(context: Context, app: AppState) {
    val intent = Session.tailscaleIntent()
    if (intent != null) context.startActivity(intent)
    else app.toast = "Tailscale ist nicht installiert – im Amazon Appstore nach „Tailscale“ suchen."
}

/** Zentrale Navigation, wenn ein Element ausgewählt wird. */
fun openItem(app: AppState, item: Item) {
    when (item.type) {
        "BoxSet", "Folder", "CollectionFolder", "Playlist", "UserView" ->
            app.push(Screen.Library(item.name, item.id, null, recursive = false))
        "Season" -> item.seriesId?.let { sid ->
            app.push(Screen.Details(sid).also { it.selectedSeason = item.id })
        }
        else -> app.push(Screen.Details(item.id))
    }
}

@Composable
fun AppRoot() {
    val app = remember { AppState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun boot() {
        if (!Session.hasLogin) {
            app.reset(Screen.Connect(firstRun = true))
            return
        }
        try {
            Session.connect()
            app.clearContent()
            app.reset(Screen.Main())
        } catch (e: Exception) {
            app.reset(Screen.Offline(friendlyError(e)))
        }
    }

    LaunchedEffect(Unit) { boot() }

    val current = app.current
    BackHandler(enabled = app.textRequest == null) {
        when {
            current is Screen.Main && app.tab != 0 -> {
                app.home.focus.restorePending = false
                app.tab = 0
                app.focusTabs = true
            }
            !app.pop() -> (context as? android.app.Activity)?.finish()
        }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when (current) {
            is Screen.Boot -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandMark(big = true)
                    Spacer(Modifier.height(24.dp))
                    LoadingDots()
                }
            }
            is Screen.Connect -> ConnectScreen(app, current)
            is Screen.Offline -> CenterMessage("Keine Verbindung", current.message) {
                val retry = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); runCatching { retry.requestFocus() } }
                PillButton("Erneut versuchen", onClick = {
                    app.reset(Screen.Boot()); scope.launch { boot() }
                }, primary = true, icon = "↻", focusRequester = retry)
                if (Session.isTailscaleInstalled()) PillButton("Tailscale öffnen", onClick = { openTailscale(context, app) })
                PillButton("Server ändern", onClick = { app.push(Screen.Connect(firstRun = false)) })
            }
            is Screen.Login -> LoginScreen(app, current)
            is Screen.Main -> MainScreen(app)
            is Screen.Details -> DetailsScreen(app, current)
            is Screen.Library -> LibraryScreen(app, current.state, current.title, showTopBar = false)
        }

        app.textRequest?.let { req ->
            TextInputOverlay(req, onClose = { value ->
                app.textRequest = null
                if (value != null) req.onDone(value)
                scope.launch {
                    delay(120)
                    runCatching { req.returnTo?.requestFocus() }
                }
            })
        }

        val toast = app.toast
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(3500); app.toast = null
            }
        }
        AnimatedVisibility(visible = toast != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
            Text(
                toast ?: "",
                color = C.Text,
                fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(C.SurfaceHigh).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun MainScreen(app: AppState) {
    when (app.tab) {
        0 -> HomeScreen(app)
        1 -> LibraryScreen(app, app.movies, "Filme", showTopBar = true)
        2 -> LibraryScreen(app, app.shows, "Serien", showTopBar = true)
        3 -> LiveTvScreen(app)
        4 -> SearchScreen(app)
        else -> SettingsScreen(app)
    }
}

@Composable
fun BrandMark(big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(if (big) 44.dp else 26.dp).clip(RoundedCornerShape(if (big) 12.dp else 7.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("▶", color = C.Accent, fontSize = if (big) 20.sp else 12.sp)
        }
        Spacer(Modifier.width(if (big) 14.dp else 10.dp))
        Text("Spectatilus", color = C.Text, fontSize = if (big) 32.sp else 18.sp, fontWeight = FontWeight.Bold)
    }
}

private val TABS = listOf("Start", "Filme", "Serien", "Live-TV", "Suche", "Einstellungen")

/** Tab-Leiste oben (tvOS-Stil). Wird von jedem Haupt-Screen als oberstes Element eingebunden. */
@Composable
fun TopBar(app: AppState, horizontalPadding: androidx.compose.ui.unit.Dp = 48.dp) {
    val requesters = remember { List(TABS.size) { FocusRequester() } }
    LaunchedEffect(app.focusTabs, app.tab) {
        if (app.focusTabs) {
            delay(60)
            runCatching { requesters[app.tab].requestFocus() }
            app.focusTabs = false
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(170.dp)) { BrandMark() }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TABS.forEachIndexed { i, title ->
                FocusBox(
                    onClick = {
                        if (app.tab != i) {
                            app.memoryForTab(i).restorePending = false
                            app.tab = i
                            app.focusTabs = true
                        }
                    },
                    shape = RoundedCornerShape(50),
                    border = false,
                    focusedScale = 1.06f,
                    focusRequester = requesters[i],
                ) { focused ->
                    val selected = i == app.tab
                    Text(
                        title,
                        color = if (focused) C.Bg else if (selected) C.Text else C.TextDim,
                        fontSize = 14.sp,
                        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier
                            .background(if (focused) Color.White else if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(170.dp), contentAlignment = Alignment.CenterEnd) { ConnectionChip() }
    }
}

@Composable
fun ConnectionChip() {
    val (label, color) = when (Session.route) {
        Route.TAILSCALE -> "Tailscale" to C.Success
        Route.HOME -> "Heimnetz" to C.AccentSoft
        null -> "Offline" to C.Warn
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, color = C.TextDim, fontSize = 12.sp)
    }
}

/** Vollbild-Eingabe; öffnet die Fire-TV-Tastatur (inkl. Spracheingabe). */
@Composable
fun TextInputOverlay(req: TextRequest, onClose: (String?) -> Unit) {
    var value by remember(req) { mutableStateOf(TextFieldValue(req.initial, TextRange(req.initial.length))) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(req) {
        delay(120)
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
    BackHandler { onClose(null) }
    val submit = { keyboard?.hide(); onClose(value.text.trim()) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 70.dp).width(600.dp)) {
            Text(req.title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 20.sp),
                cursorBrush = SolidColor(C.AccentSoft),
                visualTransformation = if (req.password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (req.password) KeyboardType.Password else KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.SurfaceHigh)
                    .border(2.dp, C.Accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
            if (req.hint != null) {
                Spacer(Modifier.height(10.dp))
                Text(req.hint, color = C.TextDim, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton("Übernehmen", onClick = { submit() }, primary = true, icon = "✓")
                PillButton("Abbrechen", onClick = { keyboard?.hide(); onClose(null) })
            }
        }
    }
}

/** Eingabefeld-Kachel: zeigt Wert/Platzhalter, öffnet beim Klick die Tastatur. */
@Composable
fun FieldButton(
    label: String,
    value: String,
    placeholder: String,
    onClick: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val own = remember { FocusRequester() }
    val req = focusRequester ?: own
    Column(modifier) {
        Text(label, color = C.TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        FocusBox(onClick = { onClick(req) }, focusRequester = req, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) { focused ->
            Text(
                when {
                    value.isEmpty() -> placeholder
                    password -> "•".repeat(value.length)
                    else -> value
                },
                color = if (value.isEmpty()) C.TextFaint else C.Text,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().background(if (focused) C.SurfaceHigh else C.Surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
    }
}
