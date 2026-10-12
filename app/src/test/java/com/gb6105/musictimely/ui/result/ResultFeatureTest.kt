package com.gb6105.musictimely.ui.result

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.EndReason
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.ObservedCount
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F10·F18 결과 화면 = Figma 시안 A만 (SCR-05, W04, DEC-A19·DEC-A20). FR-A067·A069, F18 M3·M4.
 * DEC-A20으로 비교 카드·요약 문장·곡 아이콘·캡션·곡 보정(SCR-07)·체감 시간(FR-A068)·같은 일 이어서 하기는 화면에서 제거.
 */
@RunWith(AndroidJUnit4::class)
class ResultFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val minute = 60_000L
    private var restarted = false

    private fun sessionOf(activeMs: Long, extensions: List<Long> = emptyList()) = TimerSession(
        sessionId = "s", state = TimerState.ENDED,
        allocation = Allocation(25 * minute, 25 * minute + extensions.sum(), extensions.size),
        accumulatedActiveMs = activeMs, runningAnchorMonoMs = null, extensions = extensions,
        endReason = EndReason.COMPLETED, appliedCommandIds = emptySet(), taskTitle = "기획안",
    )

    private fun song(title: String, artist: String? = "June Archive") = PlaylistEntry(0, title, artist, Provider.MELON)

    private fun showPlaylist(
        songs: List<PlaylistEntry>,
        observed: ObservedCount? = ObservedCount(songs.size, startedMidSession = false),
        activeMs: Long = 32 * minute + 18_000,
        extensions: List<Long> = emptyList(),
        memos: List<String> = emptyList(),
        onKeepMemos: () -> Unit = {},
        onDiscardMemos: () -> Unit = {},
    ) {
        val numbered = songs.mapIndexed { i, e -> e.copy(order = i + 1) }
        compose.setContent {
            ResultScreen(
                ResultUiState.from(sessionOf(activeMs, extensions), observed, numbered).copy(memos = memos),
                onRestart = { restarted = true },
                onKeepMemos = onKeepMemos,
                onDiscardMemos = onDiscardMemos,
            )
        }
    }

    private fun rowTexts(): List<List<String>> = compose.onAllNodesWithTag(ResultTags.PLAYLIST_ROW).fetchSemanticsNodes()
        .map { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } }

    // ---- 완료 (W04, DEC-A19) ----

    @Test
    fun `완료 closes the result and begins a new allocation (DEC-A19)`() {
        showPlaylist(listOf(song("a")))

        compose.onNodeWithText("완료").performClick()

        assertThat(restarted).isTrue()
    }

    @Test
    fun `there is no close button, 완료 is the only way out (Figma A)`() {
        showPlaylist(listOf(song("a")))

        compose.onNodeWithContentDescription("닫기").assertDoesNotExist()
    }

    // ---- 플레이리스트 카드·곡 행 (Figma "MT / Playlist / A Result") ----

    @Test
    fun `the identity card shows THIS SESSION, the two-line title and a four-tile collage`() {
        showPlaylist(listOf(song("Soft Landing")))

        compose.onNodeWithTag(ResultTags.IDENTITY).assertIsDisplayed()
        compose.onNodeWithText("THIS SESSION").assertIsDisplayed()
        compose.onNodeWithText("이번\n플레이리스트").assertIsDisplayed()
        assertThat(compose.onAllNodesWithTag(ResultTags.COLLAGE_TILE).fetchSemanticsNodes()).hasSize(4)
    }

    @Test
    fun `every song row has its own cover tile`() {
        showPlaylist(listOf(song("Soft Landing"), song("Window Seat", "Mellow Fields"), song("Slow Bloom")))

        (0..2).forEach { compose.onNodeWithTag(ResultTags.cover(it), useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithTag(ResultTags.cover(3), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the empty state replaces the identity card and keeps the buttons (Figma A empty)`() {
        showPlaylist(emptyList(), observed = null)

        compose.onNodeWithTag(ResultTags.IDENTITY).assertDoesNotExist()
        compose.onNodeWithText("THIS SESSION").assertDoesNotExist()
        compose.onNodeWithText("완료").assertIsDisplayed()
    }

    // ---- 플레이리스트·총 걸린 시간 (F18 M3·M4) ----

    @Test
    fun `the result opens with this session's playlist and the total time without pauses (F18 M3)`() {
        showPlaylist(listOf(song("Soft Landing")))

        compose.onNodeWithText("이번 플레이리스트").assertIsDisplayed()
        compose.onNodeWithText("총 걸린 시간 32분 18초").assertIsDisplayed()
    }

    @Test
    fun `total time shows seconds only under a minute and hours past an hour (F18 M3)`() {
        showPlaylist(emptyList(), observed = null, activeMs = 59_000)
        compose.onNodeWithText("총 걸린 시간 59초").assertIsDisplayed()
    }

    @Test
    fun `total time past an hour names hours, minutes and seconds (F18 M3)`() {
        showPlaylist(emptyList(), observed = null, activeMs = 3_600_000L + 5 * minute + 3_000)
        compose.onNodeWithText("총 걸린 시간 1시간 5분 3초").assertIsDisplayed()
    }

    @Test
    fun `songs are listed with title and artist in play order, repeats kept (F18 M4)`() {
        showPlaylist(listOf(song("Soft Landing"), song("Window Seat", "Mellow Fields"), song("Soft Landing")))

        assertThat(rowTexts()).containsExactly(
            listOf("Soft Landing", "June Archive"),
            listOf("Window Seat", "Mellow Fields"),
            listOf("Soft Landing", "June Archive"),
        ).inOrder()
    }

    @Test
    fun `an unknown artist is left out rather than invented`() {
        showPlaylist(listOf(song("Slow Bloom", artist = null)))

        assertThat(rowTexts()).containsExactly(listOf("Slow Bloom"))
    }

    @Test
    fun `without confirmed songs the empty state says so and no list is invented (F18 M4)`() {
        showPlaylist(emptyList(), observed = null)

        compose.onNodeWithText("곡 정보를 가져오지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("확인되지 않은 곡은 목록에 넣지 않아요").assertIsDisplayed()
        assertThat(rowTexts()).isEmpty()
    }

    @Test
    fun `a session counted only part of the time says the list covers only that part (F18 M4, DEC-A13)`() {
        showPlaylist(listOf(song("a"), song("b")), observed = ObservedCount(2, startedMidSession = true))

        compose.onNodeWithText("곡 세기가 켜져 있던 동안 들은 곡만 있어요").assertIsDisplayed()
    }

    @Test
    fun `a fully observed session has no partial note`() {
        showPlaylist(listOf(song("a")))

        compose.onNodeWithText("곡 세기가 켜져 있던 동안 들은 곡만 있어요").assertDoesNotExist()
        compose.onNodeWithText("곡 정보를 가져오지 못했어요").assertDoesNotExist()
    }

    @Test
    fun `with a long list the buttons stay fixed and the memos stay reachable below the list (F18 M4, FR-A069)`() {
        showPlaylist(List(20) { song("곡 $it") }, observed = null, memos = listOf("참고 자료 링크 다시 찾기"))

        compose.onNodeWithText("완료").assertIsDisplayed()
        compose.onNodeWithText("인스타로 공유하기").assertIsDisplayed()
        compose.onNodeWithText("참고 자료 링크 다시 찾기").performScrollTo().assertIsDisplayed()
    }

    // ---- 메모 (FR-A069) ----

    @Test
    fun `memos saved during the session are listed and can be kept or deleted (FR-A069)`() {
        var kept = 0
        var discarded = 0
        showPlaylist(
            listOf(song("a")), memos = listOf("참고 자료 링크 다시 찾기"),
            onKeepMemos = { kept++ }, onDiscardMemos = { discarded++ },
        )

        compose.onNodeWithText("세션 중 적어둔 생각 1개").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("참고 자료 링크 다시 찾기").assertIsDisplayed()
        compose.onNodeWithText("계속 보관").performScrollTo().performClick()
        compose.onNodeWithText("지우기").performScrollTo().performClick()

        assertThat(kept).isEqualTo(1)
        assertThat(discarded).isEqualTo(1)
    }

    @Test
    fun `without memos there is no memo card`() {
        showPlaylist(listOf(song("a")))

        compose.onNodeWithText("세션 중 적어둔 생각", substring = true).assertDoesNotExist()
    }

    // ---- 공유 버튼 (M5~M7 미구현 → 비활성) ----

    @Test
    fun `the share button is shown but disabled until sharing is built (DEC-A20, M5-M7 pending)`() {
        showPlaylist(listOf(song("a")))

        compose.onNodeWithText("인스타로 공유하기").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `the share button is disabled in the empty state too (Figma A empty)`() {
        showPlaylist(emptyList(), observed = null)

        compose.onNodeWithText("인스타로 공유하기").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `the disabled share button tells screen readers it is not ready yet`() {
        showPlaylist(listOf(song("a")))

        compose.onNodeWithText("인스타로 공유하기").assertHasNoClickAction()
        compose.onNodeWithTag(ResultTags.SHARE).assertExists()
        assertThat(
            compose.onNodeWithTag(ResultTags.SHARE).fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        ).isEqualTo("준비 중이에요")
    }

    // ---- DEC-A20으로 제거된 항목 ----

    @Test
    fun `the result has no comparison, summary, icons, caption or task title (DEC-A20)`() {
        showPlaylist(listOf(song("a")), activeMs = 28 * minute, extensions = listOf(5 * minute))

        listOf("정한 양", "PLANNED", "지난 분량", "지난 곡", "ESTIMATED", "COUNTED", "CORRECTED", "기획안").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        listOf("정했고", "곡 분량", "세션 끝", "연장 포함", "평균 곡 길이", "정한 7곡").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `the result offers no song correction, felt-time question or same-task continue (DEC-A20)`() {
        showPlaylist(emptyList(), observed = null)

        listOf("곡 분량 고치기", "방금 몇 분 같으셨어요?", "적기", "건너뛰기", "같은 일 이어서 하기").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `the result stays neutral, without failure, overrun, praise or score (FR-A067)`() {
        showPlaylist(listOf(song("a")), activeMs = 40 * minute)

        listOf("실패", "초과", "축하", "점수", "들었어요", "%").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }
}
