package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.observation.MediaSessionInfo
import com.gb6105.musictimely.domain.model.observation.MusicStoppedHint
import com.gb6105.musictimely.domain.model.observation.ObservationEvent
import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.observation.ObservationStateMachine
import com.gb6105.musictimely.domain.model.observation.SeenEntry
import com.gb6105.musictimely.domain.model.observation.SeenKind
import com.gb6105.musictimely.domain.model.observation.SongCounter
import com.gb6105.musictimely.domain.model.observation.TargetSelector
import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.translation.ObservedItem
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.ObservedCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Song counting coordinator (SRS Android §5.2, §7.2).
 *
 * Thread confinement: every method and every gateway callback runs on the single observation thread
 * (see [ObservationCoordinator]); there are no locks. Titles and artists live in memory here; only counted
 * songs leave through [onSongCounted] to the session playlist store (DEC-A18, amends FR-A038), with the album art
 * captured while that song was current (DEC-A21). Memory is
 * dropped when the session ends or observation turns off (FR-A039, FR-A040). Any failure of a media-session call — SecurityException after a revoke or a remote app dying — turns observation off
 * without an error dialog (FR-A037) and never reaches the timer (MT-T7).
 */
class MediaObserver(
    private val gateway: MediaSessionGateway,
    private val clock: ClockPort,
    private val ownPackage: String,
    private val countingEnabled: () -> Boolean,
    private val activeMs: () -> Long,
) {
    private val state = MutableStateFlow(ObservationSnapshot())
    val snapshot: StateFlow<ObservationSnapshot> = state

    /** A different song started on the same observed session (stop-at-end trigger). */
    var onTrackBoundary: (() -> Unit)? = null

    /** The observed session's playing state after each playback change. */
    var onPlaybackChanged: ((Boolean) -> Unit)? = null

    /** Called after every published snapshot (on the observation thread). */
    var onSnapshotChanged: (() -> Unit)? = null

    /** A song was just counted (F18 M1, DEC-A18): the counted item, in play order. Observation thread only. */
    var onSongCounted: ((PlaylistEntry) -> Unit)? = null

    /** The observed session went away (stop-at-end must cancel, FR-A052). */
    var onTargetLost: (() -> Unit)? = null

    private var observation = ObservationState.OFF
    private var preferredPackage: String? = null
    private var target: MediaSessionHandle? = null
    private var targetWatch: AutoCloseable? = null
    private var sessionsWatch: AutoCloseable? = null
    private var counter: SongCounter? = null
    private var hint = MusicStoppedHint()
    private var hintShown = false
    private var currentTitle: String? = null
    private var lastTrackKey: List<String>? = null

    /** Album art of the item with key [artKey], captured while it was current (DEC-A21). Memory only. */
    private var artKey: List<String>? = null
    private var art: Artwork? = null
    private var hadTarget = false
    private var everObserved = false
    private var handedOff = false
    private var listenerLost = false
    private val seen = mutableListOf<SeenEntry>()

    val isActive: Boolean get() = observation.holdsCallbacks

    /** An observed session is attached (DEC-A17 checks the paused music is still there). */
    val hasTarget: Boolean get() = target != null && observation == ObservationState.OBSERVING

    fun start(preferredPackage: String?) {
        release(resetMemory = true)
        this.preferredPackage = preferredPackage
        observation = ObservationState.OFF
        if (!countingEnabled()) return publish()
        guarded {
            val granted = gateway.isAccessGranted()
            observation = ObservationStateMachine.next(ObservationState.OFF, ObservationEvent.Enabled(granted, setting = true))
            if (observation == ObservationState.OFF) return@guarded
            observation = ObservationStateMachine.next(observation, ObservationEvent.SessionRunning)
            setCounter(SongCounter.start(observedFromActiveMs = activeMs()))
            val watchedSessions = gateway.watchSessions { guarded { onSessionsChanged() } }
            sessionsWatch = watchedSessions
            findTarget()
        }
        publish()
    }

    /**
     * Same session, observation back on (switch, permission, reconcile): keep the counter — the gap was already
     * marked, so the result stays mixed and nothing is interpolated (review 2 H3).
     */
    fun resume(preferredPackage: String?) {
        val kept = counter ?: return start(preferredPackage)
        start(preferredPackage)
        if (observation != ObservationState.OFF) setCounter(kept.onGap(activeMs()))
        publish()
    }

    /** Live playing state of the observed session, not the last published snapshot. */
    fun isTargetPlaying(): Boolean {
        val handle = target ?: return false
        var playing = false
        guarded { playing = handle.isPlaying() }
        return playing
    }

    fun stop() {
        val next = ObservationStateMachine.next(observation, ObservationEvent.SessionEnded)
        release(resetMemory = true)
        observation = next
        publish()
    }

    fun onActiveTime() {
        setCounter(counter?.advanceTo(activeMs()))
        guarded { evaluateHint() }
        publish()
    }

    fun onListenerDisconnected() {
        val next = ObservationStateMachine.next(observation, ObservationEvent.ListenerDisconnected)
        if (next == ObservationState.DEGRADED && observation != ObservationState.DEGRADED) listenerLost = true
        observation = next
        publish()
    }

    /**
     * Only lifts a degrade the listener caused. Without a target, re-register and search again so music
     * started later is still found; the counter is kept and the gap marked (review H1).
     */
    fun onListenerConnected() {
        if (!listenerLost) return
        listenerLost = false
        if (target == null) return resume(preferredPackage)
        observation = ObservationStateMachine.next(observation, ObservationEvent.CallbackResumed)
        publish()
    }

    /** Turns observation off at once when the in-app switch is off (FR-A041). */
    fun onSettingsChanged() {
        if (!countingEnabled() && observation != ObservationState.OFF) turnOff(ObservationEvent.SettingTurnedOff)
    }

    /** The integer count for the result; null when no session was ever observed. */
    fun observedCount(): ObservedCount? {
        val c = counter?.advanceTo(activeMs())?.also { setCounter(it) } ?: return null
        if (!everObserved) return null
        return ObservedCount(c.countedItems, startedMidSession = c.startedMidSession || c.hadGap)
    }

    /** Length and position of the observed item when both are known (DEC-A05 cap). */
    fun observedItem(): ObservedItem? {
        val handle = target ?: return null
        var item: ObservedItem? = null
        guarded {
            val duration = handle.metadata()?.durationMs
            val position = handle.positionMs()
            if (duration != null && position != null) item = ObservedItem(duration, position)
        }
        return item
    }

    /** Sends to the observed session only (FR-A054). */
    fun send(command: MusicCommand): Boolean {
        val handle = target ?: return false
        if (observation != ObservationState.OBSERVING) return false
        var sent = false
        guarded {
            handle.send(command)
            sent = true
            log(if (command == MusicCommand.PLAY) SeenKind.COMMAND_PLAY else SeenKind.COMMAND_PAUSE, handle, null, null)
        }
        publish()
        return sent
    }

    private val listener = object : MediaSessionListener {
        override fun onMetadataChanged() = guarded { handleMetadata() }

        override fun onPlaybackChanged() = guarded {
            val handle = target ?: return@guarded
            handle.positionMs()?.let { setCounter(counter?.onPosition(it, activeMs())) }
            evaluateHint()
            onPlaybackChanged?.invoke(handle.isPlaying())
        }

        override fun onDestroyed() = guarded { loseTarget() }
    }

    private fun onSessionsChanged() {
        if (target == null && observation == ObservationState.SEARCHING) {
            findTarget()
        } else if (target != null && gateway.activeSessions().none { it.packageName == target?.packageName }) {
            loseTarget()
        }
        publish()
    }

    private fun findTarget() {
        val sessions = gateway.activeSessions()
        val info = TargetSelector.select(preferredPackage, sessions.map { MediaSessionInfo(it.packageName, it.isPlaying()) }, ownPackage)
            ?: return
        attach(sessions.first { it.packageName == info.packageName })
        observation = ObservationStateMachine.next(observation, ObservationEvent.TargetFound)
    }

    private fun attach(handle: MediaSessionHandle) {
        everObserved = true
        handedOff = hadTarget
        hadTarget = true
        target = handle
        targetWatch = handle.watch(listener)
        // A handoff to another app is not a song boundary of the observed session.
        lastTrackKey = null
        log(SeenKind.CONNECTED, handle, null, null)
        handleMetadata()
        evaluateHint()
    }

    private fun loseTarget() {
        detachTarget()
        currentTitle = null
        onTargetLost?.invoke()
        val sessions = gateway.activeSessions()
        val alternative = TargetSelector.select(null, sessions.map { MediaSessionInfo(it.packageName, it.isPlaying()) }, ownPackage)
            ?.takeIf { it.isPlaying }
        observation = ObservationStateMachine.next(observation, ObservationEvent.TargetLost(alternativePlaying = alternative != null))
        if (alternative != null) {
            attach(sessions.first { it.packageName == alternative.packageName })
        } else {
            setCounter(counter?.onGap(activeMs()))
        }
        publish()
    }

    private fun handleMetadata() {
        val handle = target ?: return
        val track = handle.metadata() ?: return
        setCounter(counter?.onTrack(track, activeMs()))
        val title = track.title?.takeIf { it.isNotBlank() } ?: return
        currentTitle = title
        val key = trackKey(track, title)
        // After setCounter above: a previous item counted by this change still finds its own art under its key.
        if (key != artKey || art == null) {
            artKey = key
            art = readArtwork(handle, track)
        }
        if (key != lastTrackKey) {
            if (lastTrackKey != null) onTrackBoundary?.invoke()
            lastTrackKey = key
            log(SeenKind.TRACK, handle, title, track.artist)
        }
        publish()
    }

    private fun evaluateHint() {
        val playing = target?.isPlaying() ?: return
        val result = hint.onPlayback(playing, clock.monotonicNowMs())
        hint = result.next
        if (result.show) hintShown = true
        if (playing) hintShown = false // music is back: the hint no longer holds (emulator finding)
        publish()
    }

    /** Off: release callbacks and drop titles; only the integer count stays for the result. */
    private fun turnOff(event: ObservationEvent) {
        val hadCounter = counter != null
        release(resetMemory = false)
        if (hadCounter) setCounter(counter?.onGap(activeMs()))
        observation = ObservationStateMachine.next(observation, event)
        currentTitle = null
        lastTrackKey = null
        forgetArtwork()
        seen.clear()
        onTargetLost?.invoke()
        publish()
    }

    private fun release(resetMemory: Boolean) {
        detachTarget()
        runCatching { sessionsWatch?.close() }
        sessionsWatch = null
        if (resetMemory) {
            counter = null
            seen.clear()
            hint = MusicStoppedHint()
            hintShown = false
            currentTitle = null
            lastTrackKey = null
            forgetArtwork()
            hadTarget = false
            everObserved = false
            handedOff = false
            listenerLost = false
        }
    }

    private fun detachTarget() {
        runCatching { targetWatch?.close() }
        targetWatch = null
        target = null
    }

    private fun log(kind: SeenKind, handle: MediaSessionHandle, title: String?, artist: String?) {
        seen += SeenEntry(clock.monotonicNowMs(), kind, Provider.fromPackage(handle.packageName), title, artist)
    }

    /** A failing or missing bitmap is no artwork; it never turns observation off or stops the count (MT-T7). */
    private fun readArtwork(handle: MediaSessionHandle, track: TrackMetadata): Artwork? =
        runCatching { Artwork.of(handle.artwork(track)) }.getOrNull()

    private fun forgetArtwork() {
        artKey = null
        art = null
    }

    /** Same identity rule as the song counter signature (DEC-A03): package, title, artist. */
    private fun trackKey(track: TrackMetadata, title: String) = listOf(track.packageName, title, track.artist.orEmpty())

    /** Every counter transition goes through here so a newly counted song reaches the playlist exactly once. */
    private fun setCounter(next: SongCounter?) {
        counter = next
        val track = next?.justCounted ?: return
        // A failing playlist write must not turn observation off or stop the count (MT-T7); nothing is logged.
        // The art is attached only when it was captured for this very item (DEC-A21), never the next song's.
        val countedArt = art.takeIf { artKey == trackKey(track, track.title.orEmpty()) }
        runCatching { onSongCounted?.invoke(PlaylistEntry.of(next.countedItems, track, countedArt)) }
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (_: SecurityException) {
            turnOff(ObservationEvent.PermissionRevoked)
        } catch (_: RuntimeException) {
            // A remote session failing must not crash the app or the timer; the message is not logged.
            turnOff(ObservationEvent.PermissionRevoked)
        }
    }

    private fun publish() {
        val c = counter
        val observing = observation == ObservationState.OBSERVING
        state.value = ObservationSnapshot(
            state = observation,
            provider = target?.packageName?.let(Provider::fromPackage),
            title = if (observing) currentTitle else null,
            isPlaying = observing && runCatching { target?.isPlaying() }.getOrNull() == true,
            countedItems = c?.countedItems ?: 0,
            currentIndex = c?.currentIndex ?: 1,
            loops = c?.loops ?: 0,
            startedMidSession = c?.startedMidSession ?: false,
            observedFromActiveMs = c?.observedFromActiveMs ?: 0,
            hadGap = c?.hadGap ?: false,
            handedOff = observing && handedOff,
            showStoppedHint = hintShown,
            seen = seen.toList(),
        )
        onSnapshotChanged?.invoke()
    }
}
