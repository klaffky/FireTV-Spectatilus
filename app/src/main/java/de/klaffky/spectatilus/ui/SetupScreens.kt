package de.klaffky.spectatilus.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.klaffky.spectatilus.data.AuthResult
import de.klaffky.spectatilus.data.JellyfinApi
import de.klaffky.spectatilus.data.Route
import de.klaffky.spectatilus.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun SetupBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF151030), C.Bg, Color(0xFF0A1320)))
        )
    ) { content() }
}

// ---------------------------------------------------------------- Server verbinden

@Composable
fun ConnectScreen(app: AppState, screen: Screen.Connect) {
    val prefs = Session.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var remote by remember { mutableStateOf(prefs.remoteUrl ?: "") }
    var local by remember { mutableStateOf(prefs.localUrl ?: "") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var vpn by remember { mutableStateOf(Session.isVpnActive()) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100); runCatching { first.requestFocus() }
        while (true) {
            delay(3000); vpn = Session.isVpnActive()
        }
    }

    fun connect() {
        val r = JellyfinApi.normalizeUrl(remote)
        val l = JellyfinApi.normalizeUrl(local)
        if (r.isEmpty() && l.isEmpty()) {
            status = "Bitte mindestens eine Adresse eintragen."
            return
        }
        busy = true
        status = "Verbinde …"
        scope.launch {
            var reached: Pair<String, Route>? = null
            var name: String? = null
            var error: String? = null
            for ((url, route) in listOf(l to Route.HOME, r to Route.TAILSCALE)) {
                if (url.isEmpty()) continue
                try {
                    val info = Session.probe(url)
                    if (reached == null) {
                        reached = url to route; name = info.name
                    }
                } catch (e: Exception) {
                    error = (if (route == Route.HOME) "Heimnetz: " else "Tailscale: ") + friendlyError(e)
                }
            }
            busy = false
            val ok = reached
            if (ok == null) {
                status = error
                return@launch
            }
            prefs.remoteUrl = r.ifEmpty { null }
            prefs.localUrl = l.ifEmpty { null }
            prefs.serverName = name
            Session.useServer(ok.first, ok.second)
            status = null
            if (prefs.token != null && !screen.firstRun) {
                // Nur Adressen geändert – Anmeldung behalten
                app.clearContent()
                app.reset(Screen.Main())
            } else {
                app.push(Screen.Login(name ?: "Jellyfin"))
            }
        }
    }

    SetupBackground {
        Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
            Column(Modifier.weight(1.1f)) {
                BrandMark(big = true)
                Spacer(Modifier.height(22.dp))
                Text(if (screen.firstRun) "Mediathek verbinden" else "Server-Adressen", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dein Jellyfin-Server – unterwegs über Tailscale, zu Hause direkt im Heimnetz.",
                    color = C.TextDim, fontSize = 14.sp,
                )
                Spacer(Modifier.height(22.dp))
                FieldButton(
                    label = "Tailscale-Adresse (unterwegs)",
                    value = remote,
                    placeholder = "z. B. 100.101.102.103 oder server.tailXXXX.ts.net",
                    focusRequester = first,
                    onClick = { req ->
                        app.ask(TextRequest("Tailscale-Adresse des Servers", remote, hint = "Tailscale-IP (100.x.x.x) oder MagicDNS-Name. Port 8096 wird automatisch ergänzt.", returnTo = req) { remote = it })
                    },
                )
                Spacer(Modifier.height(14.dp))
                FieldButton(
                    label = "Heimnetz-Adresse (optional, wird zuerst probiert)",
                    value = local,
                    placeholder = "z. B. 192.168.178.20",
                    onClick = { req ->
                        app.ask(TextRequest("Adresse im Heimnetz", local, hint = "Lokale IP des Servers. Leer lassen, wenn immer Tailscale genutzt wird.", returnTo = req) { local = it })
                    },
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(if (busy) "Verbinde …" else "Verbinden", onClick = { if (!busy) connect() }, primary = true, icon = "→")
                    PillButton("Tailscale öffnen", onClick = { openTailscale(context, app) })
                    if (!screen.firstRun) PillButton("Abbrechen", onClick = { app.pop() })
                }
                status?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = if (busy) C.TextDim else C.Error, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(40.dp))
            Column(
                Modifier.weight(0.9f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f)).padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (vpn) C.Success else C.Warn))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (vpn) "Tailscale ist verbunden" else if (Session.isTailscaleInstalled()) "Tailscale ist nicht aktiv" else "Tailscale nicht installiert",
                        color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Step("1", "Tailscale auf dem Fire TV installieren (Amazon Appstore) und mit deinem Konto anmelden.")
                Step("2", "Auf dem Jellyfin-Server ebenfalls Tailscale installieren – im selben Konto.")
                Step("3", "Im Tailscale-Adminbereich die 100.x-IP oder den MagicDNS-Namen des Servers ablesen und links eintragen.")
                Step("4", "Unterwegs reicht dann: Tailscale verbinden → Spectatilus öffnen.")
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(bottom = 12.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(C.Accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Text(n, color = C.AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = C.TextDim, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- Anmelden

@Composable
fun LoginScreen(app: AppState, screen: Screen.Login) {
    val api = Session.api ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var qcCode by remember { mutableStateOf<String?>(null) }
    val firstTile = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        screen.users = api.publicUsers()
        screen.quickConnect = api.quickConnectEnabled()
        delay(80)
        runCatching { firstTile.requestFocus() }
    }

    fun finish(auth: AuthResult) {
        Session.saveLogin(auth)
        app.clearContent()
        app.reset(Screen.Main())
    }

    fun login(user: String, password: String) {
        busy = true
        scope.launch {
            try {
                finish(api.authenticate(user, password))
            } catch (e: Exception) {
                app.toast = if (e is de.klaffky.spectatilus.data.ApiException && e.code == 401) "Benutzername oder Passwort falsch." else friendlyError(e)
            } finally {
                busy = false
            }
        }
    }

    fun askPassword(user: String, returnTo: FocusRequester) {
        app.ask(TextRequest("Passwort für $user", "", password = true, hint = "Leer lassen, falls kein Passwort gesetzt ist.", returnTo = returnTo) { pw -> login(user, pw) })
    }

    // Quick Connect: Code anzeigen und regelmäßig nachfragen
    LaunchedEffect(qcCode) {
        val code = qcCode ?: return@LaunchedEffect
        val secret = code.substringAfter('|')
        while (qcCode != null) {
            delay(3000)
            val ok = runCatching { api.quickConnectAuthorized(secret) }.getOrDefault(false)
            if (ok) {
                runCatching { api.authenticateQuickConnect(secret) }
                    .onSuccess { finish(it) }
                    .onFailure { app.toast = friendlyError(it); qcCode = null }
                break
            }
        }
    }

    SetupBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(28.dp))
            Text("Wer schaut?", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Angemeldet wird bei „${screen.serverName}“", color = C.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(30.dp))

            val code = qcCode
            if (code != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Quick Connect", color = C.TextDim, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(code.substringBefore('|'), color = C.Text, fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 10.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Öffne Jellyfin auf dem Handy oder im Browser → Profil → Quick Connect\nund gib diesen Code ein. Die Anmeldung erfolgt dann automatisch.",
                        color = C.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    val cancel = remember { FocusRequester() }
                    LaunchedEffect(Unit) { delay(80); runCatching { cancel.requestFocus() } }
                    PillButton("Abbrechen", onClick = { qcCode = null }, focusRequester = cancel)
                }
            } else {
                ProvideFocusMemory(screen.focus) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        itemsIndexed(screen.users, key = { _, u -> u.id }) { i, user ->
                            val own = remember { FocusRequester() }
                            val req = if (i == 0) firstTile else own
                            UserTile(user.name, api.userImageUrl(user.id, user.imageTag).takeIf { user.imageTag != null }, req, focusKey = "u/${user.id}") {
                                askPassword(user.name, req)
                            }
                        }
                        item {
                            val own = remember { FocusRequester() }
                            val req = if (screen.users.isEmpty()) firstTile else own
                            UserTile("Anderer Benutzer", null, req, symbol = "+") {
                                app.ask(TextRequest("Benutzername", "", returnTo = req) { name -> if (name.isNotEmpty()) askPassword(name, req) })
                            }
                        }
                        if (screen.quickConnect) {
                            item {
                                UserTile("Quick Connect", null, remember { FocusRequester() }, symbol = "↔") {
                                    scope.launch {
                                        runCatching { api.quickConnectInitiate() }
                                            .onSuccess { (secret, c) -> qcCode = "$c|$secret" }
                                            .onFailure { app.toast = friendlyError(it) }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                if (busy) LoadingDots()
                Spacer(Modifier.height(12.dp))
                PillButton("Server ändern", onClick = { app.push(Screen.Connect(firstRun = true)) })
            }
        }
    }
}

@Composable
private fun UserTile(name: String, imageUrl: String?, requester: FocusRequester, focusKey: String? = null, symbol: String? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
        FocusBox(onClick = onClick, shape = CircleShape, focusRequester = requester, focusKey = focusKey, focusedScale = 1.12f, modifier = Modifier.size(104.dp)) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(C.Accent, Color(0xFF3B2A8F)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(symbol ?: name.take(1).uppercase(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, color = C.Text, fontSize = 14.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
