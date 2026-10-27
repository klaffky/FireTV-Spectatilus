package de.klaffky.spectatilus.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.klaffky.spectatilus.data.Item
import de.klaffky.spectatilus.data.Session
import kotlinx.coroutines.delay

/**
 * Merkt sich das zuletzt fokussierte Element eines Screens, damit nach „Zurück“
 * der Fokus wieder genau dort landet (wie bei tvOS).
 */
class FocusMemory {
    var lastKey: String? = null
    var restorePending: Boolean = false
}

val LocalFocusMemory = compositionLocalOf<FocusMemory?> { null }

@Composable
fun ProvideFocusMemory(memory: FocusMemory, content: @Composable () -> Unit) {
    DisposableEffect(memory) {
        onDispose { memory.restorePending = memory.lastKey != null }
    }
    CompositionLocalProvider(LocalFocusMemory provides memory, content = content)
}

/** Grundbaustein: fokussierbare Fläche, die beim Fokus größer wird und leuchtet. */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusKey: String? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.07f,
    border: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    val memory = LocalFocusMemory.current
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester

    if (focusKey != null && memory != null) {
        LaunchedEffect(Unit) {
            if (memory.restorePending && memory.lastKey == focusKey) {
                delay(30)
                if (runCatching { requester.requestFocus() }.isSuccess) memory.restorePending = false
            }
        }
    }

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.shadow(20.dp, shape, ambientColor = C.Accent, spotColor = Color.Black) else Modifier)
            .clip(shape)
            .then(if (focused && border) Modifier.border(2.5.dp, Color.White.copy(alpha = 0.95f), shape) else Modifier)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    if (focusKey != null) memory?.lastKey = focusKey
                    onFocused?.invoke()
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

/** Pillen-Button im Apple-TV-Stil: weiß, wenn fokussiert. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = false,
    focusKey: String? = null,
    focusRequester: FocusRequester? = null,
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier,
        focusKey = focusKey,
        shape = RoundedCornerShape(50),
        focusedScale = 1.08f,
        border = false,
        focusRequester = focusRequester,
    ) { focused ->
        val bg by animateColorAsState(
            when {
                focused -> Color.White
                primary -> C.Accent.copy(alpha = 0.9f)
                else -> Color.White.copy(alpha = 0.14f)
            }, label = "pillBg"
        )
        Row(
            Modifier.background(bg).padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Text(icon, color = if (focused) C.Bg else Color.White, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = if (focused) C.Bg else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Placeholder(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(C.SurfaceHigh, Color(0xFF2A2350)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = C.TextDim, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(C.AccentSoft))
    }
}

/** Hochformat-Poster (Filme, Serien). */
@Composable
fun PosterCard(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = 124.dp, focusKey: String? = null, onFocused: (() -> Unit)? = null) {
    val api = Session.api
    Column(if (width != null) modifier.width(width) else modifier.fillMaxWidth()) {
        FocusBox(onClick = onClick, focusKey = focusKey, onFocused = onFocused, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            val url = api?.posterUrl(item, 480)
            if (url != null) {
                AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(C.SurfaceHigh))
            } else {
                Placeholder(item.name, Modifier.fillMaxSize())
            }
            if (item.played) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else if ((item.unplayedCount ?: 0) > 0 && item.type == "Series") {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(C.Accent).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("${item.unplayedCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (item.progress > 0f && !item.played) {
                ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.name, color = C.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.year?.let { Text("$it", color = C.TextFaint, fontSize = 11.sp) }
    }
}

/** Querformat-Karte (Weiter ansehen, Episoden). */
@Composable
fun LandscapeCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 236.dp,
    title: String? = null,
    subtitle: String? = null,
    focusKey: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    val api = Session.api
    Column(modifier.width(width)) {
        FocusBox(onClick = onClick, focusKey = focusKey, onFocused = onFocused, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val url = api?.landscapeUrl(item, 640)
            if (url != null) {
                AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(C.SurfaceHigh))
            } else {
                Placeholder(item.name, Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
            if (item.progress > 0f && !item.played) {
                ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp))
            }
            if (item.played) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title ?: (item.seriesName ?: item.name), color = C.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = subtitle ?: if (item.type == "Episode") listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else item.year?.toString()
        if (!sub.isNullOrBlank()) Text(sub, color = C.TextFaint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Eine horizontale Reihe mit Überschrift. */
@Composable
fun ShelfRow(
    title: String,
    items: List<Item>,
    landscape: Boolean,
    rowKey: String,
    state: LazyListState,
    onClick: (Item) -> Unit,
    onFocused: ((Item) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = C.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp, bottom = 2.dp),
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (landscape) 18.dp else 16.dp),
        ) {
            items(items, key = { it.id }) { item ->
                if (landscape) {
                    LandscapeCard(item, onClick = { onClick(item) }, focusKey = "$rowKey/${item.id}", onFocused = { onFocused?.invoke(item) })
                } else {
                    PosterCard(item, onClick = { onClick(item) }, focusKey = "$rowKey/${item.id}", onFocused = { onFocused?.invoke(item) })
                }
            }
        }
    }
}

@Composable
fun Badge(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MetaLine(item: Item, modifier: Modifier = Modifier, showBadges: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val parts = listOfNotNull(
            item.year?.toString(),
            item.communityRating?.let { "★ %.1f".format(it) },
            item.officialRating,
            de.klaffky.spectatilus.data.formatRuntime(item.runTimeTicks).takeIf { item.type != "Series" },
            item.childCount?.takeIf { item.type == "Series" && it > 0 }?.let { if (it == 1) "1 Staffel" else "$it Staffeln" },
        )
        Text(parts.joinToString("  ·  "), color = C.TextDim, fontSize = 13.sp)
        if (showBadges) item.badges.forEach { Badge(it) }
    }
}

@Composable
fun CenterMessage(title: String, message: String? = null, actions: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(520.dp)) {
            Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(message, color = C.TextDim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { actions() }
        }
    }
}

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(280); phase = (phase + 1) % 3
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (i == phase) C.AccentSoft else C.SurfaceHigh))
        }
    }
}
