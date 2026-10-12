package com.gb6105.musictimely.ui.allocation

import androidx.compose.ui.test.performScrollTo
import com.gb6105.musictimely.testing.FakeMusicLauncher
import com.gb6105.musictimely.domain.repository.LaunchOutcome
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.domain.usecase.session.OpenSessionUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeNotificationPermission
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import kotlinx.coroutines.runBlocking
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** F03 배정 화면 (SCR-01, W01·W05). FR-A001~A003·A007, QA-02·QA-03. */
@RunWith(AndroidJUnit4::class)
class AllocationFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val repository = FakeSessionRepository()
    private val settings = FakeSettingsRepository()
    private val permission = FakeNotificationPermission()
    private val sources = FakeSourceRepository()
    private val launcher = FakeMusicLauncher()
    private val viewModel = AllocationViewModel(
        OpenSessionUseCase(repository) { "s-1" },
        DispatchTimerCommandUseCase(repository, FakeClock(), FakeTimerEffectSink()),
        settings,
        permission,
        sources,
        launcher,
    )
    private var started = false
    private var settingsOpened = 0
    private var musicOpened = 0

    private fun show() = compose.setContent {
        AllocationRoute(viewModel, onStarted = { started = true }, onOpenSettings = { settingsOpened++ }, onOpenMusic = { musicOpened++ })
    }

    @Test
    fun `shows 25 minutes as about 7 songs without asking how many songs it takes (QA-02)`() {
        show()

        compose.onNodeWithText("얼마나 하실까요?").assertIsDisplayed()
        compose.onNodeWithText("25분이면").assertIsDisplayed()
        compose.onNodeWithText("약 7곡").assertIsDisplayed()
        // FR-A003 sentence stays whole for screen readers while the card splits it visually.
        compose.onNodeWithContentDescription("25분이면 약 7곡이에요", substring = true).assertIsDisplayed()
        compose.onNodeWithText("7곡 동안 시작").assertIsDisplayed()
        compose.onNodeWithText("몇 곡", substring = true).assertDoesNotExist()
    }

    @Test
    fun `choosing 10 minutes re-translates at once`() {
        show()

        compose.onNodeWithContentDescription("10분").performClick()

        compose.onNodeWithText("10분이면").assertIsDisplayed()
        compose.onNodeWithText("약 3곡").assertIsDisplayed()
    }

    @Test
    fun `an out of range custom time shows a field error and blocks start (QA-03)`() {
        show()

        compose.onNodeWithTag(AllocationTags.CUSTOM).performClick()
        compose.onNodeWithTag(AllocationTags.CUSTOM_MINUTES).performTextInput("241")

        compose.onNodeWithText("1~240분 사이로 적어 주세요").assertIsDisplayed()
        compose.onNodeWithTag(AllocationTags.START).assertIsNotEnabled()
    }

    @Test
    fun `start begins the session with the typed title in one tap (FR-A007)`() {
        show()

        compose.onNodeWithTag(AllocationTags.TITLE).performTextInput("기획안 쓰기")
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()

        assertThat(started).isTrue()
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
        assertThat(repository.stored?.taskTitle).isEqualTo("기획안 쓰기")
    }

    @Test
    fun `the first start explains notifications and continuing starts the session (FR-A063)`() {
        permission.needsGrant = true
        show()

        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("세션 중 남은 시간을 알림으로 보여 드릴게요", substring = true).assertIsDisplayed()
        compose.onNodeWithText("계속").performClick()
        compose.waitForIdle()

        assertThat(started).isTrue()
        assertThat(repository.stored?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `starting without an end is a secondary entry (FR-A005, W01)`() {
        show()

        compose.onNodeWithText("끝을 정하지 않고 시작").performClick()
        compose.waitForIdle()

        assertThat(started).isTrue()
        assertThat(repository.stored?.allocation?.bounded).isFalse()
    }

    @Test
    fun `the start button and time chips follow the design sizes (F17, docs-design)`() {
        show()

        compose.onNodeWithTag(AllocationTags.START).assertHeightIsEqualTo(58.dp)
        compose.onNodeWithTag(AllocationTags.preset(25)).assertHeightIsEqualTo(70.dp).assertWidthIsEqualTo(70.dp)
    }

    @Test
    fun `the SET chip reveals the custom minutes input (W05)`() {
        show()

        compose.onNodeWithTag(AllocationTags.CUSTOM_MINUTES).assertDoesNotExist()
        compose.onNodeWithTag(AllocationTags.CUSTOM).performClick()
        compose.onNodeWithTag(AllocationTags.CUSTOM_MINUTES).performTextInput("50")

        compose.onNodeWithText("50분이면").assertIsDisplayed()
        compose.onNodeWithText("약 13곡").assertIsDisplayed()
    }

    @Test
    fun `the translation card names its basis, the average song length or the loop length (FR-A004, FR-A019)`() {
        runBlocking { settings.update { it.copy(durationPerUnitMs = 300_000) } }
        show()

        compose.onNodeWithText("평균 곡 길이 5분 기준").assertIsDisplayed()
        compose.onNodeWithText("약 5곡").assertIsDisplayed()
    }

    @Test
    fun `single-loop mode translates to loops of the loop length (FR-A019)`() {
        runBlocking { settings.update { it.copy(singleLoop = true, loopDurationMs = 222_000) } }
        show()

        compose.onNodeWithText("약 7바퀴").assertIsDisplayed()
        compose.onNodeWithText("한 바퀴 03:42 기준").assertIsDisplayed()
    }

    @Test
    fun `the gear opens settings and is labelled for screen readers (W01)`() {
        show()

        compose.onNodeWithText("집중 세션").assertIsDisplayed()
        compose.onNodeWithContentDescription("설정").performClick()

        assertThat(settingsOpened).isEqualTo(1)
    }

    @Test
    fun `without a source the music row asks to pick one and opens the music sheet (W01, W09)`() {
        show()

        compose.onNodeWithText("음악 앱 고르기").performClick()

        assertThat(musicOpened).isEqualTo(1)
    }

    @Test
    fun `the music row shows the chosen app and says playback control comes only with counting (ADR-0013)`() {
        runBlocking {
            sources.save(ExternalSource("a", Provider.YOUTUBE_MUSIC, "com.google.android.apps.youtube.music", null, "유튜브뮤직", 0))
            settings.update { it.copy(selectedSourceId = "a", songCountingEnabled = false) }
        }
        show()

        compose.onNodeWithText("유튜브뮤직").assertIsDisplayed()
        compose.onNodeWithText("평균 곡 길이로 추정해요 · 재생은 음악 앱에서 해요").assertIsDisplayed()

        runBlocking { settings.update { it.copy(songCountingEnabled = true) } }
        compose.waitForIdle()
        compose.onNodeWithText("곡을 세고, 세션 화면에서 재생·일시정지할 수 있어요").assertIsDisplayed()
        compose.onNodeWithText("건드리지 않아요", substring = true).assertDoesNotExist()
    }

    private val ytm = ExternalSource("a", Provider.YOUTUBE_MUSIC, "com.google.android.apps.youtube.music", null, "유튜브뮤직", 0)

    @Test
    fun `tapping the linked music app row opens that app, 바꾸기 opens the music choice`() {
        runBlocking {
            sources.save(ytm)
            settings.update { it.copy(selectedSourceId = "a") }
        }
        show()
        compose.waitForIdle()

        compose.onNodeWithText("유튜브뮤직").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(launcher.opened).containsExactly(ytm)
        assertThat(musicOpened).isEqualTo(0)

        compose.onNodeWithText("바꾸기").performScrollTo().performClick()
        assertThat(musicOpened).isEqualTo(1)
    }

    @Test
    fun `without a linked app the row opens the music choice`() {
        show()

        compose.onNodeWithText("음악 앱 고르기").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(musicOpened).isEqualTo(1)
        assertThat(launcher.opened).isEmpty()
    }

    @Test
    fun `if the app cannot be opened the home screen says to open it by hand (FR-A027)`() {
        runBlocking {
            sources.save(ytm)
            settings.update { it.copy(selectedSourceId = "a") }
        }
        launcher.outcome = LaunchOutcome.MANUAL
        show()
        compose.waitForIdle()

        compose.onNodeWithText("유튜브뮤직").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("음악 앱을 직접 열어 주세요").assertExists()
    }
}
