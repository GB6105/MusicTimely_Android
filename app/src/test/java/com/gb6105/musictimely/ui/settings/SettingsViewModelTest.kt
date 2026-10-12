package com.gb6105.musictimely.ui.settings

import com.gb6105.musictimely.domain.model.audio.NoiseSound
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.usecase.settings.DeleteAllDataUseCase
import com.gb6105.musictimely.testing.FakeCheckpointStore
import com.gb6105.musictimely.testing.FakeCountingAccess
import com.gb6105.musictimely.testing.FakeMemoRepository
import com.gb6105.musictimely.testing.FakePlaylistRepository
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.gb6105.musictimely.testutil.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** SCR-09 설정 + SCR-11 곡 세기 안내. FR-A031·A041·A047·A070·A071, MT-S1~S8, QA-A05·A28·A38. */
class SettingsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val sources = FakeSourceRepository()
    private val access = FakeCountingAccess()
    private val store = FakeCheckpointStore()
    // Built after MainDispatcherRule has set Main, so init collectors never race a stale dispatcher.
    private val viewModel by lazy { SettingsViewModel(settings, sources, access, DeleteAllDataUseCase(store, settings, sources, FakeTimerEffectSink(), FakeMemoRepository(), FakeSessionRepository(), FakePlaylistRepository())) }

    @Test
    fun `turning counting on without access shows the prompt instead (FR-A031)`() = runTest {
        viewModel.setCounting(true)

        assertThat(viewModel.state.value.showCountingPrompt).isTrue()
        assertThat(settings.settings.value.songCountingEnabled).isFalse()
    }

    @Test
    fun `the prompt's turn-on opens system settings and enables the in-app switch`() = runTest {
        viewModel.setCounting(true)

        viewModel.acceptCountingPrompt()

        assertThat(access.settingsOpened).isEqualTo(1)
        assertThat(settings.settings.value.songCountingEnabled).isTrue()
        assertThat(settings.settings.value.permissionPromptShown).isTrue()
        assertThat(viewModel.state.value.showCountingPrompt).isFalse()
    }

    @Test
    fun `later records the prompt as shown and never asks again by itself (QA-A05)`() = runTest {
        viewModel.setCounting(true)

        viewModel.declineCountingPrompt()

        assertThat(settings.settings.value.permissionPromptShown).isTrue()
        assertThat(settings.settings.value.songCountingEnabled).isFalse()
        assertThat(access.settingsOpened).isEqualTo(0)
    }

    @Test
    fun `with access granted counting turns on directly`() = runTest {
        access.granted = true

        viewModel.setCounting(true)

        assertThat(settings.settings.value.songCountingEnabled).isTrue()
        assertThat(viewModel.state.value.showCountingPrompt).isFalse()
    }

    @Test
    fun `turning counting off releases it and explains how to turn the system permission off (FR-A041)`() = runTest {
        access.granted = true
        viewModel.setCounting(true)

        viewModel.setCounting(false)

        assertThat(settings.settings.value.songCountingEnabled).isFalse()
        assertThat(viewModel.state.value.showSystemPermissionGuide).isTrue()
    }

    @Test
    fun `stop-at-end is disabled with a reason while counting is off (FR-A047)`() = runTest {
        assertThat(viewModel.state.value.stopAtEndEnabledInput).isFalse()

        viewModel.setStopAtEnd(true)
        assertThat(settings.settings.value.stopAtEndEnabled).isFalse()

        access.granted = true
        viewModel.setCounting(true)
        viewModel.setStopAtEnd(true)
        assertThat(settings.settings.value.stopAtEndEnabled).isTrue()
        assertThat(viewModel.state.value.stopAtEndEnabledInput).isTrue()
    }

    @Test
    fun `average song length is set in minutes within 1 to 30 (FR-A004, MT-S1)`() = runTest {
        viewModel.setAverageMinutes(5)
        assertThat(settings.settings.value.durationPerUnitMs).isEqualTo(300_000)

        viewModel.setAverageMinutes(0)
        viewModel.setAverageMinutes(31)
        assertThat(settings.settings.value.durationPerUnitMs).isEqualTo(300_000)
    }

    @Test
    fun `song display can be turned off (FR-A020, MT-S2)`() = runTest {
        viewModel.setShowSongs(false)

        assertThat(settings.settings.value.showSongs).isFalse()
    }

    @Test
    fun `saved sources can be deleted (MT-S5)`() = runTest {
        sources.save(ExternalSource("a", Provider.MELON, "com.iloen.melon", null, "멜론", 0))
        settings.update { it.copy(selectedSourceId = "a") }

        viewModel.deleteSource("a")

        assertThat(sources.sources.value).isEmpty()
        assertThat(settings.settings.value.selectedSourceId).isNull()
    }

    @Test
    fun `deleting all data clears everything and explains the system permission (FR-A071, QA-A38)`() = runTest {
        settings.update { MusicSettings(songCountingEnabled = true) }

        viewModel.deleteAllData()

        assertThat(settings.settings.value).isEqualTo(MusicSettings())
        assertThat(store.cleared).isEqualTo(1)
        assertThat(viewModel.state.value.showSystemPermissionGuide).isTrue()
    }

    @Test
    fun `single-loop mode and its optional loop length are set for the next session (SRS v1 6_4, QA-41)`() = runTest {
        viewModel.setSingleLoop(true)
        viewModel.setLoopLength("03:42")
        assertThat(settings.settings.value.singleLoop).isTrue()
        assertThat(settings.settings.value.loopDurationMs).isEqualTo(222_000)

        assertThat(viewModel.setLoopLength("30:01")).isFalse()
        assertThat(settings.settings.value.loopDurationMs).isEqualTo(222_000)

        viewModel.setLoopLength("")
        assertThat(settings.settings.value.loopDurationMs).isNull()
    }

    @Test
    fun `sound volume and fades are set within range and reset to defaults (FR-A070)`() = runTest {
        assertThat(viewModel.setNoiseVolume(0.5f)).isTrue()
        assertThat(viewModel.setFadeInMs(5_000)).isTrue()
        assertThat(viewModel.setFadeOutMs(10_000)).isTrue()
        assertThat(settings.settings.value.sound).isEqualTo(NoiseSound(0.5f, 5_000, 10_000))

        assertThat(viewModel.setNoiseVolume(1.5f)).isFalse()
        assertThat(viewModel.setFadeInMs(5_001)).isFalse()
        assertThat(viewModel.setFadeOutMs(10_001)).isFalse()
        assertThat(settings.settings.value.sound).isEqualTo(NoiseSound(0.5f, 5_000, 10_000))

        viewModel.resetSound()
        assertThat(settings.settings.value.sound).isEqualTo(NoiseSound())
    }

    @Test
    fun `pausing the timer with the music needs counting on and is off by default (DEC-A17)`() = runTest {
        assertThat(settings.settings.value.pauseTimerWithMusic).isFalse()

        viewModel.setPauseTimerWithMusic(true)
        assertThat(settings.settings.value.pauseTimerWithMusic).isFalse()

        access.granted = true
        viewModel.setCounting(true)
        viewModel.setPauseTimerWithMusic(true)
        assertThat(settings.settings.value.pauseTimerWithMusic).isTrue()
        assertThat(viewModel.state.value.pauseTimerWithMusicOn).isTrue()
    }
}
