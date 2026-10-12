package com.gb6105.musictimely.data.local

import android.content.Context
import androidx.core.util.AtomicFile
import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.EndReason
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.CheckpointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The single active checkpoint as JSON in `noBackupFilesDir` (excluded from cloud backup and device transfer).
 * Written through [AtomicFile]: a failed write leaves the previous checkpoint intact.
 */
class FileCheckpointStore(private val context: Context) : CheckpointStore {

    private val file get() = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override suspend fun read(): CheckpointRead = withContext(Dispatchers.IO) {
        val bytes = try {
            file.readFully()
        } catch (_: IOException) {
            return@withContext CheckpointRead.Empty
        }
        try {
            CheckpointRead.Loaded(decode(JSONObject(String(bytes, Charsets.UTF_8))))
        } catch (_: JSONException) {
            CheckpointRead.Corrupt
        } catch (_: IllegalArgumentException) {
            CheckpointRead.Corrupt
        }
    }

    override suspend fun write(checkpoint: SessionCheckpoint): Boolean = withContext(Dispatchers.IO) {
        val target = file
        val stream = try {
            target.startWrite()
        } catch (_: IOException) {
            return@withContext false
        }
        try {
            stream.write(encode(checkpoint).toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
            true
        } catch (_: IOException) {
            target.failWrite(stream)
            false
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }

    private fun encode(c: SessionCheckpoint): JSONObject {
        val s = c.session
        val a = s.allocation
        return JSONObject()
            .put("schemaVersion", SessionCheckpoint.SCHEMA_VERSION)
            .put("bootCount", c.bootCount)
            .put("updatedAtEpochMs", c.updatedAtEpochMs)
            .putOpt("countedItems", c.countedItems)
            .putOpt("observedFromActiveMs", c.observedFromActiveMs)
            .putOpt("lastCueRevision", c.lastCueRevision)
            .put("stopAtEndSnapshot", c.stopAtEndSnapshot)
            .putOpt("stopAtEndOutcome", c.stopAtEndOutcome)
            .put(
                "session",
                JSONObject()
                    .put("sessionId", s.sessionId)
                    .put("state", s.state.name)
                    .putOpt("initialDurationMs", a.initialDurationMs)
                    .putOpt("currentTargetMs", a.currentTargetMs)
                    .put("targetRevision", a.targetRevision)
                    .put("durationPerUnitMs", a.durationPerUnitMs)
                    .putOpt("loopDurationMs", a.loopDurationMs)
                    .put("singleLoop", a.singleLoop)
                    .put("accumulatedActiveMs", s.accumulatedActiveMs)
                    .putOpt("runningAnchorMonoMs", s.runningAnchorMonoMs)
                    .put("extensions", JSONArray(s.extensions))
                    .putOpt("endReason", s.endReason?.name)
                    .put("appliedCommandIds", JSONArray(s.appliedCommandIds.toList()))
                    .putOpt("taskTitle", s.taskTitle),
            )
    }

    private fun decode(json: JSONObject): SessionCheckpoint {
        require(json.getInt("schemaVersion") == SessionCheckpoint.SCHEMA_VERSION)
        val s = json.getJSONObject("session")
        val extensions = s.getJSONArray("extensions")
        val commandIds = s.getJSONArray("appliedCommandIds")
        return SessionCheckpoint(
            session = TimerSession(
                sessionId = s.getString("sessionId"),
                state = TimerState.valueOf(s.getString("state")),
                allocation = Allocation(
                    initialDurationMs = s.optLongOrNull("initialDurationMs"),
                    currentTargetMs = s.optLongOrNull("currentTargetMs"),
                    targetRevision = s.getInt("targetRevision"),
                    durationPerUnitMs = s.getLong("durationPerUnitMs"),
                    loopDurationMs = s.optLongOrNull("loopDurationMs"),
                    singleLoop = s.optBoolean("singleLoop", false),
                ),
                accumulatedActiveMs = s.getLong("accumulatedActiveMs"),
                runningAnchorMonoMs = s.optLongOrNull("runningAnchorMonoMs"),
                extensions = List(extensions.length()) { extensions.getLong(it) },
                endReason = s.optStringOrNull("endReason")?.let(EndReason::valueOf),
                appliedCommandIds = List(commandIds.length()) { commandIds.getString(it) }.toSet(),
                taskTitle = s.optStringOrNull("taskTitle"),
            ),
            bootCount = json.getInt("bootCount"),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
            countedItems = json.optLongOrNull("countedItems")?.toInt(),
            observedFromActiveMs = json.optLongOrNull("observedFromActiveMs"),
            lastCueRevision = json.optLongOrNull("lastCueRevision")?.toInt(),
            stopAtEndSnapshot = json.getBoolean("stopAtEndSnapshot"),
            stopAtEndOutcome = json.optStringOrNull("stopAtEndOutcome"),
        )
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    companion object {
        const val FILE_NAME = "session_checkpoint.json"
    }
}
