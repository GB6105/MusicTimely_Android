package com.gb6105.musictimely.ui.allocation

import com.gb6105.musictimely.domain.repository.LaunchOutcome
import com.gb6105.musictimely.domain.repository.MusicLauncherPort
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import androidx.lifecycle.ViewModel
import com.gb6105.musictimely.domain.model.allocation.AllocationInput
import com.gb6105.musictimely.domain.model.allocation.MinutesInput
import com.gb6105.musictimely.domain.model.allocation.TaskTitle
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.translation.SongTranslation
import com.gb6105.musictimely.domain.repository.NotificationPermissionPort
import com.gb6105.musictimely.domain.repository.SettingsRepository
import com.gb6105.musictimely.domain.repository.SourceRepository
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.domain.usecase.session.OpenSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import com.gb6105.musictimely.domain.model.source.ExternalSource
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.util.UUID
import javax.inject.Inject

data class AllocationUiState(
    val titleInput: String = "",
    val selectedPreset: Int? = AllocationInput.DEFAULT_MINUTES,
    val customInput: String = "",
    val customError: Boolean = false,
    val minutes: Int? = AllocationInput.DEFAULT_MINUTES,
    val songs: Int? = songsFor(AllocationInput.DEFAULT_MINUTES),
    val showNotificationPrompt: Boolean = false,
    val singleLoop: Boolean = false,
    /** FR-A004·A019: the length one song (or loop) stands for — the translation basis shown on the card. */
    val unitMs: Long = Allocation.DEFAULT_UNIT_MS,
    val music: AllocationMusic = AllocationMusic.Unset,
    val launchNotice: Boolean = false,
) {
    val canStart: Boolean get() = minutes != null

    companion object {
        private const val MINUTE_MS = 60_000L

        fun songsFor(minutes: Int, unitMs: Long = Allocation.DEFAULT_UNIT_MS): Int =
            SongTranslation.allocatedSongs(minutes * MINUTE_MS, unitMs)
    }
}

/** SCR-01 music row: the user's chosen source, in the same priority as the session music panel. */
sealed interface AllocationMusic {
    data object Unset : AllocationMusic
    data class External(val label: String, val counting: Boolean) : AllocationMusic
    data class Noise(val kind: NoiseKind) : AllocationMusic
    data object NoMusic : AllocationMusic
}

