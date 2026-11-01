package org.wishy.browser.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController
import org.wishy.browser.SiteChoice
import org.wishy.browser.ui.*

@Composable
fun ShieldsPage(c: BrowserController) {
    val cs = MaterialTheme.colorScheme
    PageScaffold("Shields and privacy", { c.popPage() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(color = cs.primaryContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().reveal(0)) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(84.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.primary),
                            contentAlignment = Alignment.Center
                        ) { Icon(Sym.ShieldFilled, null, Modifier.size(42.dp), tint = cs.onPrimary) }
                        Column(Modifier.padding(start = 22.dp)) {
                            Text("Blocking is on", style = MaterialTheme.typography.headlineSmallEmphasized)
                            Text(
                                "Wishy uses your TV's Android System WebView. Its blocker is simpler than uBlock Origin and it cannot hide your browser fingerprint. This page lists what is on, what is partial, and what is not available.",
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
            item { ActionRow(Sym.CleaningServices, "Clear cookies and site data now", "Cookies, site storage and cache") { c.clearBrowsingData() } }
            item { ActionRow(Sym.AdsClick, "Run an ad-block test", "Opens a public page that scores your blocker") { c.open("https://d3ward.github.io/toolz/adblock.html") } }

            val site = c.siteHost
            if (site != null) {
                item { SectionLabel("This site: $site") }
                item {
                    ActionRow(
                        Sym.Code, "JavaScript", "Turning it off breaks most modern sites",
                        trailing = { Switch(c.siteJsAllowed(), null) }
                    ) { c.toggleSiteJs() }
                }
                item {
                    ActionRow(
                        Sym.Mic, "Camera and microphone", "Tap to change",
                        trailing = { Text(when (c.siteMedia()) { SiteChoice.ASK -> "Ask"; SiteChoice.ALLOW -> "Allow"; SiteChoice.BLOCK -> "Block" }, style = MaterialTheme.typography.titleMedium) }
                    ) { c.cycleSiteMedia() }
                }
                item { ActionRow(Sym.Refresh, "Reset all site settings", "Back to defaults for every site") { c.resetSiteSettings() } }
            }
            item { SectionLabel("On (enforced by the app)") }
            item { InfoRow("Ad and tracker domain blocking", "${c.blockerDomains} domains", true) }
            item { InfoRow("HTTPS upgrade, warns if a site has none", "On", true) }
            item { InfoRow("Tracking parameters removed (utm_*, fbclid, gclid...)", "On", true) }
            item { InfoRow("Certificate errors", "Always blocked", true) }
            item { InfoRow("Third-party cookies", "Off", true) }
            item { InfoRow("Pop-ups", "Need a click", true) }
            item { InfoRow("Links to other apps", "Blocked", true) }
            item { InfoRow("Camera and microphone", "Ask every time", true) }
            item { InfoRow("Location, file:// and content:// access", "Off", true) }
            item { InfoRow("Cookies and site data", "Wiped at start and exit", true) }
            item { InfoRow("History, accounts, telemetry", "None", true) }

            item { SectionLabel("Partial") }
            item { InfoRow("Global Privacy Control", "Page loads only", null) }
            item { InfoRow("Ad hiding (cosmetic filters)", "Generic only", null) }
            item { InfoRow("Ad blocking coverage", "Domains, not URLs", null) }
            item { InfoRow("YouTube ad skipper", "Best effort", null) }
            item { InfoRow("Private browsing", "Wiped, not RAM-only", null) }
            item { InfoRow("Google Safe Browsing (if the TV has Play services)", "Contacts Google", null) }

            item { SectionLabel("Not available with Android WebView") }
            item { InfoRow("Fingerprint resistance", "Not available", false) }
            item { InfoRow("Total Cookie Protection", "Not available", false) }
            item { InfoRow("Encrypted DNS (uses the TV's DNS)", "Not available", false) }
            item { InfoRow("uBlock features: scriptlets, dynamic rules, custom lists", "Not available", false) }
            item { InfoRow("Web notifications and push", "Not supported by WebView", false) }
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
