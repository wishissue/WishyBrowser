package org.wishy.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import org.wishy.browser.BarSide
import org.wishy.browser.DlState
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

/** The browsing screen: the toolbar on the chosen edge (or hidden), the page, and the pointer drawn over the page. */
@Composable
fun BrowserSurface(c: BrowserController) {
    val side = c.barSide
    val canFocus = c.overlay == null && c.prompt == null
    val root = Modifier.fillMaxSize()
        .focusProperties { this.canFocus = canFocus }
        .then(if (c.isFullScreen || c.hideBar) Modifier else Modifier.safeDrawingPadding())

    // Full-screen mode: a toolbar that was brought back goes away by itself after a few unused seconds.
    LaunchedEffect(c.hideBar, c.barRevealed, c.chromeFocused, c.barTick) {
        if (c.hideBar && c.barRevealed && !c.chromeFocused) { delay(5000); c.hideBarNow() }
    }

    if (c.hideBar) {
        // The toolbar floats over the page, so showing it never resizes the page (that made it flash).
        Box(root) {
            PageArea(c, Modifier.fillMaxSize())
            if (!c.barShown && !c.atHome && !c.isFullScreen) EdgeStrip(c, Modifier.align(edgeAlignment(side)))
            BarHost(c, Modifier.align(edgeAlignment(side)))
        }
    } else {
        ToolbarLayout(c, root)
    }
    if (c.addressOpen && !c.atHome) AddressSheet(c)
}

@Composable
private fun ToolbarLayout(c: BrowserController, modifier: Modifier) {
    when (c.barSide) {
        BarSide.TOP -> Column(modifier) { BarHost(c, Modifier); PageArea(c, Modifier.weight(1f).fillMaxWidth()) }
        BarSide.BOTTOM -> Column(modifier) { PageArea(c, Modifier.weight(1f).fillMaxWidth()); BarHost(c, Modifier) }
        BarSide.LEFT -> Row(modifier) { BarHost(c, Modifier); PageArea(c, Modifier.weight(1f).fillMaxHeight()) }
        BarSide.RIGHT -> Row(modifier) { PageArea(c, Modifier.weight(1f).fillMaxHeight()); BarHost(c, Modifier) }
    }
}

private fun edgeAlignment(side: BarSide): Alignment = when (side) {
    BarSide.TOP -> Alignment.TopCenter
    BarSide.BOTTOM -> Alignment.BottomCenter
    BarSide.LEFT -> Alignment.CenterStart
    BarSide.RIGHT -> Alignment.CenterEnd
}

/** Fade + slide only (no size animation), so the page is resized once instead of every frame. */
@Composable
private fun BarHost(c: BrowserController, modifier: Modifier) {
    val side = c.barSide
    val toward: (Int) -> Int = { full -> if (side == BarSide.TOP || side == BarSide.LEFT) -full / 2 else full / 2 }
    AnimatedVisibility(
        c.barShown, modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            (if (side.vertical) slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { toward(it) }
            else slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { toward(it) }),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            (if (side.vertical) slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { toward(it) }
            else slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { toward(it) }),
    ) { Toolbar(c) }
}

