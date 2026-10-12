package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.repository.SessionRepository

class FakeSessionRepository(var stored: TimerSession? = null) : SessionRepository {
    var saveCount = 0
        private set
    var writeFailureNotice = false
    private var locked = false

    /** Set to true to assert every save happens inside [withLock]. */
    var requireLockForSave = false

    override suspend fun active(): TimerSession? = stored

    override suspend fun save(session: TimerSession) {
        check(!requireLockForSave || locked) { "save outside withLock" }
        stored = session
        saveCount++
    }

    override suspend fun <T> withLock(block: suspend () -> T): T {
        locked = true
        try {
            return block()
        } finally {
            locked = false
        }
    }

    override fun consumeWriteFailureNotice(): Boolean = writeFailureNotice.also { writeFailureNotice = false }

    var requireLockForClear = false

    override suspend fun clear() {
        check(!requireLockForClear || locked) { "clear outside withLock" }
        stored = null
    }

    val recordedObservations = mutableListOf<Int>()

    override suspend fun recordObservation(sessionId: String, countedItems: Int, observedFromActiveMs: Long) {
        recordedObservations += countedItems
    }

    /** Saved counts per session, as the checkpoint would hold them. */
    val storedCounts = mutableMapOf<String, Int>()

    override suspend fun countedItems(sessionId: String): Int? = storedCounts[sessionId]
}
