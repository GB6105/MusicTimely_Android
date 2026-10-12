package com.gb6105.musictimely.ui.session

import com.gb6105.musictimely.domain.model.translation.SongCorrection
import com.gb6105.musictimely.domain.model.translation.CorrectionAnchor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.timer.ExtensionStep
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.translation.CueKind
import com.gb6105.musictimely.domain.model.translation.LastSongCue
import com.gb6105.musictimely.domain.model.translation.SongTranslation
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.LaunchOutcome
import com.gb6105.musictimely.domain.repository.MusicControlPort
import com.gb6105.musictimely.domain.repository.MusicLauncherPort
import com.gb6105.musictimely.domain.repository.NoisePort
import com.gb6105.musictimely.domain.repository.SettingsRepository
import com.gb6105.musictimely.domain.repository.SourceRepository
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface SessionUiState {
    data object Loading : SessionUiState

    data class Active(
        val phase: TimerState,
        val taskTitle: String?,
        val elapsedMs: Long,
        val remainingMs: Long?,
        val remainingSongs: Int?,
        val totalSongs: Int?,
        val filledChips: Int,
        val elapsedTenths: Int,
        val cue: CueKind?,
        val music: MusicPanelState = MusicPanelState.Empty,
        val averageSongMinutes: Int = 4,
        val singleLoop: Boolean = false,
        val observedCount: Int? = null,
        val observedSinceMidSession: Boolean = false,
        val showSongs: Boolean = true,
        val stopNotice: StopAtEndNotice? = null,
        val musicStoppedHint: Boolean = false,
        val controlNotice: Boolean = false,
        val launchNotice: Boolean = false,
        val writeFailureNotice: Boolean = false,
        val askStopOtherMusic: Boolean = false,
        val canCorrect: Boolean = false,
        val correctionNotice: Boolean = false,
        /** SCR-02 D0 "약 N번째 곡": estimated current song (or loop), 1-based, after any correction. */
        val currentSong: Int = 1,
    ) : SessionUiState

    data class Finished(val session: TimerSession) : SessionUiState
}

