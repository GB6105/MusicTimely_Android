package com.gb6105.musictimely.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.session.RestoreDecision
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.domain.repository.MemoRepository
import com.gb6105.musictimely.domain.repository.MusicControlPort
import com.gb6105.musictimely.domain.repository.ObservedCount
import com.gb6105.musictimely.domain.repository.PlaylistRepository
import com.gb6105.musictimely.domain.repository.ShareImagePort
import com.gb6105.musictimely.domain.usecase.session.ReconcileSessionUseCase
import com.gb6105.musictimely.domain.usecase.session.RestoreSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface AppRoute {
    data object Loading : AppRoute
    data object Allocation : AppRoute
    data object Session : AppRoute
    data class Result(
        val session: TimerSession,
        val observed: ObservedCount? = null,
        val memos: List<String> = emptyList(),
        val playlist: List<PlaylistEntry> = emptyList(),
    ) : AppRoute
    data class Recovery(val session: TimerSession) : AppRoute
    data object Corrupt : AppRoute
}

/** Screens shown above the current route; closing returns to it (W05·W07·W09 style returns). */
sealed interface Overlay {
    data class Settings(val enableCounting: Boolean = false) : Overlay
    data object MusicSheet : Overlay
    data object Seen : Overlay
    data class Memo(val sessionId: String) : Overlay

    /** F18 M6 (DEC-A22): preview of the result's share image. */
    data class SharePreview(val card: ShareCard) : Overlay
}

/** Top-level route from the restored session state (User Flow 4:1327). */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val restore: RestoreSessionUseCase,
    private val repository: SessionRepository,
    private val dispatch: DispatchTimerCommandUseCase,
    private val reconcile: ReconcileSessionUseCase,
    private val music: MusicControlPort,
    private val memos: MemoRepository,
    private val playlists: PlaylistRepository,
    private val shareImages: ShareImagePort,
) : ViewModel() {
    private val _route = MutableStateFlow<AppRoute>(AppRoute.Loading)
    val route: StateFlow<AppRoute> = _route
    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())

    /** Count observed before a process death; song memory is gone, so the result is mixed (SRS Android §9.2). */
    private var restoredObserved: Pair<String, ObservedCount>? = null
    val overlays: StateFlow<List<Overlay>> = _overlays

    fun open(overlay: Overlay) {
        _overlays.value = _overlays.value + overlay
    }

    /** W07: memo for the live session; the timer keeps running. */
    fun openMemo() {
        viewModelScope.launch { repository.active()?.let { open(Overlay.Memo(it.sessionId)) } }
    }

    /** FR-A071: nothing of the deleted session stays reachable; the settings guide stays on top. */
    fun onDataDeleted() {
        restoredObserved = null
        _route.value = AppRoute.Allocation
        _overlays.value = _overlays.value.takeLast(1)
    }

    fun closeOverlay() {
        _overlays.value = _overlays.value.dropLast(1)
    }

    init {
        viewModelScope.launch {
            _route.value = routeFor(restore())
            reconcile()
        }
        // DEC-A22: a share image left by a killed process is never kept; the preview renders afresh.
        viewModelScope.launch { shareImages.clear() }
    }

    /** F18 M6: only a result with confirmed songs can be shared (the button is disabled otherwise). */
    fun openSharePreview() {
        val result = _route.value as? AppRoute.Result ?: return
        ShareCard.of(TotalTime.of(result.session.accumulatedActiveMs), result.playlist)?.let { open(Overlay.SharePreview(it)) }
    }

    /** App back on screen: re-derive notifications and observation from the stored state (SRS v1 §7.2). */
    fun onResume() {
        viewModelScope.launch { reconcile() }
    }

    fun onStarted() {
        _route.value = AppRoute.Session
    }

    /** Moves to the result only for the session that actually ended (a stale Finished state is ignored). */
    fun onFinished(sessionId: String) {
        viewModelScope.launch {
            val ended = repository.active()?.takeIf { it.sessionId == sessionId && it.state == TimerState.ENDED } ?: return@launch
            val observed = observedFor(ended.sessionId, music.awaitObserved(ended.sessionId))
            _route.value = AppRoute.Result(ended, observed, memoTexts(ended.sessionId), playlistOf(ended.sessionId))
        }
    }

    fun keepMemos(sessionId: String) {
        viewModelScope.launch { memos.keep(sessionId) }
    }

    fun discardMemos(sessionId: String) {
        viewModelScope.launch {
            memos.discard(sessionId)
            (_route.value as? AppRoute.Result)?.takeIf { it.session.sessionId == sessionId }
                ?.let { _route.value = it.copy(memos = emptyList()) }
        }
    }

    /**
     * Closing the result deletes it, its playlist and the share image (SRS v1 §9.2, DEC-A18: kept only until the
     * result closes; DEC-A22).
     */
    fun onRestart() {
        val closed = (_route.value as? AppRoute.Result)?.session?.sessionId
        _route.value = AppRoute.Allocation
        viewModelScope.launch {
            closed?.let { playlists.delete(it) }
            shareImages.clear()
            repository.withLock {
                if (repository.active()?.state == TimerState.ENDED) repository.clear()
            }
        }
    }

    fun resumeFromRecovery() {
        viewModelScope.launch {
            dispatch(TimerCommand.ResumeFromRecovery(UUID.randomUUID().toString()))
            _route.value = AppRoute.Session
        }
    }

    fun finishFromRecovery() {
        viewModelScope.launch {
            dispatch(TimerCommand.Finish(UUID.randomUUID().toString()))
            _route.value = finishedRoute()
        }
    }

    fun discardCorrupt() {
        viewModelScope.launch {
            restore.discard()
            _route.value = AppRoute.Allocation
        }
    }

    private suspend fun finishedRoute(): AppRoute =
        repository.active()?.takeIf { it.state == TimerState.ENDED }
            ?.let { AppRoute.Result(it, observedFor(it.sessionId), memoTexts(it.sessionId), playlistOf(it.sessionId)) }
            ?: AppRoute.Allocation

    private fun memoTexts(sessionId: String) = memos.memosFor(sessionId).map { it.text }

    /** F18: a store failure shows the empty state rather than breaking the result (MT-T7). */
    private suspend fun playlistOf(sessionId: String): List<PlaylistEntry> =
        runCatching { playlists.playlist(sessionId) }.getOrDefault(emptyList())

    private fun observedFor(sessionId: String, live: ObservedCount? = music.lastObserved.value): ObservedCount? {
        val restored = restoredObserved?.takeIf { it.first == sessionId }?.second ?: return live
        restoredObserved = null
        return ObservedCount(restored.count + (live?.count ?: 0), startedMidSession = true)
    }

    private suspend fun routeFor(decision: RestoreDecision): AppRoute = when (decision) {
        RestoreDecision.Fresh -> AppRoute.Allocation
        is RestoreDecision.ShowResult -> AppRoute.Result(
            decision.session,
            decision.countedItems?.let { ObservedCount(it, startedMidSession = true) },
            memoTexts(decision.session.sessionId),
            playlistOf(decision.session.sessionId),
        )
        RestoreDecision.Corrupt -> AppRoute.Corrupt
        is RestoreDecision.RecoveryRequired -> AppRoute.Recovery(decision.session)
        is RestoreDecision.Restore -> {
            restoredObserved = decision.countedItems?.let { decision.session.sessionId to ObservedCount(it, startedMidSession = true) }
            if (decision.session.state == TimerState.READY) AppRoute.Allocation else AppRoute.Session
        }
    }
}
