package org.wishy.browser.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.wishy.browser.BrowserController
import org.wishy.browser.Page
import org.wishy.browser.R
import org.wishy.browser.Shields
import org.wishy.browser.ui.theme.BagelFatOne
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private class Shortcut(val name: String, val icon: ImageVector, val color: Color, val url: String?)

private val SHORTCUTS = listOf(
    Shortcut("YouTube", Sym.SmartDisplay, Color(0xFFFF1F3D), "https://www.youtube.com"),
    Shortcut("Weather", Sym.PartlyCloudyDay, Color(0xFF4C9BFF), "https://duckduckgo.com/?q=weather&ia=weather"),
    Shortcut("Maps", Sym.Map, Color(0xFF2FBF71), "https://www.google.com/maps"),
    Shortcut("Amazon", Sym.ShoppingCart, Color(0xFFFF9900), "https://www.amazon.com"),
    Shortcut("Gmail", Sym.Mail, Color(0xFFEA4335), "https://mail.google.com"),
    Shortcut("Facebook", Sym.Forum, Color(0xFF1877F2), "https://www.facebook.com"),
    Shortcut("News", Sym.Newspaper, Color(0xFF14A3A3), "https://news.google.com"),
    Shortcut("Wikipedia", Sym.School, Color(0xFF6B7280), "https://www.wikipedia.org"),
    Shortcut("Saved", Sym.BookmarkFilled, Color(0xFFF5B301), null),
)

private val TILE_SHAPES = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon,
    MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.Gem, MaterialShapes.Cookie7Sided,
    MaterialShapes.Pill,
)

/**
 * Start screen. Top bar (shields, notifications, bookmarks, menu), then the clock and search, then
 * shelves: quick access (with a live preview once you have visited a site), your bookmarks and the
 * pages from this session. Everything below the search scrolls with the D-pad.
 */
