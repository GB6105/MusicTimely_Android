package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.TimerEffectSink
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakePlaylistRepository
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** DI wiring of ObservationCoordinator callbacks (review: onLimitDue and count persistence were untested). */
@OptIn(ExperimentalCoroutinesApi::class)
class ObservationWiringTest {
    private val minute = 60_000L
    private val ytm = "com.google.android.apps.youtube.music"
    private val clock = FakeClock()
    private val gateway = FakeMediaSessionGateway()
    private val settings = FakeSettingsRepository(MusicSettings(songCountingEnabled = true, stopAtEndEnabled = true))
    private val sessions = FakeSessionRepository()
    private val playlists = FakePlaylistRepository(clock)
    private val delays = FakeDelayer(clock)
    private val coordinator = ObservationCoordinator(gateway, clock, "com.gb6105.musictimely", { settings.settings.value }, { ytm }, delays)
    private val sink = object : TimerEffectSink {
        override suspend fun apply(session: TimerSession, effects: List<TimerEffect>) =
            effects.forEach { coordinator.handle(session, it) }
    }
    private val dispatch = DispatchTimerCommandUseCase(sessions, clock, sink)

    @Test
    fun `a boundary past the target before the alarm reaches the timer limit (SRS Android 5_3)`() = runTest(UnconfinedTestDispatcher()) {
        coordinator.connect(backgroundScope, sessions, { dispatch.tick() }, settings, playlists, pauseTimer = { dispatch(TimerCommand.Pause("music-paused")) })
        val music = gateway.add(FakeSession(ytm, track = TrackMetadata(ytm, "a", "A", 200_000), position = 50_000))
        sessions.save(TimerSession.ready("s", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start"))
        clock.advance(25 * minute + 20_000)

        music.changeTrack("b")

        assertThat(sessions.stored?.state).isEqualTo(TimerState.LIMIT_REACHED)
        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
    }

    @Test
    fun `counted songs are written to the session store (SRS Android 9_2)`() = runTest(UnconfinedTestDispatcher()) {
        coordinator.connect(backgroundScope, sessions, { dispatch.tick() }, settings, playlists, pauseTimer = { dispatch(TimerCommand.Pause("music-paused")) })
        val music = gateway.add(FakeSession(ytm, track = TrackMetadata(ytm, "a", "A", 200_000), position = 0))
        sessions.save(TimerSession.ready("s", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start"))

        clock.advance(60_000)
        music.changeTrack("b")

        assertThat(sessions.recordedObservations).isNotEmpty()
    }

    @Test
    fun `with the setting on, music paused for 10 seconds pauses the timer (DEC-A17)`() = runTest(UnconfinedTestDispatcher()) {
        settings.update { it.copy(pauseTimerWithMusic = true) }
        coordinator.connect(backgroundScope, sessions, { dispatch.tick() }, settings, playlists, pauseTimer = { dispatch(TimerCommand.Pause("music-paused")) })
        val music = gateway.add(FakeSession(ytm, track = TrackMetadata(ytm, "a", "A", 200_000), position = 50_000))
        sessions.save(TimerSession.ready("s", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start"))

        music.changePlaying(false)
        delays.runDue(after = 10_000)

        assertThat(sessions.stored?.state).isEqualTo(TimerState.PAUSED)
    }

    @Test
    fun `counted songs are appended to the session playlist in play order (F18 M1, DEC-A18)`() = runTest(UnconfinedTestDispatcher()) {
        coordinator.connect(backgroundScope, sessions, { dispatch.tick() }, settings, playlists, pauseTimer = { dispatch(TimerCommand.Pause("music-paused")) })
        val music = gateway.add(FakeSession(ytm, track = TrackMetadata(ytm, "a", "A", 200_000), position = 0))
        sessions.save(TimerSession.ready("s", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start"))

        clock.advance(200_000)
        music.changeTrack("b", artist = "B")
        clock.advance(200_000)
        music.changeTrack("a")
        clock.advance(40_000)
        dispatch(TimerCommand.Finish("finish"))

        assertThat(playlists.playlist("s").map { it.order to it.title }).containsExactly(1 to "a", 2 to "b", 3 to "a").inOrder()
        assertThat(playlists.playlist("s")[1].artist).isEqualTo("B")
    }

    @Test
    fun `a failing playlist store does not stop counting or the count checkpoint (MT-T7)`() = runTest(UnconfinedTestDispatcher()) {
        val scope = kotlinx.coroutines.CoroutineScope(backgroundScope.coroutineContext + kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> })
        playlists.failAppend = true
        coordinator.connect(scope, sessions, { dispatch.tick() }, settings, playlists, pauseTimer = { dispatch(TimerCommand.Pause("music-paused")) })
        val music = gateway.add(FakeSession(ytm, track = TrackMetadata(ytm, "a", "A", 200_000), position = 0))
        sessions.save(TimerSession.ready("s", Allocation.bounded(25 * minute)))
        dispatch(TimerCommand.Start("start"))

        clock.advance(200_000)
        music.changeTrack("b")

        assertThat(coordinator.snapshot.value.countedItems).isEqualTo(1)
        assertThat(sessions.recordedObservations).isNotEmpty()
    }
}
