package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.domain.repository.PlaylistRepository
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Connects the coordinator's callbacks to the session store, the timer tick and settings (called from DI). */
fun ObservationCoordinator.connect(
    scope: CoroutineScope,
    sessions: SessionRepository,
    tick: suspend () -> Unit,
    settings: SettingsRepository,
    playlists: PlaylistRepository,
    pauseTimer: suspend () -> Unit = {},
) {
    // The integer count goes to the checkpoint for a restore after process death (SRS Android §9.2).
    onCountChanged = { id, count, from -> scope.launch { sessions.recordObservation(id, count, from) } }
    // F18 M1 (DEC-A18): counted songs go to the on-device playlist store. UNDISPATCHED takes the store's lock on the
    // observation thread, so appends keep play order and a result read after finishing waits for the last one.
    onSongCounted = { id, entry -> scope.launch(start = CoroutineStart.UNDISPATCHED) { playlists.append(id, entry) } }
    // A song boundary past the target before the inexact alarm: reach the limit now (SRS Android §5.3).
    onLimitDue = { scope.launch { tick() } }
    // DEC-A17 (setting): music paused for 10 s while the timer runs pauses the timer; the user resumes it.
    onMusicPausedLong = { scope.launch { pauseTimer() } }
    // The song counting switch takes effect immediately, also mid-session (FR-A041, DEC-A13).
    scope.launch { settings.settings.collect { onSettingsChanged() } }
}
