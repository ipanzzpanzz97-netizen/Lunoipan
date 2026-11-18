package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchCrashTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `main activity launches and navigates across all tabs without crashing`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_terminal").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_signals").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("nav_dca").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("nav_vault").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("nav_terminal").performClick()
    composeTestRule.waitForIdle()
  }
}
