package com.das.tcamviewer2

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises SettingsScreen's batched save-on-Done / discard-on-Cancel behavior: edits are
 * held in local Compose state and only persisted to [SettingsDataManager] when Done is
 * tapped, while Cancel must discard them. A toggle wired into the UI but forgotten in the
 * Done handler's save block would pass every [SettingsDataManagerTest] yet fail here.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var manager: SettingsDataManager

    @Before
    fun setUp() {
        manager = SettingsDataManager(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun togglingExportPictureAndTappingDonePersistsIt() = runBlocking {
        manager.saveExportPicture(false)

        openSettings()
        composeRule.onNodeWithTag("switch_export_picture").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertTrue(manager.getExportPicture())
    }

    @Test
    fun togglingSpotmeterAndTappingCancelDiscardsIt() = runBlocking {
        manager.saveSpotmeter(true)

        openSettings()
        composeRule.onNodeWithTag("switch_spotmeter").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        // Cancel must not have persisted the in-UI toggle.
        assertTrue(manager.getSpotmeter())
    }

    @Test
    fun togglingManualRangeAndShutterSoundBothPersistOnDone() = runBlocking {
        manager.saveManualRange(false)
        manager.saveShutterSound(true)

        openSettings()
        composeRule.onNodeWithTag("switch_manual_range").performClick()
        composeRule.onNodeWithTag("switch_shutter_sound").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertTrue(manager.getManualRange())
        assertFalse(manager.getShutterSound())
    }

    @Test
    fun togglingExportMetadataAndTappingCancelDiscardsIt() = runBlocking {
        manager.saveExportMetadata(false)

        openSettings()
        composeRule.onNodeWithTag("switch_export_metadata").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertFalse(manager.getExportMetadata())
    }
}
