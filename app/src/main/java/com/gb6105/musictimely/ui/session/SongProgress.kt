package com.gb6105.musictimely.ui.session

import com.gb6105.musictimely.domain.model.timer.TimerState

/**
 * A2 time card "곡 진행" (current song / allocated songs). Exact only while songs are observed from the session
 * start (FR-A018); counting that began mid-session, single-loop sessions and D0 show the estimate ("약"),
 * which is never below the songs already counted.
 */
data class SongProgress(val current: Int, val total: Int?, val estimated: Boolean, val loops: Boolean) {
    companion object {
        fun of(state: SessionUiState.Active): SongProgress? {
            if (!state.showSongs || state.phase == TimerState.LIMIT_REACHED) return null
            val counted = state.observedCount
                ?.takeIf { state.music is MusicPanelState.Observed && !state.observedSinceMidSession && !state.singleLoop }
            return if (counted != null) {
                SongProgress(current = counted + 1, total = state.totalSongs, estimated = false, loops = false)
            } else {
                // Mid-session counting: still an estimate, but never fewer than the songs actually counted.
                val observedAtLeast = state.observedCount
                    ?.takeIf { state.music is MusicPanelState.Observed && !state.singleLoop }?.plus(1) ?: 0
                SongProgress(
                    current = maxOf(state.currentSong, observedAtLeast),
                    total = state.totalSongs,
                    estimated = true,
                    loops = state.singleLoop,
                )
            }
        }
    }
}
