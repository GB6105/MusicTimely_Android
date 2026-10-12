package com.gb6105.musictimely.ui.component

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrimaryButtonFeatureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a disabled primary button is announced as disabled and ignores taps`() {
        var taps = 0
        compose.setContent { PrimaryButton("7곡 동안 시작", onClick = { taps++ }, enabled = false) }

        compose.onNodeWithText("7곡 동안 시작").assertIsNotEnabled().performClick()

        assertThat(taps).isEqualTo(0)
    }

    @Test
    fun `an enabled primary button takes a tap`() {
        var taps = 0
        compose.setContent { PrimaryButton("7곡 동안 시작", onClick = { taps++ }) }

        compose.onNodeWithText("7곡 동안 시작").assertIsEnabled().performClick()

        assertThat(taps).isEqualTo(1)
    }
}
