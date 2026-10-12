package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.timer.TimerSession

interface SessionRepository {
    /** The most recent session, including an ENDED one until a new session replaces it. */
    suspend fun active(): TimerSession?

    suspend fun save(session: TimerSession)

    /** Serializes read-modify-write across UI, receiver and restore paths. */
    suspend fun <T> withLock(block: suspend () -> T): T

    /** True once after the first failed checkpoint write (CHECKPOINT_WRITE_FAILED). */
    fun consumeWriteFailureNotice(): Boolean

    /** Drops the session and any unsaved copy (data deletion). */
    suspend fun clear()

    /** Keeps only the integer song count for a restore after process death (SRS Android §9.2). */
    suspend fun recordObservation(sessionId: String, countedItems: Int, observedFromActiveMs: Long)

    /** The song count saved for [sessionId] (counts of earlier processes included), null when none. */
    suspend fun countedItems(sessionId: String): Int?
}
