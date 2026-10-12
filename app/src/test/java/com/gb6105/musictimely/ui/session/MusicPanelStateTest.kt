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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 음악 패널 상태 (SRS Android §3.1). FR-A018·A020·A043, DEC-A07·A10. */
class MusicPanelStateTest {

    private val source = ExternalSource("a", Provider.YOUTUBE_MUSIC, "ytm", null, "유튜브뮤직", 0)
    private val countingOn = MusicSettings(songCountingEnabled = true, selectedSourceId = "a")
    private val observingPlaying = ObservationSnapshot(
        state = ObservationState.OBSERVING, provider = Provider.YOUTUBE_MUSIC, title = "Slow Tide", isPlaying = true, countedItems = 3,
    )

    private fun panel(
        settings: MusicSettings = countingOn,
        selected: ExternalSource? = source,
        snapshot: ObservationSnapshot = ObservationSnapshot(),
        control: ControlUiState = ControlUiState(),
    ) = MusicPanelState.from(settings, selected, snapshot, control, NoiseState())

    @Test
    fun `no saved source asks to pick a music app (M-EMPTY)`() {
        assertThat(panel(selected = null)).isEqualTo(MusicPanelState.Empty)
    }

    @Test
    fun `counting off shows the source with the average basis and a counting link (M-EXT-OFF)`() {
        assertThat(panel(settings = countingOn.copy(songCountingEnabled = false)))
            .isEqualTo(MusicPanelState.ExternalOff(label = "유튜브뮤직"))
    }

    @Test
    fun `counting on without a target shows the source and open (M-EXT-WAIT)`() {
        assertThat(panel(snapshot = ObservationSnapshot(state = ObservationState.SEARCHING)))
            .isEqualTo(MusicPanelState.ExternalWaiting(label = "유튜브뮤직"))
        assertThat(panel(snapshot = ObservationSnapshot(state = ObservationState.DEGRADED)))
            .isEqualTo(MusicPanelState.ExternalWaiting(label = "유튜브뮤직"))
    }

    @Test
    fun `observing playing shows the title with a pause button (M-PLAYING)`() {
        assertThat(panel(snapshot = observingPlaying))
            .isEqualTo(MusicPanelState.Observed(title = "Slow Tide", playing = true, handoffProvider = null, button = MusicButton.PAUSE))
    }

    @Test
    fun `observing paused shows the title with a play button (M-PAUSED)`() {
        assertThat(panel(snapshot = observingPlaying.copy(isPlaying = false)))
            .isEqualTo(MusicPanelState.Observed(title = "Slow Tide", playing = false, handoffProvider = null, button = MusicButton.PLAY))
    }

    @Test
    fun `a handoff names the other app (M-HANDOFF)`() {
        val state = panel(snapshot = observingPlaying.copy(provider = Provider.MELON, handedOff = true)) as MusicPanelState.Observed

        assertThat(state.handoffProvider).isEqualTo(Provider.MELON)
    }

    @Test
    fun `an unresponsive app turns the button into open (DEC-A07)`() {
        val state = panel(snapshot = observingPlaying, control = ControlUiState(unresponsive = true)) as MusicPanelState.Observed

        assertThat(state.button).isEqualTo(MusicButton.OPEN)
    }

    @Test
    fun `song display off hides the title but keeps the button (FR-A020)`() {
        val state = panel(settings = countingOn.copy(showSongs = false), snapshot = observingPlaying) as MusicPanelState.Observed

        assertThat(state.title).isNull()
        assertThat(state.button).isEqualTo(MusicButton.PAUSE)
    }

    @Test
    fun `stop-at-end notices follow the phase (SCR-04, FR-A053)`() {
        assertThat(StopAtEndNotice.of(StopAtEndPhase.ARMED)).isEqualTo(StopAtEndNotice.WAITING)
        assertThat(StopAtEndNotice.of(StopAtEndPhase.FAILED)).isEqualTo(StopAtEndNotice.FAILED)
        listOf(StopAtEndPhase.IDLE, StopAtEndPhase.DISABLED, StopAtEndPhase.STOPPING, StopAtEndPhase.STOPPED, StopAtEndPhase.CANCELLED)
            .forEach { assertThat(StopAtEndNotice.of(it)).isNull() }
    }

    @Test
    fun `the D0 pause copy appears only while counting is off (SRS Android 3_1)`() {
        assertThat(panel(settings = countingOn.copy(songCountingEnabled = false)).showsD0PauseCopy).isTrue()
        assertThat(panel(snapshot = observingPlaying).showsD0PauseCopy).isFalse()
        assertThat(panel(selected = null, settings = MusicSettings()).showsD0PauseCopy).isTrue()
    }

    @Test
    fun `a chosen built-in noise shows its own play state (M-NOISE)`() {
        val state = MusicPanelState.from(
            MusicSettings(selectedNoise = NoiseKind.PINK), null, ObservationSnapshot(), ControlUiState(), NoiseState(NoiseKind.PINK, playing = true),
        )

        assertThat(state).isEqualTo(MusicPanelState.Noise(NoiseKind.PINK, playing = true, unavailable = false))
    }

    @Test
    fun `choosing no music shows music-free (M-NONE)`() {
        val state = MusicPanelState.from(MusicSettings(noMusic = true), null, ObservationSnapshot(), ControlUiState(), NoiseState())

        assertThat(state).isEqualTo(MusicPanelState.None)
    }

    @Test
    fun `noise that could not start is shown as unavailable (review 2 L6)`() {
        val state = MusicPanelState.from(
            MusicSettings(selectedNoise = NoiseKind.WHITE), null, ObservationSnapshot(), ControlUiState(),
            NoiseState(NoiseKind.WHITE, playing = false, unavailable = true),
        )

        assertThat(state).isEqualTo(MusicPanelState.Noise(NoiseKind.WHITE, playing = false, unavailable = true))
    }

    @Test
    fun `noise at zero volume is shown as muted`() {
        val state = MusicPanelState.from(
            MusicSettings(selectedNoise = NoiseKind.WHITE), null, ObservationSnapshot(), ControlUiState(),
            NoiseState(NoiseKind.WHITE, playing = false, muted = true),
        )

        assertThat(state).isEqualTo(MusicPanelState.Noise(NoiseKind.WHITE, playing = false, muted = true))
    }
}
