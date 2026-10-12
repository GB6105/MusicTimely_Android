package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.observation.TrackMetadata

class FakeMediaSessionGateway : MediaSessionGateway {
    var granted = true
    var revoked = false
    val sessions = mutableListOf<FakeSession>()
    private var sessionsWatcher: (() -> Unit)? = null
    val openSessionWatchers get() = if (sessionsWatcher != null) 1 else 0

    override fun isAccessGranted() = granted

    override fun activeSessions(): List<MediaSessionHandle> {
        if (revoked) throw SecurityException("revoked")
        return sessions.toList()
    }

    override fun watchSessions(onChanged: () -> Unit): AutoCloseable {
        sessionsWatcher = onChanged
        return AutoCloseable { sessionsWatcher = null }
    }

    fun sessionsChanged() = sessionsWatcher?.invoke()

    fun add(session: FakeSession): FakeSession = session.also { sessions += it; sessionsChanged() }

    fun remove(session: FakeSession) {
        sessions -= session
        session.destroy()
        sessionsChanged()
    }
}

class FakeSession(
    override val packageName: String,
    var playing: Boolean = true,
    var track: TrackMetadata? = null,
    var position: Long? = null,
) : MediaSessionHandle {
    private var listener: MediaSessionListener? = null
    val sent = mutableListOf<MusicCommand>()
    var failNext = false
    val watched get() = listener != null

    override fun isPlaying() = playing
    override fun metadata(): TrackMetadata? {
        if (failNext) {
            failNext = false
            throw IllegalStateException("remote died")
        }
        return track
    }
    override fun positionMs() = position

    override fun watch(listener: MediaSessionListener): AutoCloseable {
        this.listener = listener
        return AutoCloseable { this.listener = null }
    }

    override fun send(command: MusicCommand) {
        sent += command
    }

    fun changeTrack(title: String?, artist: String = "A", durationMs: Long? = 200_000) {
        track = TrackMetadata(packageName, title, artist, durationMs)
        listener?.onMetadataChanged()
    }

    fun changePlaying(isPlaying: Boolean) {
        playing = isPlaying
        listener?.onPlaybackChanged()
    }

    fun destroy() = listener?.onDestroyed()
}
