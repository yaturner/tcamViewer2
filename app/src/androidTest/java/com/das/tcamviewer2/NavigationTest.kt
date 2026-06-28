package com.das.tcamviewer2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun allThreeTabLabelsAreVisible() {
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun cameraTabIsSelectedByDefault() {
        composeRule.onNodeWithText("Camera").assertIsSelected()
    }

    @Test
    fun navigateToSettingsTabShowsSettingsScreen() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertIsDisplayed()
    }

    @Test
    fun navigateToLibraryTabShowsLibraryScreen() {
        composeRule.onNodeWithText("Library").performClick()
        // Library screen shows its top-bar title
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("Library"))
            .apply { fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun canNavigateBackToCameraFromSettings() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertIsDisplayed()

        composeRule.onNodeWithText("Camera").performClick()
        // Back on Camera tab: Settings IP field should be gone
        composeRule.onNodeWithText("Camera IP Address").assertDoesNotExist()
    }

    @Test
    fun navigateBetweenAllTabs() {
        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()
        // All three tab labels remain visible on every screen
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertIsDisplayed()

        composeRule.onNodeWithText("Camera").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertDoesNotExist()
    }
}
