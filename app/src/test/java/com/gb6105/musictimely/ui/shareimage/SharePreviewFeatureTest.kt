package com.gb6105.musictimely.ui.shareimage

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.model.shareimage.ShareSong
import com.gb6105.musictimely.testing.FakeShareImagePort
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** F18 M6 공유 미리보기 (DEC-A22): 보낼 이미지, [인스타 스토리로 보내기]·[돌아가기], 낭독 요약, 실패 안내, 글꼴 200%, 48dp. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharePreviewFeatureTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val png = ByteArrayOutputStream().also { createBitmap(54, 96).apply { eraseColor(android.graphics.Color.LTGRAY) }.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    private val port = FakeShareImagePort(image = png)
    private val card = ShareCard(TotalTime(0, 32, 18), (1..8).map { ShareSong("Song $it", "June Archive") }, moreCount = 3)
    private var backs = 0

    private fun show(fontScale: Float = 1f) {
        val viewModel = SharePreviewViewModel(port)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SharePreviewRoute(viewModel, card, onBack = { backs++ })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the preview shows the image to send with send and back buttons`() {
        show()

        compose.onNodeWithTag(SharePreviewTags.IMAGE).assertIsDisplayed()
        compose.onNodeWithText("인스타 스토리로 보내기").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("돌아가기").assertIsDisplayed()
        assertThat(port.prepared).containsExactly(card)
    }

    @Test
    fun `screen readers hear the total time and how many songs the image holds`() {
        show()

        compose.onNodeWithContentDescription("공유할 이미지예요. 총 걸린 시간 32분 18초, 곡 11개가 담겨 있어요").assertIsDisplayed()
    }

    @Test
    fun `the image is sent only when the user taps send`() {
        show()
        assertThat(port.sent).isEqualTo(0)

        compose.onNodeWithText("인스타 스토리로 보내기").performClick()

        assertThat(port.sent).isEqualTo(1)
    }

    @Test
    fun `돌아가기 closes the preview without sending`() {
        show()

        compose.onNodeWithText("돌아가기").performClick()

        assertThat(backs).isEqualTo(1)
        assertThat(port.sent).isEqualTo(0)
    }

    @Test
    fun `system back closes the preview too`() {
        show()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        assertThat(backs).isEqualTo(1)
        assertThat(port.sent).isEqualTo(0)
    }

    @Test
    fun `an image failure says so briefly and send stays off (MT-T7)`() {
        port.image = null
        show()

        compose.onNodeWithText("공유 이미지를 만들지 못했어요. 타이머와 결과는 그대로예요").assertIsDisplayed()
        compose.onNodeWithText("인스타 스토리로 보내기").assertIsNotEnabled()
        compose.onNodeWithTag(SharePreviewTags.IMAGE).assertDoesNotExist()
    }

    @Test
    fun `no app to send to says so and keeps the preview`() {
        port.target = null
        show()

        compose.onNodeWithText("인스타 스토리로 보내기").performClick()

        compose.onNodeWithText("보낼 수 있는 앱을 찾지 못했어요").assertIsDisplayed()
        compose.onNodeWithTag(SharePreviewTags.IMAGE).assertIsDisplayed()
    }

    @Test
    fun `at 200 percent font the image and both buttons stay on screen with 48dp targets`() {
        show(fontScale = 2f)

        val rootBottom = compose.onRoot().getBoundsInRoot().bottom
        compose.onNodeWithTag(SharePreviewTags.IMAGE).assertIsDisplayed()
        listOf("인스타 스토리로 보내기", "돌아가기").forEach { label ->
            val button = compose.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            assertThat(button.getBoundsInRoot().bottom).isAtMost(rootBottom)
        }
    }
}
