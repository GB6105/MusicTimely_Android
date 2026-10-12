package com.gb6105.musictimely.receiver

import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 알람·알림 동작 수신 (FR-A061 [일시정지/이어서] [마침], SRS Android §5.1 REACH_LIMIT (b)). */
class SessionActionHandlerTest {

    private val minute = 60_000L
    private val clock = FakeClock()
    private val repository = FakeSessionRepository()
    private val dispatch = DispatchTimerCommandUseCase(repository, clock, FakeTimerEffectSink())
    private val dismissal = com.gb6105.musictimely.data.notification.ProgressDismissal()
    private val handler = SessionActionHandler(dispatch, dismissal)

    private suspend fun running() {
        repository.save(TimerSession.ready("s", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start"))
    }

    @Test
    fun `the end alarm re-evaluates the clock and reaches the limit`() = runTest {
        running()
        clock.advance(25 * minute + 3_000)

        handler.handle(SessionActionReceiver.ACTION_END_ALARM)

        assertThat(repository.stored?.state).isEqualTo(TimerState.LIMIT_REACHED)
    }

    @Test
    fun `an early alarm does not end anything`() = runTest {
        running()
        clock.advance(10 * minute)

        handler.handle(SessionActionReceiver.ACTION_END_ALARM)

        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `notification actions pause, resume and finish the timer`() = runTest {
        running()

        handler.handle(SessionActionReceiver.ACTION_PAUSE)
        assertThat(repository.stored?.state).isEqualTo(TimerState.PAUSED)
        handler.handle(SessionActionReceiver.ACTION_RESUME)
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
        handler.handle(SessionActionReceiver.ACTION_FINISH)
        assertThat(repository.stored?.state).isEqualTo(TimerState.ENDED)
    }

    @Test
    fun `unknown actions are ignored`() = runTest {
        running()

        handler.handle("something.else")
        handler.handle(null)

        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `dismissing the progress notification keeps the session and remembers the dismissal (DEC-A15)`() = runTest {
        running()

        handler.handle(SessionActionReceiver.ACTION_PROGRESS_DISMISSED)

        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
        assertThat(dismissal.isDismissed).isTrue()
    }
}
