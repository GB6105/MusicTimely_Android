package com.gb6105.musictimely.domain.model.playlist

import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.domain.model.source.Provider

/** One counted song of a session (F18 M1). Titles and artists are personal data: never logged or sent (DEC-A18). */
data class PlaylistEntry(val order: Int, val title: String, val artist: String?, val provider: Provider) {
    companion object {
        fun of(order: Int, track: TrackMetadata): PlaylistEntry = PlaylistEntry(
            order = order,
            title = track.title.orEmpty(),
            artist = track.artist?.takeIf { it.isNotBlank() },
            provider = Provider.fromPackage(track.packageName),
        )
    }
}

/** Songs counted in one session, in play order with repeats (DEC-A18: kept on device at most 24 h). */
data class SessionPlaylist(val sessionId: String, val entries: List<PlaylistEntry>, val updatedAtEpochMs: Long) {
    fun append(entry: PlaylistEntry, nowEpochMs: Long): SessionPlaylist =
        copy(entries = entries + entry.copy(order = entries.size + 1), updatedAtEpochMs = nowEpochMs)

    fun isOlderThan(cutoffEpochMs: Long): Boolean = updatedAtEpochMs < cutoffEpochMs

    companion object {
        /** DEC-A18: same lifetime as an unconfirmed result (RestorePolicy.RESULT_TTL_MS). */
        const val TTL_MS: Long = 86_400_000
    }
}
