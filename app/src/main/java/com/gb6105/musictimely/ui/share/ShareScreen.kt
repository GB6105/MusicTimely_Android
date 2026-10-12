package com.gb6105.musictimely.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.MusicLauncherPort
import com.gb6105.musictimely.domain.repository.SettingsRepository
import com.gb6105.musictimely.domain.usecase.source.RegisterSourceUseCase
import com.gb6105.musictimely.domain.usecase.source.SourceCandidate
import com.gb6105.musictimely.ui.component.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareUiState {
    data object Loading : ShareUiState
    data class Confirm(val provider: Provider, val url: String, val label: String?) : ShareUiState
    data class Refused(val installed: List<Provider>) : ShareUiState
}

/** SCR-12: confirm a shared link (allowlist only) or offer app-only (FR-A023·A024). */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val register: RegisterSourceUseCase,
    private val settings: SettingsRepository,
    private val launcher: MusicLauncherPort,
) : ViewModel() {
    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Loading)
    val state: StateFlow<ShareUiState> = _state

    fun load(sharedText: String) {
        _state.value = when (val candidate = register.inspectSharedText(sharedText)) {
            is SourceCandidate.Link -> ShareUiState.Confirm(candidate.provider, candidate.normalizedUrl, label = null)
            SourceCandidate.OfferAppOnly, SourceCandidate.Rejected -> ShareUiState.Refused(launcher.installedProviders())
        }
    }

    fun onLabelChanged(label: String) = _state.update { (it as? ShareUiState.Confirm)?.copy(label = label) ?: it }

    suspend fun save(defaultLabel: String) {
        val confirm = _state.value as? ShareUiState.Confirm ?: return
        select(register.saveLink(SourceCandidate.Link(confirm.provider, confirm.url), confirm.label ?: defaultLabel, defaultLabel))
    }

    suspend fun saveAppOnly(provider: Provider, label: String) = select(register.saveAppOnly(provider, label))

    private suspend fun select(source: ExternalSource) = settings.update { it.copy(selectedSourceId = source.id) }
}

@Composable
fun ShareRoute(viewModel: ShareViewModel, sharedText: String, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(sharedText) { viewModel.load(sharedText) }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
        when (val s = state) {
            ShareUiState.Loading -> Unit
            is ShareUiState.Confirm -> {
                val service = stringResource(s.provider.displayName())
                val defaultLabel = stringResource(R.string.share_default_label, service)
                Text(service, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = s.label ?: defaultLabel,
                    onValueChange = viewModel::onLabelChanged,
                    label = { Text(stringResource(R.string.share_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            viewModel.save(defaultLabel)
                            onClose()
                        }
                    }) { Text(stringResource(R.string.share_save)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.share_cancel)) }
                }
            }
            is ShareUiState.Refused -> {
                Text(stringResource(R.string.share_unsupported))
                if (s.installed.isEmpty()) Text(stringResource(R.string.share_no_installed), style = MaterialTheme.typography.bodySmall)
                s.installed.forEach { provider ->
                    val name = stringResource(provider.displayName())
                    OutlinedButton(onClick = {
                        scope.launch {
                            viewModel.saveAppOnly(provider, name)
                            onClose()
                        }
                    }) { Text(stringResource(R.string.share_app_only, name)) }
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.share_cancel)) }
            }
        }
    }
}
