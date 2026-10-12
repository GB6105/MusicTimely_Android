package com.gb6105.musictimely.ui.session

import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.ExtensionStep
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.translation.CueKind
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeMusicControl
import com.gb6105.musictimely.testing.FakeMusicLauncher
import com.gb6105.musictimely.testing.FakeNoisePort
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.gb6105.musictimely.testutil.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** SCR-02·03·04 진행·일시정지·도달. FR-A008~A011·A014·A017, QA-05·QA-09·QA-14. */
class SessionViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val minute = 60_000L
    private val clock = FakeClock()
    private val repository = FakeSessionRepository()
    private val dispatch = DispatchTimerCommandUseCase(repository, clock, FakeTimerEffectSink())
    private val music = FakeMusicControl()
    private val settings = FakeSettingsRepository()
    private val sources = FakeSourceRepository()
    private val launcher = FakeMusicLauncher()
    private val noise = FakeNoisePort()
    // Built after MainDispatcherRule has set Main, so init collectors never race a stale dispatcher.
    private val viewModel by lazy { SessionViewModel(repository, dispatch, clock, music, settings, sources, launcher, noise) }

    private suspend fun startSession(minutes: Long = 25, title: String? = "기획안 쓰기") {
        repository.save(TimerSession.ready("s", Allocation.bounded(minutes * minute), title))
        dispatch(TimerCommand.Start("start"))
        viewModel.refresh()
    }

    @Test
    fun `running shows remaining songs and real time (FR-A014, QA-14)`() = runTest {
        startSession()
        clock.advance(564_000)

        viewModel.refresh()

        val state = viewModel.state.value as SessionUiState.Active
        assertThat(state.phase).isEqualTo(TimerState.RUNNING)
        assertThat(state.taskTitle).isEqualTo("기획안 쓰기")
        assertThat(state.remainingSongs).isEqualTo(4)
        assertThat(state.elapsedMs).isEqualTo(564_000)
        assertThat(state.remainingMs).isEqualTo(25 * minute - 564_000)
        assertThat(state.totalSongs).isEqualTo(7)
        assertThat(state.filledChips).isEqualTo(2)
    }

    @Test
    fun `pause freezes time and resume continues (FR-A008, QA-05)`() = runTest {
        startSession()
        clock.advance(60_000)
        viewModel.pause()
        clock.advance(30_000)
        viewModel.refresh()

        assertThat((viewModel.state.value as SessionUiState.Active).phase).isEqualTo(TimerState.PAUSED)
        assertThat((viewModel.state.value as SessionUiState.Active).elapsedMs).isEqualTo(60_000)

        viewModel.resume()
        clock.advance(60_000)
        viewModel.refresh()
        assertThat((viewModel.state.value as SessionUiState.Active).elapsedMs).isEqualTo(120_000)
    }

    @Test
    fun `refresh past the target shows the limit reached state (FR-A010)`() = runTest {
        startSession()
        clock.advance(25 * minute)

        viewModel.refresh()

        assertThat((viewModel.state.value as SessionUiState.Active).phase).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(repository.stored!!.state).isEqualTo(TimerState.LIMIT_REACHED)
    }

    @Test
    fun `extending resumes the timer only (FR-A011, QA-09)`() = runTest {
        startSession()
        clock.advance(25 * minute)
        viewModel.refresh()
        clock.advance(2 * minute)

        viewModel.extend(ExtensionStep.FIVE)
        clock.advance(3 * minute)
        viewModel.refresh()

        val state = viewModel.state.value as SessionUiState.Active
        assertThat(state.phase).isEqualTo(TimerState.RUNNING)
        assertThat(state.elapsedMs).isEqualTo(28 * minute)
    }

    @Test
    fun `finishing shows the result state`() = runTest {
        startSession()
        clock.advance(10 * minute)

        viewModel.finish()

        assertThat(viewModel.state.value).isInstanceOf(SessionUiState.Finished::class.java)
    }

    @Test
    fun `the last song cue appears once when about one song is left (FR-A017)`() = runTest {
        startSession()
        clock.advance(21 * minute)
        viewModel.refresh()
        assertThat((viewModel.state.value as SessionUiState.Active).cue).isEqualTo(CueKind.ABOUT_ONE_SONG_LEFT)

        viewModel.dismissCue()
        clock.advance(30_000)
        viewModel.refresh()
        assertThat((viewModel.state.value as SessionUiState.Active).cue).isNull()
    }

    @Test
    fun `a new session after finishing gets its own last song cue`() = runTest {
        startSession()
        clock.advance(21 * minute)
        viewModel.refresh()
        viewModel.finish()

        repository.save(TimerSession.ready("s2", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start-2"))
        viewModel.refresh()
        assertThat((viewModel.state.value as SessionUiState.Active).cue).isNull()
        clock.advance(21 * minute)
        viewModel.refresh()

        assertThat((viewModel.state.value as SessionUiState.Active).cue).isEqualTo(CueKind.ABOUT_ONE_SONG_LEFT)
    }

    @Test
    fun `infinite mode shows elapsed only`() = runTest {
        repository.save(TimerSession.ready("s", Allocation.infinite()))
        dispatch(TimerCommand.Start("start"))
        clock.advance(10 * minute)

        viewModel.refresh()

        val state = viewModel.state.value as SessionUiState.Active
        assertThat(state.remainingMs).isNull()
        assertThat(state.remainingSongs).isNull()
        assertThat(state.elapsedTenths).isEqualTo(25)
    }

    @Test
    fun `per-session notices do not carry over to the next session (review 2 M6)`() = runTest {
        repository.writeFailureNotice = true
        startSession()
        assertThat((viewModel.state.value as SessionUiState.Active).writeFailureNotice).isTrue()
        viewModel.finish()

        repository.save(TimerSession.ready("s2", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start-2"))
        viewModel.refresh()

        assertThat((viewModel.state.value as SessionUiState.Active).writeFailureNotice).isFalse()
    }

    @Test
    fun `correcting the current song moves only the song display (FR-A016)`() = runTest {
        startSession()
        clock.advance(564_000)
        viewModel.refresh()
        val before = repository.stored

        assertThat(viewModel.correctSong(5)).isTrue()

        val state = viewModel.state.value as SessionUiState.Active
        assertThat(state.filledChips).isEqualTo(4)
        assertThat(state.remainingSongs).isEqualTo(2)
        assertThat(state.correctionNotice).isTrue()
        assertThat(state.elapsedMs).isEqualTo(564_000)
        assertThat(state.remainingMs).isEqualTo(25 * minute - 564_000)
        assertThat(repository.stored).isEqualTo(before)

        clock.advance(240_000)
        viewModel.refresh()
        assertThat((viewModel.state.value as SessionUiState.Active).filledChips).isEqualTo(5)
    }

    @Test
    fun `a correction outside 1 to 999 is rejected (FR-A016)`() = runTest {
        startSession()
        clock.advance(564_000)
        viewModel.refresh()

        assertThat(viewModel.correctSong(0)).isFalse()
        assertThat(viewModel.correctSong(1_000)).isFalse()

        val state = viewModel.state.value as SessionUiState.Active
        assertThat(state.filledChips).isEqualTo(2)
        assertThat(state.correctionNotice).isFalse()
    }

    @Test
    fun `while songs are observed the correction entry is hidden (DEC-A11)`() = runTest {
        music.snapshot.value = ObservationSnapshot(state = ObservationState.OBSERVING, isPlaying = true, countedItems = 2)
        startSession()

        assertThat((viewModel.state.value as SessionUiState.Active).canCorrect).isFalse()
        assertThat(viewModel.correctSong(3)).isFalse()
    }

    @Test
    fun `the cue is not repeated after re-entry or restore inside the last song (QA-16)`() = runTest {
        startSession()
        clock.advance(21 * minute)
        viewModel.refresh()
        assertThat((viewModel.state.value as SessionUiState.Active).cue).isEqualTo(CueKind.ABOUT_ONE_SONG_LEFT)

        val restored = SessionViewModel(repository, dispatch, clock, music, settings, sources, launcher, noise)
        clock.advance(30_000)
        restored.refresh()

        assertThat((restored.state.value as SessionUiState.Active).cue).isNull()
    }

    @Test
    fun `after an extension the new target gets its own cue (QA-16 per targetRevision)`() = runTest {
        startSession()
        clock.advance(21 * minute)
        viewModel.refresh()
        viewModel.dismissCue()
        clock.advance(4 * minute)
        viewModel.refresh() // limit reached
        viewModel.extend(ExtensionStep.FIVE)

        val restored = SessionViewModel(repository, dispatch, clock, music, settings, sources, launcher, noise)
        restored.refresh()
        assertThat((restored.state.value as SessionUiState.Active).cue).isNull()
        clock.advance(1 * minute)
        restored.refresh()

        assertThat((restored.state.value as SessionUiState.Active).cue).isEqualTo(CueKind.ABOUT_ONE_SONG_LEFT)
    }

    @Test
    fun `the estimated current song counts from one and follows an in-session correction (SCR-02 D0, FR-A016)`() = runTest {
        startSession()
        assertThat((viewModel.state.value as SessionUiState.Active).currentSong).isEqualTo(1)

        clock.advance(564_000)
        viewModel.refresh()
        assertThat((viewModel.state.value as SessionUiState.Active).currentSong).isEqualTo(3)

        assertThat(viewModel.correctSong(5)).isTrue()
        assertThat((viewModel.state.value as SessionUiState.Active).currentSong).isEqualTo(5)
    }

    @Test
    fun `an open-ended session estimates the current song from the elapsed amount (W10)`() = runTest {
        repository.save(TimerSession.ready("s", Allocation.infinite()))
        dispatch(TimerCommand.Start("start"))
        clock.advance(10 * minute)

        viewModel.refresh()

        assertThat((viewModel.state.value as SessionUiState.Active).currentSong).isEqualTo(3)
    }

    @Test
    fun `after a process restart the running screen shows the saved count, not zero (emulator finding)`() = runTest {
        music.snapshot.value = ObservationSnapshot(state = ObservationState.OBSERVING, isPlaying = true, countedItems = 0)
        repository.storedCounts["s"] = 9
        startSession()

        assertThat((viewModel.state.value as SessionUiState.Active).observedCount).isEqualTo(9)
    }

    @Test
    fun `without a saved count the live count is shown`() = runTest {
        music.snapshot.value = ObservationSnapshot(state = ObservationState.OBSERVING, isPlaying = true, countedItems = 3)
        startSession()

        assertThat((viewModel.state.value as SessionUiState.Active).observedCount).isEqualTo(3)
    }
}
