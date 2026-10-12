package com.gb6105.musictimely.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The installed app starts on the real Hilt graph and shows allocation first (QA-01). */
@RunWith(AndroidJUnit4::class)
class AppLaunchDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppOpensOnTheAllocationScreen() {
        compose.onNodeWithText("얼마나 하실까요?").assertIsDisplayed()
    }
}
