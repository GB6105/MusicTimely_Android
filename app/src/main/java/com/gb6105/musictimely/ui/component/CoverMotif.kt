package com.gb6105.musictimely.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.gb6105.musictimely.ui.theme.MtColors

/**
 * App-drawn cover artwork (Figma "Original sample cover"), shown where a song has no album art (DEC-A21: real art
 * in-app only, never in a share image until the rights review, plan M5). Rows cycle the three variants by index;
 * the result collage shows the first four.
 */
enum class CoverMotif {
    SLEEVE, STRIPES, RECORD;

    companion object {
        fun variantFor(index: Int): CoverMotif = entries[Math.floorMod(index, entries.size)]

        /** Always four tiles (tracks 0..3), cycling the variants even when fewer songs were counted. */
        fun collage(): List<CoverMotif> = List(COLLAGE_TILES) { variantFor(it) }

        const val COLLAGE_TILES = 4
    }
}

/** One cover tile; [shifted] moves the sleeve's sun and band like the collage's fourth tile. */
@Composable
fun CoverTile(motif: CoverMotif, size: Dp, radius: Dp, modifier: Modifier = Modifier, shifted: Boolean = false) {
    Canvas(modifier.size(size).clip(RoundedCornerShape(radius))) { drawCoverMotif(motif, shifted) }
}

/**
 * The song's album art when it has a readable one, else [motif] (DEC-A21). Bytes are decoded once per artwork.
 * Decorative either way: no content description.
 */
@Composable
fun CoverTile(
    artwork: Artwork?,
    motif: CoverMotif,
    size: Dp,
    radius: Dp,
    imageTag: String,
    motifTag: String,
    modifier: Modifier = Modifier,
    shifted: Boolean = false,
) {
    val image = remember(artwork) { artwork?.let(::decode) }
    if (image != null) {
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(RoundedCornerShape(radius)).testTag(imageTag),
        )
    } else {
        CoverTile(motif, size, radius, modifier.testTag(motifTag), shifted)
    }
}

private fun decode(artwork: Artwork): ImageBitmap? = runCatching {
    val bytes = artwork.bytes
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

fun DrawScope.drawCoverMotif(motif: CoverMotif, shifted: Boolean = false) {
    val w = size.width
    val h = size.height
    when (motif) {
        CoverMotif.SLEEVE -> {
            drawRect(TileGrey)
            val sunCenter = Offset(w * if (shifted) 0.64f else 0.5f, h * 0.36f)
            val sunRadius = w * 0.24f
            drawRecordLabel(sunCenter, sunRadius)
            val bandTop = h * if (shifted) 0.58f else 0.56f
            drawRect(MtColors.Vinyl, Offset(0f, bandTop), Size(w, h * 0.14f))
            drawRect(LowerGrey, Offset(0f, bandTop + h * 0.14f), Size(w, h))
        }
        CoverMotif.STRIPES -> {
            drawRect(MtColors.Charcoal)
            val stripeWidth = w * 0.05f
            repeat(STRIPES) { i ->
                val x = w * (0.2f + i * 0.12f)
                drawRect(
                    if (i == ORANGE_STRIPE) MtColors.Orange else StripeGrey,
                    Offset(x - stripeWidth / 2, h * 0.18f),
                    Size(stripeWidth, h * 0.64f),
                )
            }
        }
        CoverMotif.RECORD -> {
            drawRect(TileGrey)
            drawCircle(MtColors.Vinyl, w * 0.36f, center)
            drawRecordLabel(center, w * 0.14f)
            drawCircle(MtColors.Vinyl, w * 0.03f, center)
        }
    }
}

private const val STRIPES = 6
private const val ORANGE_STRIPE = 2
private val TileGrey = Color(0xFFDADADD)
private val LowerGrey = Color(0xFFB4B5BC)
private val StripeGrey = Color(0xFFCFD0D4)
