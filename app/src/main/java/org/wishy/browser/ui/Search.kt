package org.wishy.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The address / search pill. Resting: shows [display] (a short, readable form of the address).
 * Press OK: turns into a text field, selects everything and opens the keyboard.
 */
@Composable
fun SearchPill(
    display: String,
    editText: String,
    placeholder: String,
    icon: ImageVector,
    onSubmit: (String) -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    loading: Boolean = false,
    startEditing: Boolean = false,
) {
    var editing by remember { mutableStateOf(startEditing) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val container by animateColorAsState(
        if (editing || focused) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "pillColor"
    )
    val shape = RoundedCornerShape(percent = 50)
    val keyboard = LocalSoftwareKeyboardController.current
    var field by remember(editing) { mutableStateOf(TextFieldValue(editText, TextRange(0, editText.length))) }
    val textFocus = remember { FocusRequester() }
    var gained by remember(editing) { mutableStateOf(false) }

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { textFocus.requestFocus() }
            keyboard?.show()
        }
    }

    Surface(
        color = container, shape = shape,
        modifier = modifier.fillMaxWidth().height(height)
            .then(if (editing) Modifier else Modifier.focusRequester(focus).tvClickable(shape, source = source) { editing = true })
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading && !editing) {
                LoadingIndicator(Modifier.size(22.dp))
            } else {
                Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editing) {
                BasicTextField(
                    value = field, onValueChange = { field = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onGo = { val t = field.text; editing = false; onSubmit(t) }),
                    modifier = Modifier.weight(1f).padding(start = 14.dp).focusRequester(textFocus)
                        .onFocusChanged {
                            if (it.isFocused) gained = true
                            else if (gained && editing) editing = false
                        }
                )
            } else {
                Text(
                    display.ifBlank { placeholder }, Modifier.weight(1f).padding(start = 14.dp),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (display.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
