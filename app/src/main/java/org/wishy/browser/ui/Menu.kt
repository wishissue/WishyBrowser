package org.wishy.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController
import org.wishy.browser.Page
import org.wishy.browser.R
import org.wishy.browser.Shields

/** Side sheet that slides in from the right: quick actions first, then every page. */
@Composable
fun MenuSheet(c: BrowserController) {
    BackHandler { c.popPage() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { c.popPage() }
    ) {
        AnimatedVisibility(
            shown, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp),
                modifier = Modifier.width(500.dp).fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            ) {
                LazyColumn(
                    Modifier.fillMaxSize().safeDrawingPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(Modifier.padding(bottom = 4.dp)) {
                            Text("Wishy Browser", style = MaterialTheme.typography.titleLarge, fontFamily = org.wishy.browser.ui.theme.BagelFatOne)
                            Text("Version ${c.versionName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item { CurrentPageCard(c) }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TvIconButton(Sym.ArrowBack, "Back", { c.back(); c.closeAll() }, Modifier.focusRequester(first), size = 48.dp)
                            TvIconButton(Sym.ArrowForward, "Forward", { c.forward(); c.closeAll() }, size = 48.dp)
                            TvIconButton(Sym.Refresh, "Reload", { c.reload(); c.closeAll() }, size = 48.dp)
                            TvIconButton(Sym.Home, "Home", { c.goHome() }, size = 48.dp)
                            TvIconButton(
                                if (c.isBookmarked) Sym.BookmarkFilled else Sym.Bookmark, "Bookmark",
                                { c.toggleBookmark() }, selected = c.isBookmarked, size = 48.dp
                            )
                            TvIconButton(Sym.AdsClick, "Pointer", { c.togglePointer() }, selected = c.pointerOn, size = 48.dp)
                        }
                    }
                    item { SectionLabel("Browse") }
                    item {
                        val active = c.downloads.count { it.state == org.wishy.browser.DlState.RUNNING || it.state == org.wishy.browser.DlState.QUEUED }
                        TileRow(
                            FeatureTile(Sym.Download, "Downloads", if (active > 0) "$active running" else "${c.downloads.size} in the list") { c.openPage(Page.DOWNLOADS) },
                            FeatureTile(Sym.BookmarkFilled, "Bookmarks", "${c.bookmarks.size} saved") { c.openPage(Page.BOOKMARKS) },
                        )
                    }
                    item {
                        TileRow(
                            FeatureTile(Sym.Notifications, "Notifications", if (c.unreadNotes > 0) "${c.unreadNotes} new" else "All caught up") { c.openPage(Page.NOTIFICATIONS) },
                            FeatureTile(Sym.ShieldFilled, "Shields", if (Shields.blockerActive) "${c.blockedSession} blocked" else "Check blocker") { c.openPage(Page.SHIELDS) },
                        )
                    }
                    item {
                        TileRow(
                            FeatureTile(Sym.ShieldFilled, "Shields", if (Shields.blockerActive) "${c.blockedSession} blocked" else "Check blocker") { c.openPage(Page.SHIELDS) },
                            if (c.readerable) FeatureTile(Sym.MenuBook, "Reader view", "Clean text") { c.closeAll(); c.openReader() }
                            else FeatureTile(Sym.Settings, "Settings", "Site, text, home") { c.openPage(Page.SETTINGS) },
                        )
                    }
                    item {
                        TileRow(
                            FeatureTile(Sym.ShieldLockFilled, "Privacy", "${c.protectionsOn} of ${c.protectionsTotal} on  \u00B7  ${c.searchEngine.label}") { c.openPage(Page.PRIVACY) },
                            FeatureTile(Sym.DeleteSweep, "Clear data", "Cookies, cache, history") { c.clearBrowsingData(); c.closeAll() },
                        )
                    }
                    item {
                        TileRow(
                            FeatureTile(Sym.Search, "Find in page", "Ctrl+F") { c.openFind() },
                            FeatureTile(Sym.ContentCopy, "Copy link", "To the clipboard") { c.copyLink() },
                        )
                    }
                    item { SectionLabel("Make it yours") }
                    item {
                        TileRow(
                            FeatureTile(Sym.Palette, "Themes", paletteName(c.themeId)) { c.openPage(Page.THEMES) },
                            FeatureTile(Sym.AdsClick, "Cursors", c.cursorSpec.name) { c.openPage(Page.CURSORS) },
                        )
                    }
                    if (c.readerable) item {
                        TileRow(FeatureTile(Sym.Settings, "Settings", "Site, text, home") { c.openPage(Page.SETTINGS) }, null)
                    }
                    item { SectionLabel("About") }
                    item { ActionRow(Sym.Code, "Developer", "wishissue") { c.openPage(Page.DEVELOPER) } }
                    item { ActionRow(Sym.Description, "Credits", "Open-source projects behind Wishy") { c.openPage(Page.CREDITS) } }
                    item { ActionRow(Sym.VolunteerActivism, "Donate", "Keep Wishy free and ad-free") { c.openPage(Page.DONATE) } }
                    item { Spacer(Modifier.height(6.dp)) }
                    item { ActionRow(Sym.Close, "Exit browser") { c.exitApp() } }
                }
            }
        }
    }
}

internal fun paletteName(id: String): String =
    org.wishy.browser.ui.theme.PALETTE_INFOS.firstOrNull { it.id == id }?.label ?: "Wishy Blue"

private class FeatureTile(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

/** Two tiles side by side (the second slot may be empty). */
@Composable
private fun TileRow(a: FeatureTile, b: FeatureTile?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TileCard(a, Modifier.weight(1f))
        if (b != null) TileCard(b, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TileCard(t: FeatureTile, modifier: Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = modifier.tvClickable(shape, onClick = t.onClick)) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(t.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Text(t.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Live preview of the page you are on, so you can see where you are before choosing anything. */
@Composable
private fun CurrentPageCard(c: BrowserController) {
    val shape = RoundedCornerShape(26.dp)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            if (c.atHome) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(Sym.HomeFilled, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Text("Start screen", Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge)
            } else {
                PageThumb(c, c.url, c.title, Modifier.fillMaxWidth())
                Text(
                    c.title.ifBlank { c.hostOf(c.url) }, Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    c.hostOf(c.url), Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
