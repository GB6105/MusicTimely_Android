package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.observation.SeenKind
import com.gb6105.musictimely.domain.repository.ObservedCount
import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndPhase
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.ExtensionStep
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 타이머 부수효과 ↔ 곡 세기·끝나면 멈추기·재생 버튼 연결.
 * FR-A043~A053, QA-A15~A21, NFR-A006(폴링 0: 마감 예약만).
 */
class ObservationCoordinatorTest {

    private val minute = 60_000L
    private val ytm = "com.google.android.apps.youtube.music"
    private val clock = FakeClock()
    private val gateway = FakeMediaSessionGateway()
    private val delays = FakeDelayer(clock)
    private var settings = MusicSettings(songCountingEnabled = true, stopAtEndEnabled = true)
    private val machine = TimerStateMachine()
    private val recorded = mutableListOf<Pair<String, Int>>()
    private val coordinator = ObservationCoordinator(gateway, clock, "com.gb6105.musictimely", { settings }, { ytm }, delays)
        .apply { onCountChanged = { id, count, _ -> recorded += id to count } }

    private var session = TimerSession.ready("s", Allocation.bounded(25 * minute))

    private suspend fun apply(command: TimerCommand) {
        val t = machine.reduce(session, command, clock.monotonicMs)
        session = t.session
        t.effects.filter(coordinator::handles).forEach { coordinator.handle(session, it) }
    }

    private fun playing(title: String = "a") = gateway.add(FakeSession(ytm, track = TrackMetadata(ytm, title, "A", 200_000), position = 50_000))

    @Test
    fun `starting the timer starts observation and finishing stops it`() = runTest {
        playing()

        apply(TimerCommand.Start("start"))
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)