/** The page, the loading spinner, the pointer and the key hints. */
@Composable
private fun PageArea(c: BrowserController, modifier: Modifier) {
    Box(modifier.onSizeChanged { c.onViewSized(it.width.toFloat(), it.height.toFloat()) }) {
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
            visible = c.loading && !c.barShown,
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

/** Touch: a thin strip on the toolbar's edge. Drag inward from it to bring the toolbar back (full-screen mode). */
@Composable
private fun EdgeStrip(c: BrowserController, modifier: Modifier) {
    val side = c.barSide
    val size = if (side.vertical) Modifier.fillMaxHeight().width(28.dp) else Modifier.fillMaxWidth().height(28.dp)
    var travelled by remember { mutableFloatStateOf(0f) }
    Box(
        modifier.then(size).pointerInput(side) {
            detectDragGestures(
                onDragStart = { travelled = 0f },
                onDrag = { change, drag ->
                    change.consume()
                    travelled += when (side) {
                        BarSide.TOP -> drag.y
                        BarSide.BOTTOM -> -drag.y
                        BarSide.LEFT -> drag.x
                        BarSide.RIGHT -> -drag.x
                    }
                    if (travelled > 40.dp.toPx()) { travelled = 0f; c.revealBar() }
                }
            )
        }
    )
}

/** The key that takes the remote from the toolbar back to the page, whichever edge the toolbar is on. */
private fun backToPageKey(side: BarSide): Key = when (side) {
    BarSide.TOP -> Key.DirectionDown
    BarSide.BOTTOM -> Key.DirectionUp
    BarSide.LEFT -> Key.DirectionRight
    BarSide.RIGHT -> Key.DirectionLeft
}

@Composable
private fun Toolbar(c: BrowserController) {
    // In full-screen mode the toolbar was just brought back by a key: it takes the remote's focus at once.
    LaunchedEffect(Unit) { if (c.takeFocusOnReveal()) runCatching { c.urlFocus.requestFocus() } }
    if (c.barSide.vertical) SideRail(c) else WideBar(c)
}

private fun downloadsActive(c: BrowserController): Int =
    c.downloads.count { it.state == DlState.RUNNING || it.state == DlState.QUEUED }

@Composable
private fun WideBar(c: BrowserController) {
    val barShape = RoundedCornerShape(32.dp)
    val back = backToPageKey(c.barSide)
    val active = downloadsActive(c)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = barShape, tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
            .onFocusChanged { c.chromeFocused = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == back) { c.leaveToolbar(); true } else false
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
                if (active > 0) BadgedIconButton(
                    Sym.Download, "Downloads", active, { c.openPage(Page.DOWNLOADS) }, size = 48.dp,
                    badgeColor = MaterialTheme.colorScheme.primary, onBadgeColor = MaterialTheme.colorScheme.onPrimary
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

/** Left and right toolbar: the same buttons in a column. The address opens as a sheet (a column has no room for it). */
@Composable
private fun SideRail(c: BrowserController) {
    val back = backToPageKey(c.barSide)
    val active = downloadsActive(c)
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(32.dp), tonalElevation = 3.dp,
        modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 10.dp)
            .onFocusChanged { c.chromeFocused = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == back) { c.leaveToolbar(); true } else false
            }
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(44.dp).height(4.dp)) {
                if (c.loading) LinearProgressIndicator({ (c.progress / 100f).coerceIn(0.03f, 1f) }, Modifier.fillMaxWidth())
            }
            TvIconButton(Sym.ArrowBack, "Back", { c.back() }, Modifier.alpha(if (c.canGoBack) 1f else 0.45f), size = 44.dp)
            TvIconButton(Sym.ArrowForward, "Forward", { c.forward() }, Modifier.alpha(if (c.canGoForward) 1f else 0.45f), size = 44.dp)
            TvIconButton(
                if (c.loading) Sym.Close else Sym.Refresh, if (c.loading) "Stop" else "Reload",
                { if (c.loading) c.stop() else c.reload() }, size = 44.dp
            )
            TvIconButton(Sym.Home, "Home", { c.goHome() }, size = 44.dp)
            TvIconButton(
                if (c.url.startsWith("https://")) Sym.Lock else Sym.Search, "Address", { c.openAddress() },
                Modifier.focusRequester(c.urlFocus), size = 44.dp
            )
            BadgedIconButton(Sym.ShieldFilled, "Shields", c.blockedNow, { c.openPage(Page.SHIELDS) }, size = 44.dp, badgeColor = primary, onBadgeColor = onPrimary)
            TvIconButton(
                if (c.isBookmarked) Sym.BookmarkFilled else Sym.Bookmark, "Bookmark", { c.toggleBookmark() },
                selected = c.isBookmarked, size = 44.dp
            )
            if (active > 0) BadgedIconButton(Sym.Download, "Downloads", active, { c.openPage(Page.DOWNLOADS) }, size = 44.dp, badgeColor = primary, onBadgeColor = onPrimary)
            TvIconButton(Sym.AdsClick, "Pointer", { c.togglePointer() }, selected = c.pointerOn, size = 44.dp)
            TvIconButton(Sym.MoreVert, "Menu", { c.openPage(Page.MENU) }, size = 44.dp)
        }
    }
}

/** Address entry for the left and right toolbars: a pill at the top of the screen that is already in edit mode. */
@Composable
private fun AddressSheet(c: BrowserController) {
    BackHandler { c.closeAddress() }
    val focus = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { c.closeAddress() }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(32.dp), tonalElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(24.dp).widthIn(max = 900.dp).fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        ) {
            Box(Modifier.padding(12.dp)) {
                SearchPill(
                    display = prettyUrl(c.url), editText = c.url, placeholder = "Search ${c.searchEngine.label} or enter address",
                    icon = if (c.url.startsWith("https://")) Sym.Lock else Sym.Search,
                    onSubmit = { c.open(it) }, focus = focus, height = 56.dp, startEditing = true
                )
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
                Hint(c.revealKeyHint, "Toolbar")
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
