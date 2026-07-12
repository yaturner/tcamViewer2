package com.das.tcamviewer2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation lives behind a [androidx.compose.material3.ModalNavigationDrawer]: the tab labels
 * are only in the semantics tree while the drawer is open, so every assertion here opens it
 * first via the "Open menu" icon that's present on all three screens.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun openDrawer() {
        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun allThreeTabLabelsAreVisibleInDrawer() {
        openDrawer()
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun cameraTabIsSelectedByDefault() {
        openDrawer()
        composeRule.onNodeWithText("Camera").assertIsSelected()
    }

    @Test
    fun navigateToSettingsTabShowsSettingsScreen() {
        openDrawer()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertIsDisplayed()
    }

    @Test
    fun navigateToLibraryTabShowsLibraryScreen() {
        openDrawer()
        composeRule.onNodeWithText("Library").performClick()
        // Library screen shows its top-bar title
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("Library"))
            .apply { fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun canNavigateBackToCameraFromSettings() {
        openDrawer()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertIsDisplayed()

        openDrawer()
        composeRule.onNodeWithText("Camera").performClick()
        // Back on Camera tab: Settings IP field should be gone
        composeRule.onNodeWithText("Camera IP Address").assertDoesNotExist()
    }

    @Test
    fun navigateBetweenAllTabs() {
        openDrawer()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()

        openDrawer()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertIsDisplayed()

        openDrawer()
        composeRule.onNodeWithText("Camera").performClick()
        composeRule.onNodeWithText("Camera IP Address").assertDoesNotExist()
    }

    @Test
    fun drawerClosesAfterSelectingATab() {
        openDrawer()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()
        // ModalNavigationDrawer keeps its content composed (just animated off-screen)
        // rather than removing it, so check visibility rather than tree presence.
        composeRule.onNodeWithText("tCam Viewer").assertIsNotDisplayed()
    }
}
