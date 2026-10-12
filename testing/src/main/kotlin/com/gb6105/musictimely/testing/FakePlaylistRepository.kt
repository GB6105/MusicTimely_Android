package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.playlist.SessionPlaylist
import com.gb6105.musictimely.domain.repository.PlaylistRepository

class FakePlaylistRepository(private val clock: FakeClock = FakeClock()) : PlaylistRepository {
    private val bySession = linkedMapOf<String, SessionPlaylist>()
    var failAppend = false
    val deleted = mutableListOf<String>()
    var cleared = 0
    var purgedBefore: Long? = null

    override suspend fun append(sessionId: String, entry: PlaylistEntry) {
        if (failAppend) throw java.io.IOException("disk full")
        val current = bySession[sessionId] ?: SessionPlaylist(sessionId, emptyList(), clock.wallNowEpochMs())
        bySession[sessionId] = current.append(entry, clock.wallNowEpochMs())
    }

    override suspend fun playlist(sessionId: String): List<PlaylistEntry> = bySession[sessionId]?.entries.orEmpty()

    override suspend fun delete(sessionId: String) {
        deleted += sessionId
        bySession.remove(sessionId)
    }

    override suspend fun clear() {
        cleared++
        bySession.clear()
    }

    override suspend fun purgeOlderThan(cutoffEpochMs: Long) {
        purgedBefore = cutoffEpochMs
        bySession.values.removeAll { it.isOlderThan(cutoffEpochMs) }
    }
}
