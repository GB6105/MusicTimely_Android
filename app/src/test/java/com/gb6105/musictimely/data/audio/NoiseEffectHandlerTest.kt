package com.gb6105.musictimely.data.audio

import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 타이머 → 내장 소리 (SRS v1 §5.1 부수효과): 일시정지 덕킹, 재개 복원, 도달·종료 페이드 아웃. 재생 시작은 하지 않음. */
class NoiseEffectHandlerTest {

    private class Recorder : NoiseTimerHooks {
        val calls = mutableListOf<String>()
        override fun onTimerPaused() { calls += "paused" }
        override fun onTimerResumed() { calls += "resumed" }
        override fun onTimerEnded() { calls += "ended" }
    }

    private val machine = TimerStateMachine()
    private val hooks = Recorder()
    private val handler = NoiseEffectHandler(hooks) { it() }
    private var session = TimerSession.ready("s", Allocation.bounded(60_000))

    private suspend fun apply(command: TimerCommand, now: Long) {
        val t = machine.reduce(session, command, now)
        session = t.session
        t.effects.filter(handler::handles).forEach { handler.handle(session, it) }
    }

    @Test
    fun `pause ducks, resume restores, limit and finish fade out, start does nothing`() = runTest {
        apply(TimerCommand.Start("a"), 0)
        apply(TimerCommand.Pause("b"), 10_000)
        apply(TimerCommand.Resume("c"), 20_000)
        apply(TimerCommand.ReachLimit("d"), 70_000)
        apply(TimerCommand.Finish("e"), 71_000)

        assertThat(hooks.calls).containsExactly("resumed", "paused", "resumed", "ended", "ended").inOrder()
    }

    @Test
    fun `handles only progress, limit and clear effects`() {
        assertThat(handler.handles(TimerEffect.ShowProgress)).isTrue()
        assertThat(handler.handles(TimerEffect.ShowLimitReached)).isTrue()
        assertThat(handler.handles(TimerEffect.ClearNotifications)).isTrue()
        assertThat(handler.handles(TimerEffect.StartObservation)).isFalse()
    }

    @Test
    fun `hooks run on the main runner, not on the caller's thread (review 2 M5)`() = runTest {
        val queued = mutableListOf<() -> Unit>()
        val mainHandler = NoiseEffectHandler(hooks) { queued += it }

        mainHandler.handle(session.copy(state = com.gb6105.musictimely.domain.model.timer.TimerState.PAUSED), TimerEffect.ShowProgress)
        assertThat(hooks.calls).isEmpty()

        queued.forEach { it() }
        assertThat(hooks.calls).containsExactly("paused")
    }
}
