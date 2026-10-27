package org.wishy.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController

/** "Find in page": a bar along the bottom with the match count and next / previous. Back closes it. */
@Composable
fun FindBar(c: BrowserController, modifier: Modifier = Modifier) {
    if (!c.findOpen) return
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    query, { query = it; c.findInPage(it) }, singleLine = true,
                    label = { Text("Find in page") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { c.findNext(true) }),
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
                Text(
                    if (query.isBlank()) "" else if (c.findTotal == 0) "No matches" else "${c.findIndex} of ${c.findTotal}",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvIconButton(Sym.ArrowUpward, "Previous match", { c.findNext(false) }, size = 48.dp)
                TvIconButton(Sym.ExpandMore, "Next match", { c.findNext(true) }, size = 48.dp)
                TvIconButton(Sym.Close, "Close", { c.closeFind() }, size = 48.dp)
            }
        }
    }
}
