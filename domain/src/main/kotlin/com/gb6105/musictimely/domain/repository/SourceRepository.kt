package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.source.ExternalSource
import kotlinx.coroutines.flow.StateFlow

/** Saved music sources: device settings only, never sent or treated as listening history (SRS Android §9.2). */
interface SourceRepository {
    val sources: StateFlow<List<ExternalSource>>

    suspend fun save(source: ExternalSource)

    suspend fun delete(id: String)

    suspend fun clear()
}
