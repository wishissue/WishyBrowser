package org.wishy.browser.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.wishy.browser.Page
import org.wishy.browser.Prefs
import org.wishy.browser.CursorImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController
import org.wishy.browser.ui.*
import org.wishy.browser.ui.theme.PALETTE_INFOS
import org.wishy.browser.ui.theme.PaletteInfo
import org.wishy.browser.ui.theme.paletteScheme

// ---------------------------------------------------------------- Bookmarks

@Composable
fun BookmarksPage(c: BrowserController) {
    PageScaffold("Bookmarks", { c.popPage() }) {
        if (c.bookmarks.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Sym.Bookmark, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("No bookmarks yet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Open a page, then select the bookmark button in the toolbar. Its preview is saved with it.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(256.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(c.bookmarks.toList(), key = { it.url }) { b ->
                    val shape = RoundedCornerShape(26.dp)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape) {
                        Column(Modifier.padding(10.dp)) {
                            PageThumb(
                                c, b.url, b.title,
                                Modifier.fillMaxWidth().tvClickable(RoundedCornerShape(18.dp), onLongClick = { c.removeBookmark(b) }) { c.open(b.url) }
                            )
                            Row(Modifier.padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        c.hostOf(b.url), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TvIconButton(Sym.DeleteOutline, "Delete bookmark", { c.removeBookmark(b) }, size = 44.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Live preview mock

/** A tiny browser drawn in the given colours, so a theme or cursor can be judged before choosing it. */
@Composable
private fun MiniBrowser(scheme: ColorScheme, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.clip(shape).background(scheme.surface)) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(26.dp).background(scheme.surfaceContainer, RoundedCornerShape(13.dp)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(3) { Box(Modifier.size(14.dp).background(scheme.surfaceContainerHighest, CircleShape)) }
                Box(Modifier.weight(1f).height(14.dp).background(scheme.surfaceContainerHigh, RoundedCornerShape(7.dp)))
                Box(Modifier.size(14.dp).background(scheme.primary, CircleShape))
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.primaryContainer, RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(scheme.secondaryContainer, RoundedCornerShape(12.dp)))
                    Box(Modifier.weight(1f).fillMaxWidth().background(scheme.tertiaryContainer, RoundedCornerShape(12.dp)))
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).background(scheme.surfaceContainerHigh, RoundedCornerShape(5.dp)))
        }
        overlay()
    }
}

// ---------------------------------------------------------------- Themes

@Composable
fun ThemesPage(c: BrowserController) {
    var hover by remember { mutableStateOf(c.themeId) }
    PageScaffold("Themes", { c.popPage() }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("dark" to "Dark", "light" to "Light", "dusk" to "Dusk", "oled" to "AMOLED").forEach { (m, label) ->
                        FilterChip(selected = c.themeMode == m, onClick = { c.setThemeMode(m) }, label = { Text(label) })
                    }
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(168.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(PALETTE_INFOS, key = { it.id }) { p ->
                        ThemeTile(p, c.themeMode, c.themeId == p.id, onFocus = { hover = p.id }) { c.setTheme(p.id) }
                    }
                }
            }
            Column(Modifier.width(300.dp).padding(top = 8.dp)) {
                val scheme = remember(hover, c.themeMode) { paletteScheme(hover, c.themeMode) }
                Text("Live preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                MiniBrowser(scheme, Modifier.padding(top = 8.dp).fillMaxWidth().aspectRatio(16f / 11f))
                Text(
                    paletteName(hover), Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (hover == c.themeId) "In use" else "Press OK to use this theme",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemeTile(p: PaletteInfo, mode: String, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    val scheme = remember(p.id, mode) { paletteScheme(p.id, mode) }
    val shape = RoundedCornerShape(28.dp)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    Surface(
        color = scheme.surfaceContainerHigh, shape = shape,
        modifier = Modifier.fillMaxWidth().height(116.dp)
            .then(if (selected) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .tvClickable(shape, source = source, onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(scheme.primary); Swatch(scheme.secondary, -8); Swatch(scheme.tertiary, -8)
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Sym.CheckCircleFilled, "Selected", Modifier.size(24.dp), tint = scheme.primary)
            }
            Text(p.label, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color, overlap: Int = 0) {
    Box(Modifier.offset(x = overlap.dp).size(30.dp).background(color, CircleShape))
}

// ---------------------------------------------------------------- Cursors

@Composable
fun CursorsPage(c: BrowserController) {
    var hover by remember { mutableStateOf(c.cursorId) }
    var adding by remember { mutableStateOf(false) }
    val shown = Cursors.resolve(hover, c.cursorLib)
    val img = shown.image

    PageScaffold("Cursors", { c.popPage() }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Left: live preview, size, and info about the cursor under the remote.
            Column(Modifier.width(300.dp).padding(top = 8.dp)) {
                Text("Live preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                MiniBrowser(MaterialTheme.colorScheme, Modifier.padding(top = 8.dp).fillMaxWidth().aspectRatio(16f / 11f)) {
                    Box(Modifier.align(Alignment.Center).offset(x = 18.dp, y = 6.dp)) {
                        CursorGlyph(shown, 38.dp * c.cursorScale)
                    }
                }
                Text(shown.name, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        img == null -> "Animated, uses your theme colours"
                        img.custom -> "Your own picture"
                        else -> "${img.pack} \u00B7 open source (GPL-3.0)"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Size", Modifier.padding(top = 14.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prefs.CURSOR_SCALES.forEachIndexed { i, v ->
                        FilterChip(
                            selected = c.cursorScale == v, onClick = { c.setCursorScale(i) },
                            label = { Text(Prefs.CURSOR_SCALE_LABELS[i].substringBefore(' ')) }
                        )
                    }
                }
                if (img != null && img.custom) {
                    Spacer(Modifier.height(10.dp))
                    ActionRow(Sym.DeleteOutline, "Delete this cursor") { c.removeCursor(img); hover = c.cursorId }
                }
            }
            LazyVerticalGrid(
                GridCells.Adaptive(140.dp), Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Yours") }
                item {
                    val shape = RoundedCornerShape(28.dp)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer, shape = shape,
                        modifier = Modifier.fillMaxWidth().height(128.dp).tvClickable(shape) { adding = true }
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Sym.AddCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.height(6.dp))
                            Text("Add your own", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                items(c.cursorLib.custom.toList(), key = { it.id }) { ci ->
                    CursorTile(Cursors.of(ci), c.cursorId == ci.id, { hover = ci.id }, { c.removeCursor(ci) }) { c.setCursor(ci.id) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Open-source pack: Bibata") }
                items(c.cursorLib.packed, key = { it.id }) { pi ->
                    CursorTile(Cursors.of(pi), c.cursorId == pi.id, { hover = pi.id }, null) { c.setCursor(pi.id) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Animated, in your theme colours") }
                items(Cursors.animated, key = { it.id }) { spec ->
                    CursorTile(spec, c.cursorId == spec.id, { hover = spec.id }, null) { c.setCursor(spec.id) }
                }
            }
        }
    }
    if (adding) AddCursorDialog(c) { adding = false }
}

@Composable
private fun CursorTile(spec: CursorSpec, selected: Boolean, onFocus: () -> Unit, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val shape = RoundedCornerShape(28.dp)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth().height(128.dp).tvClickable(shape, onLongClick = onLongClick, source = source, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Only the focused or selected animated cursor moves, so a big grid stays smooth.
            CursorGlyph(spec, 56.dp, animate = focused || selected)
            Spacer(Modifier.height(8.dp))
            Text(spec.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Add a picture as a cursor: from a file, or from a web address (handy on TVs without a file manager). */
@Composable
private fun AddCursorDialog(c: BrowserController, onClose: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Sym.AddPhotoAlternate, null) },
        title = { Text("Add your own cursor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use a PNG, JPG or WebP picture. Big pictures are shrunk to fit.", style = MaterialTheme.typography.bodyMedium)
                Text("Where does it click?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tip, { tip = true }, label = { Text("Top-left tip") })
                    FilterChip(!tip, { tip = false }, label = { Text("Centre") })
                }
                OutlinedTextField(
                    address, { address = it }, singleLine = true, label = { Text("Picture web address (https)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton({ c.addCursorFromFile(tip); onClose() }) { Text("Choose a file instead") }
            }
        },
        confirmButton = {
            TextButton({ c.addCursorFromUrl(address, tip); onClose() }, enabled = address.isNotBlank()) { Text("Add from address") }
        },
        dismissButton = { TextButton(onClose) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsPage(c: BrowserController) {
    PageScaffold("Settings", { c.popPage() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionLabel("Browsing") }
            item {
                ActionRow(Sym.AdsClick, "Pointer mode", "Move a cursor with the D-pad; OK clicks", trailing = { Switch(c.pointerOn, null) }) { c.togglePointer() }
            }
            item {
                ActionRow(Sym.DesktopWindows, "Desktop site", "Ask sites for their full desktop version", trailing = { Switch(c.desktopMode, null) }) { c.toggleDesktopMode() }
            }
            item {
                ActionRow(Sym.TextIncrease, "Text size", "${(c.textScale * 100).toInt()}%  (select to change)") { c.cycleTextSize() }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "The quick brown fox jumps over the lazy dog.", Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * c.textScale)
                    )
                }
            }
            item { SectionLabel("Previews and notifications") }
            item {
                ActionRow(Sym.Image, "Page previews", "Thumbnails of pages you visit (kept in memory; bookmarks keep theirs)", trailing = { Switch(c.thumbsEnabled, null) }) { c.toggleThumbs() }
            }
            item { ActionRow(Sym.CleaningServices, "Clear previews", "Remove every saved thumbnail") { c.clearPreviews() } }
            item {
                ActionRow(Sym.Notifications, "Block notices", "Show a card when ads or trackers were blocked", trailing = { Switch(c.notifyBlocked, null) }) { c.toggleNotifyBlocked() }
            }
            item { ActionRow(Sym.NotificationsActive, "Notification history", "${c.noteHistory.size} this session") { c.openPage(Page.NOTIFICATIONS) } }
            item { SectionLabel("Home page") }
            item { ActionRow(Sym.Home, "Use this page as home", c.homeUrl ?: "Blank start screen") { c.setHomeToCurrentPage() } }
            item { ActionRow(Sym.Refresh, "Reset home page", "Go back to the start screen") { c.clearHomePage() } }
            item { SectionLabel("Appearance") }
            item { ActionRow(Sym.Palette, "Themes", "Colours with a live preview") { c.openPage(Page.THEMES) } }
            item { ActionRow(Sym.AdsClick, "Cursors", c.cursorSpec.name) { c.openPage(Page.CURSORS) } }
            item {
                ActionRow(Sym.Animation, "Reduce motion", "Turn animations into instant changes", trailing = { Switch(c.reduceMotion, null) }) { c.setReduceMotion(!c.reduceMotion) }
            }
            item { SectionLabel("System") }
            item { ActionRow(Sym.OpenInNew, "Set as default browser", "Open links from other apps in Wishy") { c.openDefaultBrowserSettings() } }
        }
    }
}
