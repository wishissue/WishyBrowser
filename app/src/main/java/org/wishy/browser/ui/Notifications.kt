package org.wishy.browser.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController
import org.wishy.browser.Note
import org.wishy.browser.NoteKind

/** Colours and icon for one kind of notification, taken from the current theme. */
private class Look(val icon: ImageVector, val accent: Color, val badge: Color, val onBadge: Color)

private val Amber = Color(0xFFF5A524)

@Composable
private fun lookOf(kind: NoteKind): Look {
    val cs = MaterialTheme.colorScheme
    return when (kind) {
        NoteKind.INFO -> Look(Sym.InfoFilled, cs.primary, cs.primaryContainer, cs.onPrimaryContainer)
        NoteKind.SUCCESS -> Look(Sym.CheckCircleFilled, cs.tertiary, cs.tertiaryContainer, cs.onTertiaryContainer)
        NoteKind.WARNING -> Look(Sym.Warning, Amber, lerp(cs.surfaceContainerHighest, Amber, 0.32f), cs.onSurface)
        NoteKind.ERROR -> Look(Sym.Error, cs.error, cs.errorContainer, cs.onErrorContainer)
        NoteKind.SHIELD -> Look(Sym.ShieldFilled, cs.secondary, cs.secondaryContainer, cs.onSecondaryContainer)
    }
}

/**
 * Notification cards, top right, under the toolbar. They slide in, show a thin countdown line and
 * slide out; at most three at once. They never take focus, so the remote keeps working underneath.
 */
@Composable
fun NotificationHost(c: BrowserController) {
    val top = if (!c.atHome && !c.isFullScreen) 96.dp else 28.dp
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().padding(top = top, end = 28.dp, start = 28.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            c.notes.toList().forEach { n -> key(n.id) { NoteCard(c, n) } }
        }
    }
}

@Composable
private fun NoteCard(c: BrowserController, n: Note) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val left = remember { Animatable(1f) }
    // Count down, then slide out; once the exit animation has finished, drop the note.
    LaunchedEffect(n.id) {
        left.animateTo(0f, tween(n.durationMs.toInt().coerceAtLeast(600), easing = LinearEasing))
        state.targetState = false
    }
    LaunchedEffect(state.currentState, state.targetState) {
        if (!state.targetState && !state.currentState) c.dismissNote(n)
    }
    val look = lookOf(n.kind)
    val badgeShape = remember { MaterialShapes.Cookie9Sided }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 } +
            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), 0.92f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 } +
            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), 0.92f),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.5.dp, look.accent.copy(alpha = 0.45f)),
            shadowElevation = 12.dp, tonalElevation = 4.dp,
            modifier = Modifier.width(380.dp)
        ) {
            Box {
                Row(
                    Modifier.padding(start = 14.dp, end = 20.dp, top = 14.dp, bottom = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp).clip(badgeShape.toShape()).background(look.badge),
                        contentAlignment = Alignment.Center
                    ) { Icon(n.icon ?: look.icon, null, Modifier.size(28.dp), tint = look.onBadge) }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(
                            n.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        if (!n.message.isNullOrBlank()) Text(
                            n.message, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(left.value.coerceIn(0f, 1f)).height(4.dp)
                        .background(look.accent.copy(alpha = 0.85f))
                )
            }
        }
    }
}

/** Every notification from this session, newest first. */
@Composable
fun NotificationsPage(c: BrowserController) {
    PageScaffold("Notifications", { c.popPage() }) {
        if (c.noteHistory.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Sym.NotificationsOff, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("All quiet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Blocked ads, saved bookmarks and other updates show up here.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { ActionRow(Sym.DeleteSweep, "Clear all", "${c.noteHistory.size} notifications") { c.clearNoteHistory() } }
                items(c.noteHistory.toList(), key = { it.id }) { n -> NoteRow(n) }
            }
        }
    }
}

@Composable
private fun NoteRow(n: Note) {
    val look = lookOf(n.kind)
    val badgeShape = remember { MaterialShapes.Cookie9Sided }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(badgeShape.toShape()).background(look.badge),
                contentAlignment = Alignment.Center
            ) { Icon(n.icon ?: look.icon, null, Modifier.size(26.dp), tint = look.onBadge) }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!n.message.isNullOrBlank()) Text(
                    n.message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
