package org.wishy.browser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wishy.browser.BrowserController

/** The saved preview of a page, or null while there is none. Reloads when a new preview is stored. */
@Composable
fun rememberThumb(c: BrowserController, url: String): ImageBitmap? {
    val version = c.thumbs.version
    val bmp by produceState<ImageBitmap?>(null, url, version) {
        value = withContext(Dispatchers.IO) { c.thumbs.get(url)?.asImageBitmap() }
    }
    return bmp
}

/** The newest in-memory preview of any page on [host] (for the home screen shortcuts). */
@Composable
fun rememberHostThumb(c: BrowserController, host: String?): ImageBitmap? {
    val version = c.thumbs.version
    return remember(host, version) { host?.let { c.thumbs.getForHost(it)?.asImageBitmap() } }
}

private fun hueOf(host: String): Float = ((host.hashCode() and 0x7fffffff) % 360).toFloat()

/**
 * A 16:9 picture of a page. If there is no preview yet it shows a calm coloured tile with the
 * site's first letter, so a grid of pages is still easy to tell apart.
 */
@Composable
fun PageThumb(
    c: BrowserController,
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val bmp = rememberThumb(c, url)
    val host = remember(url) { c.hostOf(url) }
    Box(modifier.aspectRatio(16f / 9f).clip(shape)) {
        if (bmp != null) {
            Image(bmp, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val h = hueOf(host)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color.hsv(h, 0.52f, 0.58f), Color.hsv((h + 42f) % 360f, 0.62f, 0.34f)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Sym.Public, null, Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp), tint = Color.White.copy(alpha = 0.14f))
                Text(
                    (title.ifBlank { host }.firstOrNull { it.isLetterOrDigit() } ?: '#').uppercaseChar().toString(),
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}
