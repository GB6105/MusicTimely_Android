package com.gb6105.musictimely.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Design "분→곡 다이얼 카드": one sunset segment per allocated song, capped like the chips (FR-A021). */
class DialSegmentsTest {

    @Test
    fun `one segment per allocated song`() {
        assertThat(DialSegments.of(7).size).isEqualTo(7)
    }

    @Test
    fun `more than eight songs are drawn as eight segments`() {
        assertThat(DialSegments.of(20).size).isEqualTo(8)
    }

    @Test
    fun `no translation draws no segment`() {
        assertThat(DialSegments.of(null)).isEmpty()
        assertThat(DialSegments.of(0)).isEmpty()
    }

    @Test
    fun `segments and their gaps go once around the ring, starting at the top`() {
        val segments = DialSegments.of(4)

        assertThat(segments.first().startDegrees).isWithin(0.01f).of(-90f + DialSegments.GAP_DEGREES / 2)
        val covered = segments.sumOf { it.sweepDegrees.toDouble() } + segments.size * DialSegments.GAP_DEGREES
        assertThat(covered).isWithin(0.01).of(360.0)
    }

    @Test
    fun `a single song is one full ring without a gap`() {
        val segments = DialSegments.of(1)

        assertThat(segments.single().sweepDegrees).isEqualTo(360f)
    }
}
