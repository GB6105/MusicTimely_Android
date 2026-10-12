package com.gb6105.musictimely.ui.component

import com.gb6105.musictimely.ui.theme.MtColors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Design "Button/Primary": Default sunset, Pressed shifted to the red end, Disabled flat (docs/design components). */
class PrimaryButtonStyleTest {

    @Test
    fun `default uses the full sunset stops`() {
        assertThat(PrimaryButtonStyle.stops(enabled = true, pressed = false)?.toList()).isEqualTo(MtColors.SunsetStops.toList())
    }

    @Test
    fun `pressed shifts the gradient to coral and red`() {
        assertThat(PrimaryButtonStyle.stops(enabled = true, pressed = true)?.toList())
            .containsExactly(0f to MtColors.Coral, 1f to MtColors.Red).inOrder()
    }

    @Test
    fun `disabled has no gradient`() {
        assertThat(PrimaryButtonStyle.stops(enabled = false, pressed = true)).isNull()
    }
}
