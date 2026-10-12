package com.gb6105.musictimely.domain.usecase.session

import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 재조정 (SRS v1 §7.2, FR-A065, QA-39): 앱 실행·복귀 때 현재 상태에 맞게 알람·알림·관측을 다시 맞춘다. */
class ReconcileSessionUseCaseTest {

    private val minute = 60_000L
    private val machine = TimerStateMachine()
    private val clock = FakeClock()
    private val repository = FakeSessionRepository()
    private val sink = FakeTimerEffectSink()
    private val reconcile = ReconcileSessionUseCase(repository, clock, sink)

    private fun running() = machine.reduce(TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0).session

    @Test
    fun `running re-schedules the alarm for the current revision and re-shows progress and observation`() = runTest {
        repository.stored = running()
        clock.monotonicMs = 10 * minute

        reconcile()

        assertThat(sink.received).containsExactly(
            TimerEffect.ScheduleEndAlarm(atMonoMs = 25 * minute, targetRevision = 0),
            TimerEffect.ShowProgress,
            TimerEffect.StartObservation,
        ).inOrder()
    }

    @Test
    fun `paused cancels any alarm and keeps progress and observation`() = runTest {
        repository.stored = machine.reduce(running(), TimerCommand.Pause("p"), minute).session

        reconcile()

        assertThat(sink.received).containsExactly(TimerEffect.CancelEndAlarm, TimerEffect.ShowProgress, TimerEffect.StartObservation).inOrder()
    }

    @Test
    fun `limit reached cancels the alarm and shows the limit without a new end notification`() = runTest {
        repository.stored = machine.reduce(running(), TimerCommand.ReachLimit("r"), 25 * minute).session

        reconcile()

        assertThat(sink.received).containsExactly(TimerEffect.CancelEndAlarm, TimerEffect.ShowProgress, TimerEffect.StartObservation).inOrder()
    }

    @Test
    fun `ended and recovery clear stale alarms and notifications only`() = runTest {
        repository.stored = machine.reduce(running(), TimerCommand.Finish("f"), minute).session
        reconcile()
        assertThat(sink.received).containsExactly(TimerEffect.CancelEndAlarm, TimerEffect.ClearNotifications).inOrder()

        sink.received.clear()
        repository.stored = machine.reduce(running(), TimerCommand.InvalidateRecovery("i"), minute).session
        reconcile()
        assertThat(sink.received).containsExactly(TimerEffect.CancelEndAlarm, TimerEffect.ClearNotifications).inOrder()
    }

    @Test
    fun `nothing stored sends nothing (data deletion cancels on its own, FR-A071)`() = runTest {
        reconcile()

        assertThat(sink.received).isEmpty()
    }

    @Test
    fun `reconciling twice is harmless (idempotent upsert by key)`() = runTest {
        repository.stored = running()

        reconcile()
        reconcile()

        assertThat(sink.received.filterIsInstance<TimerEffect.ScheduleEndAlarm>().distinct()).hasSize(1)
    }
}
