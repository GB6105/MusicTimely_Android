package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.repository.CheckpointStore

class FakeCheckpointStore(var read: CheckpointRead = CheckpointRead.Empty) : CheckpointStore {
    var failWrites = false
    val written = mutableListOf<SessionCheckpoint>()
    var cleared = 0
        private set

    override suspend fun read(): CheckpointRead = read

    override suspend fun write(checkpoint: SessionCheckpoint): Boolean {
        if (failWrites) return false
        written += checkpoint
        read = CheckpointRead.Loaded(checkpoint)
        return true
    }

    override suspend fun clear() {
        cleared++
        read = CheckpointRead.Empty
    }
}