        apply(TimerCommand.Finish("finish"))
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.IDLE)
    }

    @Test
    fun `a listener disconnect degrades observation and blocks commands until it reconnects (OBS_LISTENER_DISCONNECTED)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))

        coordinator.onListenerDisconnected()
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.DEGRADED)
        coordinator.onTransportTap()
        assertThat(music.sent).isEmpty()

        coordinator.onListenerConnected()
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }

    @Test
    fun `a disconnect while searching reconnects to searching and still finds music started later (review H1)`() = runTest {
        apply(TimerCommand.Start("start"))
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.SEARCHING)

        coordinator.onListenerDisconnected()
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.DEGRADED)
        coordinator.onListenerConnected()
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.SEARCHING)

        playing()
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }

    @Test
    fun `finishing keeps only the integer count for the result, never the titles (SRS Android 9_2)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(60_000)
        music.changeTrack("b")
        clock.advance(40_000)

        apply(TimerCommand.Finish("finish"))

        assertThat(coordinator.lastObserved.value).isEqualTo(ObservedCount(count = 2, startedMidSession = false))
        assertThat(coordinator.snapshot.value.title).isNull()
    }

    @Test
    fun `a session that never observed reports no count`() = runTest {
        apply(TimerCommand.Start("start"))

        apply(TimerCommand.Finish("finish"))

        assertThat(coordinator.lastObserved.value).isNull()
    }

    @Test
    fun `at the limit with music playing it arms, then pauses at the next song (QA-A17)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.ARMED)
        assertThat(music.sent).isEmpty()

        clock.advance(30_000)
        music.changeTrack("b")

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.STOPPING)
        music.changePlaying(false)
        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.STOPPED)
    }

    @Test
    fun `without a boundary the scheduled cap pauses once (QA-A18)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        delays.runDue(after = 150_000 + 15_000)

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `extending cancels the wait and never plays again (FR-A011, QA-A19)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        apply(TimerCommand.Extend("ext", ExtensionStep.FIVE))
        music.changeTrack("b")
        delays.runDue(after = 10 * minute)

        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.CANCELLED)
        assertThat(music.sent).isEmpty()
    }

    @Test
    fun `stop now pauses immediately, keep listening sends nothing (QA-A19)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        coordinator.keepListening()
        assertThat(music.sent).isEmpty()
        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.CANCELLED)
    }

    @Test
    fun `stop now sends one pause`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        coordinator.stopNow()

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `an unconfirmed pause fails once with the notice (FR-A053)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))
        coordinator.stopNow()

        delays.runDue(after = 2_000)

        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.FAILED)
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `music already paused at the limit does nothing (QA-A21)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        music.changePlaying(false)
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.IDLE)
        assertThat(delays.pending).isEqualTo(0)
    }

    @Test
    fun `setting off keeps stop-at-end disabled (FR-A047)`() = runTest {
        settings = settings.copy(stopAtEndEnabled = false)
        playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.DISABLED)
    }

    @Test
    fun `the user button sends the opposite command once and debounces (FR-A044, QA-A15)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))

        coordinator.onTransportTap()
        coordinator.onTransportTap()

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `a late playback report from the app gives the button back on screen (emulator finding)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        coordinator.onTransportTap()
        delays.runDue(after = 2_000)
        assertThat(coordinator.control.value.unresponsive).isTrue()

        music.changePlaying(false)
        clock.advance(1_000)

        assertThat(coordinator.control.value.unresponsive).isFalse()
        coordinator.onTransportTap()
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE, MusicCommand.PLAY).inOrder()
    }

    @Test
    fun `an app that ignores the button becomes open-app after one notice (DEC-A07)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))

        coordinator.onTransportTap()
        delays.runDue(after = 2_000)

        assertThat(coordinator.control.value.unresponsive).isTrue()
        assertThat(coordinator.control.value.noticePending).isTrue()
        clock.advance(1_000)
        coordinator.onTransportTap()
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `pausing the timer pauses the playing music and resuming plays it again (DEC-A16)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))

        apply(TimerCommand.Pause("pause"))
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
        music.changePlaying(false)

        apply(TimerCommand.Resume("resume"))
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE, MusicCommand.PLAY).inOrder()
    }

    @Test
    fun `music the user had already paused is not started by resuming the timer (DEC-A16)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        music.changePlaying(false)

        apply(TimerCommand.Pause("pause"))
        apply(TimerCommand.Resume("resume"))

        assertThat(music.sent).isEmpty()
    }

    @Test
    fun `if the user plays music during the timer pause, resuming sends nothing (DEC-A16)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        apply(TimerCommand.Pause("pause"))
        music.changePlaying(false)

        music.changePlaying(true)
        apply(TimerCommand.Resume("resume"))

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `a different song started during the pause is not resumed by the timer (DEC-A16)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        apply(TimerCommand.Pause("pause"))
        music.changePlaying(false)

        music.changeTrack("another")
        apply(TimerCommand.Resume("resume"))

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `without counting there is no observed music, so the timer sends nothing (DEC-A16)`() = runTest {
        settings = settings.copy(songCountingEnabled = false)
        val music = playing()
        apply(TimerCommand.Start("start"))

        apply(TimerCommand.Pause("pause"))
        apply(TimerCommand.Resume("resume"))

        assertThat(music.sent).isEmpty()
    }

    @Test
    fun `finishing from the timer pause does not start the music again (DEC-A16)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        apply(TimerCommand.Pause("pause"))
        music.changePlaying(false)

        apply(TimerCommand.Finish("finish"))

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    // ---- DEC-A17: music paused for 10 s pauses the timer (setting, off by default) ----

    private var musicPausedLong = 0

    private fun linkMusicToTimer(on: Boolean = true) {
        settings = settings.copy(pauseTimerWithMusic = on)
        coordinator.onMusicPausedLong = { musicPausedLong++ }
    }

    @Test
    fun `with the setting on, music paused for 10 seconds asks the timer to pause once (DEC-A17)`() = runTest {
        linkMusicToTimer()
        val music = playing()
        apply(TimerCommand.Start("start"))

        music.changePlaying(false)
        delays.runDue(after = 9_999)
        assertThat(musicPausedLong).isEqualTo(0)
        delays.runDue(after = 1)

        assertThat(musicPausedLong).isEqualTo(1)
    }

    @Test
    fun `music playing again within 10 seconds keeps the timer going (DEC-A17)`() = runTest {
        linkMusicToTimer()
        val music = playing()
        apply(TimerCommand.Start("start"))

        music.changePlaying(false)
        delays.runDue(after = 5_000)
        music.changePlaying(true)
        delays.runDue(after = 10_000)

        assertThat(musicPausedLong).isEqualTo(0)
    }

    @Test
    fun `with the setting off a long music pause does not touch the timer (DEC-A17 default, FR-A042)`() = runTest {
        linkMusicToTimer(on = false)
        val music = playing()
        apply(TimerCommand.Start("start"))

        music.changePlaying(false)
        delays.runDue(after = 60_000)

        assertThat(musicPausedLong).isEqualTo(0)
    }

    @Test
    fun `music paused because the user paused the timer does not loop back (DEC-A16 with DEC-A17)`() = runTest {
        linkMusicToTimer()
        val music = playing()
        apply(TimerCommand.Start("start"))

        apply(TimerCommand.Pause("pause"))
        music.changePlaying(false)
        delays.runDue(after = 60_000)

        assertThat(musicPausedLong).isEqualTo(0)
    }

    @Test
    fun `the stop-at-end pause after the limit does not ask the timer to pause (DEC-A17)`() = runTest {
        linkMusicToTimer()
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))
        music.changeTrack("b")
        music.changePlaying(false)

        delays.runDue(after = 60_000)

        assertThat(musicPausedLong).isEqualTo(0)
    }

    @Test
    fun `a music app that goes away does not pause the timer (DEC-A17, MT-T7)`() = runTest {
        linkMusicToTimer()
        val music = playing()
        apply(TimerCommand.Start("start"))

        music.changePlaying(false)
        music.destroy()
        gateway.remove(music)
        delays.runDue(after = 60_000)

        assertThat(musicPausedLong).isEqualTo(0)
    }

    @Test
    fun `a failing timer callback leaves observation running (DEC-A17, MT-T7)`() = runTest {
        settings = settings.copy(pauseTimerWithMusic = true)
        coordinator.onMusicPausedLong = { throw IllegalStateException("boom") }
        val music = playing()
        apply(TimerCommand.Start("start"))

        music.changePlaying(false)
        delays.runDue(after = 10_000)

        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }

    // ---- review regressions ----

    @Test
    fun `stop-at-end works again in a second session of the same process (review HIGH-3)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))
        coordinator.keepListening()
        apply(TimerCommand.Finish("finish"))

        session = TimerSession.ready("s2", Allocation.bounded(25 * minute))
        apply(TimerCommand.Start("start-2"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit-2"))

        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.ARMED)
        assertThat(music.sent).isEmpty()
    }

    @Test
    fun `songs switched while the timer is paused are not counted (review HIGH-4, DEC-A14)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(60_000)
        coordinator.onActiveTime()
        apply(TimerCommand.Pause("pause"))

        music.changeTrack("b")
        clock.advance(120_000)
        coordinator.onActiveTime()
        assertThat(coordinator.snapshot.value.countedItems).isEqualTo(1)

        apply(TimerCommand.Resume("resume"))
        clock.advance(30_000)
        coordinator.onActiveTime()
        assertThat(coordinator.snapshot.value.countedItems).isEqualTo(2)
    }

    @Test
    fun `turning counting off mid session releases observation and cancels the wait (review HIGH-5, QA-A28)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        settings = settings.copy(songCountingEnabled = false)
        coordinator.onSettingsChanged()

        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OFF)
        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.CANCELLED)
        assertThat(music.watched).isFalse()
    }

    @Test
    fun `turning counting on mid session starts observing from that point (review MEDIUM-17, DEC-A13)`() = runTest {
        settings = settings.copy(songCountingEnabled = false)
        playing()
        apply(TimerCommand.Start("start"))
        clock.advance(5 * minute)

        settings = settings.copy(songCountingEnabled = true)
        coordinator.onSettingsChanged()

        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
        assertThat(coordinator.snapshot.value.startedMidSession).isTrue()
    }

    @Test
    fun `access granted later starts observing on the next active time check`() = runTest {
        gateway.granted = false
        playing()
        apply(TimerCommand.Start("start"))
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OFF)

        gateway.granted = true
        clock.advance(60_000)
        coordinator.onActiveTime()

        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }

    @Test
    fun `reconciling the same live session keeps the count (review MEDIUM-9)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(40_000)
        coordinator.onActiveTime()

        coordinator.handle(session, TimerEffect.StartObservation)

        assertThat(coordinator.snapshot.value.countedItems).isEqualTo(1)
        assertThat(music.sent).isEmpty()
    }

    @Test
    fun `a handoff is not a song boundary, losing the target cancels the wait (review MEDIUM-11)`() = runTest {
        val first = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        apply(TimerCommand.ReachLimit("limit"))

        gateway.remove(first)

        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.CANCELLED)
        val other = gateway.add(FakeSession("com.iloen.melon", track = TrackMetadata("com.iloen.melon", "x", "A", 200_000)))
        assertThat(other.sent).isEmpty()
    }

    @Test
    fun `an unexpected media error turns observation off without crashing the timer (review MEDIUM-15)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))

        music.failNext = true
        music.changeTrack("b")

        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OFF)
    }

    @Test
    fun `all work runs on the serial runner (review CRITICAL-1)`() = runTest {
        val queued = mutableListOf<() -> Unit>()
        val serialCoordinator = ObservationCoordinator(
            gateway, clock, "com.gb6105.musictimely", { settings }, { ytm }, delays,
        ) { queued += it }
        playing()

        serialCoordinator.handle(session, TimerEffect.StartObservation)
        serialCoordinator.onTransportTap()
        assertThat(serialCoordinator.snapshot.value.state).isEqualTo(ObservationState.OFF)

        queued.toList().forEach { it() }
        assertThat(serialCoordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }

    // ---- review 2 regressions ----

    @Test
    fun `the result waits for the queued stop and gets that session's count (review 2 H2)`() = runTest {
        val queued = mutableListOf<() -> Unit>()
        val serialCoordinator = ObservationCoordinator(gateway, clock, "own", { settings }, { ytm }, delays) { queued += it }
        playing()
        serialCoordinator.handle(session.copy(state = com.gb6105.musictimely.domain.model.timer.TimerState.RUNNING), TimerEffect.StartObservation)
        queued.toList().also { queued.clear() }.forEach { it() }
        clock.advance(40_000)

        serialCoordinator.handle(session.copy(state = com.gb6105.musictimely.domain.model.timer.TimerState.ENDED, accumulatedActiveMs = 40_000), TimerEffect.StopObservation)
        val result = async { serialCoordinator.awaitObserved(session.sessionId) }
        queued.toList().forEach { it() }

        assertThat(result.await()).isEqualTo(ObservedCount(count = 1, startedMidSession = false))
    }

    @Test
    fun `turning observation back on in the same session keeps the songs already counted (review 2 H3)`() = runTest {
        playing()
        apply(TimerCommand.Start("start"))
        clock.advance(40_000)
        coordinator.onActiveTime()
        assertThat(coordinator.snapshot.value.countedItems).isEqualTo(1)

        gateway.revoked = true
        gateway.sessionsChanged()
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OFF)
        gateway.revoked = false
        clock.advance(10_000)
        coordinator.onActiveTime()

        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
        assertThat(coordinator.snapshot.value.countedItems).isAtLeast(1)
        assertThat(coordinator.snapshot.value.hadGap).isTrue()
    }

    @Test
    fun `pausing the observed app for the single-source choice never sends play (review 2 H4)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        music.playing = false

        coordinator.pauseObserved()

        assertThat(music.sent).isEmpty()
        music.playing = true
        coordinator.pauseObserved()
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `an unexpected error inside queued work does not escape the observation thread (review 2 L1)`() = runTest {
        val throwing = ObservationCoordinator(gateway, clock, "own", { throw IllegalStateException("prefs") }, { ytm }, delays)

        throwing.onSettingsChanged()
        throwing.onActiveTime()
    }

    @Test
    fun `stop-at-end reads observation after probing the item, so a failing probe does not arm (review 2 L2)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute)
        music.failNext = true

        apply(TimerCommand.ReachLimit("limit"))

        assertThat(coordinator.stopAtEnd.value).isNotEqualTo(StopAtEndPhase.ARMED)
    }

    @Test
    fun `counts are recorded as they change even with no screen refresh (review 2 M2)`() = runTest {
        val music = playing()
        apply(TimerCommand.Start("start"))

        clock.advance(200_000)
        music.changeTrack("b")
        clock.advance(40_000)
        music.changeTrack("c")

        assertThat(recorded).containsExactly("s" to 0, "s" to 1, "s" to 2).inOrder()
    }


    @Test
    fun `a song boundary past the target before the late alarm reaches the limit first, then pauses once`() = runTest {
        var limitDue = 0
        coordinator.onLimitDue = { limitDue++ }
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute + 20_000) // inexact alarm has not fired yet

        music.changeTrack("b")

        assertThat(limitDue).isEqualTo(1)
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.STOPPING)

        apply(TimerCommand.ReachLimit("late-alarm"))

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
        assertThat(coordinator.stopAtEnd.value).isEqualTo(StopAtEndPhase.STOPPING)
    }

    @Test
    fun `observation changes before the target never touch the timer (MT-T7, SRS Android 5_2)`() = runTest {
        var limitDue = 0
        coordinator.onLimitDue = { limitDue++ }
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(10 * minute)

        music.changeTrack("b")
        music.changePlaying(false)
        music.changePlaying(true)
        music.destroy()
        gateway.remove(music)
        gateway.granted = false
        settings = settings.copy(songCountingEnabled = false)
        coordinator.onSettingsChanged()
        coordinator.onActiveTime()

        assertThat(limitDue).isEqualTo(0)
        assertThat(session.state).isEqualTo(TimerState.RUNNING)
        assertThat(session.elapsedMs(clock.monotonicMs)).isEqualTo(10 * minute)
    }

    @Test
    fun `a failing limit callback neither turns observation off nor blocks the pause (review L1, MT-T7)`() = runTest {
        coordinator.onLimitDue = { throw IllegalStateException("boom") }
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(25 * minute + 20_000)

        music.changeTrack("b")

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }

    @Test
    fun `a song boundary before the target neither reaches the limit nor pauses`() = runTest {
        var limitDue = 0
        coordinator.onLimitDue = { limitDue++ }
        val music = playing()
        apply(TimerCommand.Start("start"))
        clock.advance(20 * minute)

        music.changeTrack("b")

        assertThat(limitDue).isEqualTo(0)
        assertThat(music.sent).isEmpty()
    }

    // ---- F18 M1 재생 기록 (DEC-A18) ----

    @Test
    fun `counted songs are reported with the live session id, including the last one counted at finish (F18 M1)`() = runTest {
        val songs = mutableListOf<Pair<String, String>>()
        coordinator.onSongCounted = { id, entry -> songs += id to entry.title }
        val music = playing("a")
        apply(TimerCommand.Start("start"))

        clock.advance(200_000)
        music.changeTrack("b")
        clock.advance(40_000)
        apply(TimerCommand.Finish("finish"))

        assertThat(songs).containsExactly("s" to "a", "s" to "b").inOrder()
    }

    @Test
    fun `songs passing during a timer pause are not reported (F18 M1, QA-A14)`() = runTest {
        val songs = mutableListOf<String>()
        coordinator.onSongCounted = { _, entry -> songs += entry.title }
        val music = playing("a")
        apply(TimerCommand.Start("start"))
        clock.advance(10_000)
        apply(TimerCommand.Pause("pause"))

        clock.advance(200_000)
        music.changeTrack("b")
        clock.advance(200_000)
        music.changeTrack("c")
        coordinator.onActiveTime()

        assertThat(songs).isEmpty()
    }

    @Test
    fun `a failing playlist callback keeps counting (MT-T7)`() = runTest {
        coordinator.onSongCounted = { _, _ -> throw IllegalStateException("disk") }
        val music = playing("a")
        apply(TimerCommand.Start("start"))

        clock.advance(200_000)
        music.changeTrack("b")
        clock.advance(40_000)
        coordinator.onActiveTime()

        assertThat(coordinator.snapshot.value.countedItems).isEqualTo(2)
        assertThat(coordinator.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }
}

class FakeDelayer(private val clock: FakeClock) : Delayer {
    private data class Task(val atMono: Long, val action: () -> Unit, var cancelled: Boolean = false)
    private val tasks = mutableListOf<Task>()
    val pending get() = tasks.count { !it.cancelled }

    override fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable {
        val task = Task(clock.monotonicMs + delayMs, action)
        tasks += task
        return AutoCloseable { task.cancelled = true }
    }

    fun runDue(after: Long) {
        clock.advance(after)
        tasks.filter { !it.cancelled && it.atMono <= clock.monotonicMs }.forEach {
            it.cancelled = true
            it.action()
        }
    }

}
