package org.wishy.browser.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController
import org.wishy.browser.R
import org.wishy.browser.ui.*

const val DONATE_URL = "https://wishissue.github.io/wishy-apps/donate.html"
private const val GITHUB = "https://github.com/wishissue"
private const val REPO = "https://github.com/wishissue/WishyBrowser"

@Composable
fun DeveloperPage(c: BrowserController) {
    PageScaffold("Developer", { c.popPage() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.padding(bottom = 12.dp).reveal(0), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.ic_cat_logo), null,
                        Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                    )
                    Column(Modifier.padding(start = 22.dp)) {
                        Text("wishissue", style = MaterialTheme.typography.headlineLargeEmphasized)
                        Text("Maker of Wishy Browser", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "A fast, private browser built for the big screen.",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            item { ActionRow(Sym.Code, "GitHub profile", "github.com/wishissue") { c.open(GITHUB) } }
            item { ActionRow(Sym.Public, "Source code and bug reports", "github.com/wishissue/WishyBrowser") { c.open(REPO) } }
            item { ActionRow(Sym.VolunteerActivism, "Support the project", "Open the donation page") { c.open(DONATE_URL) } }
        }
    }
}

private class Credit(val name: String, val role: String, val license: String, val url: String)

private val CREDITS = listOf(
    Credit("Mozilla GeckoView", "The browser engine", "MPL-2.0", "https://mozilla.github.io/geckoview/"),
    Credit("uBlock Origin", "Ad and tracker blocking, by Raymond Hill", "GPL-3.0", "https://github.com/gorhill/uBlock"),
    Credit("Orion Store", "Design inspiration and theme seed colours", "GPL-3.0", "https://github.com/RookieEnough/Orion-Store"),
    Credit("Jetpack Compose and Material 3", "The interface toolkit", "Apache-2.0", "https://developer.android.com/compose"),
    Credit("Material Color Utilities", "Generates the 45 colour themes", "Apache-2.0", "https://github.com/material-foundation/material-color-utilities"),
    Credit("Material Symbols", "Icons", "Apache-2.0", "https://fonts.google.com/icons"),
    Credit("Bibata Cursor", "Open-source cursor pictures, by Abdulkaiz Khan", "GPL-3.0", "https://github.com/ful1e5/Bibata_Cursor"),
    Credit("EasyList, EasyPrivacy, AdGuard filters", "Ad and tracker filter lists", "GPL-3.0 / CC BY-SA", "https://easylist.to/"),
    Credit("Poppins", "Typeface, by Indian Type Foundry", "OFL-1.1", "https://fonts.google.com/specimen/Poppins"),
    Credit("TV Bro", "Inspired the D-pad pointer idea (no code used)", "GPL-3.0", "https://github.com/truefedex/tv-bro"),
)

@Composable
fun CreditsPage(c: BrowserController) {
    PageScaffold("Credits", { c.popPage() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Wishy stands on the work of these open-source projects. Select one to read about it.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(CREDITS.size) { i ->
                val cr = CREDITS[i]
                ActionRow(
                    Sym.Favorite, cr.name, cr.role,
                    trailing = { AssistChip(onClick = {}, label = { Text(cr.license) }) }
                ) { c.open(cr.url) }
            }
        }
    }
}

@Composable
fun DonatePage(c: BrowserController) {
    PageScaffold("Donate", { c.popPage() }) {
        LazyColumn(
            Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Wishy is free, ad-free and tracker-free.",
                    style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    "If it makes your TV better, a tip keeps it that way. Scan the code with your phone.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            item {
                Box(
                    Modifier.reveal(1).size(264.dp).clip(RoundedCornerShape(32.dp)).background(androidx.compose.ui.graphics.Color.White).padding(14.dp)
                ) {
                    Image(painterResource(R.drawable.qr_donate), "QR code for the Wishy Browser donation page", Modifier.fillMaxSize())
                }
            }
            item { Text(DONATE_URL.removePrefix("https://"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            item {
                Button(onClick = { c.open(DONATE_URL) }) {
                    Icon(Sym.VolunteerActivism, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Open donation page")
                }
            }
        }
    }
}
