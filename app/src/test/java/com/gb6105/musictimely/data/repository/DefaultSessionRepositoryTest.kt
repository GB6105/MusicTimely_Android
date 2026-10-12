package com.gb6105.musictimely.data.repository

import com.gb6105.musictimely.testing.FakeCheckpointStore
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 체크포인트 기반 SessionRepository: 부팅 수·저장 시각 기록, 쓰기 실패 노출 (CHECKPOINT_WRITE_FAILED). */
class DefaultSessionRepositoryTest {

    private val clock = FakeClock(monotonicMs = 0, wallEpochMs = 5_000, boot = 7)
    private val store = FakeCheckpointStore()
    private val repository = DefaultSessionRepository(store, clock)
    private val session = TimerSession.ready("s", Allocation.bounded(60_000))

    @Test
    fun `save stamps the boot count and wall time`() = runTest {
        repository.save(session)

        assertThat(store.written.single()).isEqualTo(SessionCheckpoint(session, bootCount = 7, updatedAtEpochMs = 5_000))
    }

    @Test
    fun `save keeps extension fields already in the checkpoint`() = runTest {
        store.read = CheckpointRead.Loaded(SessionCheckpoint(session, 7, 1, lastCueRevision = 0, countedItems = 2))

        repository.save(session.copy(accumulatedActiveMs = 10))

        assertThat(store.written.single().lastCueRevision).isEqualTo(0)
        assertThat(store.written.single().countedItems).isEqualTo(2)
    }

    @Test
    fun `active reads the stored session, corrupt reads as none`() = runTest {
        repository.save(session)
        assertThat(repository.active()).isEqualTo(session)

        store.read = CheckpointRead.Corrupt
        assertThat(repository.active()).isNull()
    }

    @Test
    fun `a failed write keeps the session in memory and reports the failure once`() = runTest {
        store.failWrites = true

        repository.save(session)
        repository.save(session.copy(accumulatedActiveMs = 1))

        assertThat(repository.active()).isEqualTo(session.copy(accumulatedActiveMs = 1))
        assertThat(repository.consumeWriteFailureNotice()).isTrue()
        assertThat(repository.consumeWriteFailureNotice()).isFalse()
    }

    @Test
    fun `records the integer song count of the current session only (SRS Android 9_2, review MEDIUM-12)`() = runTest {
        repository.save(session)

        repository.recordObservation(sessionId = "s", countedItems = 3, observedFromActiveMs = 0)
        repository.recordObservation(sessionId = "other", countedItems = 9, observedFromActiveMs = 0)

        val checkpoint = (store.read as CheckpointRead.Loaded).checkpoint
        assertThat(checkpoint.countedItems).isEqualTo(3)
        assertThat(checkpoint.observedFromActiveMs).isEqualTo(0)
    }

    @Test
    fun `a write failure is noticed once per session, not once per process (review 2 M6)`() = runTest {
        store.failWrites = true
        repository.save(session)
        assertThat(repository.consumeWriteFailureNotice()).isTrue()

        repository.save(TimerSession.ready("s2", Allocation.bounded(60_000)))

        assertThat(repository.consumeWriteFailureNotice()).isTrue()
    }

    @Test
    fun `counts after a restore add to the count saved before the process died (review 2 M1)`() = runTest {
        store.read = CheckpointRead.Loaded(SessionCheckpoint(session, 7, 1, countedItems = 3, observedFromActiveMs = 0))

        repository.recordObservation("s", countedItems = 0, observedFromActiveMs = 60_000)
        assertThat((store.read as CheckpointRead.Loaded).checkpoint.countedItems).isEqualTo(3)

        repository.recordObservation("s", countedItems = 2, observedFromActiveMs = 60_000)
        assertThat((store.read as CheckpointRead.Loaded).checkpoint.countedItems).isEqualTo(5)
    }

    @Test
    fun `the stored song count of the current session is readable for the running screen (emulator finding)`() = runTest {
        store.read = CheckpointRead.Loaded(SessionCheckpoint(session, 7, 1, countedItems = 9, observedFromActiveMs = 0))

        assertThat(repository.countedItems("s")).isEqualTo(9)
        assertThat(repository.countedItems("other")).isNull()
    }
}
