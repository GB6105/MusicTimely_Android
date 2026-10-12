package com.gb6105.musictimely.domain.usecase.session

import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.gb6105.musictimely.domain.model.allocation.TaskTitle
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** FR-A013 활성 세션 1개, FR-A007·QA-11 시간은 단조 시계만. */
class SessionUseCasesTest {

    private val minute = 60_000L
    private val clock = FakeClock()
    private val repository = FakeSessionRepository()
    private var nextId = 0
    private val open = OpenSessionUseCase(repository) { "session-${nextId++}" }
    private val sink = FakeTimerEffectSink()
    private val dispatch = DispatchTimerCommandUseCase(repository, clock, sink)

    @Test
    fun `opening with no active session creates a READY session with the allocation`() = runTest {
        val session = open(Allocation.bounded(25 * minute))

        assertThat(session.state).isEqualTo(TimerState.READY)
        assertThat(session.sessionId).isEqualTo("session-0")
        assertThat(repository.stored).isEqualTo(session)
    }

    @Test
    fun `opening while a session is live returns it instead of replacing it (FR-A013)`() = runTest {
        val first = open(Allocation.bounded(25 * minute))
        dispatch(TimerCommand.Start("start"))

        val second = open(Allocation.bounded(10 * minute))

        assertThat(second.sessionId).isEqualTo(first.sessionId)
        assertThat(second.state).isEqualTo(TimerState.RUNNING)
        assertThat(second.allocation.currentTargetMs).isEqualTo(25 * minute)
    }

    @Test
    fun `opening after the previous session ended starts a new session id`() = runTest {
        open(Allocation.bounded(25 * minute))
        dispatch(TimerCommand.Start("start"))
        dispatch(TimerCommand.Finish("finish"))

        val next = open(Allocation.bounded(10 * minute))

        assertThat(next.sessionId).isEqualTo("session-1")
        assertThat(next.state).isEqualTo(TimerState.READY)
    }

    @Test
    fun `dispatch reads the monotonic clock, persists the session and returns effects`() = runTest {
        open(Allocation.bounded(25 * minute))
        clock.monotonicMs = 5_000

        val result = dispatch(TimerCommand.Start("start"))!!

        assertThat(result.session.state).isEqualTo(TimerState.RUNNING)
        assertThat(result.effects).contains(TimerEffect.ScheduleEndAlarm(5_000 + 25 * minute, 0))
        assertThat(repository.stored).isEqualTo(result.session)
    }

    @Test
    fun `an ignored command does not write the session again`() = runTest {
        open(Allocation.bounded(25 * minute))
        val writes = repository.saveCount

        dispatch(TimerCommand.Pause("pause-while-ready"))

        assertThat(repository.saveCount).isEqualTo(writes)
    }

    @Test
    fun `opening stores the optional task title with the session (FR-A001)`() = runTest {
        val session = open(Allocation.bounded(25 * minute), TaskTitle.from("기획안 쓰기"))

        assertThat(session.taskTitle).isEqualTo("기획안 쓰기")
        assertThat(repository.stored?.taskTitle).isEqualTo("기획안 쓰기")
    }

    @Test
    fun `applied effects go to the effect sink, ignored commands send nothing`() = runTest {
        open(Allocation.bounded(25 * minute))

        dispatch(TimerCommand.Pause("pause-while-ready"))
        assertThat(sink.received).isEmpty()

        dispatch(TimerCommand.Start("start"))
        assertThat(sink.received).containsExactly(
            TimerEffect.ScheduleEndAlarm(25 * minute, 0),
            TimerEffect.ShowProgress,
            TimerEffect.StartObservation,
        ).inOrder()
    }

    @Test
    fun `dispatch without a session returns null`() = runTest {
        assertThat(dispatch(TimerCommand.Start("start"))).isNull()
    }

    @Test
    fun `changing the wall clock does not change elapsed on the same boot (QA-11)`() = runTest {
        open(Allocation.bounded(25 * minute))
        dispatch(TimerCommand.Start("start"))
        clock.advance(10 * minute)
        clock.wallEpochMs += 60 * minute

        val session = repository.stored as TimerSession

        assertThat(session.elapsedMs(clock.monotonicNowMs())).isEqualTo(10 * minute)
    }

    @Test
    fun `tick reaches the limit through the clock`() = runTest {
        open(Allocation.bounded(25 * minute))
        dispatch(TimerCommand.Start("start"))
        clock.advance(25 * minute)

        val result = dispatch.tick()

        assertThat(result?.session?.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(result?.effects).contains(TimerEffect.EvaluateStopAtEnd)
    }

    @Test
    fun `a READY session that never started takes the newly chosen allocation (review LOW-21)`() = runTest {
        open(Allocation.bounded(25 * minute))

        val reopened = open(Allocation.bounded(10 * minute), TaskTitle.from("새 작업"))

        assertThat(reopened.state).isEqualTo(TimerState.READY)
        assertThat(reopened.allocation.currentTargetMs).isEqualTo(10 * minute)
        assertThat(reopened.taskTitle).isEqualTo("새 작업")
        assertThat(repository.stored).isEqualTo(reopened)
    }

    @Test
    fun `read-modify-write of a command happens under the repository lock (review MEDIUM-8)`() = runTest {
        open(Allocation.bounded(25 * minute))
        repository.requireLockForSave = true

        dispatch(TimerCommand.Start("start"))
        dispatch.tick()

        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }
}
