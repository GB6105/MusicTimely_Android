package com.gb6105.musictimely.data.local

import android.content.Context
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Saved sources as a JSON array in app-private preferences (launch settings, not listening history). */
class PreferencesSourceRepository(context: Context) : SourceRepository {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val sources: StateFlow<List<ExternalSource>> = state

    override suspend fun save(source: ExternalSource) {
        val current = state.value
        val index = current.indexOfFirst { it.id == source.id }
        persist(if (index >= 0) current.toMutableList().apply { set(index, source) } else current + source)
    }

    override suspend fun delete(id: String) = persist(state.value.filterNot { it.id == id })

    override suspend fun clear() = persist(emptyList())

    private suspend fun persist(list: List<ExternalSource>) {
        withContext(Dispatchers.IO) {
            val json = JSONArray(list.map { s ->
                JSONObject()
                    .put("id", s.id)
                    .put("provider", s.provider.name)
                    .put("packageName", s.packageName)
                    .putOpt("linkUri", s.linkUri)
                    .put("label", s.label)
                    .put("createdAtEpochMs", s.createdAtEpochMs)
            })
            prefs.edit().putString(KEY, json.toString()).commit()
        }
        state.value = list
    }

    private fun read(): List<ExternalSource> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                ExternalSource(
                    id = o.getString("id"),
                    provider = Provider.valueOf(o.getString("provider")),
                    packageName = o.getString("packageName"),
                    linkUri = if (o.isNull("linkUri")) null else o.getString("linkUri"),
                    label = o.getString("label"),
                    createdAtEpochMs = o.getLong("createdAtEpochMs"),
                )
            }
        } catch (_: JSONException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private companion object {
        const val FILE = "music_sources"
        const val KEY = "sources_v1"
    }
}
