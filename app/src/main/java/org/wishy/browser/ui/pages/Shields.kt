package org.wishy.browser.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserApplication
import org.wishy.browser.BrowserController
import org.wishy.browser.Shields
import org.wishy.browser.ui.*

@Composable
fun ShieldsPage(c: BrowserController) {
    val app = LocalContext.current.applicationContext as BrowserApplication
    LaunchedEffect(Unit) { app.refreshShields() }
    val active = Shields.blockerActive
    val starting = !Shields.ready
    val ub = Shields.ublock
    val core = Shields.coreInfo
    val cs = MaterialTheme.colorScheme

    PageScaffold("Shields", { c.popPage() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(
                    color = if (active) cs.primaryContainer else if (starting) cs.surfaceContainerHigh else cs.errorContainer,
                    shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().reveal(0)
                ) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(84.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                                .background(if (active) cs.primary else if (starting) cs.secondary else cs.error),
                            contentAlignment = Alignment.Center
                        ) {
                            if (starting) LoadingIndicator(Modifier.size(44.dp))
                            else Icon(
                                if (active) Sym.ShieldFilled else Sym.Warning, null, Modifier.size(42.dp),
                                tint = if (active) cs.onPrimary else cs.onError
                            )
                        }
                        Column(Modifier.padding(start = 22.dp)) {
                            Text(
                                if (active) "Blocking is on" else if (starting) "Starting the blocker" else "The blocker is not running",
                                style = MaterialTheme.typography.headlineSmallEmphasized
                            )
                            Text(
                                if (active) "Ads, pop-ups and trackers are filtered in every tab. No blocker can promise every ad, so check a page with the test below."
                                else if (starting) "This takes a few seconds the first time."
                                else "Use Reset blocker below, then reopen the app.",
                                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Sym.Block, "${c.blockedNow}", "blocked on this page", Modifier.weight(1f))
                    StatCard(Sym.ShieldFilled, "${c.blockedSession}", "blocked this session", Modifier.weight(1f))
                }
            }
            item {
                ActionRow(
                    Sym.Notifications, "Block notices", "Show a card after a page loads",
                    trailing = { Switch(c.notifyBlocked, null) }
                ) { c.toggleNotifyBlocked() }
            }
            item { SectionLabel("Layers that remove ads") }
            item { InfoRow("uBlock filters, EasyList, EasyPrivacy", "On", active) }
            item { InfoRow("AdGuard ads and mobile ads", "On", active) }
            item { InfoRow("AdGuard popup overlays", "On", active) }
            item { InfoRow("Wishy extra rules", "On", active) }
            item { InfoRow("YouTube ad skipper", "On", core?.allowedPrivate == true && core.enabled) }
            item { InfoRow("Pop-up windows", "Always blocked", true) }
            item { SectionLabel("Ad and tracker blocker") }
            item { InfoRow("uBlock Origin", ub?.version?.let { "Version $it" } ?: "Not installed") }
            item { InfoRow("Enabled", if (ub?.enabled == true) "Yes" else "No", ub?.enabled == true) }
            item { InfoRow("Allowed in private tabs", if (ub?.allowedPrivate == true) "Yes" else "No", ub?.allowedPrivate == true) }
            item {
                InfoRow("Wishy Core (pointer snapping)", if (core?.allowedPrivate == true && core.enabled) "Running" else "Not running", core?.allowedPrivate == true && core.enabled)
            }
            Shields.error?.let { err -> item { InfoRow("Last error", err, false) } }
            item { ActionRow(Sym.Refresh, "Check again", "Re-read the blocker status") { app.refreshShields() } }
            item { ActionRow(Sym.Tune, "Reset blocker", "Reinstall uBlock Origin from scratch") { app.resetShields(); c.notify(org.wishy.browser.NoteKind.INFO, "Reinstalling the blocker", "Restart the app when it is done", Sym.Tune) } }
            item { ActionRow(Sym.AdsClick, "Run an ad-block test", "Opens a public page that scores your blocker") { c.open("https://d3ward.github.io/toolz/adblock.html") } }
            item { SectionLabel("Always on") }
            item { InfoRow("Strict tracking protection", "On", true) }
            item { InfoRow("Fingerprint resistance", "On", true) }
            item { InfoRow("HTTPS-only mode", "On", true) }
            item { InfoRow("Encrypted DNS", "On", true) }
            item { InfoRow("Global Privacy Control", "On", true) }
            item { InfoRow("History and telemetry", "None kept", true) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, good: Boolean? = null) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (good != null) Icon(
                if (good) Sym.CheckCircleFilled else Sym.Warning, null, Modifier.size(22.dp).padding(end = 0.dp),
                tint = if (good) cs.primary else cs.error
            )
            Text(value, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, big: String, label: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(26.dp), modifier = modifier) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = cs.primary)
            Column(Modifier.padding(start = 14.dp)) {
                Text(big, style = MaterialTheme.typography.headlineMediumEmphasized)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}
