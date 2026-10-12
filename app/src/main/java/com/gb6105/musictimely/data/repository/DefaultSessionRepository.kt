package com.gb6105.musictimely.data.repository

import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.repository.CheckpointStore
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.SessionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Session persistence on top of the single checkpoint. Each save stamps the boot count and wall time
 * (FR-A012) and keeps the checkpoint's extension fields of the same session.
 * A failed write keeps the session in memory and raises CHECKPOINT_WRITE_FAILED once per session (SRS v1 §8).
 * One singleton [Mutex] serializes read-modify-write from the UI, the receiver and restore (review MEDIUM-8).
 */
class DefaultSessionRepository(
    private val store: CheckpointStore,
    private val clock: ClockPort,
) : SessionRepository {
    private val mutex = Mutex()

    @Volatile
    private var unsaved: TimerSession? = null
    @Volatile private var noticedSessionId: String? = null
    private var countBase: Pair<String, Int>? = null

    @Volatile private var failureNoticePending = false

    override suspend fun active(): TimerSession? =
        unsaved ?: (store.read() as? CheckpointRead.Loaded)?.checkpoint?.session

    override suspend fun save(session: TimerSession) {
        val previous = (store.read() as? CheckpointRead.Loaded)?.checkpoint
            ?.takeIf { it.session.sessionId == session.sessionId }
        val checkpoint = previous?.copy(session = session, bootCount = clock.bootCount(), updatedAtEpochMs = clock.wallNowEpochMs())
            ?: SessionCheckpoint(session, clock.bootCount(), clock.wallNowEpochMs())
        if (store.write(checkpoint)) {
            unsaved = null
        } else {
            unsaved = session
            if (noticedSessionId != session.sessionId) {
                noticedSessionId = session.sessionId
                failureNoticePending = true
            }
        }
    }

    override suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    /** True once after the first failed write: "이번 세션은 앱 종료 후 복원되지 않을 수 있어요". */
    override fun consumeWriteFailureNotice(): Boolean = failureNoticePending.also { failureNoticePending = false }

    override suspend fun recordObservation(sessionId: String, countedItems: Int, observedFromActiveMs: Long) = mutex.withLock {
        val checkpoint = (store.read() as? CheckpointRead.Loaded)?.checkpoint ?: return@withLock
        if (checkpoint.session.sessionId != sessionId) return@withLock
        // A count saved by a previous process is the base; this process adds its live count (review 2 M1).
        val base = countBase?.takeIf { it.first == sessionId }?.second
            ?: (checkpoint.countedItems ?: 0).also { countBase = sessionId to it }
        store.write(
            checkpoint.copy(
                countedItems = base + countedItems,
                observedFromActiveMs = checkpoint.observedFromActiveMs ?: observedFromActiveMs,
            ),
        )
        Unit
    }

    override suspend fun countedItems(sessionId: String): Int? =
        (store.read() as? CheckpointRead.Loaded)?.checkpoint?.takeIf { it.session.sessionId == sessionId }?.countedItems

    override suspend fun clear() {
        unsaved = null
        store.clear()
    }
}
