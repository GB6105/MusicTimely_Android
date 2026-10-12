package com.gb6105.musictimely.domain.model.session

import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 복원 판정. FR-A012, SRS Android §5.4·§9.2, SRS v1 §5.2·§9.3, QA-07·QA-08·QA-12·QA-A32. */
class RestorePolicyTest {

    private val minute = 60_000L
    private val day = 86_400_000L
    private val machine = TimerStateMachine()

    private fun running(startMono: Long = 0): TimerSession =
        machine.reduce(TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), startMono).session

    private fun checkpoint(session: TimerSession, boot: Int = 3, savedAt: Long = 10 * day) =
        SessionCheckpoint(session = session, bootCount = boot, updatedAtEpochMs = savedAt)

    @Test
    fun `same boot restores the running session by the monotonic clock without a UI tick`() {
        val decision = RestorePolicy.decide(checkpoint(running()), bootCount = 3, nowMono = 10 * minute, nowEpochMs = 10 * day + 10 * minute)

        decision as RestoreDecision.Restore
        assertThat(decision.session.state).isEqualTo(TimerState.RUNNING)
        assertThat(decision.session.elapsedMs(10 * minute)).isEqualTo(10 * minute)
    }

    @Test
    fun `a running session killed before the target and reopened after it restores at LIMIT_REACHED (QA-08)`() {
        val decision = RestorePolicy.decide(checkpoint(running()), bootCount = 3, nowMono = 40 * minute, nowEpochMs = 10 * day + 40 * minute)

        decision as RestoreDecision.Restore
        assertThat(decision.session.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(decision.session.elapsedMs(40 * minute)).isEqualTo(25 * minute)
        assertThat(decision.reachedLimitWhileAway).isTrue()
    }

    @Test
    fun `a paused session stays paused and gains no time (QA-07)`() {
        val paused = machine.reduce(running(), TimerCommand.Pause("p"), 5 * minute).session

        val decision = RestorePolicy.decide(checkpoint(paused), bootCount = 3, nowMono = 50 * minute, nowEpochMs = 10 * day + 50 * minute)

        decision as RestoreDecision.Restore
        assertThat(decision.session.state).isEqualTo(TimerState.PAUSED)
        assertThat(decision.session.elapsedMs(50 * minute)).isEqualTo(5 * minute)
    }

    @Test
    fun `a different boot count requires recovery instead of trusting the clock (QA-12)`() {
        val decision = RestorePolicy.decide(checkpoint(running(), boot = 3), bootCount = 4, nowMono = 1_000, nowEpochMs = 10 * day + minute)

        decision as RestoreDecision.RecoveryRequired
        assertThat(decision.reason).isEqualTo(RecoveryReason.CLOCK_DISCONTINUITY)
        assertThat(decision.session.state).isEqualTo(TimerState.RECOVERY_REQUIRED)
    }

    @Test
    fun `a checkpoint older than 7 days is never restored automatically`() {
        val decision = RestorePolicy.decide(checkpoint(running(), savedAt = 0), bootCount = 3, nowMono = 1_000, nowEpochMs = 7 * day + 1)

        decision as RestoreDecision.RecoveryRequired
        assertThat(decision.reason).isEqualTo(RecoveryReason.EXPIRED)
    }

    @Test
    fun `an empty store starts fresh`() {
        assertThat(RestorePolicy.decide(null, 3, 0, 10 * day)).isEqualTo(RestoreDecision.Fresh)
    }

    @Test
    fun `an ended session's result is shown again for up to 24 hours (SRS v1 9_2)`() {
        val ended = machine.reduce(running(), TimerCommand.Finish("f"), minute).session
        val saved = 10 * day

        assertThat(RestorePolicy.decide(checkpoint(ended, savedAt = saved), 3, 0, saved + day))
            .isEqualTo(RestoreDecision.ShowResult(ended, countedItems = null))
        assertThat(RestorePolicy.decide(checkpoint(ended, savedAt = saved), 3, 0, saved + day + 1)).isEqualTo(RestoreDecision.Fresh)
    }

    @Test
    fun `restored observation data is only the integer count and starts as mixed (SRS Android 9_2)`() {
        val cp = checkpoint(running()).copy(countedItems = 3, observedFromActiveMs = 0)

        val decision = RestorePolicy.decide(cp, bootCount = 3, nowMono = minute, nowEpochMs = 10 * day + minute) as RestoreDecision.Restore

        assertThat(decision.countedItems).isEqualTo(3)
        assertThat(decision.observationInterrupted).isTrue()
    }

    @Test
    fun `a recovery choice left unanswered is asked again on the next launch (review HIGH-6)`() {
        val recovery = running().copy(state = TimerState.RECOVERY_REQUIRED, runningAnchorMonoMs = null)

        val decision = RestorePolicy.decide(checkpoint(recovery), bootCount = 3, nowMono = minute, nowEpochMs = 10 * day + minute)

        assertThat(decision).isInstanceOf(RestoreDecision.RecoveryRequired::class.java)
    }
}