/**
 * SCR-02·03·04. The UI reads derived values at most once per second via [refresh]; timer accuracy comes from
 * the monotonic clock, not from the tick count (SRS v1 §5.2). Timer commands never touch external music.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val dispatch: DispatchTimerCommandUseCase,
    private val clock: ClockPort,
    private val music: MusicControlPort,
    private val settings: SettingsRepository,
    private val sources: SourceRepository,
    private val launcher: MusicLauncherPort,
    private val noise: NoisePort,
) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state

    private var lastSession: TimerSession? = null
    private var currentSessionId: String? = null
    private var launchNotice = false
    private var writeFailureNotice = false
    private var askStopOtherMusic = false
    private var cueSessionId: String? = null
    private var cue: LastSongCue? = null
    private var shownCue: CueKind? = null
    private var correction: CorrectionAnchor? = null
    /** Count saved in the checkpoint (earlier processes included) so a restart never shows zero (emulator finding). */
    private var savedCount: Int? = null

    init {
        viewModelScope.launch {
            combine(music.snapshot, music.stopAtEnd, music.control, settings.settings, sources.sources) { _, _, _, _, _ -> }
                .combine(noise.state) { _, _ -> }
                .collect { lastSession?.let { _state.value = render(it) } }
        }
    }

    suspend fun refresh() {
        dispatch.tick()
        music.onActiveTime()
        val session = repository.active() ?: return
        if (session.sessionId != currentSessionId) startedNewSession(session.sessionId)
        if (repository.consumeWriteFailureNotice()) writeFailureNotice = true
        lastSession = session
        savedCount = repository.countedItems(session.sessionId)
        _state.value = render(session)
    }

    /** Notices, cue and recorded count belong to one session; the ViewModel outlives sessions (Activity scope). */
    private fun startedNewSession(sessionId: String) {
        currentSessionId = sessionId
        launchNotice = false
        writeFailureNotice = false
        askStopOtherMusic = false
        cue = null
        shownCue = null
        cueSessionId = sessionId
        correction = null
    }

    /** The user's music button (FR-A043·A044): pause/play, or open the app when it ignores commands. */
    fun onMusicButton() {
        if (music.control.value.unresponsive) openMusic() else music.onTransportTap()
    }

    /** [열기]: FR-A026 fallback chain; a manual guide never affects the timer (FR-A027). */
    fun openMusic() {
        val source = selectedSource() ?: return
        launchNotice = launcher.open(source) == LaunchOutcome.MANUAL
        rerender()
    }

    /** M-NOISE [재생]/[정지]: app sound starts only from this on-screen tap (FR-A056). */
    fun onNoiseButton() {
        val kind = settings.settings.value.selectedNoise ?: return
        val snapshot = music.snapshot.value
        when {
            noise.state.value.playing -> noise.stop()
            // FR-A059: one source per session — the user decides whether the observed app pauses.
            snapshot.state == ObservationState.OBSERVING && snapshot.isPlaying -> askStopOtherMusic = true
            else -> noise.play(kind)
        }
        rerender()
    }

    /** [멈추기] pauses the observed app through the user's own button path; [그대로] sends nothing. */
    fun answerStopOtherMusic(stop: Boolean) {
        askStopOtherMusic = false
        val kind = settings.settings.value.selectedNoise
        if (stop) music.pauseObserved()
        kind?.let { noise.play(it, keepOtherMusic = !stop) }
        rerender()
    }

    fun stopNow() = music.stopNow()

    fun keepListening() = music.keepListening()

    fun dismissNotices() {
        launchNotice = false
        music.consumeControlNotice()
        rerender()
    }

    private fun rerender() {
        lastSession?.let { _state.value = render(it) }
    }

    private fun selectedSource(): ExternalSource? {
        val id = settings.settings.value.selectedSourceId ?: return null
        return sources.sources.value.firstOrNull { it.id == id }
    }

    suspend fun pause() = command(TimerCommand.Pause(newId()))

    suspend fun resume() = command(TimerCommand.Resume(newId()))

    suspend fun finish() = command(TimerCommand.Finish(newId()))

    suspend fun extend(step: ExtensionStep) = command(TimerCommand.Extend(newId(), step))

    /**
     * FR-A016 in-session correction: moves the song display only; elapsed, target, alarm and cue stay.
     * Hidden while songs are observed (DEC-A11). @return false when hidden or outside 1..999.
     */
    fun correctSong(index: Int): Boolean {
        val session = lastSession ?: return false
        if (music.snapshot.value.state == ObservationState.OBSERVING) return false
        val result = SongCorrection.anchor(index, session.elapsedMs(clock.monotonicNowMs()))
        if (result !is SongCorrection.Result.Applied) return false
        correction = result.anchor
        rerender()
        return true
    }

    fun dismissCue() {
        shownCue = null
        (_state.value as? SessionUiState.Active)?.let { _state.value = it.copy(cue = null) }
    }

    private suspend fun command(command: TimerCommand) {
        dispatch(command)
        refresh()
    }

    private fun render(session: TimerSession): SessionUiState {
        if (session.state == TimerState.ENDED) return SessionUiState.Finished(session)
        if (session.sessionId != cueSessionId) {
            cueSessionId = session.sessionId
            cue = null
            shownCue = null
        }
        val now = clock.monotonicNowMs()
        val allocation = session.allocation
        val unit = allocation.displayUnitMs
        val elapsed = session.elapsedMs(now)
        val remaining = session.remainingMs(now)
        val target = allocation.currentTargetMs
        if (session.state == TimerState.RUNNING && remaining != null) updateCue(allocation.targetRevision, remaining, unit)
        val snapshot = music.snapshot.value
        val observing = snapshot.state == ObservationState.OBSERVING
        // Display-only shift from an in-session correction (FR-A016).
        val shift = correction?.let { (it.anchorIndex - 1) - (it.anchorElapsedMs / unit).toInt() } ?: 0
        val total = target?.let { SongTranslation.allocatedSongs(it, unit) }
        val panel = MusicPanelState.from(settings.settings.value, selectedSource(), snapshot, music.control.value, noise.state.value)
        return SessionUiState.Active(
            phase = session.state,
            taskTitle = session.taskTitle,
            elapsedMs = elapsed,
            remainingMs = remaining,
            remainingSongs = target?.let { (SongTranslation.remainingSongs(it, elapsed, unit) - shift).coerceAtLeast(0) },
            totalSongs = total,
            filledChips = (SongTranslation.filledChips(target, elapsed, unit) + shift).coerceIn(0, total ?: Int.MAX_VALUE),
            elapsedTenths = SongTranslation.elapsedTenths(elapsed, unit),
            cue = shownCue,
            music = panel,
            averageSongMinutes = (settings.settings.value.durationPerUnitMs / 60_000).toInt(),
            singleLoop = allocation.singleLoop,
            observedCount = maxOf(snapshot.countedItems, savedCount ?: 0).takeIf { observing },
            observedSinceMidSession = snapshot.startedMidSession || snapshot.hadGap,
            showSongs = settings.settings.value.showSongs,
            stopNotice = StopAtEndNotice.of(music.stopAtEnd.value).takeIf { session.state == TimerState.LIMIT_REACHED },
            musicStoppedHint = observing && snapshot.showStoppedHint,
            controlNotice = music.control.value.noticePending,
            launchNotice = launchNotice,
            writeFailureNotice = writeFailureNotice,
            askStopOtherMusic = askStopOtherMusic,
            canCorrect = !observing,
            correctionNotice = correction != null,
            currentSong = (SongTranslation.elapsedTenths(elapsed, unit) / 10 + 1 + shift).coerceAtLeast(1),
        )
    }

    private fun updateCue(revision: Int, remainingMs: Long, unitMs: Long) {
        val armed = cue?.takeIf { it.revision == revision } ?: LastSongCue.arm(revision, remainingMs, unitMs)
        val result = armed.evaluate(remainingMs, unitMs, observedItem = null)
        cue = result.next
        result.fired?.let { shownCue = it }
    }

    private fun newId() = UUID.randomUUID().toString()
}
