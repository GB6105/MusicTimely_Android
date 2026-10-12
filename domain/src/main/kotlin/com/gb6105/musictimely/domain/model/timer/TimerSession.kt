package com.gb6105.musictimely.domain.model.timer

enum class TimerState { READY, RUNNING, PAUSED, LIMIT_REACHED, ENDED, RECOVERY_REQUIRED }

enum class EndReason { COMPLETED, USER_ENDED, ABANDONED }

enum class ExtensionStep(val addedMs: Long) {
    FIVE(5 * 60_000L),
    TEN(10 * 60_000L),
    FIFTEEN(15 * 60_000L),
}

/**
 * Bounded when [currentTargetMs] is set; infinite mode keeps both durations null.
 * [durationPerUnitMs] and [loopDurationMs] are snapshotted at session creation (FR-A004, SRS v1 §6.4).
 */
data class Allocation(
    val initialDurationMs: Long?,
    val currentTargetMs: Long?,
    val targetRevision: Int,
    val durationPerUnitMs: Long = DEFAULT_UNIT_MS,
    val loopDurationMs: Long? = null,
    val singleLoop: Boolean = false,
) {
    val bounded: Boolean get() = currentTargetMs != null

    /** d for display: the loop length when set, otherwise the average song length. */
    val displayUnitMs: Long get() = loopDurationMs ?: durationPerUnitMs

    companion object {
        const val DEFAULT_UNIT_MS: Long = 240_000

        fun bounded(durationMs: Long, durationPerUnitMs: Long = DEFAULT_UNIT_MS, loopDurationMs: Long? = null, singleLoop: Boolean = false) =
            Allocation(durationMs, durationMs, targetRevision = 0, durationPerUnitMs, loopDurationMs, singleLoop)

        fun infinite(durationPerUnitMs: Long = DEFAULT_UNIT_MS, loopDurationMs: Long? = null, singleLoop: Boolean = false) =
            Allocation(null, null, targetRevision = 0, durationPerUnitMs, loopDurationMs, singleLoop)
    }
}

data class TimerSession(
    val sessionId: String,
    val state: TimerState,
    val allocation: Allocation,
    val accumulatedActiveMs: Long,
    val runningAnchorMonoMs: Long?,
    val extensions: List<Long>,
    val endReason: EndReason?,
    val appliedCommandIds: Set<String>,
    val taskTitle: String? = null,
) {
    /** SRS v1 §5.2: E = accumulated + max(0, now - anchor), clamped to the target when bounded. */
    fun elapsedMs(nowMono: Long): Long {
        val raw = accumulatedActiveMs + (runningAnchorMonoMs?.let { (nowMono - it).coerceAtLeast(0) } ?: 0)
        return allocation.currentTargetMs?.let { minOf(raw, it) } ?: raw
    }

    fun remainingMs(nowMono: Long): Long? =
        allocation.currentTargetMs?.let { (it - elapsedMs(nowMono)).coerceAtLeast(0) }

    /** True when a running bounded session has reached its target at [nowMono]. */
    fun hasReachedTarget(nowMono: Long): Boolean =
        allocation.currentTargetMs?.let { elapsedMs(nowMono) >= it } ?: false

    companion object {
        fun ready(sessionId: String, allocation: Allocation, taskTitle: String? = null) = TimerSession(
            sessionId = sessionId,
            state = TimerState.READY,
            allocation = allocation,
            accumulatedActiveMs = 0,
            runningAnchorMonoMs = null,
            extensions = emptyList(),
            endReason = null,
            appliedCommandIds = emptySet(),
            taskTitle = taskTitle,
        )
    }
}
