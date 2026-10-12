package com.gb6105.musictimely.domain.usecase.session

import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.repository.TimerEffectSink

/**
 * SRS v1 §7.2 reconciliation on app start/return: the stored state is authoritative; alarms and
 * notifications are re-derived from it (upsert by sessionId:targetRevision:kind), stale ones cancelled.
 * Observation restarts for live sessions; song memory lost with the process stays lost (mixed).
 */
class ReconcileSessionUseCase(
    private val repository: SessionRepository,
    private val clock: ClockPort,
    private val sink: TimerEffectSink,
) {
    suspend operator fun invoke() {
        val session = repository.active() ?: return
        val now = clock.monotonicNowMs()
        val effects = when (session.state) {
            TimerState.RUNNING -> listOfNotNull(
                session.remainingMs(now)?.let { TimerEffect.ScheduleEndAlarm(now + it, session.allocation.targetRevision) },
                TimerEffect.ShowProgress,
                TimerEffect.StartObservation,
            )
            TimerState.PAUSED, TimerState.LIMIT_REACHED ->
                listOf(TimerEffect.CancelEndAlarm, TimerEffect.ShowProgress, TimerEffect.StartObservation)
            TimerState.READY -> emptyList()
            TimerState.ENDED, TimerState.RECOVERY_REQUIRED -> listOf(TimerEffect.CancelEndAlarm, TimerEffect.ClearNotifications)
        }
        if (effects.isNotEmpty()) sink.apply(session, effects)
    }
}
