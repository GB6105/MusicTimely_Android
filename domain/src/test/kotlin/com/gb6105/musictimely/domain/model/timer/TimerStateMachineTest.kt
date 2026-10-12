package com.gb6105.musictimely.domain.model.timer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * F01 타이머 도메인. SRS v1 §5.1·§5.2, SRS Android §5.1, FR-A005·A007~A011·A013.
 */
class TimerStateMachineTest {

    private val minute = 60_000L
    private val machine = TimerStateMachine()
    private var commandSeq = 0

    private fun id() = "cmd-${commandSeq++}"

    private fun ready(targetMinutes: Long? = 25) = TimerSession.ready(
        sessionId = "s1",
        allocation = if (targetMinutes == null) Allocation.infinite() else Allocation.bounded(targetMinutes * minute),
    )

    private fun TimerSession.apply(command: TimerCommand, nowMono: Long): Transition =
        machine.reduce(this, command, nowMono)

    @Test
    fun `start moves READY to RUNNING and schedules the end alarm at the target`() {
        val t = ready().apply(TimerCommand.Start(id()), nowMono = 1_000)

        assertThat(t.session.state).isEqualTo(TimerState.RUNNING)
        assertThat(t.effects).containsExactly(
            TimerEffect.ScheduleEndAlarm(atMonoMs = 1_000 + 25 * minute, targetRevision = 0),
            TimerEffect.ShowProgress,
            TimerEffect.StartObservation,
        )
    }

