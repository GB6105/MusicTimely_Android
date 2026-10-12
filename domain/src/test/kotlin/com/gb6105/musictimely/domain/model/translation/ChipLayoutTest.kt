package com.gb6105.musictimely.domain.model.translation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A021 큰 곡 수 칩 그룹화. */
class ChipLayoutTest {

    @Test
    fun `up to eight songs are shown as individual chips`() {
        assertThat(ChipLayout.of(totalSongs = 8, filledChips = 3)).isEqualTo(ChipLayout.Chips(total = 8, filled = 3))
    }

    @Test
    fun `more than eight songs switch to the remaining summary with the total kept`() {
        assertThat(ChipLayout.of(totalSongs = 9, filledChips = 3)).isEqualTo(ChipLayout.Summary(total = 9, filled = 3))
    }
}
