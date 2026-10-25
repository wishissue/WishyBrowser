package org.wishy.browser.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.wishy.browser.CursorImage
import org.wishy.browser.CursorLibrary
import kotlin.math.min
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Three kinds of pointer, all drawn by [CursorGlyph]:
 *  - the 45 animated cursors: 9 shapes x 5 animations, drawn in code in the current theme's colours;
 *  - open-source picture packs (Bibata, GPL-3.0) from assets/cursors;
 *  - the user's own pictures, added from a file or web address (see CursorLibrary).
 * [CursorSpec.hotX]/[CursorSpec.hotY] are the click point as a fraction of the glyph box.
 */
enum class CursorShape(val label: String, val hotX: Float, val hotY: Float, val symmetric: Boolean) {
    ARROW("Arrow", 0.14f, 0.08f, false),
    DOT("Dot", 0.5f, 0.5f, true),
    RING("Ring", 0.5f, 0.5f, true),
    CROSS("Crosshair", 0.5f, 0.5f, true),
    DIAMOND("Diamond", 0.5f, 0.5f, true),
    STAR("Star", 0.5f, 0.5f, true),
    HEART("Heart", 0.5f, 0.5f, false),
    PAW("Paw", 0.5f, 0.5f, false),
    DROP("Droplet", 0.5f, 0.16f, false),
}

enum class CursorAnim(val label: String, val durationMs: Int) {
    PULSE("Pulse", 1300),
    SPIN("Spin", 2600),
    BOB("Bob", 1500),
    ORBIT("Orbit", 2200),
    AURA("Aura", 1700),
}

class CursorSpec(
    val id: String,
    val name: String,
    val shape: CursorShape,
    val anim: CursorAnim,
    /** Non-null for picture cursors (packs and your own); then [shape] and [anim] are ignored. */
    val image: CursorImage? = null,
) {
    val hotX: Float get() = image?.hotX ?: shape.hotX
    val hotY: Float get() = image?.hotY ?: shape.hotY

    /** Pictures keep their proportions inside the nominal square [size]. */
    fun boxW(size: Dp): Dp = if (image == null) size else size * min(1f, image.aspect)
    fun boxH(size: Dp): Dp = if (image == null) size else size * min(1f, 1f / image.aspect)
}

/** Lets [CursorGlyph] find picture cursors without every caller passing the library around. */
val LocalCursorLib = staticCompositionLocalOf<CursorLibrary?> { null }

object Cursors {
    /** The 45 animated, theme-coloured cursors. */
    val animated: List<CursorSpec> = CursorShape.entries.flatMap { sh ->
        CursorAnim.entries.map { an ->
            CursorSpec("${sh.name.lowercase()}_${an.name.lowercase()}", "${sh.label} ${an.label}", sh, an)
        }
    }

    val all: List<CursorSpec> get() = animated

    fun of(img: CursorImage): CursorSpec = CursorSpec(img.id, img.name, CursorShape.ARROW, CursorAnim.PULSE, img)

    /** Any id: your own cursor, a pack cursor, or one of the 45 (falls back to the first). */
    fun resolve(id: String?, lib: CursorLibrary): CursorSpec {
        lib.find(id ?: "")?.let { return of(it) }
        return animated.firstOrNull { it.id == id } ?: animated.first()
    }
}

private val Outline = Color(0xFF000000)
private val Rim = Color(0xFFFFFFFF)

