package com.gb6105.musictimely.ui.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Each cover motif shows its signature colour (Figma "Original sample cover"). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverMotifDrawingFeatureTest {

    private val side = 100

    private fun draw(motif: CoverMotif, shifted: Boolean = false): PixelMap {
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
            drawCoverMotif(motif, shifted)
        }
        return bitmap.toPixelMap()
    }

    /** Sunset orange…red: strong red, little blue. */
    private fun assertSunset(what: String, c: Color) {
        assertWithMessage("$what red").that(c.red).isGreaterThan(0.9f)
        assertWithMessage("$what blue").that(c.blue).isLessThan(0.4f)
        assertWithMessage("$what alpha").that(c.alpha).isGreaterThan(0.9f)
    }

    private fun assertDark(what: String, c: Color) {
        assertWithMessage("$what alpha").that(c.alpha).isGreaterThan(0.9f)
        assertWithMessage("$what luminance").that(c.luminance()).isLessThan(0.05f)
    }

    private fun assertLightGrey(what: String, c: Color) {
        assertWithMessage("$what alpha").that(c.alpha).isGreaterThan(0.9f)
        assertWithMessage("$what luminance").that(c.luminance()).isGreaterThan(0.5f)
        assertWithMessage("$what neutral").that(c.red - c.blue).isLessThan(0.08f)
    }

    @Test
    fun `sleeve is a light tile with a sunset circle above a dark band`() {
        val p = draw(CoverMotif.SLEEVE)

        assertLightGrey("sleeve top", p[50, 6])
        assertSunset("sleeve circle", p[50, 38])
        assertDark("sleeve band", p[50, 63])
    }

    @Test
    fun `the shifted sleeve moves its circle to the right`() {
        val p = draw(CoverMotif.SLEEVE, shifted = true)

        assertSunset("shifted circle", p[64, 38])
        assertLightGrey("shifted left of circle", p[30, 38])
    }

    @Test
    fun `stripes is a dark tile with light stripes and one orange stripe`() {
        val p = draw(CoverMotif.STRIPES)

        assertDark("stripes tile", p[50, 6])
        assertLightGrey("light stripe", p[20, 50])
        assertSunset("orange stripe", p[44, 50])
    }

    @Test
    fun `record is a light tile with a black record and a sunset centre label`() {
        val p = draw(CoverMotif.RECORD)

        assertLightGrey("record tile", p[50, 4])
        assertDark("record vinyl", p[50, 22])
        assertSunset("record label", p[59, 50])
        assertDark("record spindle", p[50, 50])
    }
}
