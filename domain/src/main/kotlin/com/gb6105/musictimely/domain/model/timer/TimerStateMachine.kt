package com.gb6105.musictimely.domain.model.timer

sealed interface TimerCommand {
    val commandId: String

    data class Start(override val commandId: String) : TimerCommand
    data class Pause(override val commandId: String) : TimerCommand
    data class Resume(override val commandId: String) : TimerCommand
    data class ReachLimit(override val commandId: String) : TimerCommand
    data class Extend(override val commandId: String, val step: ExtensionStep) : TimerCommand
    data class Finish(override val commandId: String) : TimerCommand
    data class InvalidateRecovery(override val commandId: String) : TimerCommand
    data class Abandon(override val commandId: String) : TimerCommand
    data class ResumeFromRecovery(override val commandId: String) : TimerCommand
}

/** Side effects the data layer performs after a transition. No music transport effect exists by design (FR-A008). */
sealed interface TimerEffect {
    data class ScheduleEndAlarm(val atMonoMs: Long, val targetRevision: Int) : TimerEffect
    data object CancelEndAlarm : TimerEffect
    data object ShowProgress : TimerEffect
    data object ShowLimitReached : TimerEffect
    data object StartObservation : TimerEffect
    data object StopObservation : TimerEffect
    data object EvaluateStopAtEnd : TimerEffect
    data object CancelStopAtEnd : TimerEffect
    data object ClearNotifications : TimerEffect
    data object DeliverResult : TimerEffect
    /** DEC-A16 (Android only): the user paused the timer; pause the observed music if it is playing. */
    data object PauseLinkedMusic : TimerEffect
    /** DEC-A16: the user resumed; play again only music this app paused. */
    data object ResumeLinkedMusic : TimerEffect
}

data class Transition(val session: TimerSession, val effects: List<TimerEffect>)

/**
 * SRS v1 §5.1 transition table with the Android side effects of SRS Android §5.1.
 * Commands that do not apply to the current state are ignored (no state change, no effects).
 */
class TimerStateMachine {

    fun reduce(session: TimerSession, command: TimerCommand, nowMono: Long): Transition {
        if (command.commandId in session.appliedCommandIds) return session.unchanged()
        val next = when (command) {
            is TimerCommand.Start -> start(session, nowMono)
            is TimerCommand.Pause -> pause(session, nowMono)
            is TimerCommand.Resume -> resume(session, nowMono)
            is TimerCommand.ReachLimit -> reachLimit(session, nowMono)
            is TimerCommand.Extend -> extend(session, command.step, nowMono)
            is TimerCommand.Finish -> finish(session, nowMono)
            is TimerCommand.InvalidateRecovery -> invalidate(session, nowMono)
            is TimerCommand.Abandon -> abandon(session)
            is TimerCommand.ResumeFromRecovery -> resumeFromRecovery(session)
        } ?: return session.unchanged()
        return next.copy(session = next.session.copy(appliedCommandIds = session.appliedCommandIds + command.commandId))
    }

    /** Clock re-evaluation from a UI tick, the end alarm or an observation callback (SRS Android §5.1). */
    fun tick(session: TimerSession, nowMono: Long): Transition =
        reachLimit(session, nowMono) ?: session.unchanged()

    private fun start(s: TimerSession, now: Long): Transition? {
        if (s.state != TimerState.READY) return null
        val running = s.copy(state = TimerState.RUNNING, runningAnchorMonoMs = now)
        return Transition(running, listOfNotNull(running.endAlarm(now), TimerEffect.ShowProgress, TimerEffect.StartObservation))
    }

    private fun pause(s: TimerSession, now: Long): Transition? {
        if (s.state != TimerState.RUNNING) return null
        reachLimit(s, now)?.let { return it }
        val paused = s.copy(state = TimerState.PAUSED, accumulatedActiveMs = s.elapsedMs(now), runningAnchorMonoMs = null)
        // DEC-A16 (Android): the user's pause also pauses the observed music it is playing.
        return Transition(paused, listOf(TimerEffect.CancelEndAlarm, TimerEffect.ShowProgress, TimerEffect.PauseLinkedMusic))
    }

