package com.gb6105.musictimely.ui.component

import androidx.compose.ui.graphics.luminance
import com.gb6105.musictimely.ui.component.ProgressCell.CURRENT
import com.gb6105.musictimely.ui.component.ProgressCell.EMPTY
import com.gb6105.musictimely.ui.component.ProgressCell.FILLED
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Design "ProgressPanel": one cell per song, passed cells filled, the current cell outlined; last song state. */
class ProgressCellsTest {

    @Test
    fun `passed cells are filled and the current cell is outlined`() {
        assertThat(ProgressCells.of(total = 7, filled = 2, lastSong = false))
            .containsExactly(FILLED, FILLED, CURRENT, EMPTY, EMPTY, EMPTY, EMPTY).inOrder()
    }

    @Test
    fun `in the last song state only the last cell is outlined`() {
        assertThat(ProgressCells.of(total = 7, filled = 2, lastSong = true))
            .containsExactly(FILLED, FILLED, EMPTY, EMPTY, EMPTY, EMPTY, CURRENT).inOrder()
    }

    @Test
    fun `when every song has passed nothing is outlined`() {
        assertThat(ProgressCells.of(total = 3, filled = 5, lastSong = false)).containsExactly(FILLED, FILLED, FILLED).inOrder()
    }

    @Test
    fun `more than eight songs use the summary instead of cells (FR-A021)`() {
        assertThat(ProgressCells.of(total = 9, filled = 1, lastSong = false)).isEmpty()
    }

    @Test
    fun `remaining cells are drawn as visible grey cells on the lighter track (A2 design)`() {
        assertThat(ProgressCellStyle.empty.alpha).isGreaterThan(0f)
        assertThat(ProgressCellStyle.empty).isNotEqualTo(ProgressCellStyle.track)
        assertThat(ProgressCellStyle.empty.luminance()).isLessThan(ProgressCellStyle.track.luminance())
    }
}
