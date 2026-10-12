package com.gb6105.musictimely.data.local

import android.content.Context
import androidx.core.util.AtomicFile
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.playlist.SessionPlaylist
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Session playlists (F18 M2, DEC-A18) as one JSON file in `noBackupFilesDir` (no cloud backup or device transfer),
 * written through [AtomicFile]. The only store of song titles and artists; nothing here is logged.
 * Unreadable data reads as empty and is replaced by the next write; no method throws on I/O.
 *
 * The [Mutex] is taken before any dispatch so callers starting undispatched keep their order (FIFO lock).
 */
class FilePlaylistStore(private val context: Context, private val clock: ClockPort) : PlaylistRepository {

    private val mutex = Mutex()
    private val file get() = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override suspend fun append(sessionId: String, entry: PlaylistEntry) = update { all ->
        val now = clock.wallNowEpochMs()
        val current = all[sessionId] ?: SessionPlaylist(sessionId, emptyList(), now)
        all + (sessionId to current.append(entry, now))
    }

    override suspend fun playlist(sessionId: String): List<PlaylistEntry> =
        mutex.withLock { withContext(Dispatchers.IO) { readAll()[sessionId]?.entries.orEmpty() } }

    override suspend fun delete(sessionId: String) = update { it - sessionId }

    override suspend fun clear() = mutex.withLock { withContext(Dispatchers.IO) { file.delete() } }

    override suspend fun purgeOlderThan(cutoffEpochMs: Long) = update { all -> all.filterValues { !it.isOlderThan(cutoffEpochMs) } }

    private suspend fun update(change: (Map<String, SessionPlaylist>) -> Map<String, SessionPlaylist>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val before = readAll()
            val after = change(before)
            when {
                after == before -> Unit
                after.isEmpty() -> file.delete()
                else -> write(after)
            }
        }
    }

    private fun readAll(): Map<String, SessionPlaylist> {
        val bytes = try {
            file.readFully()
        } catch (_: IOException) {
            return emptyMap()
        }
        return try {
            decode(JSONObject(String(bytes, Charsets.UTF_8)))
        } catch (_: JSONException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }

    private fun write(all: Map<String, SessionPlaylist>) {
        val target = file
        val stream = try {
            target.startWrite()
        } catch (_: IOException) {
            return
        }
        try {
            stream.write(encode(all).toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (_: IOException) {
            target.failWrite(stream)
        }
    }

    private fun encode(all: Map<String, SessionPlaylist>): JSONObject {
        val sessions = JSONArray()
        all.values.forEach { playlist ->
            val entries = JSONArray()
            playlist.entries.forEach { e ->
                entries.put(
                    JSONObject().put("order", e.order).put("title", e.title).putOpt("artist", e.artist).put("provider", e.provider.name),
                )
            }
            sessions.put(
                JSONObject().put("sessionId", playlist.sessionId).put("updatedAtEpochMs", playlist.updatedAtEpochMs).put("entries", entries),
            )
        }
        return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("sessions", sessions)
    }

    private fun decode(json: JSONObject): Map<String, SessionPlaylist> {
        require(json.getInt("schemaVersion") == SCHEMA_VERSION)
        val sessions = json.getJSONArray("sessions")
        return List(sessions.length()) { i ->
            val s = sessions.getJSONObject(i)
            val entries = s.getJSONArray("entries")
            SessionPlaylist(
                sessionId = s.getString("sessionId"),
                entries = List(entries.length()) { k ->
                    val e = entries.getJSONObject(k)
                    PlaylistEntry(
                        order = e.getInt("order"),
                        title = e.getString("title"),
                        artist = if (e.isNull("artist")) null else e.getString("artist"),
                        provider = Provider.valueOf(e.getString("provider")),
                    )
                },
                updatedAtEpochMs = s.getLong("updatedAtEpochMs"),
            )
        }.associateBy { it.sessionId }
    }

    companion object {
        const val FILE_NAME = "session_playlists.json"
        private const val SCHEMA_VERSION = 1
    }
}
