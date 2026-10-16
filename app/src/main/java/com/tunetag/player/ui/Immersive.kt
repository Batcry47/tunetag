package com.tunetag.player.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.tunetag.player.data.Song
import com.tunetag.player.tags.ArtCache
import com.tunetag.player.tags.TinyArt

/** Loads the tiny ambient version of a song's artwork (cached; shows instantly when already seen). */
@Composable
fun rememberTinyArt(song: Song?): State<TinyArt?> {
    val ctx = LocalContext.current
    return produceState<TinyArt?>(ArtCache.peekTiny(song), song?.id, song?.dateModified) {
        value = if (song == null) null else ArtCache.tiny(ctx, song)
    }
}

/** The artwork, scaled up smoothly from 32px: looks like a heavy blur, costs almost nothing, works on Android 10. */
@Composable
fun BlurredArt(tiny: TinyArt?, modifier: Modifier = Modifier) {
    if (tiny != null) {
        val img = remember(tiny) { tiny.bitmap.asImageBitmap() }
        Image(img, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
    }
}

/** Fades the bottom [fraction] of a composable to transparent (so a sharp image melts into what is behind it). */
fun Modifier.fadeBottom(fraction: Float, strength: () -> Float = { 1f }): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val s = strength().coerceIn(0f, 1f)
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black,
                    (1f - fraction) to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - s),
                ),
                blendMode = BlendMode.DstIn
            )
        }
