package com.gb6105.musictimely.domain.usecase.session

import com.gb6105.musictimely.domain.model.playlist.SessionPlaylist
import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.RestoreDecision
import com.gb6105.musictimely.domain.model.session.RestorePolicy
import com.gb6105.musictimely.domain.repository.CheckpointStore
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.PlaylistRepository

/** Process-start restore (also purges expired session playlists). Any state the policy changed is persisted at once so a second crash cannot undo it. */
class RestoreSessionUseCase(
    private val store: CheckpointStore,
    private val clock: ClockPort,
    private val playlists: PlaylistRepository,
) {
    suspend operator fun invoke(): RestoreDecision {
        // DEC-A18: song lists live at most as long as an unconfirmed result.
        playlists.purgeOlderThan(clock.wallNowEpochMs() - SessionPlaylist.TTL_MS)
        val checkpoint = when (val read = store.read()) {
            CheckpointRead.Empty -> null
            CheckpointRead.Corrupt -> return RestoreDecision.Corrupt
            is CheckpointRead.Loaded -> read.checkpoint
        }
        val decision = RestorePolicy.decide(checkpoint, clock.bootCount(), clock.monotonicNowMs(), clock.wallNowEpochMs())
        val restored = when (decision) {
            is RestoreDecision.Restore -> decision.session
            is RestoreDecision.RecoveryRequired -> decision.session
            else -> null
        }
        if (checkpoint != null && restored != null && restored != checkpoint.session) {
            store.write(checkpoint.copy(session = restored, bootCount = clock.bootCount(), updatedAtEpochMs = clock.wallNowEpochMs()))
        }
        return decision
    }

    suspend fun discard() = store.clear()
}