private fun shapePath(shape: CursorShape): Path = Path().apply {
    when (shape) {
        CursorShape.ARROW -> {
            moveTo(12f, 8f); lineTo(12f, 78f); lineTo(30f, 60f); lineTo(44f, 90f)
            lineTo(56f, 84f); lineTo(42f, 55f); lineTo(66f, 55f); close()
        }
        CursorShape.DOT -> addOval(Rect(Offset(50f, 50f), 24f))
        CursorShape.DIAMOND -> {
            moveTo(50f, 10f); lineTo(90f, 50f); lineTo(50f, 90f); lineTo(10f, 50f); close()
        }
        CursorShape.STAR -> {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 42f else 18f
                val a = -PI / 2 + i * PI / 5
                val x = 50f + r * cos(a).toFloat()
                val y = 52f + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        CursorShape.HEART -> {
            moveTo(50f, 88f)
            cubicTo(8f, 58f, 6f, 26f, 30f, 20f)
            cubicTo(42f, 17f, 50f, 26f, 50f, 32f)
            cubicTo(50f, 26f, 58f, 17f, 70f, 20f)
            cubicTo(94f, 26f, 92f, 58f, 50f, 88f)
            close()
        }
        CursorShape.PAW -> {
            addOval(Rect(Offset(26f, 50f), Offset(74f, 88f)))
            addOval(Rect(Offset(22f, 40f), 9f))
            addOval(Rect(Offset(38f, 24f), 9f))
            addOval(Rect(Offset(62f, 24f), 9f))
            addOval(Rect(Offset(78f, 40f), 9f))
        }
        CursorShape.DROP -> {
            moveTo(50f, 8f)
            cubicTo(50f, 8f, 22f, 44f, 22f, 62f)
            cubicTo(22f, 78f, 34f, 92f, 50f, 92f)
            cubicTo(66f, 92f, 78f, 78f, 78f, 62f)
            cubicTo(78f, 44f, 50f, 8f, 50f, 8f)
            close()
        }
        else -> Unit
    }
}

private fun DrawScope.drawGlyph(shape: CursorShape, path: Path, fill: Color) {
    when (shape) {
        CursorShape.RING -> {
            val c = Offset(50f, 50f)
            drawCircle(Outline, 32f, c, style = Stroke(18f))
            drawCircle(Rim, 32f, c, style = Stroke(13f))
            drawCircle(fill, 32f, c, style = Stroke(7f))
            drawCircle(Outline, 9f, c)
            drawCircle(Rim, 7.5f, c)
            drawCircle(fill, 5f, c)
        }
        CursorShape.CROSS -> {
            val segs = listOf(
                Offset(50f, 8f) to Offset(50f, 36f), Offset(50f, 64f) to Offset(50f, 92f),
                Offset(8f, 50f) to Offset(36f, 50f), Offset(64f, 50f) to Offset(92f, 50f),
            )
            for ((a, b) in segs) drawLine(Outline, a, b, strokeWidth = 16f, cap = StrokeCap.Round)
            for ((a, b) in segs) drawLine(Rim, a, b, strokeWidth = 11f, cap = StrokeCap.Round)
            for ((a, b) in segs) drawLine(fill, a, b, strokeWidth = 6f, cap = StrokeCap.Round)
            drawCircle(Outline, 9f, Offset(50f, 50f))
            drawCircle(Rim, 7.5f, Offset(50f, 50f))
            drawCircle(fill, 5f, Offset(50f, 50f))
        }
        else -> {
            drawPath(path, Outline, style = Stroke(10f, join = StrokeJoin.Round, cap = StrokeCap.Round))
            drawPath(path, Rim, style = Stroke(4f, join = StrokeJoin.Round, cap = StrokeCap.Round))
            drawPath(path, fill)
        }
    }
}

/**
 * Draws one cursor. [animate] = false draws it still (used for the grid so only the
 * focused one moves). The animation phase is read while drawing, so nothing recomposes per frame.
 */
@Composable
fun CursorGlyph(
    spec: CursorSpec,
    size: Dp,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    fill: Color = MaterialTheme.colorScheme.primary,
    accent: Color = MaterialTheme.colorScheme.tertiary,
) {
    val img = spec.image
    if (img != null) {
        val lib = LocalCursorLib.current
        val bmp = remember(img.id, lib) { lib?.bitmap(img) }
        if (bmp != null) {
            Image(bmp, null, modifier.size(spec.boxW(size), spec.boxH(size)), contentScale = ContentScale.Fit)
        }
        return
    }
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "cursor").animateFloat(
            0f, 1f, infiniteRepeatable(tween(spec.anim.durationMs, easing = LinearEasing)), label = "phase"
        )
    } else remember { mutableFloatStateOf(0.25f) }
    val path = remember(spec.shape) { shapePath(spec.shape) }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 100f
        val ph = phase.value
        val a = (ph * 2.0 * PI).toFloat()
        val sh = spec.shape
        val hot = Offset(sh.hotX * 100f, sh.hotY * 100f)
        val mid = Offset(50f, 50f)
        withTransform({ scale(s, s, Offset.Zero) }) {
            if (spec.anim == CursorAnim.AURA) {
                val c = if (sh.symmetric) mid else hot
                drawCircle(fill.copy(alpha = 0.55f * (1f - ph)), 14f + 34f * ph, c, style = Stroke(5f))
            }
            var sc = 1f
            var rot = 0f
            var dy = 0f
            when (spec.anim) {
                CursorAnim.PULSE -> sc = 1f + 0.10f * sin(a)
                CursorAnim.SPIN -> rot = if (sh.symmetric) 360f * ph else 14f * sin(a)
                CursorAnim.BOB -> dy = -5f * sin(a)
                else -> Unit
            }
            withTransform({
                scale(sc, sc, hot)
                rotate(rot, if (sh.symmetric) mid else hot)
                translate(0f, dy)
            }) { drawGlyph(sh, path, fill) }
            if (spec.anim == CursorAnim.ORBIT) {
                val p = Offset(50f + 46f * cos(a), 50f + 46f * sin(a))
                drawCircle(Outline, 8f, p)
                drawCircle(accent, 5.5f, p)
            }
        }
    }
}
