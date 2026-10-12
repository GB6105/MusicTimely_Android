package com.gb6105.musictimely.ui.music

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.usecase.source.RegisterSourceUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeCountingAccess
import com.gb6105.musictimely.testing.FakeMusicLauncher
import com.gb6105.musictimely.testing.FakeNoisePort
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** SCR-06 음악 선택 (W09). FR-A024·A025·A028·A031, DEC-A01, QA-21·QA-A05. */
@RunWith(AndroidJUnit4::class)
class MusicSheetFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val sources = FakeSourceRepository()
    private val settings = FakeSettingsRepository()
    private val launcher = FakeMusicLauncher().apply { installed = listOf(Provider.YOUTUBE_MUSIC, Provider.MELON) }
    private val access = FakeCountingAccess()
    private var nextId = 0
    private val noise = FakeNoisePort()
    private val viewModel = MusicSheetViewModel(sources, settings, launcher, RegisterSourceUseCase(sources, FakeClock()) { "src-${nextId++}" }, access, noise)
    private var closed = false

    private fun show() {
        compose.setContent { MusicSheetRoute(viewModel, onClose = { closed = true }) }
        compose.waitForIdle()
    }

    @Test
    fun `adding an installed app as app-only selects it`() {
        show()

        compose.onNodeWithText("멜론 추가").performScrollTo().performClick()
        compose.waitForIdle()

        val saved = sources.sources.value.single()
        assertThat(saved.provider).isEqualTo(Provider.MELON)
        assertThat(saved.linkUri).isNull()
        assertThat(settings.settings.value.selectedSourceId).isEqualTo(saved.id)
    }

    @Test
    fun `the first external choice explains song counting once, later never again by itself (DEC-A01, QA-A05)`() {
        show()

        compose.onNodeWithText("멜론 추가").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("곡 세기를 켤까요?").assertIsDisplayed()
        compose.onNodeWithText("나중에").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("유튜브뮤직 추가").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("곡 세기를 켤까요?").assertDoesNotExist()
        assertThat(settings.settings.value.permissionPromptShown).isTrue()
    }

    @Test
    fun `youtube music shows the premium notice once without claiming the subscription (FR-A028, QA-21)`() {
        runBlocking { settings.update { it.copy(permissionPromptShown = true) } }
        show()

        compose.onNodeWithText("유튜브뮤직 추가").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("유튜브뮤직은 Premium이 아니면 다른 앱을 쓸 때 음악이 멈출 수 있어요").assertIsDisplayed()

        assertThat(settings.settings.value.ytmNoticeShown).isTrue()
    }

    @Test
    fun `pasting a supported link saves and selects it, an unsupported one is refused (FR-A024)`() {
        runBlocking { settings.update { it.copy(permissionPromptShown = true, ytmNoticeShown = true) } }
        show()

        compose.onNodeWithTag(MusicSheetTags.PASTE).performScrollTo().performTextInput("https://www.melon.com/playlist/1")
        compose.onNodeWithText("링크 저장").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("이 링크는 열 수 없어요. 앱만 열기로 저장할까요?").performScrollTo().assertIsDisplayed()
        assertThat(sources.sources.value).isEmpty()
    }

    @Test
    fun `a pasted allowlisted link is stored normalized`() {
        runBlocking { settings.update { it.copy(permissionPromptShown = true, ytmNoticeShown = true) } }
        show()

        compose.onNodeWithTag(MusicSheetTags.PASTE).performScrollTo().performTextInput("https://open.spotify.com/album/1?si=x")
        compose.onNodeWithText("링크 저장").performScrollTo().performClick()
        compose.waitForIdle()

        val saved = sources.sources.value.single()
        assertThat(saved.linkUri).isEqualTo("https://open.spotify.com/album/1")
        assertThat(saved.label).isEqualTo("스포티파이 플레이리스트")
        assertThat(settings.settings.value.selectedSourceId).isEqualTo(saved.id)
    }

    @Test
    fun `saved sources can be chosen and music can be turned off`() {
        runBlocking {
            sources.save(ExternalSource("a", Provider.MELON, "com.iloen.melon", null, "멜론", 0))
            settings.update { MusicSettings(permissionPromptShown = true) }
        }
        show()

        compose.onNodeWithText("멜론").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(settings.settings.value.selectedSourceId).isEqualTo("a")

        compose.onNodeWithText("음악 없이").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(settings.settings.value.selectedSourceId).isNull()
    }

    @Test
    fun `built-in noise and no music are choices without starting any sound (DEC-05)`() {
        show()

        compose.onNodeWithText("핑크 노이즈").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(settings.settings.value.selectedNoise).isEqualTo(NoiseKind.PINK)
        assertThat(settings.settings.value.selectedSourceId).isNull()

        compose.onNodeWithText("음악 없이").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(settings.settings.value.noMusic).isTrue()
        assertThat(settings.settings.value.selectedNoise).isNull()
    }

    @Test
    fun `choosing another source stops built-in noise that is playing (review 2 M7)`() {
        noise.play(NoiseKind.PINK)
        show()

        compose.onNodeWithText("음악 없이").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(noise.state.value.playing).isFalse()
    }
}
