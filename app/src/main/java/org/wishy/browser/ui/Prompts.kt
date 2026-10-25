package org.wishy.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.wishy.browser.BrowserController
import org.wishy.browser.PromptRequest

/** Dialogs a web page asks for, plus the first-run explainer, as Material 3 dialogs. */
@Composable
fun PromptHost(c: BrowserController) {
    val focus = remember { FocusRequester() }
    // Dialogs open without focus on a TV; give the main button focus so OK works at once.
    LaunchedEffect(c.prompt, c.showOnboarding) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    when (val p = c.prompt) {
        null -> Unit
        is PromptRequest.Alert -> AlertDialog(
            onDismissRequest = p.done,
            title = titleOf(p.title),
            text = { Text(p.message ?: "") },
            confirmButton = { TextButton(p.done, Modifier.focusRequester(focus)) { Text("OK") } },
        )
        is PromptRequest.Confirm -> AlertDialog(
            onDismissRequest = { p.done(false) },
            title = titleOf(p.title),
            text = { Text(p.message ?: "") },
            confirmButton = { TextButton({ p.done(true) }, Modifier.focusRequester(focus)) { Text("OK") } },
            dismissButton = { TextButton({ p.done(false) }) { Text("Cancel") } },
        )
        is PromptRequest.TextInput -> {
            var text by remember(p) { mutableStateOf(p.initial) }
            AlertDialog(
                onDismissRequest = { p.done(null) },
                title = titleOf(p.title),
                text = {
                    Column {
                        if (!p.message.isNullOrBlank()) Text(p.message, Modifier.padding(bottom = 12.dp))
                        OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.focusRequester(focus))
                    }
                },
                confirmButton = { TextButton({ p.done(text) }) { Text("OK") } },
                dismissButton = { TextButton({ p.done(null) }) { Text("Cancel") } },
            )
        }
        is PromptRequest.Permission -> AlertDialog(
            onDismissRequest = { p.done(false) },
            title = { Text("${p.host} is asking to") },
            text = { Text("use your camera and/or microphone") },
            confirmButton = { TextButton({ p.done(true) }) { Text("Allow") } },
            dismissButton = { TextButton({ p.done(false) }, Modifier.focusRequester(focus)) { Text("Deny") } },
        )
    }
    if (c.showOnboarding && c.prompt == null) {
        AlertDialog(
            onDismissRequest = { c.dismissOnboarding() },
            title = { Text("How Wishy Browser works") },
            text = {
                Text(
                    "Type in the search bar to search or open a site.\n\n" +
                        "On a page, the D-pad moves a pointer and OK clicks. MENU turns the pointer off; hold MENU for the menu.\n\n" +
                        "Move the pointer to the top edge to reach the toolbar. Back goes back, then exits."
                )
            },
            confirmButton = { TextButton({ c.dismissOnboarding() }, Modifier.focusRequester(focus)) { Text("Got it") } },
        )
    }
}

private fun titleOf(t: String?): (@Composable () -> Unit)? =
    if (t.isNullOrBlank()) null else ({ Text(t) })
