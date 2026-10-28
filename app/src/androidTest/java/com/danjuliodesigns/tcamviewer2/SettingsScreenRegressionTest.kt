package com.danjuliodesigns.tcamviewer2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings-screen regressions not already covered by SettingsScreenUiTest's save/cancel
 *  persistence checks: issues #16, #22/#31/#32 (navigation/unsaved-changes), #47 (About dialog),
 *  and #55 (Spotmeter/Region Measurement mutual exclusion). */
@RunWith(AndroidJUnit4::class)
class SettingsScreenRegressionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var manager: SettingsDataManager

    @Before
    fun setUp() {
        manager = SettingsDataManager(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    // --- issue #55: Spotmeter and Region Measurement are mutually exclusive ---

    @Test
    fun enablingRegionMeasurementDisablesSpotmeter() {
        runBlocking {
            manager.saveSpotmeter(true)
            manager.saveRegionMeasurement(false)
        }

        openSettings()
        composeRule.onNodeWithTag("switch_spotmeter").assertIsOn()
        composeRule.onNodeWithTag("switch_region_measurement").performClick()

        composeRule.onNodeWithTag("switch_region_measurement").assertIsOn()
        composeRule.onNodeWithTag("switch_spotmeter").assertIsOff()
    }

    @Test
    fun enablingSpotmeterDisablesRegionMeasurement() {
        runBlocking {
            manager.saveSpotmeter(false)
            manager.saveRegionMeasurement(true)
        }

        openSettings()
        composeRule.onNodeWithTag("switch_region_measurement").assertIsOn()
        composeRule.onNodeWithTag("switch_spotmeter").performClick()

        composeRule.onNodeWithTag("switch_spotmeter").assertIsOn()
        composeRule.onNodeWithTag("switch_region_measurement").assertIsOff()
    }

    // --- issue #16: hitting Save after changing the IP address returns to the main screen ---

    @Test
    fun changingIpAddressAndTappingSaveReturnsToCameraScreen() = runBlocking {
        manager.saveCameraIp("10.0.0.1")

        openSettings()
        composeRule.onNodeWithText("Camera IP Address").performTextClearance()
        composeRule.onNodeWithText("Camera IP Address").performTextInput("10.0.0.99")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        // Back on the Camera tab -- the Settings-only field is gone.
        composeRule.onNodeWithText("Camera IP Address").assertDoesNotExist()
        assertEqualsIp("10.0.0.99", manager.getCameraIp())
    }

    private fun assertEqualsIp(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }

    // --- issues #31/#32: hardware back with an unsaved change prompts rather than silently
    // discarding (or silently exiting) ---

    @Test
    fun hardwareBackWithUnsavedIpChangePromptsBeforeDiscarding() = runBlocking {
        manager.saveCameraIp("10.0.0.1")

        openSettings()
        composeRule.onNodeWithText("Camera IP Address").performTextClearance()
        composeRule.onNodeWithText("Camera IP Address").performTextInput("10.0.0.50")

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        // The unsaved value must not have been persisted just by pressing back.
        assertEqualsIp("10.0.0.1", manager.getCameraIp())

        // "Keep editing" dismisses the prompt and leaves the edit in place, still unsaved.
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Camera IP Address").assertIsDisplayed()
        assertEqualsIp("10.0.0.1", manager.getCameraIp())
    }

    @Test
    fun hardwareBackWithNoUnsavedChangesExitsSettingsDirectly() {
        openSettings()

        Espresso.pressBack()
        composeRule.waitForIdle()

        // No prompt -- straight back to Camera, matching issue #22's expectation when there's
        // nothing to lose.
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Camera IP Address").assertDoesNotExist()
    }

    // --- issue #47: the Version row is an About dialog with author/app/camera website info ---

    @Test
    fun aboutRowOpensDialogWithExpectedContent() {
        openSettings()
        composeRule.onNodeWithContentDescription("About").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("About tCam Viewer").assertIsDisplayed()
        composeRule.onNodeWithText("tcamviewer.darcangel.com").assertIsDisplayed()
        composeRule.onNodeWithText("danjuliodesigns.com").assertIsDisplayed()
    }

    @Test
    fun aboutDialogClosesViaCloseButton() {
        openSettings()
        composeRule.onNodeWithContentDescription("About").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("About tCam Viewer").assertIsDisplayed()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("About tCam Viewer").assertDoesNotExist()
    }
}
