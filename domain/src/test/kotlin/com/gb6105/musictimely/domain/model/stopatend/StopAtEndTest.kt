package com.gb6105.musictimely.domain.model.stopatend

import com.gb6105.musictimely.domain.model.translation.ObservedItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 끝나면 멈추기 (SRS Android §5.3, DEC-A05). FR-A047~A053, QA-A17~A21. */
class StopAtEndTest {

    private val min = 60_000L
    private val eligible = StopAtEndConditions(settingOn = true, countingOn = true, infiniteMode = false)

    private fun armed(item: ObservedItem? = null, now: Long = 0): StopAtEnd =
        StopAtEnd.initial().on(StopAtEndEvent.LimitReached(revision = 0, conditions = eligible, observing = true, playing = true, item = item, nowMono = now)).next

    @Test
    fun `limit reached while observing playing music arms and shows the waiting copy (FR-A048, QA-A17)`() {
        val result = StopAtEnd.initial().on(
            StopAtEndEvent.LimitReached(0, eligible, observing = true, playing = true, item = null, nowMono = 0),
        )

        assertThat(result.next.phase).isEqualTo(StopAtEndPhase.ARMED)
        assertThat(result.effects).containsExactly(StopAtEndEffect.ShowWaiting)
    }

    @Test
    fun `setting off, counting off or infinite mode disables it (FR-A047, QA-A21)`() {
        listOf(
            eligible.copy(settingOn = false),
            eligible.copy(countingOn = false),
            eligible.copy(infiniteMode = true),
        ).forEach { conditions ->
            val result = StopAtEnd.initial().on(StopAtEndEvent.LimitReached(0, conditions, true, true, null, 0))
            assertThat(result.next.phase).isEqualTo(StopAtEndPhase.DISABLED)
            assertThat(result.effects).isEmpty()
        }
    }

    @Test
    fun `music already stopped or not observed does nothing (QA-A21)`() {
        val paused = StopAtEnd.initial().on(StopAtEndEvent.LimitReached(0, eligible, observing = true, playing = false, item = null, nowMono = 0))
        val unobserved = StopAtEnd.initial().on(StopAtEndEvent.LimitReached(0, eligible, observing = false, playing = true, item = null, nowMono = 0))

        assertThat(paused.next.phase).isEqualTo(StopAtEndPhase.IDLE)
        assertThat(paused.effects).isEmpty()
        assertThat(unobserved.next.phase).isEqualTo(StopAtEndPhase.IDLE)
    }

    @Test
    fun `the first track boundary after arming sends one pause (FR-A049)`() {
        val boundary = armed().on(StopAtEndEvent.TrackBoundary(nowMono = 90_000))

        assertThat(boundary.next.phase).isEqualTo(StopAtEndPhase.STOPPING)
        assertThat(boundary.effects).containsExactly(StopAtEndEffect.SendPause)
        assertThat(boundary.next.on(StopAtEndEvent.TrackBoundary(nowMono = 95_000)).effects).isEmpty()
    }

    @Test
    fun `confirmed pause ends in STOPPED`() {
        val stopping = armed().on(StopAtEndEvent.TrackBoundary(nowMono = 1_000)).next

        val stopped = stopping.on(StopAtEndEvent.PlaybackChanged(isPlaying = false, nowMono = 1_500))

        assertThat(stopped.next.phase).isEqualTo(StopAtEndPhase.STOPPED)
        assertThat(stopped.effects).isEmpty()
    }

    @Test
    fun `without a boundary the cap is remaining length plus 15 seconds when known (FR-A050)`() {
        val s = armed(item = ObservedItem(durationMs = 200_000, positionMs = 50_000), now = 0)

        assertThat(s.on(StopAtEndEvent.Tick(nowMono = 164_999)).effects).isEmpty()
        assertThat(s.on(StopAtEndEvent.Tick(nowMono = 165_000)).effects).containsExactly(StopAtEndEffect.SendPause)
    }

    @Test
    fun `without song info the cap is 6 minutes (QA-A18)`() {
        val s = armed(item = null, now = 10_000)

        assertThat(s.on(StopAtEndEvent.Tick(nowMono = 10_000 + 6 * min - 1)).effects).isEmpty()
        assertThat(s.on(StopAtEndEvent.Tick(nowMono = 10_000 + 6 * min)).effects).containsExactly(StopAtEndEffect.SendPause)
    }

    @Test
    fun `stop now pauses immediately, keep listening cancels without a command (FR-A051, QA-A19)`() {
        val now = armed().on(StopAtEndEvent.StopNow(nowMono = 5_000))
        val keep = armed().on(StopAtEndEvent.KeepListening)

        assertThat(now.effects).containsExactly(StopAtEndEffect.SendPause)
        assertThat(keep.next.phase).isEqualTo(StopAtEndPhase.CANCELLED)
        assertThat(keep.effects).isEmpty()
    }

    @Test
    fun `extend, finish, user pause, target loss and counting off cancel without a command (FR-A052, QA-A19, QA-A20)`() {
        listOf(
            StopAtEndEvent.Extended,
            StopAtEndEvent.Finished,
            StopAtEndEvent.PlaybackChanged(isPlaying = false, nowMono = 1_000),
            StopAtEndEvent.TargetLost,
            StopAtEndEvent.CountingTurnedOff,
        ).forEach { event ->
            val result = armed().on(event)
            assertThat(result.next.phase).isEqualTo(StopAtEndPhase.CANCELLED)
            assertThat(result.effects).isEmpty()
        }
    }

    @Test
    fun `pause not confirmed within 2 seconds fails once with the notice (FR-A053)`() {
        val stopping = armed().on(StopAtEndEvent.TrackBoundary(nowMono = 1_000)).next

        assertThat(stopping.on(StopAtEndEvent.Tick(nowMono = 2_999)).effects).isEmpty()
        val failed = stopping.on(StopAtEndEvent.Tick(nowMono = 3_000))
        assertThat(failed.next.phase).isEqualTo(StopAtEndPhase.FAILED)
        assertThat(failed.effects).containsExactly(StopAtEndEffect.ShowStopFailed)
        assertThat(failed.next.on(StopAtEndEvent.Tick(nowMono = 9_000)).effects).isEmpty()
    }

    @Test
    fun `an outcome is final for its revision and a new revision evaluates again`() {
        val cancelled = armed().on(StopAtEndEvent.Extended).next

        assertThat(cancelled.on(StopAtEndEvent.StopNow(nowMono = 1)).effects).isEmpty()
        assertThat(cancelled.on(StopAtEndEvent.LimitReached(0, eligible, true, true, null, 2)).next.phase).isEqualTo(StopAtEndPhase.CANCELLED)
        val rearmed = cancelled.on(StopAtEndEvent.LimitReached(revision = 1, conditions = eligible, observing = true, playing = true, item = null, nowMono = 3))
        assertThat(rearmed.next.phase).isEqualTo(StopAtEndPhase.ARMED)
        assertThat(rearmed.next.revision).isEqualTo(1)
    }

    @Test
    fun `the next deadline is the cap while armed and the confirm limit while stopping`() {
        val s = armed(item = null, now = 1_000)
        assertThat(s.nextDeadlineMono).isEqualTo(1_000 + 6 * min)

        val stopping = s.on(StopAtEndEvent.StopNow(nowMono = 5_000)).next
        assertThat(stopping.nextDeadlineMono).isEqualTo(7_000)

        assertThat(stopping.on(StopAtEndEvent.PlaybackChanged(false, 6_000)).next.nextDeadlineMono).isNull()
        assertThat(StopAtEnd.initial().nextDeadlineMono).isNull()
    }
}
