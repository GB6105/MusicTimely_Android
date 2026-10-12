package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint

/** Atomic local storage of the single active checkpoint. Excluded from OS backup. */
interface CheckpointStore {
    suspend fun read(): CheckpointRead

    /** @return false when the write failed (the previous checkpoint is kept). */
    suspend fun write(checkpoint: SessionCheckpoint): Boolean

    suspend fun clear()
}
