package com.gb6105.musictimely.ui.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The dial gradient runs once around from the top, so no colour jump shows inside a segment (emulator finding). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialDrawingFeatureTest {

    @Test
    fun `no colour seam at three o'clock inside a segment`() {
        val size = 400
        val stroke = 30f
        val bitmap = ImageBitmap(size, size)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(size.toFloat(), size.toFloat())) {
            drawDial(DialSegments.of(7), stroke, Color.LightGray)
        }
        val pixels = bitmap.toPixelMap()
        val radius = (size - stroke) / 2
        fun at(degrees: Double) = pixels[
            (size / 2 + radius * cos(Math.toRadians(degrees))).toInt(),
            (size / 2 + radius * sin(Math.toRadians(degrees))).toInt(),
        ]

        val above = at(-2.0)
        val below = at(2.0)

        assertWithMessage("green either side of 3 o'clock").that(abs(above.green - below.green)).isLessThan(0.05f)
    }
}
