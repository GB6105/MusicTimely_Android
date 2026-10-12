package com.gb6105.musictimely.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.EndReason
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** F02 원자 저장·백업 제외·손상 처리 (SRS Android §9.2, SRS v1 §8 CORRUPT_CHECKPOINT). */
@RunWith(AndroidJUnit4::class)
class FileCheckpointStoreIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = FileCheckpointStore(context)
    private val file = File(context.noBackupFilesDir, FileCheckpointStore.FILE_NAME)

    private val checkpoint = SessionCheckpoint(
        session = TimerSession(
            sessionId = "s-1",
            state = TimerState.LIMIT_REACHED,
            allocation = Allocation(
                initialDurationMs = 1_500_000, currentTargetMs = 1_800_000, targetRevision = 1,
                durationPerUnitMs = 200_000, loopDurationMs = 222_000, singleLoop = true,
            ),
            accumulatedActiveMs = 1_800_000,
            runningAnchorMonoMs = null,
            extensions = listOf(300_000),
            endReason = EndReason.COMPLETED,
            appliedCommandIds = setOf("a", "b"),
            taskTitle = "기획안 첫 문단 쓰기",
        ),
        bootCount = 12,
        updatedAtEpochMs = 1_760_000_000_000,
        countedItems = 4,
        observedFromActiveMs = 60_000,
        lastCueRevision = 1,
        stopAtEndSnapshot = true,
        stopAtEndOutcome = "stopped",
    )

    @Test
    fun `reads back exactly what was written`() = runTest {
        assertThat(store.write(checkpoint)).isTrue()

        assertThat(FileCheckpointStore(context).read()).isEqualTo(CheckpointRead.Loaded(checkpoint))
    }

    @Test
    fun `round trips nullable fields`() = runTest {
        val minimal = SessionCheckpoint(TimerSession.ready("s-2", Allocation.infinite()), bootCount = 1, updatedAtEpochMs = 5)

        store.write(minimal)

        assertThat(store.read()).isEqualTo(CheckpointRead.Loaded(minimal))
    }

    @Test
    fun `is empty before the first write and after clearing`() = runTest {
        assertThat(store.read()).isEqualTo(CheckpointRead.Empty)

        store.write(checkpoint)
        store.clear()

        assertThat(store.read()).isEqualTo(CheckpointRead.Empty)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `garbage or an unknown schema reads as corrupt without throwing`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText("{not json")
        assertThat(store.read()).isEqualTo(CheckpointRead.Corrupt)

        file.writeText("""{"schemaVersion":99}""")
        assertThat(store.read()).isEqualTo(CheckpointRead.Corrupt)
    }

    @Test
    fun `lives in the no-backup directory so OS backup and transfer skip it`() = runTest {
        store.write(checkpoint)

        assertThat(file.exists()).isTrue()
        assertThat(file.parentFile).isEqualTo(context.noBackupFilesDir)
    }

    @Test
    fun `the stored file has no song fields`() = runTest {
        store.write(checkpoint)

        val json = file.readText()
        listOf("title\"", "artist", "signature").forEach { assertThat(json).doesNotContain(it) }
    }

    @Test
    fun `a v1 fixture without later optional fields still restores (NFR-A005 migration fixture)`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText(javaClass.getResource("/checkpoint/v1-minimal.json")!!.readText())

        val read = store.read() as CheckpointRead.Loaded
        val session = read.checkpoint.session

        assertThat(session.sessionId).isEqualTo("old-1")
        assertThat(session.state).isEqualTo(TimerState.PAUSED)
        assertThat(session.accumulatedActiveMs).isEqualTo(600_000)
        assertThat(session.allocation.loopDurationMs).isNull()
        assertThat(session.allocation.singleLoop).isFalse()
        assertThat(session.taskTitle).isNull()
        assertThat(session.appliedCommandIds).containsExactly("start", "pause")
        assertThat(read.checkpoint.countedItems).isNull()
        assertThat(read.checkpoint.bootCount).isEqualTo(7)
    }

    @Test
    fun `a failed write keeps the previous checkpoint intact (NFR-A005 atomic)`() = runTest {
        store.write(checkpoint)
        val dir = file.parentFile!!
        dir.setWritable(false)
        try {
            val changed = checkpoint.copy(bootCount = checkpoint.bootCount + 1)
            assertThat(store.write(changed)).isFalse()
        } finally {
            dir.setWritable(true)
        }

        assertThat(store.read()).isEqualTo(CheckpointRead.Loaded(checkpoint))
    }
}
