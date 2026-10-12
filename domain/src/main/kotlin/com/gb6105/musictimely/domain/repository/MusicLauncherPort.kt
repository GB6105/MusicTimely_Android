package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider

enum class LaunchOutcome { OPENED_LINK, OPENED_APP, OPENED_STORE, MANUAL }

/** Opens a saved music source (FR-A026). A successful open is not evidence of playback. */
interface MusicLauncherPort {
    fun open(source: ExternalSource): LaunchOutcome
    fun installedProviders(): List<Provider>
}
