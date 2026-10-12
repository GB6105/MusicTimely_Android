package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.settings.MusicSettings
import kotlinx.coroutines.flow.StateFlow

/** Versioned key-value settings (FR-A070). Kept until the user deletes data. */
interface SettingsRepository {
    val settings: StateFlow<MusicSettings>

    suspend fun update(transform: (MusicSettings) -> MusicSettings)

    suspend fun clear()
}
