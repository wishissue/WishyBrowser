package org.wishy.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import org.wishy.browser.CursorImage
import org.wishy.browser.CursorLibrary
import kotlin.math.min

/**
 * Pointers are pictures only: open-source packs shipped in assets/cursors (Bibata, Vimix, Layan, Capitaine;
 * licences in index.json and the credits page) and the user's own pictures (see CursorLibrary).
 * Nothing is drawn or animated in code, so the pointer costs no frame time while it moves.
 * [CursorSpec.hotX]/[CursorSpec.hotY] are the click point as a fraction of the picture.
 */
const val DEFAULT_CURSOR_ID = "bibata_arrow_classic"

class CursorSpec(
    val id: String,
    val name: String,
    /** Null only if the picture assets could not be read; a plain arrow is drawn instead. */
    val image: CursorImage?,
) {
    val hotX: Float get() = image?.hotX ?: 0.1f
    val hotY: Float get() = image?.hotY ?: 0.05f

    /** Pictures keep their proportions inside the nominal square [size]. */
    fun boxW(size: Dp): Dp = if (image == null) size else size * min(1f, image.aspect)
    fun boxH(size: Dp): Dp = if (image == null) size else size * min(1f, 1f / image.aspect)
}

/** Lets [CursorGlyph] find pictures without every caller passing the library around. */
val LocalCursorLib = staticCompositionLocalOf<CursorLibrary?> { null }

object Cursors {
    fun of(img: CursorImage): CursorSpec = CursorSpec(img.id, img.name, img)

    /** Any saved id. Ids from older versions (the removed drawn cursors) fall back to the default picture. */
    fun resolve(id: String?, lib: CursorLibrary): CursorSpec {
        lib.find(id ?: "")?.let { return of(it) }
        lib.find(DEFAULT_CURSOR_ID)?.let { return of(it) }
        return CursorSpec("fallback", "Pointer", null)
    }
}

@Composable
fun CursorGlyph(spec: CursorSpec, size: Dp, modifier: Modifier = Modifier) {
    val img = spec.image
    val lib = LocalCursorLib.current
    val bmp = remember(img?.id, lib) { if (img != null) lib?.bitmap(img) else null }
    if (bmp != null) {
        Image(bmp, null, modifier.size(spec.boxW(size), spec.boxH(size)), contentScale = ContentScale.Fit)
        return
    }
    // Fallback arrow, only used if the picture could not be loaded.
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 100f
        val p = Path().apply {
            moveTo(10f * s, 5f * s); lineTo(10f * s, 85f * s); lineTo(32f * s, 64f * s); lineTo(52f * s, 92f * s)
            lineTo(64f * s, 85f * s); lineTo(44f * s, 58f * s); lineTo(74f * s, 56f * s); close()
        }
        drawPath(p, Color.White)
        drawPath(p, Color.Black, style = Stroke(5f * s))
    }
}
