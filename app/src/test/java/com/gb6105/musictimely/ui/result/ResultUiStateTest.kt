package com.gb6105.musictimely.ui.result

import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.EndReason
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.ObservedCount
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SCR-05 결과 상태 = 시안 A (DEC-A19·DEC-A20): 출처·총 걸린 시간·플레이리스트·메모만. */
class ResultUiStateTest {

    private val minute = 60_000L

    private fun ended(activeMs: Long, extensions: List<Long> = emptyList()) = TimerSession(
        sessionId = "s", state = TimerState.ENDED,
        allocation = Allocation(25 * minute, 25 * minute + extensions.sum(), extensions.size),
        accumulatedActiveMs = activeMs, runningAnchorMonoMs = null, extensions = extensions,
        endReason = EndReason.COMPLETED, appliedCommandIds = emptySet(), taskTitle = "기획안",
    )

    @Test
    fun `a session without a count is labelled estimated`() {
        val state = ResultUiState.from(ended(activeMs = 28 * minute, extensions = listOf(5 * minute)))

        assertThat(state.source).isEqualTo(SongCountSource.ESTIMATED)
    }

    @Test
    fun `an observed count is labelled observed, a mid-session start as mixed (FR-A015)`() {
        val observed = ResultUiState.from(ended(28 * minute), ObservedCount(count = 8, startedMidSession = false))
        val mixed = ResultUiState.from(ended(28 * minute), ObservedCount(count = 5, startedMidSession = true))

        assertThat(observed.source).isEqualTo(SongCountSource.OBSERVED)
        assertThat(mixed.source).isEqualTo(SongCountSource.MIXED)
    }

    @Test
    fun `total time is the active session time without pauses, and the playlist is passed through (F18 M3, M4)`() {
        val songs = listOf(PlaylistEntry(1, "Soft Landing", "June Archive", Provider.MELON))

        val state = ResultUiState.from(ended(32 * minute + 18_500), playlist = songs)

        assertThat(state.totalTime).isEqualTo(TotalTime(0, 32, 18))
        assertThat(state.playlist).isEqualTo(songs)
    }
}
