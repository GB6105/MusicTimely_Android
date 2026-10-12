package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndPhase
import kotlinx.coroutines.flow.StateFlow

data class ControlUiState(val unresponsive: Boolean = false, val noticePending: Boolean = false)

/** The integer song count kept for the result after song memory is dropped (SRS Android §9.2). */
data class ObservedCount(val count: Int, val startedMidSession: Boolean)

/** Song counting, the user play/pause button and stop-at-end as seen by the UI layer. */
interface MusicControlPort {
    val snapshot: StateFlow<ObservationSnapshot>
    val stopAtEnd: StateFlow<StopAtEndPhase>
    val control: StateFlow<ControlUiState>

    /** Count of the last finished session, null when it was never observed. */
    val lastObserved: StateFlow<ObservedCount?>

    /**
     * The user's play/pause button — the only path that may send `play` (FR-A045).
     * Queued on the observation thread; when [control] says unresponsive the UI opens the app instead.
     */
    fun onTransportTap()

    fun consumeControlNotice()

    /** FR-A059 [멈추기]: pauses the observed app if it is playing; never sends play. */
    fun pauseObserved()

    /** The count of [sessionId] once its observation stop has been processed (null when never observed). */
    suspend fun awaitObserved(sessionId: String): ObservedCount?

    /** [지금 멈추기]. */
    fun stopNow()

    /** [계속 듣기]. */
    fun keepListening()

    fun onActiveTime()

    /** Re-reads the song counting switch (on: start mid-session, off: release at once). */
    fun onSettingsChanged()
}
