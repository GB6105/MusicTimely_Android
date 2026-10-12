package com.gb6105.musictimely.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.CountingAccessPort
import com.gb6105.musictimely.domain.repository.MusicLauncherPort
import com.gb6105.musictimely.domain.repository.NoisePort
import com.gb6105.musictimely.domain.repository.SettingsRepository
import com.gb6105.musictimely.domain.repository.SourceRepository
import com.gb6105.musictimely.domain.usecase.source.RegisterSourceUseCase
import com.gb6105.musictimely.domain.usecase.source.SourceCandidate
import com.gb6105.musictimely.ui.component.displayName
import com.gb6105.musictimely.ui.settings.CountingPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

object MusicSheetTags {
    const val PASTE = "music_sheet_paste"
}

data class MusicSheetUiState(
    val sources: List<ExternalSource> = emptyList(),
    val selectedId: String? = null,
    val installed: List<Provider> = emptyList(),
    val pasteRefused: Boolean = false,
    val showCountingPrompt: Boolean = false,
    val showYtmNotice: Boolean = false,
)

/**
 * SCR-06 / W09. Choosing a source never starts playback (DEC-05). The first external choice explains song
 * counting once (DEC-A01) and YouTube Music's background limit once (FR-A028).
 */
@HiltViewModel
class MusicSheetViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    private val launcher: MusicLauncherPort,
    private val register: RegisterSourceUseCase,
    private val access: CountingAccessPort,
    private val noise: NoisePort,
) : ViewModel() {
    private val _state = MutableStateFlow(MusicSheetUiState(installed = launcher.installedProviders()))
    val state: StateFlow<MusicSheetUiState> = _state

    init {
        viewModelScope.launch {
            combine(sources.sources, settings.settings) { list, s -> list to s.selectedSourceId }.collect { (list, selected) ->
                _state.value = _state.value.copy(sources = list, selectedId = selected)
            }
        }
    }

    suspend fun select(id: String) {
        val source = sources.sources.value.firstOrNull { it.id == id } ?: return
        stopNoiseUnless(kind = null)
        settings.update { it.copy(selectedSourceId = id, selectedNoise = null, noMusic = false) }
        afterExternalChoice(source.provider)
    }

    suspend fun addAppOnly(provider: Provider, label: String) {
        val source = register.saveAppOnly(provider, label)
        select(source.id)
    }

    suspend fun pasteLink(text: String, labelFor: (Provider) -> String) {
        when (val candidate = register.inspectLink(text)) {
            is SourceCandidate.Link -> {
                _state.value = _state.value.copy(pasteRefused = false)
                val source = register.saveLink(candidate, label = "", defaultLabel = labelFor(candidate.provider))
                select(source.id)
            }
            SourceCandidate.OfferAppOnly, SourceCandidate.Rejected -> _state.value = _state.value.copy(pasteRefused = true)
        }
    }

    suspend fun chooseNoMusic() {
        stopNoiseUnless(kind = null)
        settings.update { it.copy(selectedSourceId = null, selectedNoise = null, noMusic = true) }
    }

    /** Choosing noise never starts it; playback begins with the session screen's play tap (DEC-05). */
    suspend fun chooseNoise(kind: NoiseKind) {
        stopNoiseUnless(kind)
        settings.update { it.copy(selectedSourceId = null, selectedNoise = kind, noMusic = false) }
    }

    /** One source per session (FR-A059): leaving built-in noise stops it, the panel that controls it goes away. */
    private fun stopNoiseUnless(kind: NoiseKind?) {
        val state = noise.state.value
        if (state.playing && state.kind != kind) noise.stop()
    }

    suspend fun acceptCountingPrompt() {
        _state.value = _state.value.copy(showCountingPrompt = false)
        settings.update { it.copy(songCountingEnabled = true, permissionPromptShown = true) }
        access.openAccessSettings()
    }

    suspend fun declineCountingPrompt() {
        _state.value = _state.value.copy(showCountingPrompt = false)
        settings.update { it.copy(permissionPromptShown = true) }
    }

    fun dismissYtmNotice() {
        _state.value = _state.value.copy(showYtmNotice = false)
    }

    private suspend fun afterExternalChoice(provider: Provider) {
        val s = settings.settings.value
        val prompt = !s.permissionPromptShown && !access.isGranted()
        val ytm = provider == Provider.YOUTUBE_MUSIC && !s.ytmNoticeShown
        if (ytm) settings.update { it.copy(ytmNoticeShown = true) }
        _state.value = _state.value.copy(showCountingPrompt = prompt, showYtmNotice = ytm || _state.value.showYtmNotice)
    }
}

@Composable
fun MusicSheetRoute(viewModel: MusicSheetViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val defaultLabels = Provider.entries.associateWith { stringResource(R.string.share_default_label, stringResource(it.displayName())) }
    var paste by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.sheet_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.sheet_subtitle), style = MaterialTheme.typography.bodySmall)
        if (state.showYtmNotice) Text(stringResource(R.string.ytm_notice), style = MaterialTheme.typography.bodyMedium)
        if (state.sources.isNotEmpty()) Text(stringResource(R.string.sheet_saved), style = MaterialTheme.typography.titleSmall)
        state.sources.forEach { source ->
            TextButton(onClick = { scope.launch { viewModel.select(source.id) } }) {
                Text(if (source.id == state.selectedId) stringResource(R.string.sheet_selected, source.label) else source.label)
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.sheet_add_apps), style = MaterialTheme.typography.titleSmall)
        state.installed.forEach { provider ->
            val name = stringResource(provider.displayName())
            OutlinedButton(onClick = { scope.launch { viewModel.addAppOnly(provider, name) } }) {
                Text(stringResource(R.string.sheet_add_app, name))
            }
        }
        OutlinedTextField(
            value = paste,
            onValueChange = { paste = it },
            label = { Text(stringResource(R.string.sheet_paste_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(MusicSheetTags.PASTE),
        )
        OutlinedButton(onClick = {
            scope.launch {
                viewModel.pasteLink(paste) { p -> defaultLabels.getValue(p) }
            }
        }) { Text(stringResource(R.string.sheet_paste_save)) }
        if (state.pasteRefused) Text(stringResource(R.string.share_unsupported), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(stringResource(R.string.sheet_noise), style = MaterialTheme.typography.titleSmall)
        NoiseKind.entries.forEach { kind ->
            TextButton(onClick = { scope.launch { viewModel.chooseNoise(kind) } }) { Text(stringResource(kind.displayName())) }
        }
        TextButton(onClick = { scope.launch { viewModel.chooseNoMusic() } }) { Text(stringResource(R.string.sheet_no_music)) }
        Text(stringResource(R.string.sheet_note), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onClose) { Text(stringResource(R.string.sheet_done)) }
    }
    if (state.showCountingPrompt) {
        CountingPrompt(
            onTurnOn = { scope.launch { viewModel.acceptCountingPrompt() } },
            onLater = { scope.launch { viewModel.declineCountingPrompt() } },
        )
    }
}
