package com.gb6105.musictimely.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One shadow layer (x, y, blur, colour) from docs/design/README.md 그림자. */
data class ShadowLayer(val x: Dp, val y: Dp, val blur: Dp, val color: Color)

object MtShadows {
    val RaisedLight = listOf(
        ShadowLayer((-9).dp, (-9).dp, 20.dp, Color.White.copy(alpha = 0.95f)),
        ShadowLayer(10.dp, 12.dp, 26.dp, Color(0xFF92949B).copy(alpha = 0.38f)),
    )
    val RaisedDark = listOf(
        ShadowLayer((-7).dp, (-7).dp, 16.dp, Color.White.copy(alpha = 0.05f)),
        ShadowLayer(9.dp, 11.dp, 22.dp, Color.Black.copy(alpha = 0.5f)),
    )
    val AccentGlow = listOf(ShadowLayer(0.dp, 12.dp, 12.dp, Color(0xFFFF5046).copy(alpha = 0.27f)))
    val ChipGlow = listOf(ShadowLayer(0.dp, 10.dp, 10.dp, Color(0xFFFF5046).copy(alpha = 0.30f)))
    val Field = listOf(ShadowLayer(0.dp, 8.dp, 10.dp, Color(0xFF96989E).copy(alpha = 0.18f)))
    val Vinyl = listOf(
        ShadowLayer((-7).dp, (-7).dp, 18.dp, Color.White.copy(alpha = 0.24f)),
        ShadowLayer(0.dp, 24.dp, 38.dp, Color.Black.copy(alpha = 0.30f)),
    )

    fun raised(dark: Boolean) = if (dark) RaisedDark else RaisedLight
}

/** Dark palette in use (the shadows differ per theme). */
@Composable
fun isMtDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Soft shadows drawn behind [shape] with `setShadowLayer`. The shape is filled with [fill] (the element's own
 * background) because a transparent paint casts no shadow. Text never gets a shadow.
 */
fun Modifier.softShadow(shape: Shape, fill: Color, layers: List<ShadowLayer>): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawIntoCanvas { canvas ->
        layers.forEach { layer ->
            val paint = Paint().apply { color = fill }
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                setShadowLayer(layer.blur.toPx() / 2, layer.x.toPx(), layer.y.toPx(), layer.color.toArgb())
            }
            canvas.drawOutline(outline, paint)
        }
    }
}
