package org.wishy.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand scheme using the logo's signature blue (#0166FF):
 * primary #0166FF / #66A3FF, background #E6F1FF / #080B12, surface #FFFFFF / #111725.
 */
internal fun wishyBrandScheme(mode: String): ColorScheme {
    if (mode == "light") return lightColorScheme(
        primary = Color(0xFF0166FF), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFD6E4FF), onPrimaryContainer = Color(0xFF001B3E),
        inversePrimary = Color(0xFF66A3FF),
        secondary = Color(0xFF4B5B73), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFDCE7F8), onSecondaryContainer = Color(0xFF14213A),
        tertiary = Color(0xFF946200), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFFFDEA8), onTertiaryContainer = Color(0xFF301F00),
        background = Color(0xFFE6F1FF), onBackground = Color(0xFF101828),
        surface = Color(0xFFE6F1FF), onSurface = Color(0xFF101828),
        surfaceVariant = Color(0xFFDAE5F6), onSurfaceVariant = Color(0xFF536176), surfaceTint = Color(0xFF0166FF),
        inverseSurface = Color(0xFF101828), inverseOnSurface = Color(0xFFF1F4FB),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF7A879B), outlineVariant = Color(0xFFD4DEED), scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFD4DEED),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8FBFF), surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFF3F7FF), surfaceContainerHighest = Color(0xFFE9F0FC),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF66A3FF), onPrimary = Color(0xFF002266), primaryContainer = Color(0xFF0044B3), onPrimaryContainer = Color(0xFFD6E4FF),
        inversePrimary = Color(0xFF0166FF),
        secondary = Color(0xFFB4C6E4), onSecondary = Color(0xFF1F3050), secondaryContainer = Color(0xFF2A3B5C), onSecondaryContainer = Color(0xFFD7E3FA),
        tertiary = Color(0xFFF4C66A), onTertiary = Color(0xFF402D00), tertiaryContainer = Color(0xFF5C4300), onTertiaryContainer = Color(0xFFFFDEA8),
        background = Color(0xFF080B12), onBackground = Color(0xFFF1F4FB),
        surface = Color(0xFF080B12), onSurface = Color(0xFFF1F4FB),
        surfaceVariant = Color(0xFF182235), onSurfaceVariant = Color(0xFFB0BCD0), surfaceTint = Color(0xFF66A3FF),
        inverseSurface = Color(0xFFF1F4FB), inverseOnSurface = Color(0xFF101828),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF6F7C93), outlineVariant = Color(0xFF303B4F), scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF262F45), surfaceDim = Color(0xFF080B12),
        surfaceContainerLowest = Color(0xFF05070C), surfaceContainerLow = Color(0xFF0D1220), surfaceContainer = Color(0xFF111725),
        surfaceContainerHigh = Color(0xFF182235), surfaceContainerHighest = Color(0xFF202C42),
    )
    return when (mode) {
        "dusk" -> dark.copy(
            background = Color(0xFF0F1626), surface = Color(0xFF0F1626), surfaceDim = Color(0xFF0F1626),
            surfaceContainerLowest = Color(0xFF0A101D), surfaceContainerLow = Color(0xFF131B2D), surfaceContainer = Color(0xFF18223A),
            surfaceContainerHigh = Color(0xFF1F2B45), surfaceContainerHighest = Color(0xFF283553), surfaceVariant = Color(0xFF283553),
        )
        "oled" -> dark.copy(
            background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080B12), surfaceContainer = Color(0xFF0E1320), surfaceContainerHigh = Color(0xFF151C2D),
            surfaceContainerHighest = Color(0xFF1C2538), surfaceVariant = Color(0xFF1C2538),
        )
        else -> dark
    }
}
