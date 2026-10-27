package org.wishy.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.wishy.browser.BrowserController
import org.wishy.browser.ui.theme.WishyTheme

/** The whole app: browsing screen at the bottom, home on top of it, then pages, dialogs and toasts. */
@Composable
fun WishyApp(c: BrowserController) {
    WishyTheme(c.themeId, c.themeMode, c.reduceMotion) {
      CompositionLocalProvider(LocalCursorLib provides c.cursorLib) {
        BackHandler { c.handleBack() }
        // Stale toolbar-focus state must never survive the toolbar disappearing.
        LaunchedEffect(c.atHome) { c.chromeFocused = false }
        // When a page, menu or dialog closes (or a page loads), the page gets the remote back.
        LaunchedEffect(c.overlay, c.prompt, c.atHome) { c.settleFocus() }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            BrowserSurface(c)
            AnimatedVisibility(
                c.atHome,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) { HomeScreen(c) }
            Overlays(c)
            FindBar(c, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            PromptHost(c)
            PromoHost(c)
            NotificationHost(c)
        }
      }
    }
}
