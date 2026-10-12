package com.gb6105.musictimely.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The record label shows the whole sunset across its own width: orange on the left, red on the right (review L3). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TurntableDrawingFeatureTest {

    @Test
    fun `the label gradient runs orange to red across the label, wherever it sits`() {
        val bitmap = ImageBitmap(400, 200)
        val center = Offset(300f, 100f)
        val radius = 60f
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(400f, 200f)) {
            drawRecordLabel(center, radius)
        }
        val pixels = bitmap.toPixelMap()

        val left = pixels[(center.x - radius * 0.9f).toInt(), center.y.toInt()]
        val right = pixels[(center.x + radius * 0.9f).toInt(), center.y.toInt()]

        // Orange #FF9C3F green ≈ 0.61, red #FF3B4D green ≈ 0.23.
        assertWithMessage("left edge green").that(left.green).isGreaterThan(0.55f)
        assertWithMessage("right edge green").that(right.green).isLessThan(0.33f)
    }
}
