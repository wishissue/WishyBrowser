package org.wishy.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Press-squash, D-pad focus lift and a primary focus ring, all on Material's motion springs.
 * Every clickable thing in the app goes through here, so the remote always shows where it is.
 */
@Composable
fun Modifier.tvClickable(
    shape: Shape = RoundedCornerShape(20.dp),
    onLongClick: (() -> Unit)? = null,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val k by animateFloatAsState(
        if (pressed) 0.95f else if (focused) 1.05f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "focusScale"
    )
    val ringAlpha by animateFloatAsState(
        if (focused) 1f else 0f,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "focusRingAlpha"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    return this
        .graphicsLayer { scaleX = k; scaleY = k }
        .then(if (ringAlpha > 0f) Modifier.border(BorderStroke(3.dp, ringColor.copy(alpha = ringAlpha)), shape) else Modifier)
        .clip(shape)
        .combinedClickable(source, ripple(), onLongClick = onLongClick, onClick = onClick)
}

/** Rise-and-fade entrance, staggered by [index]. Runs once per item. */
@Composable
fun Modifier.reveal(index: Int = 0): Modifier {
    if (index > 10) return this
    var done by rememberSaveable { mutableStateOf(false) }
    val p = remember { Animatable(if (done) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (!done) {
            delay(index.coerceIn(0, 10) * 40L)
            p.animateTo(1f, spec)
            done = true
        }
    }
    return graphicsLayer { alpha = p.value.coerceIn(0f, 1f); translationY = (1f - p.value) * 24.dp.toPx() }
}

/** Round icon button that morphs from a soft square to a circle when focused. */
@Composable
fun TvIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color? = null,
    size: androidx.compose.ui.unit.Dp = 52.dp,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val corner by animateIntAsState(if (focused) 50 else 30, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "corner")
    val shape = RoundedCornerShape(corner)
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = tint ?: if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.size(size).background(container, shape).tvClickable(shape, source = source, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(26.dp), tint = content)
    }
}

/** [TvIconButton] with a small round count on its corner (hidden at 0). */
@Composable
fun BadgedIconButton(
    icon: ImageVector,
    description: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    badgeColor: Color = MaterialTheme.colorScheme.error,
    onBadgeColor: Color = MaterialTheme.colorScheme.onError,
) {
    Box(modifier) {
        TvIconButton(icon, description, onClick, size = size)
        if (count > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(22.dp).background(badgeColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (count > 9) "9+" else count.toString(), color = onBadgeColor,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
            )
        }
    }
}

/** A row heading with an icon, used above shelves of cards. */
@Composable
fun ShelfHeader(icon: ImageVector, text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text, Modifier.padding(start = 10.dp).weight(1f),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
        )
        if (trailing != null) trailing()
    }
}

/** A tonal card row used for settings and menu entries. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth().tvClickable(shape, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) trailing()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Full-screen page with a back button and title. Takes the first focus on its back
 * button so the remote works immediately, and handles Back itself.
 */
@Composable
fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    val backFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { contentFocus.requestFocus() } }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvIconButton(
                    Sym.ArrowBack, "Back", onBack,
                    Modifier.focusRequester(backFocus).focusProperties { down = contentFocus }
                )
                Text(title, style = MaterialTheme.typography.headlineMediumEmphasized, color = LocalContentColor.current)
            }
            Box(Modifier.fillMaxSize().focusRequester(contentFocus)) { content() }
        }
    }
}
