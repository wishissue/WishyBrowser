package org.wishy.browser.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController
import org.wishy.browser.Opt
import org.wishy.browser.SearchEngine
import org.wishy.browser.ui.ActionRow
import org.wishy.browser.ui.PageScaffold
import org.wishy.browser.ui.SectionLabel
import org.wishy.browser.ui.Sym

/** Search engine and every privacy switch in one place. All protections start on. */
@Composable
fun PrivacyPage(c: BrowserController) {
    val cs = MaterialTheme.colorScheme
    PageScaffold("Privacy", { c.popPage() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(color = cs.primaryContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).background(cs.primary, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            Icon(Sym.ShieldLockFilled, null, Modifier.size(34.dp), tint = cs.onPrimary)
                        }
                        Column(Modifier.padding(start = 20.dp)) {
                            Text("${c.protectionsOn} of ${c.protectionsTotal} protections on", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (c.protectionsOn == c.protectionsTotal) "Everything is protecting you. Turn any of them off if a site breaks."
                                else "Some protections are off. Select the button below to turn them all on.",
                                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            if (c.protectionsOn != c.protectionsTotal) item {
                ActionRow(Sym.ShieldFilled, "Turn every protection on", "One press") { c.turnAllProtectionsOn() }
            }
            item { SectionLabel("Search engine") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in SearchEngine.entries) {
                        FilterChip(selected = c.searchEngine == e, onClick = { c.setSearchEngine(e) }, label = { Text(e.label) })
                    }
                }
            }
            for (group in listOf("Blocking", "Tracking", "Identity", "Connection", "Device", "Speed")) {
                item { SectionLabel(group) }
                for (o in Opt.entries.filter { it.group == group }) {
                    item(key = o.key) {
                        ActionRow(o.icon, o.title, o.desc, trailing = { Switch(c.isOn(o), null) }) { c.setOpt(o, !c.isOn(o)) }
                    }
                }
            }
            item { SectionLabel("Clean up") }
            item { ActionRow(Sym.DeleteSweep, "Clear browsing data now", "Cookies, cache, history and previews, then go Home") { c.clearBrowsingData() } }
            item { SectionLabel("Always on (set by the app)") }
            item { ActionRow(Sym.VerifiedUser, "Safe Browsing and usage metrics are off", "Nothing is sent to Google about the pages you visit") { } }
            item { ActionRow(Sym.Dns, "Encrypted DNS", "Android cannot set this per app: turn on Private DNS in system settings") { } }
        }
    }
}
