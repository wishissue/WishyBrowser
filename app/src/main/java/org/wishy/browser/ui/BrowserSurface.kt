package org.wishy.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView
import org.wishy.browser.BrowserController
import org.wishy.browser.Page
import kotlin.math.roundToInt

/** The browsing screen: floating toolbar on top, the page below, the pointer drawn over the page. */
@Composable
fun BrowserSurface(c: BrowserController) {
    val showBar = !c.atHome && !c.isFullScreen
    val canFocus = c.overlay == null && c.prompt == null
    Column(
        Modifier.fillMaxSize()
            .focusProperties { this.canFocus = canFocus }
            .then(if (c.isFullScreen) Modifier else Modifier.safeDrawingPadding())
    ) {
        // Fade + slide only (no size animation), so the page is resized once instead of every frame.
        AnimatedVisibility(
            showBar,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it / 2 },
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it / 2 },
        ) { Toolbar(c) }

        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onSizeChanged { c.onViewSized(it.width.toFloat(), it.height.toFloat()) }
        ) {
            AndroidView(
                factory = { ctx -> GeckoView(ctx).also { it.setSession(c.session); c.geckoView = it } },
                modifier = Modifier.fillMaxSize()
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = c.loading && !showBar,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 24.dp),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), 0.8f),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), 0.8f),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    tonalElevation = 6.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                    }
                }
            }
            PointerOverlay(c)
            KeyHints(c, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Toolbar(c: BrowserController) {
    val barShape = RoundedCornerShape(32.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = barShape, tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
            .onFocusChanged { c.chromeFocused = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { c.focusPage(); true } else false
            }
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvIconButton(Sym.ArrowBack, "Back", { c.back() }, Modifier.alpha(if (c.canGoBack) 1f else 0.45f), size = 48.dp)
                TvIconButton(Sym.ArrowForward, "Forward", { c.forward() }, Modifier.alpha(if (c.canGoForward) 1f else 0.45f), size = 48.dp)
                TvIconButton(
                    if (c.loading) Sym.Close else Sym.Refresh, if (c.loading) "Stop" else "Reload",
                    { if (c.loading) c.stop() else c.reload() }, size = 48.dp
                )
                TvIconButton(Sym.Home, "Home", { c.goHome() }, size = 48.dp)
                SearchPill(
                    display = prettyUrl(c.url), editText = c.url, placeholder = "Search or enter address",
                    icon = if (c.url.startsWith("https://")) Sym.Lock else Sym.Search,
                    onSubmit = { c.open(it) }, focus = c.urlFocus, modifier = Modifier.weight(1f), height = 48.dp,
                    loading = c.loading
                )
                BadgedIconButton(
                    Sym.ShieldFilled, "Shields", c.blockedNow, { c.openPage(Page.SHIELDS) }, size = 48.dp,
                    badgeColor = MaterialTheme.colorScheme.primary, onBadgeColor = MaterialTheme.colorScheme.onPrimary
                )
                TvIconButton(
                    if (c.isBookmarked) Sym.BookmarkFilled else Sym.Bookmark, "Bookmark", { c.toggleBookmark() },
                    selected = c.isBookmarked, size = 48.dp
                )
                TvIconButton(Sym.AdsClick, "Pointer", { c.togglePointer() }, selected = c.pointerOn, size = 48.dp)
                TvIconButton(Sym.MoreVert, "Menu", { c.openPage(Page.MENU) }, size = 48.dp)
            }
            if (c.loading) {
                // Plain bar: the wavy Expressive one animates every frame, exactly while a heavy page is loading.
                LinearProgressIndicator({ (c.progress / 100f).coerceIn(0.03f, 1f) }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp))
            }
        }
    }
}

/** "https://www.example.com/a/b" -> "example.com/a/b" */
private fun prettyUrl(u: String): String =
    u.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

/** The animated cursor plus a ripple where OK was pressed. Never intercepts touches. */
@Composable
private fun PointerOverlay(c: BrowserController) {
    val density = LocalDensity.current
    val sizeDp = 38.dp * c.cursorScale
    val sizePx = with(density) { sizeDp.toPx() }
    val spec = c.cursorSpec
    val boxW = with(density) { spec.boxW(sizeDp).toPx() }
    val boxH = with(density) { spec.boxH(sizeDp).toPx() }
    val ring = MaterialTheme.colorScheme.primary
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(c.clickTick) {
        if (c.clickTick > 0) { ripple.snapTo(0f); ripple.animateTo(1f, tween(450)) }
    }
    val r12 = with(density) { 12.dp.toPx() }
    val r40 = with(density) { 40.dp.toPx() }
    val sw = with(density) { 4.dp.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val p = ripple.value
        if (p < 1f) drawCircle(ring.copy(alpha = 0.55f * (1f - p)), r12 + r40 * p, Offset(c.cursorX, c.cursorY), style = Stroke(sw))
    }
    // Calm pointer: fade it out a little when it has not moved for a few seconds.
    var idle by remember { mutableStateOf(false) }
    // cursorX/cursorY are read inside snapshotFlow, not passed as effect keys: keys are read during
    // composition, which recomposed this whole overlay and restarted this coroutine on every frame the
    // pointer moved, competing with the web page for the main thread.
    LaunchedEffect(c.clickTick, c.pointerVisible) {
        snapshotFlow { c.cursorX to c.cursorY }.collectLatest {
            idle = false
            delay(4000)
            idle = true
        }
    }
    val calm by animateFloatAsState(if (idle) 0.35f else 1f, tween(600), label = "calm")
    AnimatedVisibility(
        c.pointerVisible,
        modifier = Modifier.alpha(calm).offset {
            IntOffset(
                (c.cursorX - spec.hotX * boxW).roundToInt(),
                (c.cursorY - spec.hotY * boxH).roundToInt()
            )
        },
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), 0.6f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), 0.6f),
    ) { CursorGlyph(spec, sizeDp, animate = !idle) } // an idle pointer stops animating: no 60 fps redraw while you read
}

/** Remote hints, shown for a few seconds after the first page loads. */
@Composable
private fun KeyHints(c: BrowserController, modifier: Modifier) {
    AnimatedVisibility(
        c.showHints && c.overlay == null, modifier.padding(bottom = 24.dp),
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Hint("OK", "Click")
                Hint("MENU", "Pointer on/off")
                Hint("Hold MENU", "Menu")
                Hint("Up at top", "Toolbar")
                Hint("CH +/-", "Scroll")
            }
        }
    }
}

@Composable
private fun Hint(key: String, what: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(key, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(what, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}
