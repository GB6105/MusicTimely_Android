package com.gb6105.musictimely.domain.usecase.session

import com.gb6105.musictimely.testing.FakeCheckpointStore
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakePlaylistRepository
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.RecoveryReason
import com.gb6105.musictimely.domain.model.session.RestoreDecision
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** FR-A012 복원, CORRUPT_CHECKPOINT (SRS v1 §8). */
class RestoreSessionUseCaseTest {

    private val minute = 60_000L
    private val clock = FakeClock(monotonicMs = 0, wallEpochMs = 1_000_000, boot = 2)
    private val store = FakeCheckpointStore()
    private val playlists = FakePlaylistRepository(clock)
    private val restore = RestoreSessionUseCase(store, clock, playlists)

    private fun running() = TimerStateMachine()
        .reduce(TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0).session

    @Test
    fun `a corrupt checkpoint is never executed and asks to recover or discard`() = runTest {
        store.read = CheckpointRead.Corrupt

        assertThat(restore()).isEqualTo(RestoreDecision.Corrupt)
    }

    @Test
    fun `a limit reached while away is persisted immediately`() = runTest {
        store.read = CheckpointRead.Loaded(SessionCheckpoint(running(), bootCount = 2, updatedAtEpochMs = 1_000_000))
        clock.advance(30 * minute)

        val decision = restore() as RestoreDecision.Restore

        assertThat(decision.session.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(store.written.last().session).isEqualTo(decision.session)
    }

    @Test
    fun `a recovery decision is persisted so a crash cannot auto-resume it`() = runTest {
        store.read = CheckpointRead.Loaded(SessionCheckpoint(running(), bootCount = 1, updatedAtEpochMs = 1_000_000))

        val decision = restore() as RestoreDecision.RecoveryRequired

        assertThat(decision.reason).isEqualTo(RecoveryReason.CLOCK_DISCONTINUITY)
        assertThat(store.written.last().session.state).isEqualTo(TimerState.RECOVERY_REQUIRED)
        assertThat(store.written.last().bootCount).isEqualTo(2)
    }

    @Test
    fun `discarding after corruption clears the store`() = runTest {
        store.read = CheckpointRead.Corrupt

        restore.discard()

        assertThat(store.cleared).isEqualTo(1)
        assertThat(restore()).isEqualTo(RestoreDecision.Fresh)
    }

    @Test
    fun `app start purges session playlists older than 24 hours (F18 M2, DEC-A18)`() = runTest {
        playlists.append("old", PlaylistEntry(1, "a", "A", Provider.MELON))
        clock.wallEpochMs += 24 * 60 * minute + 1
        playlists.append("new", PlaylistEntry(1, "b", "B", Provider.MELON))

        restore()

        assertThat(playlists.playlist("old")).isEmpty()
        assertThat(playlists.playlist("new")).hasSize(1)
    }

    @Test
    fun `a corrupt checkpoint still purges expired playlists`() = runTest {
        store.read = CheckpointRead.Corrupt
        playlists.append("old", PlaylistEntry(1, "a", "A", Provider.MELON))
        clock.wallEpochMs += 24 * 60 * minute + 1

        restore()

        assertThat(playlists.playlist("old")).isEmpty()
    }
}
