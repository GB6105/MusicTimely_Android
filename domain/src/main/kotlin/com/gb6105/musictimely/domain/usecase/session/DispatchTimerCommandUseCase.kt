package com.gb6105.musictimely.domain.usecase.session

import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.gb6105.musictimely.domain.model.timer.Transition
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.repository.TimerEffectSink

/** Applies a timer command at the monotonic now, persists real transitions, then hands effects to the sink. */
class DispatchTimerCommandUseCase(
    private val repository: SessionRepository,
    private val clock: ClockPort,
    private val effects: TimerEffectSink,
    private val machine: TimerStateMachine = TimerStateMachine(),
) {
    suspend operator fun invoke(command: TimerCommand): Transition? =
        apply { session, now -> machine.reduce(session, command, now) }

    suspend fun tick(): Transition? = apply { session, now -> machine.tick(session, now) }

    private suspend fun apply(step: (TimerSession, Long) -> Transition): Transition? {
        val transition = repository.withLock {
            val session = repository.active() ?: return@withLock null
            step(session, clock.monotonicNowMs()).also { if (it.session != session) repository.save(it.session) }
        } ?: return null
        if (transition.effects.isNotEmpty()) effects.apply(transition.session, transition.effects)
        return transition
    }
}
