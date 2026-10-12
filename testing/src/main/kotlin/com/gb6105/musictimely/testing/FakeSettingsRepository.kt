package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSettingsRepository(initial: MusicSettings = MusicSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<MusicSettings> = state

    override suspend fun update(transform: (MusicSettings) -> MusicSettings) {
        state.value = transform(state.value)
    }

    override suspend fun clear() {
        state.value = MusicSettings()
    }
}
