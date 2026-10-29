package org.wishy.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons added after Symbols.kt was generated (same Material Symbols Rounded set, Apache-2.0). */
private val pauseIcon: ImageVector by lazy {
    ImageVector.Builder("Pause", 24.dp, 24.dp, 960f, 960f).addGroup(translationY = 960f)
        .addPath(
            addPathNodes("M600-200q-33 0-56.5-23.5T520-280v-400q0-33 23.5-56.5T600-760h40q33 0 56.5 23.5T720-680v400q0 33-23.5 56.5T640-200h-40Zm-280 0q-33 0-56.5-23.5T240-280v-400q0-33 23.5-56.5T320-760h40q33 0 56.5 23.5T440-680v400q0 33-23.5 56.5T360-200h-40Z"),
            fill = SolidColor(Color.Black)
        ).clearGroup().build()
}

val Sym.Pause: ImageVector get() = pauseIcon
