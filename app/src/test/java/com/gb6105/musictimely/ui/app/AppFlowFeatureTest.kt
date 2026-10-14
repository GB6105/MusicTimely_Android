package com.gb6105.musictimely.ui.app

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.session.CheckpointRead
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.session.SessionCheckpoint
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.domain.usecase.session.OpenSessionUseCase
import com.gb6105.musictimely.domain.usecase.session.ReconcileSessionUseCase
import com.gb6105.musictimely.domain.usecase.session.RestoreSessionUseCase
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.repository.ObservedCount
import com.google.common.truth.Truth.assertThat
import com.gb6105.musictimely.data.repository.DefaultSessionRepository
import com.gb6105.musictimely.testing.FakeCheckpointStore
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeMusicControl
import com.gb6105.musictimely.testing.FakeMusicLauncher
import com.gb6105.musictimely.testing.FakeSettingsRepository
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.gb6105.musictimely.testing.FakeTimerEffectSink
import com.gb6105.musictimely.ui.allocation.AllocationTags
import com.gb6105.musictimely.ui.allocation.AllocationViewModel
import com.gb6105.musictimely.ui.music.MusicSheetViewModel
import com.gb6105.musictimely.ui.seen.SeenViewModel
import com.gb6105.musictimely.ui.session.SessionViewModel
import com.gb6105.musictimely.ui.settings.SettingsViewModel
import com.gb6105.musictimely.domain.usecase.settings.DeleteAllDataUseCase
import com.gb6105.musictimely.domain.usecase.source.RegisterSourceUseCase
import com.gb6105.musictimely.testing.FakeCountingAccess
import com.gb6105.musictimely.testing.FakeMemoRepository
import com.gb6105.musictimely.testing.FakePlaylistRepository
import com.gb6105.musictimely.testing.FakeShareImagePort
import com.gb6105.musictimely.testing.FakeNoisePort
import com.gb6105.musictimely.testing.FakeNotificationPermission
import com.gb6105.musictimely.ui.memo.MemoTags
import com.gb6105.musictimely.ui.memo.MemoViewModel
import com.gb6105.musictimely.ui.shareimage.SharePreviewTags
import com.gb6105.musictimely.ui.shareimage.SharePreviewViewModel
import com.gb6105.musictimely.domain.model.result.TotalTime
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 앱 흐름 (User Flow 4:1327, W01→W02→W04). FR-A012·A013, QA-01·QA-08. */
@RunWith(AndroidJUnit4::class)
class AppFlowFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val minute = 60_000L
    private val clock = FakeClock(boot = 1)
    private val store = FakeCheckpointStore()
    private val repository = DefaultSessionRepository(store, clock)
    private val sink = FakeTimerEffectSink()
    private val settings = FakeSettingsRepository()
    private val sources = FakeSourceRepository()
    private val launcher = FakeMusicLauncher()
    private val memos = FakeMemoRepository()
    private val playlists = FakePlaylistRepository(clock)
    private val shareImages = FakeShareImagePort()
    private val music = FakeMusicControl()
    private val dispatch = DispatchTimerCommandUseCase(repository, clock, sink)

    private lateinit var app: AppViewModel
    private val noisePort = FakeNoisePort()

    /** Activity-scoped in production: one instance across sessions (review 2 H1). */
    private val sessionVm by lazy { SessionViewModel(repository, dispatch, clock, music, settings, sources, launcher, noisePort) }

    private fun show() {
        app = AppViewModel(RestoreSessionUseCase(store, clock, playlists), repository, dispatch, ReconcileSessionUseCase(repository, clock, sink), music, memos, playlists, shareImages)
        compose.setContent {
            MusicTimelyApp(
                appViewModel = app,
                allocationViewModel = { remember { AllocationViewModel(OpenSessionUseCase(repository) { "s-${clock.monotonicMs}" }, dispatch, settings, FakeNotificationPermission(), sources, launcher) } },
                sessionViewModel = { sessionVm },
                settingsViewModel = { remember { SettingsViewModel(settings, sources, FakeCountingAccess(), DeleteAllDataUseCase(store, settings, sources, sink, memos, repository, playlists, shareImages)) } },
                musicSheetViewModel = {
                    remember { MusicSheetViewModel(sources, settings, launcher, RegisterSourceUseCase(sources, clock) { "src" }, FakeCountingAccess(), noisePort) }
                },
                seenViewModel = { remember { SeenViewModel(music) } },
                memoViewModel = { remember { MemoViewModel(memos) } },
                sharePreviewViewModel = { remember { SharePreviewViewModel(shareImages) } },
                tickIntervalMs = Long.MAX_VALUE,
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun `first launch goes straight to allocation, start, finish, result and restart (QA-01)`() {
        show()

        compose.onNodeWithText("얼마나 하실까요?").assertIsDisplayed()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("약 7곡 분량 남았어요", substring = true).assertIsDisplayed()

        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("완료").assertIsDisplayed()

        compose.onNodeWithText("완료").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("얼마나 하실까요?").assertIsDisplayed()
    }

    @Test
    fun `a session killed before the target reopens at the limit (QA-08)`() {
        val running = TimerStateMachine().reduce(
            TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0,
        ).session
        store.read = CheckpointRead.Loaded(SessionCheckpoint(running, bootCount = 1, updatedAtEpochMs = clock.wallEpochMs))
        clock.advance(40 * minute)

        show()

        compose.onNodeWithText("정한 시간이 됐어요").assertIsDisplayed()
        assertThat(sink.received).containsAtLeast(TimerEffect.CancelEndAlarm, TimerEffect.ShowProgress, TimerEffect.StartObservation)
    }

    @Test
    fun `another boot asks before resuming (QA-12)`() {
        val running = TimerStateMachine().reduce(
            TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0,
        ).session
        store.read = CheckpointRead.Loaded(SessionCheckpoint(running, bootCount = 0, updatedAtEpochMs = clock.wallEpochMs))

        show()

        compose.onNodeWithText("저장된 지점에서 이어가기").assertIsDisplayed()
        compose.onNodeWithText("여기서 마치기").assertIsDisplayed()
    }

    @Test
    fun `settings and music selection open from allocation and close back (W01, W09)`() {
        show()

        compose.onNodeWithContentDescription("설정").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("데이터 전체 삭제").assertExists()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("음악 앱 고르기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("음악 바꾸기").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("얼마나 하실까요?").assertIsDisplayed()
    }

    @Test
    fun `the session can open music selection without stopping the timer (W02)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("더 보기").performClick()
        compose.onNodeWithText("음악 바꾸기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("한 세션에는 한 소스만 사용해요").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("약 7곡 분량 남았어요", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a thought memo taken during the session appears in the result (W07, W04)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("생각 메모").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MemoTags.INPUT).performTextInput("참고 자료 링크 다시 찾기")
        compose.onNodeWithText("저장하고 돌아가기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("참고 자료 링크 다시 찾기").assertIsDisplayed()
    }

    @Test
    fun `deleting all data during a session returns to a clean allocation (review MEDIUM-14)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("더 보기").performClick()
        compose.onNodeWithText("음악 바꾸기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("닫기").performClick()
        compose.waitForIdle()
        app.open(Overlay.Settings())
        compose.waitForIdle()
        compose.onNodeWithText("데이터 전체 삭제").performScrollTo().performClick()
        compose.onNodeWithText("지우기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("시스템 설정 > 알림 접근에서 뮤직 타이머를 끄면 권한도 해제돼요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("얼마나 하실까요?").assertIsDisplayed()
        assertThat(runBlocking { repository.active() }).isNull()
    }

    @Test
    fun `a count saved before a process death is reported as mixed after restore (QA-A32, review MEDIUM-12)`() {
        val running = TimerStateMachine().reduce(
            TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0,
        ).session
        store.read = CheckpointRead.Loaded(SessionCheckpoint(running, bootCount = 1, updatedAtEpochMs = clock.wallEpochMs, countedItems = 3, observedFromActiveMs = 0))
        clock.advance(10 * minute)
        runBlocking { playlists.append(running.sessionId, songOf("Soft Landing")) }
        show()

        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Soft Landing").assertIsDisplayed()
        compose.onNodeWithText("곡 세기가 켜져 있던 동안 들은 곡만 있어요").assertIsDisplayed()
    }

    @Test
    fun `a second session after a result is not bounced back to allocation (review 2 H1)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("완료").performClick()
        compose.waitForIdle()

        clock.advance(1_000)
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("약 7곡 분량 남았어요", substring = true).assertIsDisplayed()
        assertThat(runBlocking { repository.active() }?.state).isEqualTo(TimerState.RUNNING)
    }

    @Test
    fun `a restored count never leaks into another session (review 2 M3)`() {
        val running = TimerStateMachine().reduce(
            TimerSession.ready("s-old", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0,
        ).session
        store.read = CheckpointRead.Loaded(SessionCheckpoint(running, bootCount = 1, updatedAtEpochMs = clock.wallEpochMs, countedItems = 3))
        show()
        app.onDataDeleted()
        compose.waitForIdle()

        clock.advance(1_000)
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        val id = runBlocking { repository.active()!!.sessionId }
        runBlocking { playlists.append(id, songOf("Slow Bloom")) }
        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Slow Bloom").assertIsDisplayed()
        compose.onNodeWithText("곡 세기가 켜져 있던 동안 들은 곡만 있어요").assertDoesNotExist()
    }

    @Test
    fun `returning to the app re-posts the progress of a running session (review 2 M4, DEC-A15)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        sink.received.clear()

        app.onResume()
        compose.waitForIdle()

        assertThat(sink.received).contains(TimerEffect.ShowProgress)
    }

    @Test
    fun `an open-ended observed session shows the songs that passed and its total time (review 2 L10, DEC-A20)`() {
        show()
        compose.onNodeWithText("끝을 정하지 않고 시작").performClick()
        compose.waitForIdle()
        val id = runBlocking { repository.active()!!.sessionId }
        runBlocking { playlists.append(id, songOf("Soft Landing")) }
        clock.advance(10 * minute)
        music.lastObserved.value = ObservedCount(count = 1, startedMidSession = false)

        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Soft Landing").assertIsDisplayed()
        compose.onNodeWithText("총 걸린 시간 10분 0초").assertIsDisplayed()
    }

    @Test
    fun `a recent result is shown again after relaunch and closing it deletes it (SRS v1 9_2)`() {
        val ended = TimerStateMachine().reduce(
            TimerStateMachine().reduce(TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0).session,
            TimerCommand.Finish("f"), 25 * minute,
        ).session
        store.read = CheckpointRead.Loaded(SessionCheckpoint(ended, bootCount = 1, updatedAtEpochMs = clock.wallEpochMs))
        show()

        compose.onNodeWithText("완료").assertIsDisplayed()
        compose.onNodeWithText("완료").performClick()
        compose.waitForIdle()

        assertThat(store.read).isEqualTo(CheckpointRead.Empty)
        compose.onNodeWithText("얼마나 하실까요?").assertIsDisplayed()
    }

    @Test
    fun `memos in the result can be kept or deleted (FR-A069, SRS v1 9_3)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        val id = runBlocking { repository.active() }!!.sessionId
        runBlocking { memos.add(id, "보관할 생각") }
        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("계속 보관").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(memos.keptSessions).containsExactly(id)

        compose.onNodeWithText("지우기").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(memos.memosFor(id)).isEmpty()
        compose.onNodeWithText("보관할 생각").assertDoesNotExist()
    }

    @Test
    fun `wireframe moves W02 to W06 and back, W06 finish to W04 (W02, W04, W06)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("세션 잠시 멈춤").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("타이머만 쉬고 있어요").assertIsDisplayed()
        compose.onNodeWithText("세션 다시 시작").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("약 7곡 분량 남았어요", substring = true).assertIsDisplayed()

        compose.onNodeWithText("세션 잠시 멈춤").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("완료").assertIsDisplayed()
        compose.onNodeWithText("곡 분량 고치기").assertDoesNotExist()
    }

    @Test
    fun `W01 open-ended start goes to W10 without a target or cue (W10)`() {
        show()

        compose.onNodeWithText("끝을 정하지 않고 시작").performScrollTo().performClick()
        compose.waitForIdle()
        clock.advance(10 * 60_000L)
        runBlocking { sessionVm.refresh() }
        compose.waitForIdle()

        compose.onNodeWithText("10:00").assertIsDisplayed()
        compose.onNodeWithText("남음").assertDoesNotExist()
        compose.onNodeWithText("남았어요", substring = true).assertDoesNotExist()
    }

    // ---- F18 M2·M4 플레이리스트 보관·삭제 (DEC-A18) ----

    private fun songOf(title: String) = PlaylistEntry(0, title, "June Archive", Provider.MELON)

    private fun storedResult(): TimerSession {
        val ended = TimerStateMachine().reduce(
            TimerStateMachine().reduce(TimerSession.ready("s", Allocation.bounded(25 * minute)), TimerCommand.Start("a"), 0).session,
            TimerCommand.Finish("f"), 25 * minute,
        ).session
        store.read = CheckpointRead.Loaded(SessionCheckpoint(ended, bootCount = 1, updatedAtEpochMs = clock.wallEpochMs, countedItems = 2))
        runBlocking {
            playlists.append("s", songOf("Soft Landing"))
            playlists.append("s", songOf("Window Seat"))
        }
        return ended
    }

    @Test
    fun `after a process death the result shows the stored playlist and 완료 deletes it (F18 M2, M4)`() {
        storedResult()
        show()

        compose.onNodeWithText("Soft Landing").assertIsDisplayed()
        compose.onNodeWithText("Window Seat").assertIsDisplayed()
        compose.onNodeWithText("완료").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { playlists.playlist("s") }).isEmpty()
        assertThat(playlists.deleted).containsExactly("s")
    }

    @Test
    fun `closing a restored result with 완료 deletes its playlist (F18 M2, Figma A has no X)`() {
        storedResult()
        show()

        compose.onNodeWithText("완료").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { playlists.playlist("s") }).isEmpty()
    }

    @Test
    fun `a finished session shows the songs counted during it and 완료 deletes them (F18 M2, M4)`() {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        val id = runBlocking { repository.active()!!.sessionId }
        runBlocking { playlists.append(id, songOf("Slow Bloom")) }
        music.lastObserved.value = ObservedCount(count = 1, startedMidSession = false)

        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Slow Bloom").assertIsDisplayed()

        compose.onNodeWithText("완료").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { playlists.playlist(id) }).isEmpty()
    }

    @Test
    fun `expired playlists are purged when the app starts (F18 M2)`() {
        runBlocking { playlists.append("old", songOf("x")) }
        clock.wallEpochMs += 24 * 60 * minute + 1

        show()

        assertThat(runBlocking { playlists.playlist("old") }).isEmpty()
    }

    // ---- F18 M5~M7 공유 (DEC-A22) ----

    private fun finishWithSongs(vararg titles: String): String {
        show()
        compose.onNodeWithTag(AllocationTags.START).performClick()
        compose.waitForIdle()
        val id = runBlocking { repository.active()!!.sessionId }
        runBlocking { titles.forEach { playlists.append(id, songOf(it)) } }
        music.lastObserved.value = ObservedCount(count = titles.size, startedMidSession = false)
        compose.onNodeWithText("마치기").performClick()
        compose.waitForIdle()
        return id
    }

    @Test
    fun `the share button opens a preview of this playlist and 돌아가기 returns to the result (F18 M6, DEC-A22)`() {
        finishWithSongs("Slow Bloom", "Soft Landing")

        compose.onNodeWithText("인스타로 공유하기").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SharePreviewTags.IMAGE).assertIsDisplayed()
        assertThat(shareImages.prepared.single().songs.map { it.title }).containsExactly("Slow Bloom", "Soft Landing").inOrder()
        assertThat(shareImages.prepared.single().totalTime).isEqualTo(TotalTime.of(runBlocking { repository.active()!!.accumulatedActiveMs }))
        assertThat(shareImages.sent).isEqualTo(0)

        compose.onNodeWithText("돌아가기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Slow Bloom").assertIsDisplayed()
    }

    @Test
    fun `sending from the preview leaves the timer result as it was (DEC-A22)`() {
        val id = finishWithSongs("Slow Bloom")
        compose.onNodeWithText("인스타로 공유하기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("인스타 스토리로 보내기").performClick()
        compose.waitForIdle()

        assertThat(shareImages.sent).isEqualTo(1)
        assertThat(runBlocking { playlists.playlist(id) }).hasSize(1)
        assertThat(runBlocking { repository.active()!!.state }).isEqualTo(TimerState.ENDED)
    }

    @Test
    fun `closing the result deletes the share image with the playlist (DEC-A18, DEC-A22)`() {
        finishWithSongs("Slow Bloom")
        compose.onNodeWithText("인스타로 공유하기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("돌아가기").performClick()
        compose.waitForIdle()
        val before = shareImages.cleared

        compose.onNodeWithText("완료").performClick()
        compose.waitForIdle()

        assertThat(shareImages.cleared).isEqualTo(before + 1)
    }

    @Test
    fun `app start deletes a share image left from before (DEC-A22)`() {
        show()

        assertThat(shareImages.cleared).isEqualTo(1)
    }
}
