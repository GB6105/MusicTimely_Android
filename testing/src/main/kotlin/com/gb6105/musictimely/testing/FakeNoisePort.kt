package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.repository.NoisePort
import com.gb6105.musictimely.domain.repository.NoiseState
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNoisePort : NoisePort {
    override val state = MutableStateFlow(NoiseState())
    val played = mutableListOf<NoiseKind>()
    var stops = 0
        private set

    val keptOthers = mutableListOf<Boolean>()

    override fun play(kind: NoiseKind, keepOtherMusic: Boolean) {
        played += kind
        keptOthers += keepOtherMusic
        state.value = NoiseState(kind, playing = true)
    }

    override fun stop() {
        stops++
        state.value = state.value.copy(playing = false)
    }
}
