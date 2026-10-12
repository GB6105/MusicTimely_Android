package com.gb6105.musictimely.domain.model.translation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A016 곡 보정 (SRS v1 §6.2), QA-15. */
class SongCorrectionTest {

    private val d = 240_000L

    @Test
    fun `correction anchors the displayed index without touching time`() {
        val correction = SongCorrection.anchor(index = 5, elapsedMs = 600_000) as SongCorrection.Result.Applied

        assertThat(correction.anchor.displayIndex(elapsedMs = 600_000, durationPerUnitMs = d)).isEqualTo(5)
        assertThat(correction.anchor.displayIndex(elapsedMs = 600_000 + 239_000, durationPerUnitMs = d)).isEqualTo(5)
        assertThat(correction.anchor.displayIndex(elapsedMs = 600_000 + 240_000, durationPerUnitMs = d)).isEqualTo(6)
    }

    @Test
    fun `correction accepts integers from 1 to 999 only`() {
        assertThat(SongCorrection.anchor(index = 0, elapsedMs = 0)).isEqualTo(SongCorrection.Result.OutOfRange)
        assertThat(SongCorrection.anchor(index = 1000, elapsedMs = 0)).isEqualTo(SongCorrection.Result.OutOfRange)
        assertThat(SongCorrection.anchor(index = 999, elapsedMs = 0)).isInstanceOf(SongCorrection.Result.Applied::class.java)
    }
}
