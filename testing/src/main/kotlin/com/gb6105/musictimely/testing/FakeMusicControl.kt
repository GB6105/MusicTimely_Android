package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndPhase
import com.gb6105.musictimely.domain.repository.ControlUiState
import com.gb6105.musictimely.domain.repository.MusicControlPort
import com.gb6105.musictimely.domain.repository.ObservedCount
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMusicControl : MusicControlPort {
    override val snapshot = MutableStateFlow(ObservationSnapshot())
    override val stopAtEnd = MutableStateFlow(StopAtEndPhase.IDLE)
    override val control = MutableStateFlow(ControlUiState())
    override val lastObserved = MutableStateFlow<ObservedCount?>(null)
    var taps = 0
        private set
    var stopNowCalls = 0
        private set
    var keepListeningCalls = 0
        private set
    var pauseObservedCalls = 0
        private set

    override fun onTransportTap() {
        taps++
    }
    override fun consumeControlNotice() {
        control.value = control.value.copy(noticePending = false)
    }
    override fun stopNow() {
        stopNowCalls++
    }
    override fun keepListening() {
        keepListeningCalls++
    }
    override fun onActiveTime() = Unit
    override fun pauseObserved() {
        pauseObservedCalls++
    }
    override suspend fun awaitObserved(sessionId: String) = lastObserved.value
    override fun onSettingsChanged() = Unit
}
