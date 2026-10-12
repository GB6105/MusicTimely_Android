package com.gb6105.musictimely.ui.share

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.usecase.source.RegisterSourceUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeMusicLauncher
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** SCR-12 공유 수신 확인. FR-A023·A024, QA-A02·A03. */
@RunWith(AndroidJUnit4::class)
class ShareFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val sources = FakeSourceRepository()
    private val settings = FakeSettingsRepository()
    private val launcher = FakeMusicLauncher()
    private var closed = false

    private fun show(text: String) {
        val viewModel = ShareViewModel(RegisterSourceUseCase(sources, FakeClock()) { "src-1" }, settings, launcher)
        compose.setContent { ShareRoute(viewModel, sharedText = text, onClose = { closed = true }) }
        compose.waitForIdle()
    }

    @Test
    fun `a youtube music playlist share is confirmed and saved with tracking removed (QA-A02)`() {
        show("확인해 보세요 https://music.youtube.com/playlist?list=PL1&si=abc")

        compose.onNodeWithText("유튜브뮤직").assertIsDisplayed()
        compose.onNodeWithText("유튜브뮤직 플레이리스트").assertIsDisplayed()
        compose.onNodeWithText("저장").performClick()
        compose.waitForIdle()

        val saved = sources.sources.value.single()
        assertThat(saved.linkUri).isEqualTo("https://music.youtube.com/playlist?list=PL1")
        assertThat(saved.label).isEqualTo("유튜브뮤직 플레이리스트")
        assertThat(settings.settings.value.selectedSourceId).isEqualTo("src-1")
        assertThat(closed).isTrue()
    }

    @Test
    fun `an unsupported link is refused and offers app-only with installed apps (QA-A03)`() {
        launcher.installed = listOf(Provider.MELON)
        show("https://www.melon.com/playlist/1")

        compose.onNodeWithText("이 링크는 열 수 없어요. 앱만 열기로 저장할까요?").assertIsDisplayed()
        compose.onNodeWithText("멜론 앱만 열기").performClick()
        compose.waitForIdle()

        val saved = sources.sources.value.single()
        assertThat(saved.linkUri).isNull()
        assertThat(saved.provider).isEqualTo(Provider.MELON)
    }

    @Test
    fun `a malicious scheme is refused the same way and nothing is saved by default (QA-A03)`() {
        show("javascript:alert(1)")

        compose.onNodeWithText("이 링크는 열 수 없어요. 앱만 열기로 저장할까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.waitForIdle()

        assertThat(sources.sources.value).isEmpty()
        assertThat(closed).isTrue()
    }
}