    private fun resume(s: TimerSession, now: Long): Transition? {
        if (s.state != TimerState.PAUSED) return null
        val running = s.copy(state = TimerState.RUNNING, runningAnchorMonoMs = now)
        return Transition(running, listOfNotNull(running.endAlarm(now), TimerEffect.ShowProgress, TimerEffect.ResumeLinkedMusic))
    }

    private fun reachLimit(s: TimerSession, now: Long): Transition? {
        if (s.state != TimerState.RUNNING || !s.hasReachedTarget(now)) return null
        val limited = s.copy(state = TimerState.LIMIT_REACHED, accumulatedActiveMs = s.elapsedMs(now), runningAnchorMonoMs = null)
        return Transition(limited, listOf(TimerEffect.CancelEndAlarm, TimerEffect.ShowLimitReached, TimerEffect.EvaluateStopAtEnd))
    }

    private fun extend(s: TimerSession, step: ExtensionStep, now: Long): Transition? {
        if (s.state != TimerState.LIMIT_REACHED) return null
        val target = s.allocation.currentTargetMs ?: return null
        val extended = s.copy(
            state = TimerState.RUNNING,
            allocation = s.allocation.copy(
                currentTargetMs = target + step.addedMs,
                targetRevision = s.allocation.targetRevision + 1,
            ),
            runningAnchorMonoMs = now,
            extensions = s.extensions + step.addedMs,
        )
        return Transition(extended, listOfNotNull(TimerEffect.CancelStopAtEnd, extended.endAlarm(now), TimerEffect.ShowProgress))
    }

    private fun finish(s: TimerSession, now: Long): Transition? {
        if (s.state !in LIVE_STATES && s.state != TimerState.RECOVERY_REQUIRED) return null
        val elapsed = s.elapsedMs(now)
        val initial = s.allocation.initialDurationMs
        val reason = if (initial != null && elapsed >= initial) EndReason.COMPLETED else EndReason.USER_ENDED
        val ended = s.copy(state = TimerState.ENDED, accumulatedActiveMs = elapsed, runningAnchorMonoMs = null, endReason = reason)
        return Transition(
            ended,
            listOf(
                TimerEffect.CancelEndAlarm,
                TimerEffect.CancelStopAtEnd,
                TimerEffect.StopObservation,
                TimerEffect.ClearNotifications,
                TimerEffect.DeliverResult,
            ),
        )
    }

    private fun invalidate(s: TimerSession, now: Long): Transition? {
        if (s.state !in LIVE_STATES && s.state != TimerState.READY) return null
        val recovery = s.copy(state = TimerState.RECOVERY_REQUIRED, accumulatedActiveMs = s.elapsedMs(now), runningAnchorMonoMs = null)
        return Transition(recovery, listOf(TimerEffect.CancelEndAlarm, TimerEffect.StopObservation))
    }

    /** "저장된 지점에서 이어가기": back to PAUSED at the last confirmed time; nothing starts automatically. */
    private fun resumeFromRecovery(s: TimerSession): Transition? {
        if (s.state != TimerState.RECOVERY_REQUIRED) return null
        return Transition(s.copy(state = TimerState.PAUSED, runningAnchorMonoMs = null), listOf(TimerEffect.ShowProgress))
    }

    private fun abandon(s: TimerSession): Transition? {
        if (s.state != TimerState.RECOVERY_REQUIRED) return null
        return Transition(s.copy(state = TimerState.ENDED, endReason = EndReason.ABANDONED), emptyList())
    }

    private fun TimerSession.endAlarm(now: Long): TimerEffect.ScheduleEndAlarm? =
        remainingMs(now)?.let { TimerEffect.ScheduleEndAlarm(atMonoMs = now + it, targetRevision = allocation.targetRevision) }

    private fun TimerSession.unchanged() = Transition(this, emptyList())

    private companion object {
        val LIVE_STATES = setOf(TimerState.RUNNING, TimerState.PAUSED, TimerState.LIMIT_REACHED)
    }
}
