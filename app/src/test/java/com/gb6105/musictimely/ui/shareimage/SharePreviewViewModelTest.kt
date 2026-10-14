package com.gb6105.musictimely.ui.shareimage

import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.model.shareimage.ShareSong
import com.gb6105.musictimely.domain.repository.ShareTarget
import com.gb6105.musictimely.testing.FakeShareImagePort
import com.gb6105.musictimely.testutil.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test

/** F18 M6 미리보기 상태 (DEC-A22): 만들기 → 보이기 → 사용자 탭으로만 보내기, 실패는 짧은 안내만. */
class SharePreviewViewModelTest {

    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

    private val port = FakeShareImagePort(image = byteArrayOf(7, 7))
    private val viewModel = SharePreviewViewModel(port)
    private val card = ShareCard(TotalTime(0, 32, 18), listOf(ShareSong("Soft Landing", "June Archive")), 0)

    @Test
    fun `loading renders the card and shows its image`() {
        viewModel.load(card)
        assertThat(viewModel.state.value.status).isEqualTo(SharePreviewUiState.Status.PREPARING)

        main.dispatcher.scheduler.advanceUntilIdle()

        assertThat(port.prepared).containsExactly(card)
        assertThat(viewModel.state.value.status).isEqualTo(SharePreviewUiState.Status.READY)
        assertThat(viewModel.state.value.image).isEqualTo(byteArrayOf(7, 7))
        assertThat(viewModel.state.value.card).isEqualTo(card)
    }

    @Test
    fun `nothing is sent while the image is being made`() {
        viewModel.load(card)

        viewModel.send()

        assertThat(port.sent).isEqualTo(0)
    }

    @Test
    fun `sending happens only on the user's send, once per tap`() {
        viewModel.load(card)
        main.dispatcher.scheduler.advanceUntilIdle()
        assertThat(port.sent).isEqualTo(0)

        viewModel.send()

        assertThat(port.sent).isEqualTo(1)
        assertThat(viewModel.state.value.status).isEqualTo(SharePreviewUiState.Status.READY)
    }

    @Test
    fun `an image failure shows a message and never sends (MT-T7)`() {
        port.image = null
        viewModel.load(card)
        main.dispatcher.scheduler.advanceUntilIdle()

        viewModel.send()

        assertThat(viewModel.state.value.status).isEqualTo(SharePreviewUiState.Status.IMAGE_FAILED)
        assertThat(viewModel.state.value.image).isNull()
        assertThat(port.sent).isEqualTo(0)
    }

    @Test
    fun `no app to send to keeps the preview with a message, and a retry can still send`() {
        port.target = null
        viewModel.load(card)
        main.dispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        assertThat(viewModel.state.value.status).isEqualTo(SharePreviewUiState.Status.NO_TARGET)
        assertThat(viewModel.state.value.image).isNotNull()

        port.target = ShareTarget.CHOOSER
        viewModel.send()
        assertThat(viewModel.state.value.status).isEqualTo(SharePreviewUiState.Status.READY)
        assertThat(port.sent).isEqualTo(2)
    }

    @Test
    fun `opening again renders afresh, replacing the previous image`() {
        viewModel.load(card)
        main.dispatcher.scheduler.advanceUntilIdle()
        val other = card.copy(totalTime = TotalTime(0, 1, 0))
        port.image = byteArrayOf(9)

        viewModel.load(other)
        main.dispatcher.scheduler.advanceUntilIdle()

        assertThat(port.prepared).containsExactly(card, other).inOrder()
        assertThat(viewModel.state.value.image).isEqualTo(byteArrayOf(9))
        assertThat(viewModel.state.value.card).isEqualTo(other)
    }
}
