package com.gb6105.musictimely.data.local

import android.content.Context
import com.gb6105.musictimely.domain.model.memo.MemoAdd
import com.gb6105.musictimely.domain.model.memo.MemoRetention
import com.gb6105.musictimely.domain.model.memo.ThoughtMemo
import com.gb6105.musictimely.domain.model.memo.ThoughtMemos
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.MemoRepository
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Thought memos in app-private storage (FR-A069). Unhandled memos expire after 24 hours, checked on every
 * access (SRS v1 §9.3: deletion happens at the next access, not at an exact time). Never sent anywhere.
 */
class PreferencesMemoRepository(
    context: Context,
    private val clock: ClockPort,
    private val newId: () -> String,
) : MemoRepository {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override suspend fun add(sessionId: String, text: String): MemoAdd = synchronized(this) {
        val all = load()
        val session = ThoughtMemos(sessionId, all[sessionId].orEmpty())
        val result = session.add(newId(), text, clock.wallNowEpochMs())
        if (result is MemoAdd.Added) store(all + (sessionId to result.memos.items))
        result
    }

    override fun memosFor(sessionId: String): List<ThoughtMemo> = synchronized(this) { load()[sessionId].orEmpty() }

    override suspend fun keep(sessionId: String) = synchronized(this) {
        val all = load()
        store(all + (sessionId to all[sessionId].orEmpty().map { it.copy(kept = true) }))
    }

    override suspend fun discard(sessionId: String) = synchronized(this) { store(load() - sessionId) }

    override suspend fun clear() = synchronized(this) { prefs.edit().clear().commit(); Unit }

    private fun load(): Map<String, List<ThoughtMemo>> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        val now = clock.wallNowEpochMs()
        val parsed = try {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { session ->
                val array = root.getJSONArray(session)
                List(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    ThoughtMemo(o.getString("id"), o.getString("text"), o.getLong("createdAtEpochMs"), o.getBoolean("kept"))
                }
            }
        } catch (_: JSONException) {
            emptyMap()
        }
        val alive = parsed.mapValues { MemoRetention.alive(it.value, now) }.filterValues { it.isNotEmpty() }
        if (alive != parsed) store(alive)
        return alive
    }

    private fun store(all: Map<String, List<ThoughtMemo>>) {
        val root = JSONObject()
        all.forEach { (session, memos) ->
            root.put(session, JSONArray(memos.map {
                JSONObject().put("id", it.id).put("text", it.text).put("createdAtEpochMs", it.createdAtEpochMs).put("kept", it.kept)
            }))
        }
        prefs.edit().putString(KEY, root.toString()).commit()
    }

    private companion object {
        const val FILE = "thought_memos"
        const val KEY = "memos_v1"
    }
}
