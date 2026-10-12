package com.gb6105.musictimely.ui.settings

import com.gb6105.musictimely.domain.model.audio.NoiseSound
import com.gb6105.musictimely.domain.model.allocation.LoopInput
import com.gb6105.musictimely.domain.model.allocation.SongLength
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.repository.CountingAccessPort
import com.gb6105.musictimely.domain.repository.SettingsRepository
import com.gb6105.musictimely.domain.repository.SourceRepository
import com.gb6105.musictimely.domain.usecase.settings.DeleteAllDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val countingOn: Boolean = false,
    val accessGranted: Boolean = false,
    val stopAtEndOn: Boolean = false,
    val stopAtEndEnabledInput: Boolean = false,
    val pauseTimerWithMusicOn: Boolean = false,
    val showSongs: Boolean = true,
    val averageMinutes: Int = 4,
    val sources: List<ExternalSource> = emptyList(),
    val showCountingPrompt: Boolean = false,
    val showSystemPermissionGuide: Boolean = false,
    val sound: NoiseSound = NoiseSound(),
    val singleLoop: Boolean = false,
    val loopDurationMs: Long? = null,
)

/** SCR-09 settings and the SCR-11 song counting prompt (DEC-A01: no automatic re-prompt after "나중에"). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sources: SourceRepository,
    private val access: CountingAccessPort,
    private val deleteAll: DeleteAllDataUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state
    private var showPrompt = false
    private var showGuide = false

    init {
        viewModelScope.launch {
            combine(settings.settings, sources.sources) { s, list -> s to list }.collect { (s, list) -> render(s, list) }
        }
    }

    /** Re-checks the system permission, e.g. when returning from system settings (FR-A030). */
    fun onResume() = render()

    suspend fun setCounting(on: Boolean) {
        when {
            !on -> {
                settings.update { it.copy(songCountingEnabled = false) }
                showGuide = true
            }
            access.isGranted() -> settings.update { it.copy(songCountingEnabled = true) }
            else -> showPrompt = true
        }
        render()
    }

    /** SCR-11 [켜기]: the in-app switch is on; observation starts once the system permission is granted. */
    suspend fun acceptCountingPrompt() {
        showPrompt = false
        settings.update { it.copy(songCountingEnabled = true, permissionPromptShown = true) }
        access.openAccessSettings()
        render()
    }

    /** SCR-11 [나중에]. */
    suspend fun declineCountingPrompt() {
        showPrompt = false
        settings.update { it.copy(permissionPromptShown = true) }
        render()
    }

    suspend fun setStopAtEnd(on: Boolean) {
        if (on && !settings.settings.value.songCountingEnabled) return
        settings.update { it.copy(stopAtEndEnabled = on) }
        render()
    }

    /** DEC-A17 (Android only, off by default): needs song counting, like stop-at-end. */
    suspend fun setPauseTimerWithMusic(on: Boolean) {
        if (on && !settings.settings.value.songCountingEnabled) return
        settings.update { it.copy(pauseTimerWithMusic = on) }
        render()
    }

    suspend fun setAverageMinutes(minutes: Int) {
        if (minutes !in AVERAGE_RANGE) return
        settings.update { it.copy(durationPerUnitMs = minutes * 60_000L) }
        render()
    }

    suspend fun setShowSongs(on: Boolean) {
        settings.update { it.copy(showSongs = on) }
        render()
    }

    /** SRS v1 §6.4: applies from the next session; a live session keeps its snapshot. */
    suspend fun setSingleLoop(on: Boolean) {
        settings.update { it.copy(singleLoop = on) }
        render()
    }

    /** "mm:ss", 1..30 minutes; blank uses the average song length. @return false when invalid (value kept). */
    suspend fun setLoopLength(text: String): Boolean {
        val length = when (val parsed = SongLength.parseLoop(text)) {
            is LoopInput.Valid -> parsed.durationMs
            LoopInput.Blank -> null
            LoopInput.Invalid -> return false
        }
        settings.update { it.copy(loopDurationMs = length) }
        render()
        return true
    }

    /** FR-A070: out-of-range values are rejected and the stored value is kept. */
    suspend fun setNoiseVolume(volume: Float): Boolean = updateSound(NoiseSound.isValidVolume(volume)) { it.copy(volume = volume) }

    suspend fun setFadeInMs(ms: Long): Boolean = updateSound(NoiseSound.isValidFadeIn(ms)) { it.copy(fadeInMs = ms) }

    suspend fun setFadeOutMs(ms: Long): Boolean = updateSound(NoiseSound.isValidFadeOut(ms)) { it.copy(fadeOutMs = ms) }

    suspend fun resetSound() {
        updateSound(valid = true) { NoiseSound() }
    }

    private suspend fun updateSound(valid: Boolean, change: (NoiseSound) -> NoiseSound): Boolean {
        if (!valid) return false
        settings.update { it.copy(sound = change(it.sound)) }
        render()
        return true
    }

    suspend fun deleteSource(id: String) {
        sources.delete(id)
        settings.update { if (it.selectedSourceId == id) it.copy(selectedSourceId = null) else it }
        render()
    }

    suspend fun deleteAllData() {
        deleteAll()
        showGuide = true
        render()
    }

    fun dismissGuide() {
        showGuide = false
        render()
    }

    private fun render(s: MusicSettings = settings.settings.value, list: List<ExternalSource> = sources.sources.value) {
        _state.value = SettingsUiState(
            countingOn = s.songCountingEnabled,
            accessGranted = access.isGranted(),
            stopAtEndOn = s.stopAtEndEnabled,
            stopAtEndEnabledInput = s.songCountingEnabled,
            pauseTimerWithMusicOn = s.pauseTimerWithMusic && s.songCountingEnabled,
            showSongs = s.showSongs,
            averageMinutes = (s.durationPerUnitMs / 60_000).toInt(),
            sources = list,
            showCountingPrompt = showPrompt,
            showSystemPermissionGuide = showGuide,
            sound = s.sound,
            singleLoop = s.singleLoop,
            loopDurationMs = s.loopDurationMs,
        )
    }

    private companion object {
        val AVERAGE_RANGE = 1..30
    }
}
