package com.gb6105.musictimely.domain.model.allocation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A006 이전 작업 시간 제안 (DEC-02), QA-30. */
class TaskHistoryTest {

    private val day = 86_400_000L
    private val now = 100 * day

    private val entry = LastTaskDuration(taskId = "t1", lastActiveDurationMs = 30 * 60_000, updatedAtEpochMs = now - day)

    @Test
    fun `suggests the last duration of the exact same task when consented`() {
        assertThat(TaskHistory.suggest(taskId = "t1", consent = true, entries = listOf(entry), nowEpochMs = now))
            .isEqualTo(30 * 60_000L)
    }

    @Test
    fun `no suggestion without consent, for another task, or after 30 days`() {
        assertThat(TaskHistory.suggest("t1", consent = false, entries = listOf(entry), nowEpochMs = now)).isNull()
        assertThat(TaskHistory.suggest("t2", consent = true, entries = listOf(entry), nowEpochMs = now)).isNull()
        assertThat(TaskHistory.suggest(null, consent = true, entries = listOf(entry), nowEpochMs = now)).isNull()
        assertThat(TaskHistory.suggest("t1", consent = true, entries = listOf(entry), nowEpochMs = entry.updatedAtEpochMs + 31 * day)).isNull()
    }

    @Test
    fun `recording keeps one entry per task`() {
        val updated = TaskHistory.record(listOf(entry), taskId = "t1", activeDurationMs = 40 * 60_000, nowEpochMs = now)

        assertThat(updated).containsExactly(LastTaskDuration("t1", 40 * 60_000, now))
    }
}
