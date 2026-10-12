package com.gb6105.musictimely.domain.usecase.settings

import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.CheckpointStore
import com.gb6105.musictimely.domain.repository.MemoRepository
import com.gb6105.musictimely.domain.repository.PlaylistRepository
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.repository.SettingsRepository
import com.gb6105.musictimely.domain.repository.SourceRepository
import com.gb6105.musictimely.domain.repository.TimerEffectSink

/**
 * FR-A071: stop what the live session runs (alarm, stop-at-end, observation, notifications), then delete the
 * checkpoint, settings, saved sources, thought memos and session playlists (DEC-A18). The system
 * notification-access permission cannot be revoked by the app; the UI explains how. External music and files are never touched.
 */
class DeleteAllDataUseCase(
    private val store: CheckpointStore,
    private val settings: SettingsRepository,
    private val sources: SourceRepository,
    private val sink: TimerEffectSink,
    private val memos: MemoRepository,
    private val sessions: SessionRepository,
    private val playlists: PlaylistRepository,
) {
    suspend operator fun invoke() {
        // Under the session lock: a concurrent alarm or notification action cannot revive the session (review 2 L7).
        sessions.withLock {
            val session = sessions.active()
            if (session != null && session.state != TimerState.ENDED) {
                sink.apply(
                    session,
                    listOf(TimerEffect.CancelEndAlarm, TimerEffect.CancelStopAtEnd, TimerEffect.StopObservation, TimerEffect.ClearNotifications),
                )
            }
            sessions.clear()
        }
        store.clear()
        settings.clear()
        sources.clear()
        memos.clear()
        playlists.clear()
    }
}
