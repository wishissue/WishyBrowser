package org.wishy.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.wishy.browser.BrowserController
import org.wishy.browser.Promo

/**
 * The big, friendly card shown on top of the browser for two things only: "a new version exists" and the
 * "buy me a coffee" reminder. It never blocks anything for long: OK / Maybe later closes it.
 */
@Composable
fun PromoHost(c: BrowserController) {
    val p = c.promo ?: return
    val shown = remember(p) { MutableTransitionState(false).apply { targetState = true } }
    val focus = remember(p) { FocusRequester() }
    LaunchedEffect(p) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { c.dismissPromo() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            shown,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), 0.9f),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Surface(
                color = cs.surfaceContainerHigh, shape = RoundedCornerShape(40.dp), shadowElevation = 24.dp,
                modifier = Modifier.safeDrawingPadding().padding(24.dp).widthIn(max = 600.dp).fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            ) {
                Column {
                    Banner(if (p is Promo.Update) Sym.TipsAndUpdatesFilled else Sym.VolunteerActivism)
                    Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (p) {
                            is Promo.Update -> UpdateBody(p, c, focus)
                            is Promo.Donate -> DonateBody(p, c, focus)
                        }
                    }
                }
            }
        }
    }
}

/** Gradient top with a big badge and a few soft circles. Still, so it costs nothing on weak TVs. */
@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().height(170.dp).background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.offset(x = (-210).dp, y = (-34).dp).size(120.dp).background(Color.White.copy(alpha = 0.10f), CircleShape))
        Box(Modifier.offset(x = 230.dp, y = 40.dp).size(150.dp).background(Color.White.copy(alpha = 0.08f), CircleShape))
        Box(Modifier.offset(x = 150.dp, y = (-52).dp).size(44.dp).background(Color.White.copy(alpha = 0.14f), CircleShape))
        Box(
            Modifier.size(104.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(54.dp), tint = cs.primary) }
    }
}

@Composable
private fun UpdateBody(p: Promo.Update, c: BrowserController, focus: FocusRequester) {
    val cs = MaterialTheme.colorScheme
    Text("A new Wishy Browser is out", style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center, modifier = Modifier.reveal(0))
    Surface(color = cs.secondaryContainer, shape = CircleShape, modifier = Modifier.padding(top = 12.dp).reveal(1)) {
        Text(
            "${p.current}  →  ${p.version}", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer
        )
    }
    Text(
        "Nothing was downloaded. Whenever you like, get the new version from the project page and install it over this one.",
        style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 14.dp).reveal(2)
    )
    val notes = p.notes.lines().map { it.trim().trimStart('-', '*', '#', ' ') }.filter { it.isNotBlank() }.take(4)
    if (notes.isNotEmpty()) {
        Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(top = 16.dp).fillMaxWidth().reveal(3)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What's new", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                for (n in notes) Text("•  " + n.take(90), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Button({ c.dismissPromo() }, Modifier.focusRequester(focus).width(220.dp)) { Text("OK") }
}

@Composable
private fun DonateBody(p: Promo.Donate, c: BrowserController, focus: FocusRequester) {
    val cs = MaterialTheme.colorScheme
    val used = if (p.months <= 1) "a month" else "${p.months} months"
    Text("Thank you for using Wishy", style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center, modifier = Modifier.reveal(0))
    Text(
        "You have been browsing with Wishy for about $used.",
        style = MaterialTheme.typography.titleMedium, color = cs.primary, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp).reveal(1)
    )
    Text(
        "Wishy is free, with no ads and no trackers. It is made by one person and kept alive by people like you. " +
            "If it has made your TV nicer to use, you can buy me a coffee. It pays for updates and new features, and it means a lot.",
        style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 14.dp).reveal(2)
    )
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button({ c.openDonateFromPromo() }, Modifier.focusRequester(focus)) {
            Icon(Sym.Coffee, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Buy me a coffee")
        }
        FilledTonalButton({ c.openDonateQrFromPromo() }) { Text("Show QR code") }
    }
    TextButton({ c.dismissPromo() }, Modifier.padding(top = 6.dp)) { Text("Maybe later") }
}
