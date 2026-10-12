package com.gb6105.musictimely.domain.model.timer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A004: the average song length (and optional loop length) is snapshotted into the session at creation. */
class AllocationSnapshotTest {

    @Test
    fun `allocation defaults to a 240 second song and no loop length`() {
        val allocation = Allocation.bounded(25 * 60_000L)

        assertThat(allocation.durationPerUnitMs).isEqualTo(240_000)
        assertThat(allocation.loopDurationMs).isNull()
    }

    @Test
    fun `display unit uses the loop length when set`() {
        val allocation = Allocation.bounded(25 * 60_000L, durationPerUnitMs = 200_000, loopDurationMs = 222_000)

        assertThat(allocation.displayUnitMs).isEqualTo(222_000)
        assertThat(Allocation.infinite(durationPerUnitMs = 300_000).displayUnitMs).isEqualTo(300_000)
    }

    @Test
    fun `extending keeps the snapshot`() {
        val machine = TimerStateMachine()
        var s = TimerSession.ready("s", Allocation.bounded(60_000, durationPerUnitMs = 300_000))
        s = machine.reduce(s, TimerCommand.Start("a"), 0).session
        s = machine.reduce(s, TimerCommand.ReachLimit("b"), 60_000).session
        s = machine.reduce(s, TimerCommand.Extend("c", ExtensionStep.FIVE), 61_000).session

        assertThat(s.allocation.durationPerUnitMs).isEqualTo(300_000)
    }

    @Test
    fun `single-loop mode uses the loop length, or the average when blank (SRS v1 6_4)`() {
        val withLoop = Allocation.bounded(25 * 60_000L, durationPerUnitMs = 240_000, loopDurationMs = 222_000, singleLoop = true)
        val blank = Allocation.bounded(25 * 60_000L, durationPerUnitMs = 240_000, loopDurationMs = null, singleLoop = true)

        assertThat(withLoop.singleLoop).isTrue()
        assertThat(withLoop.displayUnitMs).isEqualTo(222_000)
        assertThat(blank.displayUnitMs).isEqualTo(240_000)
        assertThat(Allocation.bounded(60_000).singleLoop).isFalse()
    }
}
