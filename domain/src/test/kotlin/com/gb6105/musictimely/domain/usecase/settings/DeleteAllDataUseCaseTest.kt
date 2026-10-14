package com.gb6105.musictimely.domain.usecase.settings

import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.gb6105.musictimely.testing.FakeCheckpointStore
import com.gb6105.musictimely.testing.FakeMemoRepository
import com.gb6105.musictimely.testing.FakePlaylistRepository
import com.gb6105.musictimely.testing.FakeSessionRepository
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeShareImagePort
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 데이터 전체 삭제 (FR-A071, SRS v1 FR-039), QA-33·QA-A38. */
class DeleteAllDataUseCaseTest {

    private val store = FakeCheckpointStore()
    private val settings = FakeSettingsRepository(MusicSettings(songCountingEnabled = true, selectedSourceId = "a"))
    private val sources = FakeSourceRepository()
    private val sink = FakeTimerEffectSink()
    private val memos = FakeMemoRepository()
    private val sessions = FakeSessionRepository()
    private val playlists = FakePlaylistRepository()
    private val shareImages = FakeShareImagePort()
    private val delete = DeleteAllDataUseCase(store, settings, sources, sink, memos, sessions, playlists, shareImages)

    @Test
    fun `cancels the live session's alarm, observation and notifications, then clears all stored data`() = runTest {
        val running = TimerStateMachine().reduce(TimerSession.ready("s", Allocation.bounded(60_000)), TimerCommand.Start("a"), 0).session
        sessions.save(running)
        store.read = CheckpointRead.Loaded(SessionCheckpoint(running, 1, 1))
        sources.save(ExternalSource("a", Provider.MELON, "com.iloen.melon", null, "멜론", 0))
        memos.add("s", "생각")
        playlists.append("s", PlaylistEntry(1, "Soft Landing", "June Archive", Provider.MELON))

        delete()

        assertThat(sink.received).containsExactly(
            TimerEffect.CancelEndAlarm, TimerEffect.CancelStopAtEnd, TimerEffect.StopObservation, TimerEffect.ClearNotifications,
        ).inOrder()
        assertThat(store.read).isEqualTo(CheckpointRead.Empty)
        assertThat(settings.settings.value).isEqualTo(MusicSettings())
        assertThat(sources.sources.value).isEmpty()
        assertThat(memos.memosFor("s")).isEmpty()
        assertThat(playlists.playlist("s")).isEmpty() // F18 M2, DEC-A18
        assertThat(shareImages.cleared).isEqualTo(1) // F18 M5 temporary share image, DEC-A22
        assertThat(sessions.active()).isNull()
    }

    @Test
    fun `with no session it only clears data`() = runTest {
        delete()

        assertThat(sink.received).isEmpty()
        assertThat(store.cleared).isEqualTo(1)
    }

    @Test
    fun `deletion runs under the session lock so a concurrent save cannot revive the session (review 2 L7)`() = runTest {
        sessions.requireLockForClear = true

        delete()

        assertThat(sessions.active()).isNull()
    }
}
