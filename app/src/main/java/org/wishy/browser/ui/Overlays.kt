package org.wishy.browser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.wishy.browser.BrowserController
import org.wishy.browser.Page
import org.wishy.browser.ui.pages.BookmarksPage
import org.wishy.browser.ui.pages.CreditsPage
import org.wishy.browser.ui.pages.CursorsPage
import org.wishy.browser.ui.pages.DeveloperPage
import org.wishy.browser.ui.pages.DonatePage
import org.wishy.browser.ui.pages.DownloadsPage
import org.wishy.browser.ui.pages.PrivacyPage
import org.wishy.browser.ui.pages.SettingsPage
import org.wishy.browser.ui.pages.ShieldsPage
import org.wishy.browser.ui.pages.ThemesPage

/** Shows the page on top of the back stack, cross-fading with a soft scale on Material's motion springs. */
@Composable
fun Overlays(c: BrowserController) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    AnimatedContent(
        targetState = c.overlay,
        transitionSpec = { (fadeIn(effects) + scaleIn(spatial, 0.94f)) togetherWith fadeOut(fastEffects) },
        label = "overlay",
    ) { page ->
        when (page) {
            null -> Unit
            Page.MENU -> MenuSheet(c)
            Page.DOWNLOADS -> DownloadsPage(c)
            Page.BOOKMARKS -> BookmarksPage(c)
            Page.THEMES -> ThemesPage(c)
            Page.CURSORS -> CursorsPage(c)
            Page.SETTINGS -> SettingsPage(c)
            Page.SHIELDS -> ShieldsPage(c)
            Page.PRIVACY -> PrivacyPage(c)
            Page.NOTIFICATIONS -> NotificationsPage(c)
            Page.DEVELOPER -> DeveloperPage(c)
            Page.CREDITS -> CreditsPage(c)
            Page.DONATE -> DonatePage(c)
        }
    }
}
