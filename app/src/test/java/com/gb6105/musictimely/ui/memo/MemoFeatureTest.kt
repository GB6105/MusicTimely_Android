package com.gb6105.musictimely.ui.memo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.testing.FakeMemoRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** SCR-08 생각 메모 (W07). FR-A069, QA-28. 메모 중에도 세션은 계속된다. */
@RunWith(AndroidJUnit4::class)
class MemoFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val memos = FakeMemoRepository()
    private var closed = false

    private fun show() {
        compose.setContent { MemoRoute(MemoViewModel(memos), sessionId = "s", onClose = { closed = true }) }
    }

    @Test
    fun `saving keeps the memo and returns to the session`() {
        show()

        compose.onNodeWithText("메모 중에도 세션은 계속돼요").assertIsDisplayed()
        compose.onNodeWithTag(MemoTags.INPUT).performTextInput("참고 자료 링크 다시 찾기")
        compose.onNodeWithText("저장하고 돌아가기").performClick()
        compose.waitForIdle()

        assertThat(memos.memosFor("s").map { it.text }).containsExactly("참고 자료 링크 다시 찾기")
        assertThat(closed).isTrue()
    }

    @Test
    fun `a save failure keeps the typed text and shows no success (QA-28)`() {
        memos.failNext = true
        show()

        compose.onNodeWithTag(MemoTags.INPUT).performTextInput("잊지 말 것")
        compose.onNodeWithText("저장하고 돌아가기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("저장하지 못했어요. 다시 시도해 주세요").assertIsDisplayed()
        compose.onNodeWithTag(MemoTags.INPUT).assertTextContains("잊지 말 것")
        assertThat(closed).isFalse()
    }

    @Test
    fun `too long memos are refused with the limit`() {
        show()

        compose.onNodeWithTag(MemoTags.INPUT).performTextInput("가".repeat(501))
        compose.onNodeWithText("저장하고 돌아가기").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("500자까지 적을 수 있어요").assertIsDisplayed()
        assertThat(memos.memosFor("s")).isEmpty()
    }

    @Test
    fun `cancel returns without saving`() {
        show()

        compose.onNodeWithTag(MemoTags.INPUT).performTextInput("임시")
        compose.onNodeWithText("취소").performClick()

        assertThat(memos.memosFor("s")).isEmpty()
        assertThat(closed).isTrue()
    }

    @Test
    fun `reopening the memo does not show the previous error (review 2 L8)`() {
        val viewModel = MemoViewModel(memos)
        kotlinx.coroutines.runBlocking { viewModel.save("s", "가".repeat(501)) }

        compose.setContent { MemoRoute(viewModel, sessionId = "s", onClose = {}) }
        compose.waitForIdle()

        compose.onNodeWithText("500자까지 적을 수 있어요").assertDoesNotExist()
    }
}
