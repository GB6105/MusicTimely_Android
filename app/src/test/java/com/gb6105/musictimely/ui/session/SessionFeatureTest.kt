package com.gb6105.musictimely.ui.session

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndPhase
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.ControlUiState
import com.gb6105.musictimely.domain.repository.LaunchOutcome
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeMusicControl
import com.gb6105.musictimely.testing.FakeMusicLauncher
import com.gb6105.musictimely.testing.FakeNoisePort
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** F01 진행·일시정지·도달 화면 (SCR-02·03·04, W02·W03·W06). FR-A008~A011·A014, QA-05·QA-09·QA-14. */
@RunWith(AndroidJUnit4::class)
class SessionFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val minute = 60_000L
    private val clock = FakeClock()
    private val repository = FakeSessionRepository()
    private val dispatch = DispatchTimerCommandUseCase(repository, clock, FakeTimerEffectSink())
    private val music = FakeMusicControl()
    private val settings = FakeSettingsRepository()
    private val sources = FakeSourceRepository()
    private val launcher = FakeMusicLauncher()
    private val noise = FakeNoisePort()
    private val viewModel = SessionViewModel(repository, dispatch, clock, music, settings, sources, launcher, noise)
    private var finished = false

    private fun showRunning(title: String? = "기획안 쓰기") {
        runBlocking {
            repository.save(TimerSession.ready("s", Allocation.bounded(25 * minute), title))
            dispatch(TimerCommand.Start("start"))
        }
        compose.setContent { SessionRoute(viewModel, onFinished = { finished = true }, tickIntervalMs = Long.MAX_VALUE) }
        compose.waitForIdle()
    }

    private fun advance(ms: Long) {
        clock.advance(ms)
        runBlocking { viewModel.refresh() }
        compose.waitForIdle()
    }

    @Test
    fun `running shows the remaining song amount and the real elapsed time (QA-14)`() {
        showRunning()

        advance(564_000)

        compose.onNodeWithText("기획안 쓰기").assertIsDisplayed()
        // A2 time card: elapsed and remaining clocks; the SRS sentences stay for screen readers.
        compose.onNodeWithText("경과").assertIsDisplayed()
        compose.onNodeWithText("09:24").assertIsDisplayed()
        compose.onNodeWithText("남음").assertIsDisplayed()
        compose.onNodeWithText("15:36").assertIsDisplayed()
        compose.onNodeWithContentDescription("약 4곡 분량 남았어요, 09:24 경과 · 25분 중").assertIsDisplayed()
    }

    @Test
    fun `no title shows the default task label`() {
        showRunning(title = null)

        compose.onNodeWithText("지금 하는 일").assertIsDisplayed()
    }

    @Test
    fun `pausing rests the timer only and says so (SCR-03, QA-05)`() {
        showRunning()
        advance(60_000)

        compose.onNodeWithText("세션 잠시 멈춤").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("타이머만 쉬고 있어요").assertIsDisplayed()
        compose.onNodeWithText("타이머만 멈췄어요. 음악은 음악 앱에서 조절해요").assertIsDisplayed()
        advance(30_000)
        compose.onNodeWithText("세션 다시 시작").performClick()
        advance(60_000)
        compose.onNodeWithText("02:00").assertIsDisplayed()
    }

    @Test
    fun `reaching the target offers finish and explicit extensions only (SCR-04)`() {
        showRunning()

        advance(25 * minute)

        compose.onNodeWithText("정한 시간이 됐어요").assertIsDisplayed()
        listOf("+5분", "+10분", "+15분", "마치기").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `extending continues the same session (QA-09)`() {
        showRunning()
        advance(25 * minute)

        compose.onNodeWithText("+5분").performClick()
        advance(minute)

        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
        compose.onNodeWithText("26:00").assertIsDisplayed()
        compose.onNodeWithText("04:00").assertIsDisplayed()
    }

    @Test
    fun `about one song left shows the last song cue (FR-A017)`() {
        showRunning()

        advance(21 * minute)

        compose.onNodeWithText("대략 한 곡 분량 남았어요").assertIsDisplayed()
    }

    @Test
    fun `finishing goes to the result`() {
        showRunning()
        advance(10 * minute)

        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()

        assertThat(finished).isTrue()
        assertThat(repository.stored?.state).isEqualTo(TimerState.ENDED)
    }

    // ---- music panel (SRS Android 3.1) ----

    private val ytmSource = ExternalSource("a", Provider.YOUTUBE_MUSIC, "com.google.android.apps.youtube.music", null, "유튜브뮤직", 0)

    private fun withSource(countingOn: Boolean) = runBlocking {
        sources.save(ytmSource)
        settings.update { it.copy(selectedSourceId = "a", songCountingEnabled = countingOn) }
    }

    private val observing = ObservationSnapshot(
        state = ObservationState.OBSERVING, provider = Provider.YOUTUBE_MUSIC, title = "Slow Tide", isPlaying = true, countedItems = 3,
    )

    @Test
    fun `observed music shows the current title, the counted songs and a music pause button (QA-A07, QA-A15)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithText("Slow Tide").assertIsDisplayed()
        compose.onNodeWithText("3곡 지났어요").assertIsDisplayed()
        compose.onNodeWithContentDescription("음악 일시정지").performClick()
        compose.waitForIdle()

        assertThat(music.taps).isEqualTo(1)
    }

    @Test
    fun `counting off shows the source on the average basis with open and a counting link (M-EXT-OFF)`() {
        withSource(countingOn = false)
        showRunning()

        compose.onNodeWithText("유튜브뮤직 · 평균 4분 기준").assertIsDisplayed()
        compose.onNodeWithText("곡 세기 켜기").assertIsDisplayed()
        compose.onNodeWithText("열기").performClick()
        compose.waitForIdle()

        assertThat(launcher.opened).containsExactly(ytmSource)
    }

    @Test
    fun `nothing to open asks the user to open the music app, the timer keeps going (FR-A027)`() {
        withSource(countingOn = false)
        launcher.outcome = LaunchOutcome.MANUAL
        showRunning()

        compose.onNodeWithText("열기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("음악 앱을 직접 열어 주세요").assertIsDisplayed()
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `pausing while observed says the music rests too once it has stopped (DEC-A16)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithText("세션 잠시 멈춤").performClick()
        music.snapshot.value = observing.copy(isPlaying = false)
        advance(1_000)

        compose.onNodeWithText("타이머와 음악이 쉬고 있어요").assertIsDisplayed()
        compose.onNodeWithText("타이머만 멈췄어요. 음악은 음악 앱에서 조절해요").assertDoesNotExist()
    }

    @Test
    fun `if the observed music keeps playing during the pause, only the timer is said to rest (DEC-A16)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithText("세션 잠시 멈춤").performClick()
        advance(1_000)

        compose.onNodeWithText("타이머만 쉬고 있어요").assertIsDisplayed()
    }

    @Test
    fun `waiting to stop at the end offers stop now and keep listening (SCR-04, QA-A19)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()
        advance(25 * minute)
        music.stopAtEnd.value = StopAtEndPhase.ARMED
        compose.waitForIdle()

        compose.onNodeWithText("지금 곡이 끝나면 음악도 멈춰요").assertIsDisplayed()
        compose.onNodeWithText("지금 멈추기").performClick()
        compose.onNodeWithText("계속 듣기").performClick()
        compose.waitForIdle()

        assertThat(music.stopNowCalls).isEqualTo(1)
        assertThat(music.keepListeningCalls).isEqualTo(1)
    }

    @Test
    fun `a failed stop asks the user to stop in the music app (FR-A053)`() {
        withSource(countingOn = true)
        showRunning()
        advance(25 * minute)
        music.stopAtEnd.value = StopAtEndPhase.FAILED
        compose.waitForIdle()

        compose.onNodeWithText("음악을 멈추지 못했어요. 음악 앱에서 멈춰 주세요").assertIsDisplayed()
    }

    @Test
    fun `an unresponsive app shows one notice and the button opens the app (DEC-A07)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        music.control.value = ControlUiState(unresponsive = true, noticePending = true)
        showRunning()

        compose.onNodeWithText("음악 앱에서 직접 조절해 주세요").assertIsDisplayed()
        compose.onNodeWithContentDescription("음악 앱 열기").assertIsDisplayed()
    }

    @Test
    fun `music paused for a while shows a quiet hint and the timer keeps running (FR-A042)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing.copy(isPlaying = false, showStoppedHint = true)
        showRunning()

        compose.onNodeWithText("음악이 멈췄어요").assertIsDisplayed()
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `a session finished from the notification moves on to the result (review HIGH-7)`() {
        showRunning()

        runBlocking { dispatch(TimerCommand.Finish("from-notification")) }
        advance(1_000)

        assertThat(finished).isTrue()
    }

    @Test
    fun `a failed checkpoint write is told once (CHECKPOINT_WRITE_FAILED, review MEDIUM-13)`() {
        repository.writeFailureNotice = true
        showRunning()

        compose.onNodeWithText("이번 세션은 앱 종료 후 복원되지 않을 수 있어요").assertIsDisplayed()
    }

    @Test
    fun `built-in noise starts only from the user's tap and can be stopped (M-NOISE, FR-A056, QA-22)`() {
        runBlocking { settings.update { it.copy(selectedNoise = NoiseKind.PINK) } }
        showRunning()

        compose.onNodeWithText("핑크 노이즈").assertIsDisplayed()
        assertThat(noise.played).isEmpty()
        compose.onNodeWithContentDescription("소리 재생").performClick()
        compose.waitForIdle()
        assertThat(noise.played).containsExactly(NoiseKind.PINK)

        compose.onNodeWithContentDescription("소리 정지").performClick()
        compose.waitForIdle()
        assertThat(noise.stops).isEqualTo(1)
    }

    @Test
    fun `no music shows music-free (M-NONE)`() {
        runBlocking { settings.update { it.copy(noMusic = true) } }
        showRunning()

        compose.onNodeWithText("음악 없이").assertIsDisplayed()
    }

    @Test
    fun `turning noise on over observed playing music asks first, stop pauses it (FR-A059, QA-A33)`() {
        runBlocking { settings.update { it.copy(selectedNoise = NoiseKind.WHITE) } }
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithContentDescription("소리 재생").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("음악 앱을 멈출까요?").assertIsDisplayed()
        assertThat(noise.played).isEmpty()

        compose.onNodeWithText("멈추기").performClick()
        compose.waitForIdle()

        assertThat(music.pauseObservedCalls).isEqualTo(1)
        assertThat(music.taps).isEqualTo(0)
        assertThat(noise.played).containsExactly(NoiseKind.WHITE)
    }

    @Test
    fun `keeping the music sends no command to the other app (FR-A059, QA-A33)`() {
        runBlocking { settings.update { it.copy(selectedNoise = NoiseKind.WHITE) } }
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithContentDescription("소리 재생").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("그대로").performClick()
        compose.waitForIdle()

        assertThat(music.taps).isEqualTo(0)
        assertThat(music.pauseObservedCalls).isEqualTo(0)
        assertThat(noise.played).containsExactly(NoiseKind.WHITE)
        assertThat(noise.keptOthers).containsExactly(true)
    }

    @Test
    fun `an open-ended session shows the passed amount and elapsed time only, no limit (W10, QA-10)`() {
        runBlocking {
            repository.save(TimerSession.ready("s", Allocation.infinite()))
            dispatch(TimerCommand.Start("start"))
        }
        compose.setContent { SessionRoute(viewModel, onFinished = { finished = true }, tickIntervalMs = Long.MAX_VALUE) }
        compose.waitForIdle()

        advance(10 * minute)

        compose.onNodeWithContentDescription("약 2.5곡 분량이 지났어요, 10:00 경과").assertIsDisplayed()
        compose.onNodeWithText("10:00").assertIsDisplayed()
        compose.onNodeWithText("남음").assertDoesNotExist()
        compose.onNodeWithText("남았어요", substring = true).assertDoesNotExist()
        advance(500 * minute)
        compose.onNodeWithText("정한 시간이 됐어요").assertDoesNotExist()
    }

    @Test
    fun `up to eight songs show chips, larger amounts a summary, both with the total for screen readers (FR-A021)`() {
        runBlocking {
            repository.save(TimerSession.ready("s", Allocation.bounded(40 * minute)))
            dispatch(TimerCommand.Start("start"))
        }
        compose.setContent { SessionRoute(viewModel, onFinished = {}, tickIntervalMs = Long.MAX_VALUE) }
        compose.waitForIdle()
        advance(8 * minute)

        compose.onNodeWithText("●", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("10곡 분량 중 2곡 분량 지남").assertIsDisplayed()
    }

    @Test
    fun `seven songs are drawn as progress cells with the current one outlined (ProgressPanel)`() {
        showRunning()
        advance(8 * minute)

        compose.onNodeWithContentDescription("7곡 분량 중 2곡 분량 지남").assertIsDisplayed()
        compose.onAllNodesWithTag("progress_cell_filled").assertCountEquals(2)
        compose.onAllNodesWithTag("progress_cell_current").assertCountEquals(1)
        compose.onAllNodesWithTag("progress_cell_empty").assertCountEquals(4)
    }

    @Test
    fun `in the last song only the last cell is outlined (ProgressPanel lastSong)`() {
        showRunning()
        advance(21 * minute)

        compose.onNodeWithText("대략 한 곡 분량 남았어요").assertIsDisplayed()
        compose.onAllNodesWithTag("progress_cell_filled").assertCountEquals(5)
        compose.onAllNodesWithTag("progress_cell_empty").assertCountEquals(1)
        compose.onAllNodesWithTag("progress_cell_current").assertCountEquals(1)
    }

    @Test
    fun `the record spins only while observed music plays (DEC-A12)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()
        compose.onNodeWithContentDescription("레코드가 돌고 있어요").assertExists()

        music.snapshot.value = observing.copy(isPlaying = false)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("레코드가 멈춰 있어요").assertExists()
    }

    @Test
    fun `a single-loop session counts loops instead of songs (FR-A019)`() {
        runBlocking {
            repository.save(TimerSession.ready("s", Allocation.bounded(25 * minute, loopDurationMs = 222_000, singleLoop = true)))
            dispatch(TimerCommand.Start("start"))
        }
        compose.setContent { SessionRoute(viewModel, onFinished = {}, tickIntervalMs = Long.MAX_VALUE) }
        compose.waitForIdle()

        advance(444_000)

        compose.onNodeWithContentDescription("약 5바퀴 분량 남았어요", substring = true).assertIsDisplayed()
        compose.onNodeWithText("바퀴 진행").assertIsDisplayed()
        compose.onNodeWithText("약 3/7").assertIsDisplayed()
    }

    @Test
    fun `song display off keeps the numeric timer and controls but hides song amounts and chips (FR-A020)`() {
        runBlocking { settings.update { it.copy(showSongs = false) } }
        showRunning()

        advance(564_000)

        compose.onNodeWithText("09:24").assertIsDisplayed()
        compose.onNodeWithText("15:36").assertIsDisplayed()
        compose.onNodeWithContentDescription("15:36 남았어요, 09:24 경과 · 25분 중").assertIsDisplayed()
        compose.onNodeWithText("세션 잠시 멈춤").assertIsDisplayed()
        compose.onNodeWithText("곡 진행").assertDoesNotExist()
        compose.onNodeWithContentDescription("곡 분량", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("7곡 분량 중 2곡 분량 지남").assertDoesNotExist()
    }

    @Test
    fun `song display off gives a plain end cue instead of the song cue (FR-A020)`() {
        runBlocking { settings.update { it.copy(showSongs = false) } }
        showRunning()

        advance(21 * minute)

        compose.onNodeWithText("곧 정한 시간이에요").assertIsDisplayed()
        compose.onNodeWithText("대략 한 곡 분량 남았어요").assertDoesNotExist()
    }

    @Test
    fun `opening the music app is not shown as playing until observation sees it (launch is not playback)`() {
        withSource(countingOn = true)
        launcher.outcome = LaunchOutcome.OPENED_LINK
        showRunning()

        compose.onNodeWithText("열기").performClick()
        compose.waitForIdle()

        assertThat(launcher.opened).containsExactly(ytmSource)
        assertThat((viewModel.state.value as SessionUiState.Active).music).isEqualTo(MusicPanelState.ExternalWaiting("유튜브뮤직"))
        compose.onNodeWithContentDescription("음악 일시정지").assertDoesNotExist()
        compose.onNodeWithContentDescription("음악 재생").assertDoesNotExist()

        music.snapshot.value = observing
        runBlocking { viewModel.refresh() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("음악 일시정지").assertIsDisplayed()
    }

    @Test
    fun `the current song can be corrected in session without touching time (FR-A016)`() {
        showRunning()
        advance(564_000)

        compose.onNodeWithContentDescription("더 보기").performClick()
        compose.onNodeWithText("곡 맞추기").performClick()
        compose.onNodeWithTag("session_correct_input").performTextInput("5")
        compose.onNodeWithText("이 곡으로 맞추기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("곡 표시만 맞췄어요. 정한 시간은 그대로예요.").assertExists()
        compose.onNodeWithContentDescription("7곡 분량 중 4곡 분량 지남").assertExists()
        compose.onNodeWithText("09:24").assertExists()
    }

    @Test
    fun `song display off in an open-ended session shows elapsed time only (FR-A020, review M3)`() {
        runBlocking {
            settings.update { it.copy(showSongs = false) }
            repository.save(TimerSession.ready("s", Allocation.infinite()))
            dispatch(TimerCommand.Start("start"))
        }
        compose.setContent { SessionRoute(viewModel, onFinished = {}, tickIntervalMs = Long.MAX_VALUE) }
        compose.waitForIdle()

        advance(10 * minute)

        compose.onNodeWithText("10:00").assertIsDisplayed()
        compose.onNodeWithText("곡 진행").assertDoesNotExist()
        compose.onNodeWithText("곡 분량", substring = true).assertDoesNotExist()
        compose.onNodeWithText("곡 정보 없이 평균 길이로 추정 중").assertDoesNotExist()
    }

    @Test
    fun `song display off hides the estimate note in a bounded session too (FR-A020)`() {
        runBlocking { settings.update { it.copy(showSongs = false) } }
        showRunning()

        compose.onNodeWithText("곡 정보 없이 평균 길이로 추정 중").assertDoesNotExist()
    }

    @Test
    fun `turning song display off also hides the correction notice (review L5)`() {
        showRunning()
        advance(564_000)
        assertThat(viewModel.correctSong(5)).isTrue()
        compose.waitForIdle()
        compose.onNodeWithText("곡 표시만 맞췄어요. 정한 시간은 그대로예요.").assertExists()

        runBlocking { settings.update { it.copy(showSongs = false) } }
        advance(1_000)

        compose.onNodeWithText("곡 표시만 맞췄어요. 정한 시간은 그대로예요.").assertDoesNotExist()
    }

    // ---- design layout (Figma Gui 4:480·4:1224·4:1045·4:1123) ----

    @Test
    fun `the top bar shows the session caption and the task, the time card the song progress (SCR-02, A2)`() {
        showRunning()
        advance(564_000)

        compose.onNodeWithText("집중 세션").assertIsDisplayed()
        compose.onNodeWithText("기획안 쓰기").assertIsDisplayed()
        compose.onNodeWithText("곡 진행").assertIsDisplayed()
        compose.onNodeWithText("약 3/7").assertIsDisplayed()
    }

    // ---- A2 time card and record label ----

    @Test
    fun `observed songs show the exact song progress without the estimate mark (A2, FR-A018)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing.copy(countedItems = 2)
        showRunning()

        compose.onNodeWithText("3/7").assertIsDisplayed()
        compose.onNodeWithText("약 3/7").assertDoesNotExist()
    }

    @Test
    fun `the record label carries the observed title with NOW PLAYING and SIDE A (A2, FR-A018)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithTag(SessionTags.RECORD_LABEL).assertIsDisplayed()
        compose.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        compose.onNodeWithText("Slow Tide").assertIsDisplayed()
        compose.onNodeWithText("SIDE A").assertIsDisplayed()
    }

    @Test
    fun `an estimating label never shows a song title, only the estimated ordinal (A2 D0, FR-A018)`() {
        withSource(countingOn = true)
        // The listener may know a title before observation is confirmed; the label must not use it.
        music.snapshot.value = observing.copy(state = ObservationState.DEGRADED)
        showRunning()
        advance(564_000)

        compose.onNodeWithText("Slow Tide", substring = true).assertDoesNotExist()
        compose.onNodeWithText("NOW PLAYING").assertDoesNotExist()
        compose.onNodeWithText("약 3번째 곡").assertIsDisplayed()
        compose.onNodeWithText("SIDE A").assertIsDisplayed()
    }

    @Test
    fun `the caption above the record says what is playing while observed music plays (A2)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithText("지금 듣고 있는 곡").assertIsDisplayed()
        compose.onNodeWithContentDescription("음악 일시정지").assertIsDisplayed()
    }

    @Test
    fun `observed music that stopped is captioned as stopped, never as listening (A2)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing.copy(isPlaying = false)
        showRunning()

        compose.onNodeWithText("잠시 멈춘 곡").assertIsDisplayed()
        compose.onNodeWithText("지금 듣고 있는 곡").assertDoesNotExist()
        compose.onNodeWithText("NOW PLAYING").assertDoesNotExist()
        compose.onNodeWithContentDescription("음악 재생").assertIsDisplayed()
    }

    @Test
    fun `the caption above the record is the estimate note without observation and no music button (A2 D0)`() {
        showRunning()

        compose.onNodeWithText("곡 정보 없이 평균 길이로 추정 중").assertIsDisplayed()
        compose.onNodeWithContentDescription("음악 일시정지").assertDoesNotExist()
        compose.onNodeWithContentDescription("음악 재생").assertDoesNotExist()
    }

    @Test
    fun `paused and reached states caption the record with their own lines (A2, SCR-03, SCR-04)`() {
        showRunning()
        compose.onNodeWithText("세션 잠시 멈춤").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("타이머만 쉬고 있어요").assertIsDisplayed()
        compose.onNodeWithText("곡 정보 없이 평균 길이로 추정 중").assertDoesNotExist()

        compose.onNodeWithText("세션 다시 시작").performClick()
        advance(25 * minute)
        compose.onNodeWithText("정한 시간이 됐어요").assertIsDisplayed()
        compose.onNodeWithText("곡 정보 없이 평균 길이로 추정 중").assertDoesNotExist()
    }

    @Test
    fun `the last-song cue shows as a short line under the time card (A2, FR-A017)`() {
        showRunning()
        advance(21 * minute)

        compose.onNodeWithTag(SessionTags.CUE_LINE).assertIsDisplayed()
        compose.onNodeWithText("대략 한 곡 분량 남았어요").assertIsDisplayed()
        compose.onNodeWithContentDescription("대략 한 곡 분량 남았어요, 21:00 경과 · 25분 중").assertExists()
    }

    @Test
    fun `no cue line before the last song (A2)`() {
        showRunning()
        advance(564_000)

        compose.onNodeWithTag(SessionTags.CUE_LINE).assertDoesNotExist()
    }

    @Test
    fun `without observation the record caption is an estimated ordinal, never a title (SCR-02 D0, FR-A018)`() {
        showRunning()
        advance(564_000)

        compose.onNodeWithText("약 3번째 곡").assertIsDisplayed()
        compose.onNodeWithText("곡 정보 없이 평균 길이로 추정 중").assertIsDisplayed()
    }

    @Test
    fun `observed music shows no estimated ordinal (FR-A018)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()

        compose.onNodeWithText("번째 곡", substring = true).assertDoesNotExist()
        compose.onNodeWithText("곡 정보 없이 평균 길이로 추정 중").assertDoesNotExist()
    }

    @Test
    fun `the more menu offers music change and settings (W02, W09)`() {
        var changed = 0
        var settingsOpened = 0
        runBlocking {
            repository.save(TimerSession.ready("s", Allocation.bounded(25 * minute), "기획안 쓰기"))
            dispatch(TimerCommand.Start("start"))
        }
        compose.setContent {
            SessionRoute(
                viewModel, onFinished = {}, tickIntervalMs = Long.MAX_VALUE,
                onOpenMusic = { changed++ }, onOpenSettings = { settingsOpened++ },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("더 보기").performClick()
        compose.onNodeWithText("음악 바꾸기").performClick()
        compose.onNodeWithContentDescription("더 보기").performClick()
        compose.onNodeWithText("설정").performClick()
        compose.waitForIdle()

        assertThat(changed).isEqualTo(1)
        assertThat(settingsOpened).isEqualTo(1)
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `the thought memo control opens the memo without pausing (W07)`() {
        var memo = 0
        runBlocking {
            repository.save(TimerSession.ready("s", Allocation.bounded(25 * minute), "기획안 쓰기"))
            dispatch(TimerCommand.Start("start"))
        }
        compose.setContent { SessionRoute(viewModel, onFinished = {}, tickIntervalMs = Long.MAX_VALUE, onOpenMemo = { memo++ }) }
        compose.waitForIdle()

        compose.onNodeWithText("생각 메모").performClick()

        assertThat(memo).isEqualTo(1)
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `paused shows the resume control in place of pause (SCR-03)`() {
        showRunning()

        compose.onNodeWithText("세션 잠시 멈춤").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("세션 다시 시작").assertIsDisplayed()
        compose.onNodeWithText("세션 잠시 멈춤").assertDoesNotExist()
    }

    @Test
    fun `the song title on the record label sits above the spindle, never on it (emulator finding)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing
        showRunning()

        val label = compose.onNodeWithTag(SessionTags.RECORD_LABEL).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("Slow Tide").getUnclippedBoundsInRoot()
        val spindleTop = (label.top + label.bottom) / 2 - 4.dp

        assertThat(title.bottom.value).isAtMost(spindleTop.value)
    }

    @Test
    fun `the cells follow the song progress shown next to them, not the clock (emulator finding)`() {
        withSource(countingOn = true)
        music.snapshot.value = observing // 3 songs counted from the start → 곡 진행 4/7
        showRunning()

        compose.onNodeWithText("4/7").assertExists()
        compose.onAllNodesWithTag("progress_cell_filled").assertCountEquals(3)
        compose.onAllNodesWithTag("progress_cell_current").assertCountEquals(1)
        compose.onAllNodesWithTag("progress_cell_empty").assertCountEquals(3)
    }
}
