package com.gb6105.musictimely.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.gb6105.musictimely.domain.model.audio.NoiseSound
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.usecase.settings.DeleteAllDataUseCase
import com.gb6105.musictimely.testing.FakeCheckpointStore
import com.gb6105.musictimely.testing.FakeCountingAccess
import com.gb6105.musictimely.testing.FakeMemoRepository
import com.gb6105.musictimely.testing.FakePlaylistRepository
import com.gb6105.musictimely.testing.FakeShareImagePort
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** SCR-09 설정 + SCR-11 곡 세기 안내. FR-A031·A041·A047·A071, QA-A05·A28·A38. */
@RunWith(AndroidJUnit4::class)
class SettingsFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val settings = FakeSettingsRepository()
    private val sources = FakeSourceRepository()
    private val access = FakeCountingAccess()
    private val store = FakeCheckpointStore()
    private val viewModel = SettingsViewModel(settings, sources, access, DeleteAllDataUseCase(store, settings, sources, FakeTimerEffectSink(), FakeMemoRepository(), FakeSessionRepository(), FakePlaylistRepository(), FakeShareImagePort()))

    private fun show() {
        compose.setContent { SettingsRoute(viewModel, onBack = {}, onSeen = {}) }
        compose.waitForIdle()
    }

    @Test
    fun `stop-at-end is disabled with its reason while counting is off (FR-A047)`() {
        show()

        // Stop-at-end and the DEC-A17 switch share the same reason line.
        compose.onAllNodesWithText("곡 세기를 켜야 쓸 수 있어요").onFirst().assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.STOP_AT_END).assertIsNotEnabled()
    }

    @Test
    fun `turning counting on shows the prompt that explains the system warning first (SCR-11)`() {
        show()

        compose.onNodeWithTag(SettingsTags.COUNTING).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("곡 세기를 켤까요?").assertIsDisplayed()
        compose.onNodeWithText("다른 앱의 알림 내용은 읽지 않아요", substring = true).assertIsDisplayed()
        compose.onNodeWithText("켜기").performClick()
        compose.waitForIdle()

        assertThat(access.settingsOpened).isEqualTo(1)
    }

    @Test
    fun `later closes the prompt without opening settings (QA-A05)`() {
        show()
        compose.onNodeWithTag(SettingsTags.COUNTING).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("나중에").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("곡 세기를 켤까요?").assertDoesNotExist()
        assertThat(access.settingsOpened).isEqualTo(0)
    }

    @Test
    fun `turning counting off explains how to turn the system permission off (FR-A041, QA-A28)`() {
        access.granted = true
        runBlocking { settings.update { it.copy(songCountingEnabled = true) } }
        show()

        compose.onNodeWithTag(SettingsTags.COUNTING).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("시스템 설정 > 알림 접근에서 뮤직 타이머를 끄면 권한도 해제돼요").assertIsDisplayed()
    }

    @Test
    fun `saved music apps are listed and can be deleted (MT-S5)`() {
        runBlocking { sources.save(ExternalSource("a", Provider.MELON, "com.iloen.melon", null, "멜론", 0)) }
        show()

        compose.onNodeWithText("멜론").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("삭제").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(sources.sources.value).isEmpty()
    }

    @Test
    fun `deleting all data asks once, clears and explains the permission (FR-A071, QA-A38)`() {
        show()

        compose.onNodeWithText("데이터 전체 삭제").performScrollTo().performClick()
        compose.onNodeWithText("모든 데이터를 지울까요?").assertIsDisplayed()
        compose.onNodeWithText("지우기").performClick()
        compose.waitForIdle()

        assertThat(store.cleared).isEqualTo(1)
        compose.onNodeWithText("시스템 설정 > 알림 접근에서 뮤직 타이머를 끄면 권한도 해제돼요").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `built-in sound volume and fades can be adjusted and reset (SCR-09, FR-A070)`() {
        show()

        compose.onNodeWithTag("settings_volume_up").performScrollTo().performClick()
        compose.onNodeWithTag("settings_fade_in_up").performScrollTo().performClick()
        compose.onNodeWithTag("settings_fade_out_up").performScrollTo().performClick()
        compose.waitForIdle()

        // Value rows: title on the left, value on the right.
        compose.onNodeWithText("내장 소리 볼륨").assertExists()
        compose.onNodeWithText("30%").assertExists()
        compose.onNodeWithText("시작 페이드").assertExists()
        compose.onNodeWithText("2.0초").assertExists()
        compose.onNodeWithText("끝 페이드").assertExists()
        compose.onNodeWithText("4초").assertExists()
        assertThat(settings.settings.value.sound).isEqualTo(NoiseSound(0.3f, 2_000, 4_000))

        compose.onNodeWithTag("settings_sound_reset").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(settings.settings.value.sound).isEqualTo(NoiseSound())
    }

    @Test
    fun `single-loop mode and its loop length are set for the next session (SCR-09, FR-A019)`() {
        show()

        compose.onNodeWithTag("settings_single_loop").performScrollTo().performClick()
        compose.onNodeWithTag("settings_loop_length").performScrollTo().performTextInput("30:01")
        compose.onNodeWithTag("settings_loop_apply").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1분에서 30분 사이 분:초로 입력해 주세요").assertExists()
        assertThat(settings.settings.value.loopDurationMs).isNull()

        compose.onNodeWithTag("settings_loop_length").performTextClearance()
        compose.onNodeWithTag("settings_loop_length").performTextInput("03:42")
        compose.onNodeWithTag("settings_loop_apply").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(settings.settings.value.singleLoop).isTrue()
        assertThat(settings.settings.value.loopDurationMs).isEqualTo(222_000)
        compose.onNodeWithText("1분에서 30분 사이 분:초로 입력해 주세요").assertDoesNotExist()
    }

    @Test
    fun `the music-pauses-timer switch is off and disabled until counting is on (DEC-A17)`() {
        show()

        compose.onNodeWithTag("settings_pause_with_music").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("음악을 멈추면 타이머도 잠시 멈추기").assertExists()
    }

    // ---- Figma settings visual language: headed card sections (A2 settings restyle) ----

    @Test
    fun `settings are grouped under section headings (SCR-09 restyle)`() {
        show()

        listOf("곡 세기와 음악 연동", "세션 표시", "음악과 소리", "알림과 개인정보").forEach { heading ->
            compose.onNode(hasText(heading) and isHeading()).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `the footer note says what the app controls and what stays in the music app`() {
        show()

        compose.onNodeWithText("곡 세기를 켜면 세션 화면에서 음악을 재생·일시정지할 수 있어요. 볼륨과 곡 넘기기는 음악 앱에서 해 주세요.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `every switch still changes its setting (SCR-09)`() {
        access.granted = true
        runBlocking { settings.update { it.copy(songCountingEnabled = true) } }
        show()

        compose.onNodeWithTag(SettingsTags.COUNTING).assertIsOn()
        compose.onNodeWithTag(SettingsTags.STOP_AT_END).performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag(SettingsTags.PAUSE_WITH_MUSIC).performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag(SettingsTags.SHOW_SONGS).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag(SettingsTags.SINGLE_LOOP).performScrollTo().assertIsOff().performClick()
        compose.waitForIdle()

        val saved = settings.settings.value
        assertThat(saved.stopAtEndEnabled).isTrue()
        assertThat(saved.pauseTimerWithMusic).isTrue()
        assertThat(saved.showSongs).isFalse()
        assertThat(saved.singleLoop).isTrue()
        compose.onNodeWithTag(SettingsTags.STOP_AT_END).assertIsOn()
    }

    @Test
    fun `the volume slider sets the built-in sound volume and shows the percentage (FR-A070)`() {
        show()

        compose.onNodeWithTag(SettingsTags.VOLUME_SLIDER).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.6f) }
        compose.waitForIdle()

        assertThat(settings.settings.value.sound.volume).isWithin(0.001f).of(0.6f)
        compose.onNodeWithText("60%").assertExists()
    }

    @Test
    fun `average song length is a value row with steppers (SCR-09)`() {
        show()

        compose.onNodeWithText("평균 곡 길이").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.AVERAGE_UP).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("5분").assertExists()
        assertThat(settings.settings.value.durationPerUnitMs).isEqualTo(5 * 60_000L)
    }
}
