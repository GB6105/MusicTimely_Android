package com.gb6105.musictimely.ui.allocation

import kotlinx.coroutines.runBlocking
import com.gb6105.musictimely.testing.FakeMusicLauncher
import com.gb6105.musictimely.domain.repository.LaunchOutcome
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.domain.usecase.session.OpenSessionUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeNotificationPermission
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.gb6105.musictimely.testutil.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** SCR-01 배정. FR-A001~A003·A007, QA-02·QA-03. */
class AllocationViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeSessionRepository()
    private val settings = FakeSettingsRepository()
    private val permission = FakeNotificationPermission()
    private val clock = FakeClock()
    private val sources = FakeSourceRepository()
    private val launcher = FakeMusicLauncher()
    private val ytm = ExternalSource("a", Provider.YOUTUBE_MUSIC, "com.google.android.apps.youtube.music", null, "유튜브뮤직", 0)

    /** Lazy: built after [main] has replaced Dispatchers.Main, so the settings collection runs. */
    private val viewModel by lazy {
        AllocationViewModel(
            openSession = OpenSessionUseCase(repository) { "s-1" },
            dispatch = DispatchTimerCommandUseCase(repository, clock, FakeTimerEffectSink()),
            settings,
            permission,
            sources,
            launcher,
        )
    }

    @Test
    fun `starts with 25 minutes selected and translated to about 7 songs (FR-A002, FR-A003)`() {
        val state = viewModel.state.value

        assertThat(state.selectedPreset).isEqualTo(25)
        assertThat(state.minutes).isEqualTo(25)
        assertThat(state.songs).isEqualTo(7)
        assertThat(state.canStart).isTrue()
    }

    @Test
    fun `choosing a preset re-translates immediately`() {
        viewModel.onPresetSelected(10)

        assertThat(viewModel.state.value.minutes).isEqualTo(10)
        assertThat(viewModel.state.value.songs).isEqualTo(3)
    }

    @Test
    fun `custom minutes translate and invalid input blocks start (QA-03)`() {
        viewModel.onCustomMinutesChanged("40")
        assertThat(viewModel.state.value.selectedPreset).isNull()
        assertThat(viewModel.state.value.songs).isEqualTo(10)

        viewModel.onCustomMinutesChanged("241")
        assertThat(viewModel.state.value.customError).isTrue()
        assertThat(viewModel.state.value.canStart).isFalse()
        assertThat(viewModel.state.value.songs).isNull()
    }

    @Test
    fun `start opens a session with the title and starts it in one tap (FR-A007)`() = runTest {
        viewModel.onTitleChanged("  기획안 쓰기 ")

        viewModel.start()

        val session = repository.stored!!
        assertThat(session.state).isEqualTo(TimerState.RUNNING)
        assertThat(session.taskTitle).isEqualTo("기획안 쓰기")
        assertThat(session.allocation.currentTargetMs).isEqualTo(25 * 60_000L)
    }

    @Test
    fun `start twice keeps one running session (QA-04)`() = runTest {
        viewModel.start()
        viewModel.start()

        assertThat(repository.stored!!.sessionId).isEqualTo("s-1")
        assertThat(repository.stored!!.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `start is ignored while the input is invalid`() = runTest {
        viewModel.onCustomMinutesChanged("0")

        viewModel.start()

        assertThat(repository.stored).isNull()
    }

    @Test
    fun `the first start on Android 13+ explains notifications before starting (FR-A063)`() = runTest {
        permission.needsGrant = true

        val started = viewModel.start()

        assertThat(started).isFalse()
        assertThat(viewModel.state.value.showNotificationPrompt).isTrue()
        assertThat(repository.stored).isNull()
    }

    @Test
    fun `continuing from the explanation starts the session whatever the answer and never asks again (FR-A063, QA-A31)`() = runTest {
        permission.needsGrant = true
        viewModel.start()

        val started = viewModel.continueAfterNotificationPrompt()

        assertThat(started).isTrue()
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
        assertThat(settings.settings.value.notificationPromptShown).isTrue()
        assertThat(viewModel.state.value.showNotificationPrompt).isFalse()
    }

    @Test
    fun `once asked, later starts go straight through`() = runTest {
        permission.needsGrant = true
        settings.update { it.copy(notificationPromptShown = true) }

        assertThat(viewModel.start()).isTrue()
        assertThat(viewModel.state.value.showNotificationPrompt).isFalse()
    }

    @Test
    fun `the secondary entry starts an open-ended session (FR-A005, W10)`() = runTest {
        assertThat(viewModel.startOpenEnded()).isTrue()

        val session = repository.stored!!
        assertThat(session.state).isEqualTo(TimerState.RUNNING)
        assertThat(session.allocation.bounded).isFalse()
    }

    @Test
    fun `the average song length setting drives the translation and is snapshotted into the session (FR-A004)`() = runTest {
        settings.update { it.copy(durationPerUnitMs = 300_000) }
        viewModel.onPresetSelected(25)

        assertThat(viewModel.state.value.songs).isEqualTo(5)
        viewModel.start()
        assertThat(repository.stored!!.allocation.durationPerUnitMs).isEqualTo(300_000)

        settings.update { it.copy(durationPerUnitMs = 120_000) }
        assertThat(repository.stored!!.allocation.durationPerUnitMs).isEqualTo(300_000)
    }

    @Test
    fun `single-loop mode translates to loops of the loop length and snapshots it (FR-A019, QA-41)`() = runTest {
        settings.update { it.copy(singleLoop = true, loopDurationMs = 222_000) }
        viewModel.onPresetSelected(25)

        assertThat(viewModel.state.value.songs).isEqualTo(7)
        assertThat(viewModel.state.value.singleLoop).isTrue()
        viewModel.start()
        val allocation = repository.stored!!.allocation
        assertThat(allocation.singleLoop).isTrue()
        assertThat(allocation.loopDurationMs).isEqualTo(222_000)
    }

    @Test
    fun `the translation basis is the average song length, or the loop length in single-loop mode (FR-A004, FR-A019)`() = runTest {
        assertThat(viewModel.state.value.unitMs).isEqualTo(240_000)

        settings.update { it.copy(durationPerUnitMs = 300_000) }
        assertThat(viewModel.state.value.unitMs).isEqualTo(300_000)

        settings.update { it.copy(singleLoop = true, loopDurationMs = 222_000) }
        assertThat(viewModel.state.value.unitMs).isEqualTo(222_000)
    }

    @Test
    fun `the music row reflects the chosen source and whether songs are counted (SCR-01 music row)`() = runTest {
        assertThat(viewModel.state.value.music).isEqualTo(AllocationMusic.Unset)

        sources.save(ExternalSource("a", Provider.YOUTUBE_MUSIC, "com.google.android.apps.youtube.music", null, "유튜브뮤직", 0))
        settings.update { it.copy(selectedSourceId = "a", songCountingEnabled = true) }
        assertThat(viewModel.state.value.music).isEqualTo(AllocationMusic.External("유튜브뮤직", counting = true))

        settings.update { it.copy(selectedNoise = NoiseKind.PINK) }
        assertThat(viewModel.state.value.music).isEqualTo(AllocationMusic.Noise(NoiseKind.PINK))

        settings.update { it.copy(selectedNoise = null, noMusic = true) }
        assertThat(viewModel.state.value.music).isEqualTo(AllocationMusic.NoMusic)
    }

    @Test
    fun `the home music row opens the linked music app itself`() = runTest {
        sources.save(ytm)
        settings.update { it.copy(selectedSourceId = "a") }

        assertThat(viewModel.onMusicRow()).isTrue()

        assertThat(launcher.opened).containsExactly(ytm)
        assertThat(viewModel.state.value.launchNotice).isFalse()
    }

    @Test
    fun `when the app cannot be opened the row says to open it by hand (FR-A027)`() = runTest {
        sources.save(ytm)
        settings.update { it.copy(selectedSourceId = "a") }
        launcher.outcome = LaunchOutcome.MANUAL

        viewModel.onMusicRow()

        assertThat(viewModel.state.value.launchNotice).isTrue()
    }

    @Test
    fun `without a linked app the row is for choosing one, nothing is launched`() = runTest {
        assertThat(viewModel.onMusicRow()).isFalse()
        assertThat(launcher.opened).isEmpty()
    }
}
