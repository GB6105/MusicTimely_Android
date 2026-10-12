package com.gb6105.musictimely.ui.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.memo.MemoAdd
import com.gb6105.musictimely.domain.repository.MemoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object MemoTags {
    const val INPUT = "memo_input"
}

enum class MemoError { TOO_LONG, FULL, FAILED }

/** SCR-08 / W07: the timer keeps running; a failed save keeps the input and shows no success (SRS v1 §8). */
@HiltViewModel
class MemoViewModel @Inject constructor(private val memos: MemoRepository) : ViewModel() {
    private val _error = MutableStateFlow<MemoError?>(null)
    val error: StateFlow<MemoError?> = _error

    fun clearError() {
        _error.value = null
    }

    /** @return true when saved. */
    suspend fun save(sessionId: String, text: String): Boolean {
        val result = try {
            memos.add(sessionId, text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _error.value = MemoError.FAILED
            return false
        }
        _error.value = when (result) {
            is MemoAdd.Added, MemoAdd.Blank -> null
            MemoAdd.TooLong -> MemoError.TOO_LONG
            MemoAdd.Full -> MemoError.FULL
        }
        return result is MemoAdd.Added || result == MemoAdd.Blank
    }
}

@Composable
fun MemoRoute(viewModel: MemoViewModel, sessionId: String, onClose: () -> Unit) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(sessionId) { viewModel.clearError() }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.memo_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.memo_subtitle), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag(MemoTags.INPUT),
        )
        Text(stringResource(R.string.memo_running), style = MaterialTheme.typography.bodySmall)
        error?.let {
            Text(
                stringResource(
                    when (it) {
                        MemoError.TOO_LONG -> R.string.memo_too_long
                        MemoError.FULL -> R.string.memo_full
                        MemoError.FAILED -> R.string.memo_failed
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.memo_cancel)) }
            Button(onClick = { scope.launch { if (viewModel.save(sessionId, text)) onClose() } }) {
                Text(stringResource(R.string.memo_save))
            }
        }
    }
}
