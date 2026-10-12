package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSourceRepository : SourceRepository {
    private val state = MutableStateFlow<List<ExternalSource>>(emptyList())
    override val sources: StateFlow<List<ExternalSource>> = state

    override suspend fun save(source: ExternalSource) {
        state.value = state.value.filterNot { it.id == source.id } + source
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}
