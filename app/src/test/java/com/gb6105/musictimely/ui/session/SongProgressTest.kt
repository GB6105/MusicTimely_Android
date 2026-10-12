package com.gb6105.musictimely.ui.session

import com.gb6105.musictimely.domain.model.timer.TimerState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A2 시간 카드 "곡 진행": 관측 중에만 정확한 번호, 그 외는 추정 ("약"). FR-A018·A019·A020. */
class SongProgressTest {

    private val observed = MusicPanelState.Observed("Slow Tide", playing = true, handoffProvider = null, button = MusicButton.PAUSE)

    private fun state(
        phase: TimerState = TimerState.RUNNING,
        total: Int? = 7,
        currentSong: Int = 3,
        music: MusicPanelState = MusicPanelState.Empty,
        observedCount: Int? = null,
        sinceMid: Boolean = false,
        showSongs: Boolean = true,
        singleLoop: Boolean = false,
    ) = SessionUiState.Active(
        phase = phase, taskTitle = null, elapsedMs = 0, remainingMs = null, remainingSongs = null, totalSongs = total,
        filledChips = 0, elapsedTenths = 0, cue = null, music = music, observedCount = observedCount,
        observedSinceMidSession = sinceMid, showSongs = showSongs, singleLoop = singleLoop, currentSong = currentSong,
    )

    @Test
    fun `observed from the start gives the exact current song (counted plus the playing one)`() {
        assertThat(SongProgress.of(state(music = observed, observedCount = 2)))
            .isEqualTo(SongProgress(current = 3, total = 7, estimated = false, loops = false))
    }

    @Test
    fun `without observation the current song is an estimate`() {
        assertThat(SongProgress.of(state(currentSong = 3)))
            .isEqualTo(SongProgress(current = 3, total = 7, estimated = true, loops = false))
    }

    @Test
    fun `counting started mid-session does not know the true index, so it stays an estimate`() {
        assertThat(SongProgress.of(state(music = observed, observedCount = 1, sinceMid = true, currentSong = 4)))
            .isEqualTo(SongProgress(current = 4, total = 7, estimated = true, loops = false))
    }

    @Test
    fun `mid-session counting never shows fewer songs than were counted (emulator finding)`() {
        assertThat(SongProgress.of(state(music = observed, observedCount = 3, sinceMid = true, currentSong = 1)))
            .isEqualTo(SongProgress(current = 4, total = 7, estimated = true, loops = false))
    }

    @Test
    fun `a single-loop session counts estimated loops (FR-A019)`() {
        assertThat(SongProgress.of(state(music = observed, observedCount = 2, singleLoop = true, currentSong = 2)))
            .isEqualTo(SongProgress(current = 2, total = 7, estimated = true, loops = true))
    }

    @Test
    fun `an open-ended session has no total`() {
        assertThat(SongProgress.of(state(total = null, currentSong = 3))?.total).isNull()
    }

    @Test
    fun `song display off hides song progress (FR-A020)`() {
        assertThat(SongProgress.of(state(showSongs = false))).isNull()
    }

    @Test
    fun `reaching the target hides song progress like the old total`() {
        assertThat(SongProgress.of(state(phase = TimerState.LIMIT_REACHED))).isNull()
    }
}
