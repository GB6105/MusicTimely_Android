package com.gb6105.musictimely.domain.model.observation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * O2 집계 (SRS Android §6.2). DEC-A03·A04·A13·A14, FR-A034·A035·A036,
 * QA-A07·A08·A09·A10·A11·A13·A14.
 */
class SongCounterTest {

    private val sec = 1_000L
    private fun track(title: String, pkg: String = "ytm", durationMs: Long? = 200 * sec, artist: String = "A") =
        TrackMetadata(packageName = pkg, title = title, artist = artist, durationMs = durationMs)

    private fun SongCounter.play(track: TrackMetadata, atActiveMs: Long) = onTrack(track, atActiveMs)

    @Test
    fun `three songs of more than 30 seconds count three (QA-A07)`() {
        var c = SongCounter.start(observedFromActiveMs = 0)
        c = c.play(track("a"), 0)
        c = c.play(track("b"), 200 * sec)
        c = c.play(track("c"), 400 * sec)
        c = c.advanceTo(600 * sec)

        assertThat(c.countedItems).isEqualTo(3)
        assertThat(c.currentIndex).isEqualTo(3)
        assertThat(c.finishedCountedItems).isEqualTo(2)
    }

    @Test
    fun `an item counts the moment it reaches 30 seconds of running time`() {
        var c = SongCounter.start(0).play(track("a"), 0)

        assertThat(c.advanceTo(29 * sec).countedItems).isEqualTo(0)
        assertThat(c.advanceTo(29 * sec).currentIndex).isEqualTo(1)
        c = c.advanceTo(30 * sec)
        assertThat(c.countedItems).isEqualTo(1)
        assertThat(c.currentIndex).isEqualTo(1)
    }

    @Test
    fun `a 15 second ad between songs is not counted (QA-A08)`() {
        var c = SongCounter.start(0)
        c = c.play(track("a"), 0)
        c = c.play(track("ad", durationMs = null), 200 * sec)
        c = c.play(track("b"), 215 * sec)
        c = c.advanceTo(300 * sec)

        assertThat(c.countedItems).isEqualTo(2)
    }

    @Test
    fun `an item known to be shorter than 30 seconds never counts`() {
        var c = SongCounter.start(0).play(track("jingle", durationMs = 20 * sec), 0)
        c = c.advanceTo(45 * sec)

        assertThat(c.countedItems).isEqualTo(0)
    }

    @Test
    fun `signature ignores duration so a late duration is the same item`() {
        var c = SongCounter.start(0).play(track("a", durationMs = null), 0)
        c = c.play(track("a", durationMs = 200 * sec), 5 * sec)
        c = c.advanceTo(40 * sec)

        assertThat(c.countedItems).isEqualTo(1)
        assertThat(c.loops).isEqualTo(0)
    }

    @Test
    fun `republished metadata of the same song early in the song is not a new song nor a loop (QA-A09)`() {
        var c = SongCounter.start(0).play(track("a"), 0)
        c = c.play(track("a"), 3 * sec)
        c = c.play(track("a"), 50 * sec)
        c = c.advanceTo(100 * sec)

        assertThat(c.countedItems).isEqualTo(1)
        assertThat(c.loops).isEqualTo(0)
    }

    @Test
    fun `same signature republished after the song length is one loop (QA-A10)`() {
        var c = SongCounter.start(0).play(track("a"), 0)
        c = c.play(track("a"), 200 * sec)
        c = c.play(track("a"), 400 * sec)
        c = c.play(track("a"), 600 * sec)

        assertThat(c.loops).isEqualTo(3)
        assertThat(c.countedItems).isEqualTo(1)
    }

    @Test
    fun `position rewinding from the end to the start is one loop`() {
        var c = SongCounter.start(0).play(track("a"), 0)
        c = c.onPosition(positionMs = 197 * sec, atActiveMs = 197 * sec)
        c = c.onPosition(positionMs = 1 * sec, atActiveMs = 200 * sec)

        assertThat(c.loops).isEqualTo(1)
    }

    @Test
    fun `a manual seek back from the middle is not a loop`() {
        var c = SongCounter.start(0).play(track("a"), 0)
        c = c.onPosition(positionMs = 100 * sec, atActiveMs = 100 * sec)
        c = c.onPosition(positionMs = 1 * sec, atActiveMs = 101 * sec)

        assertThat(c.loops).isEqualTo(0)
    }

    @Test
    fun `switching apps hands off and keeps the count (QA-A11)`() {
        var c = SongCounter.start(0)
        c = c.play(track("a", pkg = "ytm"), 0)
        c = c.play(track("b", pkg = "melon"), 200 * sec)
        c = c.advanceTo(260 * sec)

        assertThat(c.countedItems).isEqualTo(2)
    }

    @Test
    fun `time that does not advance while the timer is paused does not count songs (QA-A14)`() {
        var c = SongCounter.start(0).play(track("a"), 0).advanceTo(60 * sec)
        // Timer paused at active 60s: two songs skipped, active time frozen.
        c = c.play(track("b"), 60 * sec)
        c = c.play(track("c"), 60 * sec)
        assertThat(c.countedItems).isEqualTo(1)

        // Resumed: "c" keeps playing for 30 s of running time.
        c = c.advanceTo(90 * sec)
        assertThat(c.countedItems).isEqualTo(2)
    }

    @Test
    fun `missing title is not an item`() {
        var c = SongCounter.start(0).play(track("a"), 0)
        c = c.play(TrackMetadata("ytm", title = null, artist = null, durationMs = null), 10 * sec)
        c = c.advanceTo(50 * sec)

        assertThat(c.countedItems).isEqualTo(1)
    }

    @Test
    fun `observation started mid session is labelled started-after (DEC-A13, QA-A13)`() {
        val c = SongCounter.start(observedFromActiveMs = 300 * sec)

        assertThat(c.startedMidSession).isTrue()
        assertThat(SongCounter.start(0).startedMidSession).isFalse()
    }

    @Test
    fun `a gap marks the count as mixed and is never interpolated`() {
        var c = SongCounter.start(0).play(track("a"), 0).advanceTo(40 * sec)
        c = c.onGap(atActiveMs = 40 * sec)
        c = c.play(track("b"), 400 * sec).advanceTo(440 * sec)

        assertThat(c.hadGap).isTrue()
        assertThat(c.countedItems).isEqualTo(2)
    }

    @Test
    fun `after a gap the previous item does not keep accumulating`() {
        var c = SongCounter.start(0).play(track("a", durationMs = null), 0).advanceTo(10 * sec)
        c = c.onGap(atActiveMs = 10 * sec)
        c = c.advanceTo(100 * sec)

        assertThat(c.countedItems).isEqualTo(0)
    }
}
