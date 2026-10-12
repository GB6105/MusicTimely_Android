package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.audio.NoiseKind
import kotlinx.coroutines.flow.StateFlow

data class NoiseState(
    val kind: NoiseKind? = null,
    val playing: Boolean = false,
    val unavailable: Boolean = false,
    /** Volume is 0: nothing plays, so no silent playback holds the service (SRS Android §7.6). */
    val muted: Boolean = false,
)

/** App-owned noise as seen by the UI. [play] must come from a user tap while the app is on screen (FR-A056). */
interface NoisePort {
    val state: StateFlow<NoiseState>
    /** [keepOtherMusic]: the user chose to keep another app playing (FR-A059 [그대로]). */
    fun play(kind: NoiseKind, keepOtherMusic: Boolean = false)
    fun stop()
}
