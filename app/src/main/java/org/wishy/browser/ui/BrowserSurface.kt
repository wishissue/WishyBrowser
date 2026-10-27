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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.SideEffect
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
            // One native container holds the page AND the pointer, so the pointer is drawn by the view system
            // right on top of the WebView. key(): if the renderer dies a new WebView is built and re-hosted here.
            key(c.webGen) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            (c.web.parent as? ViewGroup)?.removeView(c.web)
                            (c.pointer.parent as? ViewGroup)?.removeView(c.pointer)
                            addView(c.web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            addView(c.pointer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
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
                    display = prettyUrl(c.url), editText = c.url, placeholder = "Search ${c.searchEngine.label} or enter address",
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
            // Fixed-height slot: if the bar appeared and disappeared, the toolbar would change height and the WebView
            // would be resized (and flash) at the start and end of every page load.
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp).height(4.dp)) {
                if (c.loading) LinearProgressIndicator({ (c.progress / 100f).coerceIn(0.03f, 1f) }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** "https://www.example.com/a/b" -> "example.com/a/b" */
private fun prettyUrl(u: String): String =
    u.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

/**
 * Only tells the native [BrowserController.pointer] WHICH glyph to show and WHETHER to show it. Movement never goes
 * through Compose, so a moving pointer causes no recomposition, no layout and no redraw of the page.
 */
@Composable
private fun PointerOverlay(c: BrowserController) {
    val density = LocalDensity.current
    val lib = LocalCursorLib.current
    val spec = c.cursorSpec
    val sizePx = with(density) { (38.dp * c.cursorScale).toPx() }.roundToInt().coerceAtLeast(16)
    val glyph = remember(spec.id, sizePx, lib) { renderCursor(spec, sizePx, lib) }
    val visible = c.pointerVisible
    SideEffect {
        glyph?.let { c.pointer.setGlyph(it.bitmap, it.hotXpx, it.hotYpx) }
        c.pointer.setShown(visible && glyph != null)
        c.placeCursor()
    }
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
