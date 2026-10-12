package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.data.effect.TimerEffectHandler
import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.control.TransportButton
import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.stopatend.StopAtEnd
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndConditions
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndEffect
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndEvent
import com.gb6105.musictimely.domain.model.stopatend.StopAtEndPhase
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.ControlUiState
import com.gb6105.musictimely.domain.repository.MusicControlPort
import com.gb6105.musictimely.domain.repository.ObservedCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Runs observation work on the single observation thread (the thread media callbacks arrive on). */
fun interface SerialRunner {
    fun run(block: () -> Unit)
}

/** One-shot delayed work on the observation thread. No polling (NFR-A006). */
fun interface Delayer {
    fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable
}

/**
 * Connects timer effects to song counting, stop-at-end (SRS Android §5.3), the user play/pause button (DEC-A07)
 * and the timer link (DEC-A16). `play` is sent only from [onTransportTap] and from resuming music this app paused
 * with the timer; stop-at-end only ever pauses (FR-A045).
 *
 * Thread confinement: every entry point is queued on [serial]; media callbacks and [delayer] tasks run on the
 * same thread, so no locks are needed (no lock-order deadlocks between UI, receiver and media threads).
 * State is published through StateFlows that any thread may read.
 */
class ObservationCoordinator(
    gateway: MediaSessionGateway,
    private val clock: ClockPort,
    ownPackage: String,
    private val settings: () -> MusicSettings,
    private val preferredPackage: () -> String?,
    private val delayer: Delayer,
    private val serial: SerialRunner = SerialRunner { it() },
) : TimerEffectHandler, MusicControlPort {

    /** Integer count changes for the checkpoint (sessionId, count, observedFromActiveMs); memory titles never leave. */
    var onCountChanged: ((String, Int, Long) -> Unit)? = null

    /** F18 M1: a counted song for the session playlist (sessionId, entry); runs on the observation thread. */
    var onSongCounted: ((String, PlaylistEntry) -> Unit)? = null

    /** A song boundary arrived past the target before the inexact alarm; the timer should reach the limit now. */
    var onLimitDue: (() -> Unit)? = null

    /** DEC-A17: observed music has stayed paused long enough; the timer should pause too. */
    var onMusicPausedLong: (() -> Unit)? = null

    /** Latest session from any timer effect, so active time freezes while paused (DEC-A14). */
    private var session: TimerSession? = null
    private var observedSessionId: String? = null

    private val observer = MediaObserver(gateway, clock, ownPackage, { settings().songCountingEnabled }) {
        session?.elapsedMs(clock.monotonicNowMs()) ?: 0
    }
    override val snapshot: StateFlow<ObservationSnapshot> = observer.snapshot

    private val lastObservedState = MutableStateFlow<ObservedCount?>(null)
    override val lastObserved: StateFlow<ObservedCount?> = lastObservedState
    private val finished = MutableStateFlow<Pair<String, ObservedCount?>?>(null)

    private val stopPhase = MutableStateFlow(StopAtEndPhase.IDLE)
    override val stopAtEnd: StateFlow<StopAtEndPhase> = stopPhase
    private var stopState = StopAtEnd.initial()
    private var stopDeadline: AutoCloseable? = null

    private val controlState = MutableStateFlow(ControlUiState())
    override val control: StateFlow<ControlUiState> = controlState
    private var button = TransportButton()
    private var buttonConfirm: AutoCloseable? = null

    init {
        observer.onSnapshotChanged = { reportCount() }
        observer.onTrackBoundary = { onTrackBoundary() }
        observer.onSongCounted = { entry ->
            val id = observedSessionId
            // A failing playlist callback must not stop observation (MT-T7); the entry is never logged.
            if (id != null) runCatching { onSongCounted?.invoke(id, entry) }
        }
        observer.onPlaybackChanged = { playing -> onPlayback(playing) }
        observer.onTargetLost = {
            cancelMusicPauseWatch()
            onStop(StopAtEndEvent.TargetLost)
        }
    }

    /** Sees every effect to keep [session] current; acts only on observation and stop-at-end effects. */
    override fun handles(effect: TimerEffect): Boolean = true

    override suspend fun handle(session: TimerSession, effect: TimerEffect) = onSerial {
        this.session = session
        if (session.state != TimerState.RUNNING) cancelMusicPauseWatch()
        when (effect) {
            TimerEffect.StartObservation -> startObservation(session)
            TimerEffect.StopObservation -> {
                onStop(StopAtEndEvent.Finished)
                val count = observer.observedCount()
                lastObservedState.value = count
                finished.value = session.sessionId to count
                observer.stop()
                observedSessionId = null
                linkedPause = null
                resetButton()
            }
            TimerEffect.EvaluateStopAtEnd ->
                if (limitHandled != session.sessionId to session.allocation.targetRevision) evaluateStopAtEnd(session)
            TimerEffect.PauseLinkedMusic -> pauseLinked(session)
            TimerEffect.ResumeLinkedMusic -> resumeLinked(session)
            TimerEffect.CancelStopAtEnd ->
                onStop(if (session.state == TimerState.ENDED) StopAtEndEvent.Finished else StopAtEndEvent.Extended)
            else -> Unit
        }
    }

    override fun onTransportTap() = onSerial {
        val tap = button.onTap(isPlaying = snapshot.value.isPlaying, nowMono = clock.monotonicNowMs())
        button = tap.next
        val command = tap.command ?: return@onSerial
        if (!observer.send(command)) return@onSerial
        buttonConfirm?.close()
        buttonConfirm = delayer.schedule(CONFIRM_MS) { onButtonTimeout() }
    }

    /** FR-A059 [멈추기]: pause only when the observed app is actually playing; never a toggle (review 2 H4). */
    override fun pauseObserved() = onSerial {
        if (observer.isTargetPlaying()) observer.send(MusicCommand.PAUSE)
    }

    /** Waits until StopObservation for [sessionId] ran on the observation thread (review 2 H2). */
    override suspend fun awaitObserved(sessionId: String): ObservedCount? =
        withTimeoutOrNull(AWAIT_STOP_MS) { finished.first { it?.first == sessionId } }?.second

    override fun consumeControlNotice() = onSerial {
        controlState.value = controlState.value.copy(noticePending = false)
    }

    override fun stopNow() = onSerial { onStop(StopAtEndEvent.StopNow(clock.monotonicNowMs())) }

    override fun keepListening() = onSerial { onStop(StopAtEndEvent.KeepListening) }

    /** Moves the song counter with session time and picks up access granted later (DEC-A13). */
    override fun onActiveTime() = onSerial {
        startIfNowPossible()
        observer.onActiveTime()
    }

    /** The notification listener lost its binding: observation degrades until it reconnects (SRS Android §7.2). */
    fun onListenerDisconnected() = onSerial { observer.onListenerDisconnected() }

    fun onListenerConnected() = onSerial { observer.onListenerConnected() }

    override fun onSettingsChanged() = onSerial {
        if (!settings().songCountingEnabled) {
            observer.onSettingsChanged()
            onStop(StopAtEndEvent.CountingTurnedOff)
        } else {
            startIfNowPossible()
        }
    }

    private fun startObservation(session: TimerSession) {
        if (observedSessionId == session.sessionId) {
            if (!observer.isActive) observer.resume(preferredPackage())
            return
        }
        resetStopAtEnd()
        observedSessionId = session.sessionId
        lastObservedState.value = null
        observer.start(preferredPackage())
    }

    /** Counting switched on or access granted while a session is live: observe from now (mixed result). */
    private fun startIfNowPossible() {
        val live = session ?: return
        if (live.state !in LIVE_STATES || observer.isActive || !settings().songCountingEnabled) return
        if (observedSessionId == live.sessionId) {
            observer.resume(preferredPackage())
        } else {
            observedSessionId = live.sessionId
            observer.start(preferredPackage())
        }
    }

    private var musicPauseWatch: AutoCloseable? = null

    /**
     * DEC-A17 (setting, off by default): observed music that stays paused for [MUSIC_PAUSE_MS] while the timer runs
     * asks the timer to pause. Our own pauses never trigger it (the timer is already paused or at the limit).
     */
    private fun watchMusicPause(playing: Boolean) {
        cancelMusicPauseWatch()
        val s = settings()
        if (playing || !s.pauseTimerWithMusic || !s.songCountingEnabled || session?.state != TimerState.RUNNING) return
        musicPauseWatch = delayer.schedule(MUSIC_PAUSE_MS) {
            musicPauseWatch = null
            if (session?.state == TimerState.RUNNING && observer.hasTarget && !observer.isTargetPlaying()) {
                runCatching { onMusicPausedLong?.invoke() } // a failing timer callback must not stop observation (MT-T7)
            }
        }
    }

    private fun cancelMusicPauseWatch() {
        musicPauseWatch?.close()
        musicPauseWatch = null
    }

    /** Session whose music this app paused together with the timer (DEC-A16, Android only). */
    private var linkedPause: String? = null

    private fun pauseLinked(session: TimerSession) {
        if (observer.isTargetPlaying() && observer.send(MusicCommand.PAUSE)) linkedPause = session.sessionId
    }

    /** Plays again only music this app paused, still the same song and still paused. */
    private fun resumeLinked(session: TimerSession) {
        if (linkedPause != session.sessionId) return
        linkedPause = null
        if (!observer.isTargetPlaying()) observer.send(MusicCommand.PLAY)
    }

    /** (sessionId, targetRevision) whose limit a song boundary already handled before the alarm. */
    private var limitHandled: Pair<String, Int>? = null

    /**
     * A boundary past the target while the inexact alarm is late: handle the limit first (stop-at-end
     * evaluation, then this boundary pauses), and ask the timer to reach the limit now.
     */
    private fun onTrackBoundary() {
        val now = clock.monotonicNowMs()
        val live = session
        linkedPause = null // another song is not the one this app paused (DEC-A16)
        val overdue = live != null && live.state == TimerState.RUNNING && (live.remainingMs(now) ?: 1) <= 0
        if (overdue && live != null) {
            limitHandled = live.sessionId to live.allocation.targetRevision
            evaluateStopAtEnd(live)
            // A failing timer callback must not turn observation off or skip the pause (review L1, MT-T7).
            runCatching { onLimitDue?.invoke() }
        }
        onStop(StopAtEndEvent.TrackBoundary(now))
    }

    private fun evaluateStopAtEnd(session: TimerSession) {
        // Probe the item first: a failing probe turns observation off and must not arm (review 2 L2).
        val item = observer.observedItem()
        val snap = snapshot.value
        val s = settings()
        onStop(
            StopAtEndEvent.LimitReached(
                revision = session.allocation.targetRevision,
                conditions = StopAtEndConditions(
                    settingOn = s.stopAtEndEnabled,
                    countingOn = s.songCountingEnabled,
                    infiniteMode = !session.allocation.bounded,
                ),
                observing = snap.state == ObservationState.OBSERVING,
                playing = snap.isPlaying,
                item = item,
                nowMono = clock.monotonicNowMs(),
            ),
        )
    }

    private fun onStop(event: StopAtEndEvent) {
        val result = stopState.on(event)
        stopState = result.next
        stopPhase.value = result.next.phase
        result.effects.forEach { if (it == StopAtEndEffect.SendPause) observer.send(MusicCommand.PAUSE) }
        rescheduleStopDeadline()
    }

    private fun rescheduleStopDeadline() {
        stopDeadline?.close()
        stopDeadline = stopState.nextDeadlineMono?.let { deadline ->
            delayer.schedule((deadline - clock.monotonicNowMs()).coerceAtLeast(0)) {
                onStop(StopAtEndEvent.Tick(clock.monotonicNowMs()))
            }
        }
    }

    /** Every queued task is isolated: an unexpected error must not kill the observation thread (review 2 L1). */
    private fun onSerial(block: () -> Unit) = serial.run {
        try {
            block()
            reportCount()
        } catch (_: RuntimeException) {
            // Contained; the message may carry song data and is not logged.
        }
    }

    private var lastReported: Pair<String, Int>? = null

    /** Reports integer count changes on the observation thread, screen on or off (review 2 M2). */
    private fun reportCount() {
        val id = observedSessionId ?: return
        val snap = snapshot.value
        if (snap.state != ObservationState.OBSERVING && snap.countedItems == 0) return
        val key = id to snap.countedItems
        if (key == lastReported) return
        lastReported = key
        onCountChanged?.invoke(id, snap.countedItems, snap.observedFromActiveMs)
    }

    private fun resetStopAtEnd() {
        stopDeadline?.close()
        stopDeadline = null
        stopState = StopAtEnd.initial()
        stopPhase.value = stopState.phase
    }

    private fun onPlayback(playing: Boolean) {
        // The user played music during the timer pause: it is theirs now, resume must not touch it (DEC-A16).
        if (playing) linkedPause = null
        watchMusicPause(playing)
        button = button.onPlayback(playing)
        if (controlState.value.unresponsive && !button.unresponsive) controlState.value = controlState.value.copy(unresponsive = false)
        onStop(StopAtEndEvent.PlaybackChanged(playing, clock.monotonicNowMs()))
    }

    private fun onButtonTimeout() {
        val tick = button.onTick(clock.monotonicNowMs())
        button = tick.next
        controlState.value = ControlUiState(
            unresponsive = button.unresponsive,
            noticePending = tick.notice || controlState.value.noticePending,
        )
    }

    private fun resetButton() {
        buttonConfirm?.close()
        button = TransportButton()
        controlState.value = ControlUiState()
    }

    private companion object {
        const val CONFIRM_MS = 2_000L
        const val AWAIT_STOP_MS = 2_000L
        const val MUSIC_PAUSE_MS = 10_000L
        val LIVE_STATES = setOf(TimerState.RUNNING, TimerState.PAUSED, TimerState.LIMIT_REACHED)
    }
}
