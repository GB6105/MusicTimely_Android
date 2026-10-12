package com.gb6105.musictimely.domain.model.session

import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine

/**
 * Persisted session state (SRS v1 SessionCheckpoint + SRS Android AndroidCheckpointExt).
 * Never contains song titles, artists or signatures — only the integer count (§9.2).
 */
data class SessionCheckpoint(
    val session: TimerSession,
    val bootCount: Int,
    val updatedAtEpochMs: Long,
    val countedItems: Int? = null,
    val observedFromActiveMs: Long? = null,
    val lastCueRevision: Int? = null,
    val stopAtEndSnapshot: Boolean = false,
    val stopAtEndOutcome: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

enum class RecoveryReason { CLOCK_DISCONTINUITY, EXPIRED }

sealed interface CheckpointRead {
    data object Empty : CheckpointRead
    data class Loaded(val checkpoint: SessionCheckpoint) : CheckpointRead

    /** Unreadable or unknown schema: never executed (CORRUPT_CHECKPOINT). */
    data object Corrupt : CheckpointRead
}

sealed interface RestoreDecision {
    data object Fresh : RestoreDecision
    data object Corrupt : RestoreDecision
    data class ShowResult(val session: TimerSession, val countedItems: Int?) : RestoreDecision
    data class Restore(
        val session: TimerSession,
        val reachedLimitWhileAway: Boolean,
        val countedItems: Int?,
        val observationInterrupted: Boolean,
    ) : RestoreDecision
    data class RecoveryRequired(val session: TimerSession, val reason: RecoveryReason) : RestoreDecision
}

/**
 * Process-start restore (FR-A012). Same boot → trust the monotonic clock and re-evaluate the limit;
 * another boot or a checkpoint older than 7 days → recovery choice, never an automatic restore.
 * Song memory is gone after a process death, so a live observed session restores as interrupted (mixed).
 */
object RestorePolicy {
    const val MAX_AGE_MS: Long = 7L * 86_400_000
    const val RESULT_TTL_MS: Long = 86_400_000

    fun decide(checkpoint: SessionCheckpoint?, bootCount: Int, nowMono: Long, nowEpochMs: Long): RestoreDecision {
        val session = checkpoint?.session ?: return RestoreDecision.Fresh
        if (session.state == TimerState.ENDED) {
            // A finished session's result survives a relaunch for at most 24 hours (SRS v1 §9.2).
            val fresh = nowEpochMs - checkpoint.updatedAtEpochMs <= RESULT_TTL_MS
            return if (fresh) RestoreDecision.ShowResult(session, checkpoint.countedItems) else RestoreDecision.Fresh
        }
        if (session.state == TimerState.RECOVERY_REQUIRED) return RestoreDecision.RecoveryRequired(session, RecoveryReason.CLOCK_DISCONTINUITY)
        if (nowEpochMs - checkpoint.updatedAtEpochMs > MAX_AGE_MS) return recovery(session, RecoveryReason.EXPIRED)
        if (checkpoint.bootCount != bootCount) return recovery(session, RecoveryReason.CLOCK_DISCONTINUITY)
        val ticked = TimerStateMachine().tick(session, nowMono).session
        return RestoreDecision.Restore(
            session = ticked,
            reachedLimitWhileAway = ticked.state == TimerState.LIMIT_REACHED && session.state != TimerState.LIMIT_REACHED,
            countedItems = checkpoint.countedItems,
            observationInterrupted = checkpoint.countedItems != null,
        )
    }

    /** Keeps the last confirmed active time; the monotonic anchor of another boot is meaningless. */
    private fun recovery(session: TimerSession, reason: RecoveryReason) = RestoreDecision.RecoveryRequired(
        session.copy(state = TimerState.RECOVERY_REQUIRED, runningAnchorMonoMs = null),
        reason,
    )
}
