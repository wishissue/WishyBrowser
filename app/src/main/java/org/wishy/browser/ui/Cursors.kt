package org.wishy.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import org.wishy.browser.CursorImage
import org.wishy.browser.CursorLibrary
import kotlin.math.min

/**
 * Pointers are pictures only:
 *  - open-source picture packs (Bibata, GPL-3.0) from assets/cursors;
 *  - the user's own pictures, added from a file or web address (see CursorLibrary).
 * The code-drawn, animated cursors of earlier versions are gone. [Cursors.FALLBACK] is a plain still arrow that is
 * only used when no picture can be loaded (for example if assets/cursors is missing), so the pointer never vanishes.
 * [CursorSpec.hotX]/[CursorSpec.hotY] are the click point as a fraction of the glyph box.
 */
class CursorSpec(
    val id: String,
    val name: String,
    /** Null only for [Cursors.FALLBACK]. */
    val image: CursorImage? = null,
) {
    val hotX: Float get() = image?.hotX ?: 0.14f
    val hotY: Float get() = image?.hotY ?: 0.08f

    /** Pictures keep their proportions inside the nominal square [size]. */
    fun boxW(size: Dp): Dp = if (image == null) size else size * min(1f, image.aspect)
    fun boxH(size: Dp): Dp = if (image == null) size else size * min(1f, 1f / image.aspect)
}

/** Lets [CursorGlyph] find picture cursors without every caller passing the library around. */
val LocalCursorLib = staticCompositionLocalOf<CursorLibrary?> { null }

object Cursors {
    val FALLBACK = CursorSpec("system_arrow", "Plain arrow")

    fun of(img: CursorImage): CursorSpec = CursorSpec(img.id, img.name, img)

    /** Your own cursor or a pack cursor by id; anything else (including ids of the removed cursors) gives the first picture. */
    fun resolve(id: String?, lib: CursorLibrary): CursorSpec {
        lib.find(id ?: "")?.let { return of(it) }
        lib.custom.firstOrNull()?.let { return of(it) }
        lib.packed.firstOrNull()?.let { return of(it) }
        return FALLBACK
    }
}

private val arrowPath: Path = Path().apply {
    moveTo(12f, 8f); lineTo(12f, 78f); lineTo(30f, 60f); lineTo(44f, 90f)
    lineTo(56f, 84f); lineTo(42f, 55f); lineTo(66f, 55f); close()
}

/** The still fallback arrow: white with a black outline, no theme colours, no animation. */
private fun DrawScope.drawFallbackArrow() {
    val s = this.size.minDimension / 100f
    withTransform({ scale(s, s, Offset.Zero) }) {
        drawPath(arrowPath, Color.Black, style = Stroke(10f, join = StrokeJoin.Round))
        drawPath(arrowPath, Color.White)
    }
}

/** Draws one cursor, still. */
@Composable
fun CursorGlyph(spec: CursorSpec, size: Dp, modifier: Modifier = Modifier) {
    val img = spec.image
    if (img != null) {
        val lib = LocalCursorLib.current
        val bmp = remember(img.id, lib) { lib?.bitmap(img) }
        if (bmp != null) {
            Image(bmp, null, modifier.size(spec.boxW(size), spec.boxH(size)), contentScale = ContentScale.Fit)
        }
        return
    }
    Canvas(modifier.size(size)) { drawFallbackArrow() }
}

class RenderedCursor(val bitmap: android.graphics.Bitmap, val hotXpx: Float, val hotYpx: Float)

/**
 * Draws a cursor once into a bitmap for the native [org.wishy.browser.PointerView]. The page then only ever
 * moves a cached picture; nothing is redrawn while the pointer travels.
 */
fun renderCursor(spec: CursorSpec, sizePx: Int, lib: CursorLibrary?): RenderedCursor? {
    val img = spec.image
    if (img != null) {
        val src = lib?.bitmap(img)?.asAndroidBitmap() ?: return null
        val w = (sizePx * min(1f, img.aspect)).toInt().coerceAtLeast(1)
        val h = (sizePx * min(1f, 1f / img.aspect)).toInt().coerceAtLeast(1)
        val b = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
        return RenderedCursor(b, spec.hotX * w, spec.hotY * h)
    }
    val ib = ImageBitmap(sizePx, sizePx)
    val canvas = androidx.compose.ui.graphics.Canvas(ib)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(sizePx.toFloat(), sizePx.toFloat())) {
        drawFallbackArrow()
    }
    return RenderedCursor(ib.asAndroidBitmap(), spec.hotX * sizePx, spec.hotY * sizePx)
}
