package com.gb6105.musictimely.ui.session

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** FR-A001: a long task title is cut at two lines with an ellipsis, while screen readers get the whole title. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionTitleFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val title = "분기 보고서 초안을 정리하고 팀 피드백을 반영해서 최종본을 마무리하기 ".repeat(3).trim()

    private fun show() {
        val state = SessionUiState.Active(
            phase = TimerState.RUNNING, taskTitle = title, elapsedMs = 0, remainingMs = 25 * 60_000L,
            remainingSongs = 7, totalSongs = 7, filledChips = 0, elapsedTenths = 0, cue = null,
        )
        compose.setContent { SessionScreen(state, onPause = {}, onResume = {}, onExtend = {}, onFinish = {}) }
        compose.waitForIdle()
    }

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    @Test
    fun `a long title stops at two lines with an ellipsis`() {
        show()

        val layout = layout()
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(layout.isLineEllipsized(1)).isTrue()
    }

    @Test
    fun `screen readers get the whole title`() {
        show()

        compose.onNodeWithText(title).assertExists()
    }
}