@Composable
fun HomeScreen(c: BrowserController) {
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(c.overlay) {
        if (c.overlay == null) {
            delay(250)
            runCatching { c.urlFocus.requestFocus() }
        }
    }

    val canFocus = c.overlay == null && c.prompt == null

    Box(
        Modifier.fillMaxSize()
            .focusProperties { this.canFocus = canFocus }
            .background(cs.background)
    ) {

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 48.dp, vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().focusProperties { down = c.urlFocus },
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
            ) {
                ShieldPill(c)
                Spacer(Modifier.size(12.dp))
                BadgedIconButton(Sym.Notifications, "Notifications", c.unreadNotes, { c.openPage(Page.NOTIFICATIONS) })
                Spacer(Modifier.size(12.dp))
                TvIconButton(Sym.BookmarkFilled, "Bookmarks", { c.openPage(Page.BOOKMARKS) })
                Spacer(Modifier.size(12.dp))
                TvIconButton(Sym.MoreVert, "Menu", { c.openPage(Page.MENU) })
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Clock(Modifier.reveal(0))
                        Spacer(Modifier.height(16.dp))
                        SearchPill(
                            display = "", editText = "", placeholder = "Search ${c.searchEngine.label} or type an address",
                            icon = Sym.Search, onSubmit = { c.open(it) }, focus = c.urlFocus,
                            modifier = Modifier.widthIn(max = 840.dp).reveal(1), height = 68.dp,
                            loading = c.loading
                        )
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().reveal(2)) {
                        ShelfHeader(Sym.GridView, "Quick access")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            items(SHORTCUTS.size) { i ->
                                val sc = SHORTCUTS[i]
                                Tile(c, sc, TILE_SHAPES[i % TILE_SHAPES.size]) {
                                    val u = sc.url
                                    if (u == null) c.openPage(Page.BOOKMARKS) else c.open(u)
                                }
                            }
                        }
                    }
                }
                if (c.bookmarks.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth()) {
                        ShelfHeader(Sym.BookmarkFilled, "Bookmarks") {
                            Text("${c.bookmarks.size} saved", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            items(c.bookmarks.toList(), key = { it.url }) { b ->
                                PreviewCard(c, b.title, b.url) { c.open(b.url) }
                            }
                        }
                    }
                }
                if (c.recents.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth()) {
                        ShelfHeader(Sym.History, "Recent this session") {
                            Text("Not saved anywhere", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            items(c.recents.toList(), key = { it.url }) { r ->
                                PreviewCard(c, r.title, r.url) { c.open(r.url) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Shields on · 42 blocked": opens the Shields page. */
@Composable
private fun ShieldPill(c: BrowserController) {
    val ok = Shields.blockerActive
    val ready = Shields.ready
    if (!ok && ready) return
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        shape = shape,
        color = cs.secondaryContainer,
        modifier = Modifier.tvClickable(shape) { c.openPage(Page.SHIELDS) }
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!ready) LoadingIndicator(Modifier.size(24.dp))
            else Icon(
                Sym.ShieldFilled, null, Modifier.size(22.dp),
                tint = cs.onSecondaryContainer
            )
            Text(
                when {
                    !ready -> "Starting shields"
                    c.blockedSession > 0 -> "Shields on · ${c.blockedSession} blocked"
                    else -> "Shields on"
                },
                Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Big auto-scaling time in expressively styled font (system 12/24-hour setting) with AM/PM indicator. Updates on the minute. */
@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60_000L - value % 60_000L + 50L) }
    }
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(ctx) }
    val date = Date(now)
    val loc = remember { java.util.Locale.getDefault() }
    val time = java.text.SimpleDateFormat(if (is24) "HH:mm" else "h:mm", loc).format(date)
    val ampm = if (is24) null else java.text.SimpleDateFormat("a", loc).format(date)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val targetSp = (maxWidth.value * 0.16f).coerceIn(48f, 82f)
        var scaledSp by remember(time, ampm, maxWidth) { mutableFloatStateOf(targetSp) }
        var readyToDraw by remember(time, ampm, maxWidth) { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer { alpha = if (readyToDraw) 1f else 0f }
        ) {
            Text(
                text = time,
                color = cs.onBackground,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow && scaledSp > 32f) {
                        scaledSp -= 2f
                    } else {
                        readyToDraw = true
                    }
                },
                style = TextStyle(
                    fontFamily = BagelFatOne,
                    fontSize = scaledSp.sp,
                    lineHeight = (scaledSp * 1.05f).sp,
                    letterSpacing = (-1).sp,
                    fontFeatureSettings = "tnum",
                )
            )
            if (ampm != null) {
                val ampmSp = (scaledSp * 0.28f).coerceAtLeast(16f)
                Text(
                    text = ampm.uppercase(),
                    color = cs.primary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = (scaledSp * 0.1f).dp, bottom = (scaledSp * 0.12f).dp),
                    style = TextStyle(
                        fontFamily = BagelFatOne,
                        fontSize = ampmSp.sp,
                        lineHeight = (ampmSp * 1.1f).sp,
                    )
                )
            }
        }
    }
}

/**
 * A shortcut. After you have visited the site this session, its tile shows a small live preview
 * of the page instead of just the icon.
 */
@Composable
private fun Tile(c: BrowserController, sc: Shortcut, polygon: androidx.graphics.shapes.RoundedPolygon, onClick: () -> Unit) {
    val cardShape = RoundedCornerShape(28.dp)
    val shape = polygon.toShape()
    val host = remember(sc.url) { sc.url?.let { c.hostOf(it) } }
    val preview = rememberHostThumb(c, host)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = cardShape,
        modifier = Modifier.size(width = 156.dp, height = 140.dp).tvClickable(cardShape, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (preview != null) {
                Box(Modifier.width(132.dp).height(74.dp).clip(RoundedCornerShape(16.dp))) {
                    Image(preview, sc.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(5.dp).size(28.dp).clip(shape).background(sc.color),
                        contentAlignment = Alignment.Center
                    ) { Icon(sc.icon, null, Modifier.size(17.dp), tint = Color.White) }
                }
            } else {
                Box(Modifier.size(74.dp).clip(shape).background(sc.color), contentAlignment = Alignment.Center) {
                    Icon(sc.icon, null, Modifier.size(38.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(sc.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A page card: live preview on top, title and site underneath. */
@Composable
private fun PreviewCard(c: BrowserController, title: String, url: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = shape,
        modifier = Modifier.width(236.dp).tvClickable(shape, onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp)) {
            PageThumb(c, url, title, Modifier.fillMaxWidth())
            Text(
                title.ifBlank { c.hostOf(url) }, Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                c.hostOf(url), Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
