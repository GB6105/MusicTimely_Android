package com.gb6105.musictimely.ui.shareimage

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.repository.ShareImagePort
import com.gb6105.musictimely.ui.component.PillButton
import com.gb6105.musictimely.ui.component.PrimaryButton
import com.gb6105.musictimely.ui.result.totalTimeText
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** F18 M6 preview (DEC-A22). [image] is the PNG that will be sent, exactly. */
class SharePreviewUiState(
    val card: ShareCard? = null,
    val image: ByteArray? = null,
    val status: Status = Status.PREPARING,
) {
    enum class Status { PREPARING, READY, IMAGE_FAILED, NO_TARGET }

    val canSend: Boolean get() = image != null && (status == Status.READY || status == Status.NO_TARGET)
}

/** Renders the card for the preview and sends it only on the user's tap (DEC-A18: no automatic upload). */
@HiltViewModel
class SharePreviewViewModel @Inject constructor(private val images: ShareImagePort) : ViewModel() {
    private val _state = MutableStateFlow(SharePreviewUiState())
    val state: StateFlow<SharePreviewUiState> = _state

    /** Renders afresh each time the preview opens (the file is overwritten). */
    fun load(card: ShareCard) {
        _state.value = SharePreviewUiState(card = card)
        viewModelScope.launch {
            val image = images.prepare(card)
            if (_state.value.card != card) return@launch
            _state.value = SharePreviewUiState(
                card = card,
                image = image,
                status = if (image == null) SharePreviewUiState.Status.IMAGE_FAILED else SharePreviewUiState.Status.READY,
            )
        }
    }

    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val status = if (images.send() == null) SharePreviewUiState.Status.NO_TARGET else SharePreviewUiState.Status.READY
        _state.value = SharePreviewUiState(current.card, current.image, status)
    }
}

object SharePreviewTags {
    const val IMAGE = "share_preview_image"
    const val MESSAGE = "share_preview_message"
}

@Composable
fun SharePreviewRoute(viewModel: SharePreviewViewModel, card: ShareCard, onBack: () -> Unit) {
    LaunchedEffect(card) { viewModel.load(card) }
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    SharePreviewScreen(state, onSend = viewModel::send, onBack = onBack)
}

/**
 * F18 M6 (DEC-A22): the image scaled to fit, [인스타 스토리로 보내기] and [돌아가기]. Screen readers hear a summary
 * (total time, song count). Failures show one short line; the timer and result are untouched.
 */
@Composable
fun SharePreviewScreen(state: SharePreviewUiState, onSend: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(state.image) { state.image?.let(::decode) }
    Column(modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = MtDimens.ScreenPadding)
                .padding(top = 16.dp),
        ) {
            if (bitmap != null && state.card != null) {
                Image(
                    bitmap,
                    contentDescription = stringResource(
                        R.string.share_preview_description,
                        totalTimeText(state.card.totalTime),
                        state.card.songCount,
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().testTag(SharePreviewTags.IMAGE),
                )
            } else if (state.status == SharePreviewUiState.Status.PREPARING) {
                Text(stringResource(R.string.share_preview_preparing), style = MtType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MtDimens.ScreenPadding, vertical = 12.dp),
        ) {
            message(state.status)?.let {
                Text(
                    stringResource(it),
                    style = MtType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(SharePreviewTags.MESSAGE).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(stringResource(R.string.share_preview_send), onClick = onSend, enabled = state.canSend)
            PillButton(stringResource(R.string.share_preview_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun message(status: SharePreviewUiState.Status): Int? = when (status) {
    SharePreviewUiState.Status.IMAGE_FAILED -> R.string.share_preview_image_failed
    SharePreviewUiState.Status.NO_TARGET -> R.string.share_preview_no_target
    SharePreviewUiState.Status.PREPARING, SharePreviewUiState.Status.READY -> null
}

private fun decode(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
