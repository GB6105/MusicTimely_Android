package com.gb6105.musictimely.domain.model.settings

import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.audio.NoiseSound
import com.gb6105.musictimely.domain.model.timer.Allocation

/** SRS Android §9.1. Song counting and stop-at-end are opt-in (MT-A1, MT-A12). */
data class MusicSettings(
    val selectedSourceId: String? = null,
    val durationPerUnitMs: Long = Allocation.DEFAULT_UNIT_MS,
    val songCountingEnabled: Boolean = false,
    val stopAtEndEnabled: Boolean = false,
    val permissionPromptShown: Boolean = false,
    val ytmNoticeShown: Boolean = false,
    val showSongs: Boolean = true,
    val notificationPromptShown: Boolean = false,
    /** Built-in noise chosen as the session sound (SCR-06); null when an external app or nothing is chosen. */
    val selectedNoise: NoiseKind? = null,
    /** "음악 없이" chosen explicitly (M-NONE). */
    val noMusic: Boolean = false,
    /** SCR-09 "다음 세션 표시 방식: 한 곡 반복" (SRS v1 §6.4). */
    val singleLoop: Boolean = false,
    /** Optional loop length; null uses the average song length. */
    val loopDurationMs: Long? = null,
    /** FR-A070 built-in sound volume and fades. */
    val sound: NoiseSound = NoiseSound(),
    /** DEC-A17 (Android only, off by default): observed music paused for 10 s also pauses the timer. */
    val pauseTimerWithMusic: Boolean = false,
) {
    /** FR-A047: disabled with a reason while song counting is off. */
    val stopAtEndUsable: Boolean get() = songCountingEnabled && stopAtEndEnabled
}
