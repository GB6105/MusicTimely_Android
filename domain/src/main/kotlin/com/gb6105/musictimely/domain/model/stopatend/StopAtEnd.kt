package com.gb6105.musictimely.domain.model.stopatend

import com.gb6105.musictimely.domain.model.translation.ObservedItem

enum class StopAtEndPhase { DISABLED, IDLE, ARMED, STOPPING, STOPPED, CANCELLED, FAILED }

data class StopAtEndConditions(val settingOn: Boolean, val countingOn: Boolean, val infiniteMode: Boolean)

sealed interface StopAtEndEvent {
    data class LimitReached(
        val revision: Int,
        val conditions: StopAtEndConditions,
        val observing: Boolean,
        val playing: Boolean,
        val item: ObservedItem?,
        val nowMono: Long,
    ) : StopAtEndEvent
    data class TrackBoundary(val nowMono: Long) : StopAtEndEvent
    data class Tick(val nowMono: Long) : StopAtEndEvent
    data class StopNow(val nowMono: Long) : StopAtEndEvent
    data class PlaybackChanged(val isPlaying: Boolean, val nowMono: Long) : StopAtEndEvent
    data object KeepListening : StopAtEndEvent
    data object Extended : StopAtEndEvent
    data object Finished : StopAtEndEvent
    data object TargetLost : StopAtEndEvent
    data object CountingTurnedOff : StopAtEndEvent
}

sealed interface StopAtEndEffect {
    data object ShowWaiting : StopAtEndEffect
    data object SendPause : StopAtEndEffect
    data object ShowStopFailed : StopAtEndEffect
}

/**
 * Stop-at-end (SRS Android §5.3, DEC-A05). Only ever sends `pause`; never `play` (FR-A045).
 * Each target revision ends at most once in STOPPED, CANCELLED or FAILED.
 */
data class StopAtEnd(
    val phase: StopAtEndPhase,
    val revision: Int,
    private val capDeadlineMono: Long? = null,
    private val stoppingSinceMono: Long? = null,
) {
    data class Result(val next: StopAtEnd, val effects: List<StopAtEndEffect>)

    /** When the next [StopAtEndEvent.Tick] matters: the cap while ARMED, the 2 s confirm limit while STOPPING. */
    val nextDeadlineMono: Long?
        get() = when (phase) {
            StopAtEndPhase.ARMED -> capDeadlineMono
            StopAtEndPhase.STOPPING -> stoppingSinceMono?.plus(CONFIRM_MS)
            else -> null
        }

    fun on(event: StopAtEndEvent): Result = when (event) {
        is StopAtEndEvent.LimitReached -> onLimitReached(event)
        is StopAtEndEvent.TrackBoundary -> if (phase == StopAtEndPhase.ARMED) stop(event.nowMono) else same()
        is StopAtEndEvent.StopNow -> if (phase == StopAtEndPhase.ARMED) stop(event.nowMono) else same()
        is StopAtEndEvent.Tick -> onTick(event.nowMono)
        is StopAtEndEvent.PlaybackChanged -> when {
            event.isPlaying -> same()
            phase == StopAtEndPhase.ARMED -> cancel()
            phase == StopAtEndPhase.STOPPING -> Result(copy(phase = StopAtEndPhase.STOPPED), emptyList())
            else -> same()
        }
        StopAtEndEvent.KeepListening, StopAtEndEvent.Extended, StopAtEndEvent.Finished,
        StopAtEndEvent.TargetLost, StopAtEndEvent.CountingTurnedOff,
        -> if (phase == StopAtEndPhase.ARMED) cancel() else same()
    }

    private fun onLimitReached(e: StopAtEndEvent.LimitReached): Result {
        if (phase in FINAL && e.revision <= revision) return same()
        val c = e.conditions
        if (!c.settingOn || !c.countingOn || c.infiniteMode) {
            return Result(StopAtEnd(StopAtEndPhase.DISABLED, e.revision), emptyList())
        }
        if (!e.observing || !e.playing) return Result(StopAtEnd(StopAtEndPhase.IDLE, e.revision), emptyList())
        val waitMs = e.item?.let { (it.durationMs - it.positionMs).coerceAtLeast(0) + CAP_GRACE_MS } ?: CAP_UNKNOWN_MS
        return Result(
            StopAtEnd(StopAtEndPhase.ARMED, e.revision, capDeadlineMono = e.nowMono + waitMs),
            listOf(StopAtEndEffect.ShowWaiting),
        )
    }

    private fun onTick(now: Long): Result = when {
        phase == StopAtEndPhase.ARMED && capDeadlineMono != null && now >= capDeadlineMono -> stop(now)
        phase == StopAtEndPhase.STOPPING && stoppingSinceMono != null && now - stoppingSinceMono >= CONFIRM_MS ->
            Result(copy(phase = StopAtEndPhase.FAILED), listOf(StopAtEndEffect.ShowStopFailed))
        else -> same()
    }

    private fun stop(now: Long) =
        Result(copy(phase = StopAtEndPhase.STOPPING, stoppingSinceMono = now), listOf(StopAtEndEffect.SendPause))

    private fun cancel() = Result(copy(phase = StopAtEndPhase.CANCELLED), emptyList())

    private fun same() = Result(this, emptyList())

    companion object {
        private const val CAP_GRACE_MS = 15_000L
        private const val CAP_UNKNOWN_MS = 6 * 60_000L
        private const val CONFIRM_MS = 2_000L
        private val FINAL = setOf(StopAtEndPhase.STOPPED, StopAtEndPhase.CANCELLED, StopAtEndPhase.FAILED)

        fun initial() = StopAtEnd(StopAtEndPhase.IDLE, revision = -1)
    }
}
