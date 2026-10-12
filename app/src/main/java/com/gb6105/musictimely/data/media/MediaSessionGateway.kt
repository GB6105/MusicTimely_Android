package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.observation.TrackMetadata

/** Android-free view of other apps' media sessions (MediaSessionManager behind it). */
interface MediaSessionGateway {
    fun isAccessGranted(): Boolean

    /** @throws SecurityException when notification access was revoked. */
    fun activeSessions(): List<MediaSessionHandle>

    fun watchSessions(onChanged: () -> Unit): AutoCloseable
}

interface MediaSessionHandle {
    val packageName: String
    fun isPlaying(): Boolean
    fun metadata(): TrackMetadata?
    fun positionMs(): Long?
    fun watch(listener: MediaSessionListener): AutoCloseable

    /** Only `play` and `pause` exist (FR-A046). */
    fun send(command: MusicCommand)
}

interface MediaSessionListener {
    fun onMetadataChanged()
    fun onPlaybackChanged()
    fun onDestroyed()
}
