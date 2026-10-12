package com.gb6105.musictimely.ui.component

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.foundation.layout.Column
import com.gb6105.musictimely.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Design "Actions": CircleIconButton 8 kinds at 48dp (44pt raised), TimeChip default and selected. */
@RunWith(AndroidJUnit4::class)
class CircleIconButtonFeatureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the eight circle icons map to the design icons`() {
        assertThat(CircleIcon.entries.map { it.name })
            .containsExactly("BACK", "GEAR", "MORE", "PLUS", "PAUSE", "STOP", "PLAY", "CLOSE").inOrder()
        assertThat(CircleIcon.BACK.drawable).isEqualTo(R.drawable.ic_mt_back)
        assertThat(CircleIcon.GEAR.drawable).isEqualTo(R.drawable.ic_mt_gear)
        assertThat(CircleIcon.PLAY.drawable).isEqualTo(R.drawable.ic_mt_play)
    }

    @Test
    fun `a circle icon button is 48dp, labelled and clickable`() {
        var taps = 0
        compose.setContent { CircleIconButton(CircleIcon.GEAR, "설정", onClick = { taps++ }) }

        compose.onNodeWithContentDescription("설정").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).performClick()

        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun `time chips announce which one is selected`() {
        compose.setContent {
            Column {
                TimeChip("10", selected = false, onClick = {})
                TimeChip("25", selected = true, onClick = {})
            }
        }

        compose.onNodeWithText("10").assertIsNotSelected()
        compose.onNodeWithText("25").assertIsSelected()
    }
}
