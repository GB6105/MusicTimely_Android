package com.gb6105.musictimely.domain.model.translation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F03 D0 계산. SRS v1 §6.1 예시 표, FR-A003·A014, QA-02·QA-14. */
class SongTranslationTest {

    private val d = 240_000L
    private val minute = 60_000L

    @Test
    fun `25 minutes at 240 seconds is about 7 songs (FR-A003, QA-02)`() {
        assertThat(SongTranslation.allocatedSongs(allocationMs = 25 * minute, durationPerUnitMs = d)).isEqualTo(7)
    }

    @Test
    fun `allocated songs is at least one`() {
        assertThat(SongTranslation.allocatedSongs(allocationMs = 2 * minute, durationPerUnitMs = d)).isEqualTo(1)
    }

    @Test
    fun `remaining songs rounds up the remaining time (QA-14)`() {
        assertThat(SongTranslation.remainingSongs(targetMs = 25 * minute, elapsedMs = 564_000, durationPerUnitMs = d)).isEqualTo(4)
    }

    @Test
    fun `remaining songs is zero at the target`() {
        assertThat(SongTranslation.remainingSongs(targetMs = 25 * minute, elapsedMs = 25 * minute, durationPerUnitMs = d)).isEqualTo(0)
    }

    @Test
    fun `elapsed amount is rounded to one decimal in tenths`() {
        assertThat(SongTranslation.elapsedTenths(elapsedMs = 564_000, durationPerUnitMs = d)).isEqualTo(24)
        assertThat(SongTranslation.elapsedTenths(elapsedMs = 480_000, durationPerUnitMs = d)).isEqualTo(20)
        assertThat(SongTranslation.elapsedTenths(elapsedMs = 2_268_000, durationPerUnitMs = d)).isEqualTo(95)
    }

    @Test
    fun `segment index is floor of elapsed over d plus one`() {
        assertThat(SongTranslation.segmentIndex(elapsedMs = 0, durationPerUnitMs = d)).isEqualTo(1)
        assertThat(SongTranslation.segmentIndex(elapsedMs = 564_000, durationPerUnitMs = d)).isEqualTo(3)
    }

    @Test
    fun `filled chips are whole segments, and the fractional last chip completes at the target`() {
        assertThat(SongTranslation.filledChips(targetMs = 25 * minute, elapsedMs = 564_000, durationPerUnitMs = d)).isEqualTo(2)
        assertThat(SongTranslation.filledChips(targetMs = 25 * minute, elapsedMs = 24 * minute, durationPerUnitMs = d)).isEqualTo(6)
        assertThat(SongTranslation.filledChips(targetMs = 25 * minute, elapsedMs = 25 * minute, durationPerUnitMs = d)).isEqualTo(7)
    }

    @Test
    fun `infinite mode fills chips by elapsed only`() {
        assertThat(SongTranslation.filledChips(targetMs = null, elapsedMs = 564_000, durationPerUnitMs = d)).isEqualTo(2)
    }
}
