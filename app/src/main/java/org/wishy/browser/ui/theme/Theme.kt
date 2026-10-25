package org.wishy.browser.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.wishy.browser.R

private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun TextStyle.p() = copy(fontFamily = Poppins)

private fun poppinsTypography(): Typography = Typography().run {
    Typography(
        displayLarge.p(), displayMedium.p(), displaySmall.p(),
        headlineLarge.p(), headlineMedium.p(), headlineSmall.p(),
        titleLarge.p(), titleMedium.p(), titleSmall.p(),
        bodyLarge.p(), bodyMedium.p(), bodySmall.p(),
        labelLarge.p(), labelMedium.p(), labelSmall.p(),
        displayLargeEmphasized.p(), displayMediumEmphasized.p(), displaySmallEmphasized.p(),
        headlineLargeEmphasized.p(), headlineMediumEmphasized.p(), headlineSmallEmphasized.p(),
        titleLargeEmphasized.p(), titleMediumEmphasized.p(), titleSmallEmphasized.p(),
        bodyLargeEmphasized.p(), bodyMediumEmphasized.p(), bodySmallEmphasized.p(),
        labelLargeEmphasized.p(), labelMediumEmphasized.p(), labelSmallEmphasized.p(),
    )
}

/** "Reduce motion": every Material motion token becomes an instant change (for very weak hardware). */
private object InstantMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/** Material 3 Expressive theme: one of 45 palettes, in dark / light / dusk / amoled. */
@Composable
fun WishyTheme(themeId: String, mode: String, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val colors = remember(themeId, mode) { paletteScheme(themeId, mode) }
    val motion = remember(reduceMotion) { if (reduceMotion) InstantMotion else MotionScheme.expressive() }
    val type = remember { poppinsTypography() }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = motion, typography = type, content = content)
}