    @Test
    fun `elapsed follows accumulated plus monotonic delta`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        assertThat(running.elapsedMs(nowMono = 9 * minute)).isEqualTo(9 * minute)
        assertThat(running.remainingMs(nowMono = 9 * minute)).isEqualTo(16 * minute)
    }

    @Test
    fun `60s run, 30s pause, 60s run equals 120s and the user's pause and resume carry the music link (FR-A008, DEC-A16)`() {
        var s = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
        val pause = s.apply(TimerCommand.Pause(id()), nowMono = 60_000)
        s = pause.session
        val resume = s.apply(TimerCommand.Resume(id()), nowMono = 90_000)
        s = resume.session

        assertThat(s.elapsedMs(nowMono = 150_000)).isEqualTo(120_000)
        assertThat(pause.effects).containsExactly(TimerEffect.CancelEndAlarm, TimerEffect.ShowProgress, TimerEffect.PauseLinkedMusic)
        assertThat(resume.effects).containsExactly(
            TimerEffect.ScheduleEndAlarm(atMonoMs = 90_000 + 25 * minute - 60_000, targetRevision = 0),
            TimerEffect.ShowProgress,
            TimerEffect.ResumeLinkedMusic,
        )
    }

    @Test
    fun `paused time does not grow elapsed`() {
        var s = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
        s = s.apply(TimerCommand.Pause(id()), nowMono = 60_000).session

        assertThat(s.state).isEqualTo(TimerState.PAUSED)
        assertThat(s.elapsedMs(nowMono = 10 * minute)).isEqualTo(60_000)
    }

    @Test
    fun `ten rapid starts keep one running session and one alarm (FR-A007, QA-04)`() {
        var s = ready()
        val alarms = mutableListOf<TimerEffect>()
        repeat(10) { i ->
            val t = s.apply(TimerCommand.Start(id()), nowMono = i.toLong())
            s = t.session
            alarms += t.effects.filterIsInstance<TimerEffect.ScheduleEndAlarm>()
        }

        assertThat(s.state).isEqualTo(TimerState.RUNNING)
        assertThat(s.sessionId).isEqualTo("s1")
        assertThat(alarms).hasSize(1)
    }

    @Test
    fun `a repeated command id is applied once`() {
        val running = ready().apply(TimerCommand.Start("same"), nowMono = 0).session
        val paused = running.apply(TimerCommand.Pause("p"), nowMono = 1_000).session

        val again = paused.apply(TimerCommand.Pause("p"), nowMono = 5_000)

        assertThat(again.session).isEqualTo(paused)
        assertThat(again.effects).isEmpty()
    }

    @Test
    fun `reaching the target clamps elapsed and enters LIMIT_REACHED (FR-A010)`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val t = running.apply(TimerCommand.ReachLimit(id()), nowMono = 25 * minute + 7_000)

        assertThat(t.session.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(t.session.elapsedMs(nowMono = 40 * minute)).isEqualTo(25 * minute)
        assertThat(t.effects).containsExactly(
            TimerEffect.CancelEndAlarm,
            TimerEffect.ShowLimitReached,
            TimerEffect.EvaluateStopAtEnd,
        )
    }

    @Test
    fun `reach limit before the target is ignored`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val t = running.apply(TimerCommand.ReachLimit(id()), nowMono = 10 * minute)

        assertThat(t.session.state).isEqualTo(TimerState.RUNNING)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `tick past the target reaches the limit once`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val first = machine.tick(running, nowMono = 25 * minute)
        val second = machine.tick(first.session, nowMono = 25 * minute + 1_000)

        assertThat(first.session.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(first.effects).contains(TimerEffect.EvaluateStopAtEnd)
        assertThat(second.effects).isEmpty()
    }

    @Test
    fun `extend keeps the initial allocation, adds to the target and bumps the revision (FR-A011)`() {
        var s = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
        s = s.apply(TimerCommand.ReachLimit(id()), nowMono = 25 * minute).session

        val t = s.apply(TimerCommand.Extend(id(), ExtensionStep.FIVE), nowMono = 27 * minute)

        assertThat(t.session.state).isEqualTo(TimerState.RUNNING)
        assertThat(t.session.allocation.initialDurationMs).isEqualTo(25 * minute)
        assertThat(t.session.allocation.currentTargetMs).isEqualTo(30 * minute)
        assertThat(t.session.allocation.targetRevision).isEqualTo(1)
        assertThat(t.effects).containsExactly(
            TimerEffect.CancelStopAtEnd,
            TimerEffect.ScheduleEndAlarm(atMonoMs = 27 * minute + 5 * minute, targetRevision = 1),
            TimerEffect.ShowProgress,
        )
    }

    @Test
    fun `waiting at the limit is excluded after extending (QA-09)`() {
        var s = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
        s = s.apply(TimerCommand.ReachLimit(id()), nowMono = 25 * minute).session
        s = s.apply(TimerCommand.Extend(id(), ExtensionStep.FIVE), nowMono = 27 * minute).session

        val finished = s.apply(TimerCommand.Finish(id()), nowMono = 30 * minute).session

        assertThat(finished.elapsedMs(nowMono = 30 * minute)).isEqualTo(28 * minute)
        assertThat(finished.extensions).containsExactly(5 * minute)
        assertThat(finished.endReason).isEqualTo(EndReason.COMPLETED)
    }

    @Test
    fun `extension steps are 5, 10 and 15 minutes`() {
        assertThat(ExtensionStep.entries.map { it.addedMs }).containsExactly(5 * minute, 10 * minute, 15 * minute).inOrder()
    }

    @Test
    fun `extend is ignored unless the limit was reached`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val t = running.apply(TimerCommand.Extend(id(), ExtensionStep.TEN), nowMono = minute)

        assertThat(t.session).isEqualTo(running)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `finish before the target ends as user_ended and releases everything (FR-A009)`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val t = running.apply(TimerCommand.Finish(id()), nowMono = 10 * minute)

        assertThat(t.session.state).isEqualTo(TimerState.ENDED)
        assertThat(t.session.endReason).isEqualTo(EndReason.USER_ENDED)
        assertThat(t.effects).containsExactly(
            TimerEffect.CancelEndAlarm,
            TimerEffect.CancelStopAtEnd,
            TimerEffect.StopObservation,
            TimerEffect.ClearNotifications,
            TimerEffect.DeliverResult,
        )
    }

    @Test
    fun `finish on the same tick as the target normalises elapsed to the target`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val t = running.apply(TimerCommand.Finish(id()), nowMono = 25 * minute + 900)

        assertThat(t.session.elapsedMs(nowMono = 26 * minute)).isEqualTo(25 * minute)
        assertThat(t.session.endReason).isEqualTo(EndReason.COMPLETED)
    }

    @Test
    fun `finish from PAUSED and from LIMIT_REACHED is allowed`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
        val paused = running.apply(TimerCommand.Pause(id()), nowMono = minute).session
        val limit = running.apply(TimerCommand.ReachLimit(id()), nowMono = 25 * minute).session

        assertThat(paused.apply(TimerCommand.Finish(id()), nowMono = 2 * minute).session.state).isEqualTo(TimerState.ENDED)
        assertThat(limit.apply(TimerCommand.Finish(id()), nowMono = 26 * minute).session.state).isEqualTo(TimerState.ENDED)
    }

    @Test
    fun `an ended session cannot be resumed`() {
        val ended = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
            .apply(TimerCommand.Finish(id()), nowMono = minute).session

        val t = ended.apply(TimerCommand.Resume(id()), nowMono = 2 * minute)

        assertThat(t.session).isEqualTo(ended)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `infinite mode has no target, no alarm, no limit and ends as user_ended (FR-A005, QA-10)`() {
        val start = ready(targetMinutes = null).apply(TimerCommand.Start(id()), nowMono = 0)
        val running = start.session

        assertThat(start.effects).containsExactly(TimerEffect.ShowProgress, TimerEffect.StartObservation)
        assertThat(running.remainingMs(nowMono = 20 * minute)).isNull()
        assertThat(machine.tick(running, nowMono = 500 * minute).effects).isEmpty()
        val ended = running.apply(TimerCommand.Finish(id()), nowMono = 20 * minute).session
        assertThat(ended.elapsedMs(nowMono = 20 * minute)).isEqualTo(20 * minute)
        assertThat(ended.endReason).isEqualTo(EndReason.USER_ENDED)
    }

    @Test
    fun `invalid recovery moves any live state to RECOVERY_REQUIRED without starting anything`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val t = running.apply(TimerCommand.InvalidateRecovery(id()), nowMono = minute)

        assertThat(t.session.state).isEqualTo(TimerState.RECOVERY_REQUIRED)
        assertThat(t.effects).containsExactly(TimerEffect.CancelEndAlarm, TimerEffect.StopObservation)
    }

    @Test
    fun `abandoning recovery ends as abandoned`() {
        val recovery = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
            .apply(TimerCommand.InvalidateRecovery(id()), nowMono = minute).session

        val t = recovery.apply(TimerCommand.Abandon(id()), nowMono = 2 * minute)

        assertThat(t.session.state).isEqualTo(TimerState.ENDED)
        assertThat(t.session.endReason).isEqualTo(EndReason.ABANDONED)
    }

    @Test
    fun `resuming from recovery returns to PAUSED at the saved point without starting (SRS v1 5_2)`() {
        val recovery = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
            .apply(TimerCommand.Pause(id()), nowMono = 9 * minute).session
            .apply(TimerCommand.InvalidateRecovery(id()), nowMono = 10 * minute).session

        val t = recovery.apply(TimerCommand.ResumeFromRecovery(id()), nowMono = 5)

        assertThat(t.session.state).isEqualTo(TimerState.PAUSED)
        assertThat(t.session.elapsedMs(nowMono = 999 * minute)).isEqualTo(9 * minute)
        assertThat(t.effects).containsExactly(TimerEffect.ShowProgress)
    }

    @Test
    fun `finishing from recovery ends at the saved point`() {
        val recovery = ready().apply(TimerCommand.Start(id()), nowMono = 0).session
            .apply(TimerCommand.Pause(id()), nowMono = 9 * minute).session
            .apply(TimerCommand.InvalidateRecovery(id()), nowMono = 10 * minute).session

        val t = recovery.apply(TimerCommand.Finish(id()), nowMono = 5)

        assertThat(t.session.state).isEqualTo(TimerState.ENDED)
        assertThat(t.session.endReason).isEqualTo(EndReason.USER_ENDED)
        assertThat(t.session.elapsedMs(nowMono = 5)).isEqualTo(9 * minute)
    }

    @Test
    fun `pausing after the target passed reaches the limit instead (review LOW-20)`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val t = running.apply(TimerCommand.Pause(id()), nowMono = 26 * minute)

        assertThat(t.session.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(t.effects).contains(TimerEffect.EvaluateStopAtEnd)
    }

    @Test
    fun `a pause that lands past the target reaches the limit without the music link (DEC-A16)`() {
        val running = ready().apply(TimerCommand.Start(id()), nowMono = 0).session

        val late = running.apply(TimerCommand.Pause(id()), nowMono = 26 * minute)

        assertThat(late.session.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(late.effects).doesNotContain(TimerEffect.PauseLinkedMusic)
    }
}
