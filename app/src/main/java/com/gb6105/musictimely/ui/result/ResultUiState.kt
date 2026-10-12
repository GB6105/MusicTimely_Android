package com.gb6105.musictimely.ui.result

import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.repository.ObservedCount

/** FR-A015 result source label: D0 "약 N곡 분량" / observed "N곡 지났어요" / mixed "(곡 세기 시작 후)". */
enum class SongCountSource { ESTIMATED, OBSERVED, MIXED }

/** SCR-05 as design A only (DEC-A19, DEC-A20): total time, playlist and memos. No overrun rate, failure, praise or score (FR-A067). */
data class ResultUiState(
    val source: SongCountSource,
    val memos: List<String> = emptyList(),
    /** F18 M3: active time, pauses excluded. */
    val totalTime: TotalTime = TotalTime(0, 0, 0),
    /** F18 M4: counted songs in play order; empty means nothing confirmed (no invented list). */
    val playlist: List<PlaylistEntry> = emptyList(),
) {
    companion object {
        fun from(session: TimerSession, observed: ObservedCount? = null, playlist: List<PlaylistEntry> = emptyList()): ResultUiState =
            ResultUiState(
                source = when {
                    observed == null -> SongCountSource.ESTIMATED
                    observed.startedMidSession -> SongCountSource.MIXED
                    else -> SongCountSource.OBSERVED
                },
                totalTime = TotalTime.of(session.accumulatedActiveMs),
                playlist = playlist,
            )
    }
}
