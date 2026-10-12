package com.gb6105.musictimely.domain.model.allocation

/** DEC-02: opt-in, one last active duration per taskId, 30-day TTL. No song counts. */
data class LastTaskDuration(val taskId: String, val lastActiveDurationMs: Long, val updatedAtEpochMs: Long)

object TaskHistory {
    const val TTL_MS: Long = 30L * 86_400_000

    fun suggest(taskId: String?, consent: Boolean, entries: List<LastTaskDuration>, nowEpochMs: Long): Long? {
        if (!consent || taskId == null) return null
        return entries.firstOrNull { it.taskId == taskId && nowEpochMs - it.updatedAtEpochMs <= TTL_MS }
            ?.lastActiveDurationMs
    }

    fun record(entries: List<LastTaskDuration>, taskId: String, activeDurationMs: Long, nowEpochMs: Long): List<LastTaskDuration> =
        entries.filterNot { it.taskId == taskId } + LastTaskDuration(taskId, activeDurationMs, nowEpochMs)
}