/** SCR-01: time first, translated to songs at once (D0). Never asks how many songs the task takes. */
@HiltViewModel
class AllocationViewModel @Inject constructor(
    private val openSession: OpenSessionUseCase,
    private val dispatch: DispatchTimerCommandUseCase,
    private val settings: SettingsRepository,
    private val notifications: NotificationPermissionPort,
    private val sources: SourceRepository,
    private val launcher: MusicLauncherPort,
) : ViewModel() {
    private val _state = MutableStateFlow(AllocationUiState())
    private var pendingOpenEnded = false

    init {
        viewModelScope.launch {
            combine(settings.settings, sources.sources) { s, saved -> s to saved }.collect { (s, saved) ->
                _state.update {
                    it.copy(
                        singleLoop = s.singleLoop,
                        unitMs = unitMs(s),
                        songs = it.minutes?.let { m -> AllocationUiState.songsFor(m, unitMs(s)) },
                        music = musicOf(s, saved),
                    )
                }
            }
        }
    }
    val state: StateFlow<AllocationUiState> = _state

    fun onTitleChanged(text: String) = _state.update { it.copy(titleInput = text) }

    fun onPresetSelected(minutes: Int) = _state.update {
        it.copy(selectedPreset = minutes, customInput = "", customError = false, minutes = minutes, songs = songsFor(minutes), singleLoop = singleLoop())
    }

    /**
     * FR-A004: the current average song length, snapshotted per session. SRS v1 §6.4: in single-loop mode the
     * unit is the loop length (or the average when blank).
     */
    private fun unitMs(s: MusicSettings = settings.settings.value) =
        if (s.singleLoop) s.loopDurationMs ?: s.durationPerUnitMs else s.durationPerUnitMs

    /**
     * Home music row: with a linked music app, opens that app itself (FR-A026 fallback chain); a manual-open
     * outcome is told on screen (FR-A027). @return false when there is no linked app, so the UI shows the choice.
     */
    fun onMusicRow(): Boolean {
        val s = settings.settings.value
        if (s.selectedNoise != null || s.noMusic) return false
        val source = sources.sources.value.firstOrNull { it.id == s.selectedSourceId } ?: return false
        val outcome = launcher.open(source)
        _state.update { it.copy(launchNotice = outcome == LaunchOutcome.MANUAL) }
        return true
    }

    private fun musicOf(s: MusicSettings, saved: List<ExternalSource>): AllocationMusic {
        s.selectedNoise?.let { return AllocationMusic.Noise(it) }
        if (s.noMusic) return AllocationMusic.NoMusic
        val source = saved.firstOrNull { it.id == s.selectedSourceId } ?: return AllocationMusic.Unset
        return AllocationMusic.External(source.label, counting = s.songCountingEnabled)
    }

    private fun loopMs() = settings.settings.value.let { if (it.singleLoop) it.loopDurationMs else null }

    private fun singleLoop() = settings.settings.value.singleLoop

    private fun songsFor(minutes: Int) = AllocationUiState.songsFor(minutes, unitMs())

    fun onCustomMinutesChanged(text: String) = _state.update {
        when (val parsed = AllocationInput.parseMinutes(text)) {
            is MinutesInput.Valid -> it.copy(
                selectedPreset = null, customInput = text, customError = false,
                minutes = parsed.minutes, songs = songsFor(parsed.minutes),
            )
            MinutesInput.Invalid -> it.copy(
                selectedPreset = null, customInput = text, customError = text.isNotBlank(), minutes = null, songs = null,
            )
        }
    }

    /** W01 "끝을 정하지 않고 시작" (FR-A005, DEC-04 secondary entry): no target, no cue, no stop-at-end. */
    suspend fun startOpenEnded(): Boolean {
        pendingOpenEnded = true
        if (needsNotificationPrompt()) {
            _state.update { it.copy(showNotificationPrompt = true) }
            return false
        }
        return startSession()
    }

    /** After the explanation: the OS request runs in the UI; the session starts whatever the answer. */
    suspend fun continueAfterNotificationPrompt(): Boolean {
        settings.update { it.copy(notificationPromptShown = true) }
        _state.update { it.copy(showNotificationPrompt = false) }
        return startSession()
    }

    /**
     * FR-A063: the first start on Android 13+ explains notifications once before asking; never again.
     * @return false when the input is invalid or the explanation is shown first.
     */
    suspend fun start(): Boolean {
        if (_state.value.minutes == null) return false
        pendingOpenEnded = false
        if (needsNotificationPrompt()) {
            _state.update { it.copy(showNotificationPrompt = true) }
            return false
        }
        return startSession()
    }

    private fun needsNotificationPrompt() =
        notifications.needsRuntimeGrant() && !settings.settings.value.notificationPromptShown

    private suspend fun startSession(): Boolean {
        val current = _state.value
        val allocation = if (pendingOpenEnded) {
            Allocation.infinite(durationPerUnitMs = settings.settings.value.durationPerUnitMs, loopDurationMs = loopMs(), singleLoop = singleLoop())
        } else {
            Allocation.bounded(
                (current.minutes ?: return false) * 60_000L,
                durationPerUnitMs = settings.settings.value.durationPerUnitMs,
                loopDurationMs = loopMs(),
                singleLoop = singleLoop(),
            )
        }
        openSession(allocation, TaskTitle.from(current.titleInput))
        dispatch(TimerCommand.Start(UUID.randomUUID().toString()))
        return true
    }
}
