package com.gb6105.musictimely.domain.model.playlist

import com.gb6105.musictimely.domain.model.observation.SongCounter
import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.domain.model.source.Provider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * F18 M1 재생 기록 규칙: 곡 세기가 한 곡을 셀 때(30초 규칙, 광고 제외, 일시정지 중 시간 정지)만 그 곡이 기록된다.
 * DEC-A18. Records are collected from [SongCounter.justCounted] after every transition, as the observer does.
 */
class PlaylistRecordingTest {

    private val sec = 1_000L
    private val ytm = "com.google.android.apps.youtube.music"
    private fun track(title: String, artist: String? = "A", durationMs: Long? = 200 * sec) = TrackMetadata(ytm, title, artist, durationMs)

    private var counter = SongCounter.start(observedFromActiveMs = 0)
    private val recorded = mutableListOf<PlaylistEntry>()

    private fun step(next: SongCounter) {
        counter = next
        next.justCounted?.let { recorded += PlaylistEntry.of(next.countedItems, it) }
    }

    @Test
    fun `a song is recorded the moment it is counted, with its title, artist and app`() {
        step(counter.onTrack(track("Soft Landing", "June Archive"), 0))
        step(counter.advanceTo(29 * sec))
        assertThat(recorded).isEmpty()

        step(counter.advanceTo(30 * sec))

        assertThat(recorded).containsExactly(PlaylistEntry(1, "Soft Landing", "June Archive", Provider.YOUTUBE_MUSIC))
    }

    @Test
    fun `the counted song is the one that was playing, not the next one at the boundary`() {
        step(counter.onTrack(track("a"), 0))
        step(counter.onTrack(track("b"), 20 * sec)) // "a" reached only 20 s
        step(counter.onTrack(track("c"), 200 * sec))
        step(counter.advanceTo(240 * sec))

        assertThat(recorded.map { it.title }).containsExactly("b", "c").inOrder()
        assertThat(recorded.map { it.order }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `a song played again later is a new entry (repeats kept in play order)`() {
        step(counter.onTrack(track("a"), 0))
        step(counter.onTrack(track("b"), 200 * sec))
        step(counter.onTrack(track("a"), 400 * sec))
        step(counter.advanceTo(450 * sec))

        assertThat(recorded.map { it.title }).containsExactly("a", "b", "a").inOrder()
    }

    @Test
    fun `an ad shorter than 30 seconds is not recorded (QA-A08)`() {
        step(counter.onTrack(track("a"), 0))
        step(counter.onTrack(track("ad", durationMs = 15 * sec), 200 * sec))
        step(counter.advanceTo(260 * sec))

        assertThat(recorded.map { it.title }).containsExactly("a")
    }

    @Test
    fun `songs passing while the timer is paused are not recorded (QA-A14)`() {
        step(counter.onTrack(track("a"), 0))
        step(counter.advanceTo(60 * sec))
        // Paused at 60 s: active time does not move while two songs pass.
        step(counter.onTrack(track("b"), 60 * sec))
        step(counter.onTrack(track("c"), 60 * sec))
        step(counter.advanceTo(60 * sec))

        assertThat(recorded.map { it.title }).containsExactly("a")
    }

    @Test
    fun `a transition that counts nothing carries no record`() {
        step(counter.onTrack(track("a"), 0))
        step(counter.advanceTo(30 * sec))
        step(counter.advanceTo(90 * sec))

        assertThat(counter.justCounted).isNull()
        assertThat(recorded).hasSize(1)
    }

    @Test
    fun `an unknown artist stays unknown, never invented`() {
        step(counter.onTrack(track("a", artist = ""), 0))
        step(counter.advanceTo(30 * sec))

        assertThat(recorded.single().artist).isNull()
    }
}
