package com.gb6105.musictimely.domain.model.translation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A017 마지막 곡 예고, SRS v1 §6.5 targetRevision당 1회, QA-16·QA-17. */
class LastSongCueTest {

    private val d = 240_000L
    private val minute = 60_000L

    private fun armed(remainingAtStartMs: Long, revision: Int = 0) =
        LastSongCue.arm(revision = revision, remainingMs = remainingAtStartMs, durationPerUnitMs = d)

    @Test
    fun `time based cue fires once when remaining drops to one song`() {
        var cue = armed(25 * minute)

        val early = cue.evaluate(remainingMs = 5 * minute, durationPerUnitMs = d, observedItem = null)
        cue = early.next
        val hit = cue.evaluate(remainingMs = 240_000, durationPerUnitMs = d, observedItem = null)
        cue = hit.next
        val again = cue.evaluate(remainingMs = 100_000, durationPerUnitMs = d, observedItem = null)

        assertThat(early.fired).isNull()
        assertThat(hit.fired).isEqualTo(CueKind.ABOUT_ONE_SONG_LEFT)
        assertThat(again.fired).isNull()
    }

    @Test
    fun `allocation shorter than one song never cues at start (QA-17)`() {
        val cue = armed(2 * minute)

        assertThat(cue.evaluate(remainingMs = 2 * minute, durationPerUnitMs = d, observedItem = null).fired).isNull()
        assertThat(cue.evaluate(remainingMs = 1_000, durationPerUnitMs = d, observedItem = null).fired).isNull()
    }

    @Test
    fun `no cue once the limit is reached`() {
        val cue = armed(25 * minute)

        assertThat(cue.evaluate(remainingMs = 0, durationPerUnitMs = d, observedItem = null).fired).isNull()
    }

    @Test
    fun `observed song boundary cue when this song ends at or after the target`() {
        val cue = armed(25 * minute)

        val result = cue.evaluate(
            remainingMs = 200_000,
            durationPerUnitMs = d,
            observedItem = ObservedItem(durationMs = 300_000, positionMs = 50_000),
        )

        assertThat(result.fired).isEqualTo(CueKind.THIS_SONG_ENDS_AT_TARGET)
    }

    @Test
    fun `observed item that ends before the target does not cue yet`() {
        val cue = armed(25 * minute)

        val result = cue.evaluate(
            remainingMs = 230_000,
            durationPerUnitMs = d,
            observedItem = ObservedItem(durationMs = 200_000, positionMs = 0),
        )

        assertThat(result.fired).isNull()
    }

    @Test
    fun `a new revision after extending re-arms the cue (QA-16)`() {
        var cue = armed(25 * minute)
        cue = cue.evaluate(remainingMs = 200_000, durationPerUnitMs = d, observedItem = null).next

        cue = LastSongCue.arm(revision = 1, remainingMs = 5 * minute, durationPerUnitMs = d)
        val hit = cue.evaluate(remainingMs = 200_000, durationPerUnitMs = d, observedItem = null)

        assertThat(hit.fired).isEqualTo(CueKind.ABOUT_ONE_SONG_LEFT)
        assertThat(hit.next.revision).isEqualTo(1)
    }

    @Test
    fun `restoring the same revision with a fired cue does not fire again (QA-16)`() {
        val restored = LastSongCue(revision = 0, eligible = true, fired = true)

        assertThat(restored.evaluate(remainingMs = 100_000, durationPerUnitMs = d, observedItem = null).fired).isNull()
    }
}
