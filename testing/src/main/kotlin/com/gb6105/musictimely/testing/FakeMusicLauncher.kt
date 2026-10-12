package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.LaunchOutcome
import com.gb6105.musictimely.domain.repository.MusicLauncherPort

class FakeMusicLauncher(var outcome: LaunchOutcome = LaunchOutcome.OPENED_LINK) : MusicLauncherPort {
    val opened = mutableListOf<ExternalSource>()
    var installed: List<Provider> = emptyList()

    override fun open(source: ExternalSource): LaunchOutcome = outcome.also { opened += source }
    override fun installedProviders(): List<Provider> = installed
}
