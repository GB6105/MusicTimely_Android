package com.gb6105.musictimely.ui.session

import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndPhase
import com.gb6105.musictimely.domain.repository.ControlUiState
import com.gb6105.musictimely.domain.repository.NoiseState

enum class MusicButton { PAUSE, PLAY, OPEN }

/**
 * Music panel states (SRS Android §3.1). Observed titles appear only while OBSERVING (FR-A018) and are
 * hidden when song display is off, while the button stays (FR-A020).
 */
sealed interface MusicPanelState {
    /** "타이머만 멈췄어요. 음악은 음악 앱에서 조절해요" only while song counting does not observe. */
    val showsD0PauseCopy: Boolean get() = this is Empty || this is ExternalOff

    /** M-EMPTY. */
    data object Empty : MusicPanelState

    /** M-EXT-OFF: "〈앱〉 · 평균 4분 기준" + "곡 세기 켜기". */
    data class ExternalOff(val label: String) : MusicPanelState

    /** M-EXT-WAIT. */
    data class ExternalWaiting(val label: String) : MusicPanelState

    /** M-NOISE: the app's own [재생]/[정지]. */
    data class Noise(val kind: NoiseKind, val playing: Boolean, val unavailable: Boolean = false, val muted: Boolean = false) : MusicPanelState

    /** M-NONE. */
    data object None : MusicPanelState

    /** M-PLAYING, M-PAUSED, M-HANDOFF. */
    data class Observed(val title: String?, val playing: Boolean, val handoffProvider: Provider?, val button: MusicButton) : MusicPanelState

    companion object {
        fun from(
            settings: MusicSettings,
            selected: ExternalSource?,
            snapshot: ObservationSnapshot,
            control: ControlUiState,
            noise: NoiseState,
        ): MusicPanelState {
            // The user's chosen source decides the panel; observation only enriches an external choice.
            settings.selectedNoise?.let { kind ->
                val current = noise.kind == kind
                return Noise(kind, playing = noise.playing && current, unavailable = noise.unavailable && current, muted = noise.muted && current)
            }
            if (settings.noMusic) return None
            if (snapshot.state == ObservationState.OBSERVING) {
                return Observed(
                    title = snapshot.title.takeIf { settings.showSongs },
                    playing = snapshot.isPlaying,
                    handoffProvider = snapshot.provider.takeIf { snapshot.handedOff },
                    button = when {
                        control.unresponsive -> MusicButton.OPEN
                        snapshot.isPlaying -> MusicButton.PAUSE
                        else -> MusicButton.PLAY
                    },
                )
            }
            val source = selected ?: return Empty
            return if (settings.songCountingEnabled) ExternalWaiting(source.label) else ExternalOff(source.label)
        }
    }
}

enum class StopAtEndNotice {
    /** "지금 곡이 끝나면 음악도 멈춰요" [지금 멈추기] [계속 듣기]. */
    WAITING,

    /** "음악을 멈추지 못했어요. 음악 앱에서 멈춰 주세요". */
    FAILED,
    ;

    companion object {
        fun of(phase: StopAtEndPhase): StopAtEndNotice? = when (phase) {
            StopAtEndPhase.ARMED -> WAITING
            StopAtEndPhase.FAILED -> FAILED
            else -> null
        }
    }
}
