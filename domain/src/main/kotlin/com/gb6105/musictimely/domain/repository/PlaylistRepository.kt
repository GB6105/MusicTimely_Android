package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry

/**
 * Session playlists (F18, DEC-A18): counted songs kept on device only until the result is confirmed, at most 24 h.
 * The only place song titles and artists are persisted. Never logged, never sent to analytics, excluded from backup.
 * Implementations never throw on unreadable data (it reads as empty).
 */
interface PlaylistRepository {
    /** Appends in arrival order; the entry is renumbered after the stored ones (repeats kept). */
    suspend fun append(sessionId: String, entry: PlaylistEntry)
    suspend fun playlist(sessionId: String): List<PlaylistEntry>

    /** The result was closed (완료, 다음 할 일, 같은 일 이어서, 닫기). */
    suspend fun delete(sessionId: String)

    /** "데이터 전체 삭제" (FR-A071). */
    suspend fun clear()

    /** Drops every playlist last written before [cutoffEpochMs] (24 h TTL). */
    suspend fun purgeOlderThan(cutoffEpochMs: Long)
}
