package com.danjuliodesigns.tcamviewer2

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
    // MainActivity requests this permission at runtime on launch (issue #37); without granting
    // it ahead of time, the system permission dialog steals the foreground from MainActivity the
    // instant it launches, and the Compose test framework finds no composed hierarchy at all.
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant("android.permission.ACCESS_LOCAL_NETWORK")

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var manager: SettingsDataManager

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // This DataStore is a real on-device store shared across the whole instrumented-test
        // suite (every test class in the run shares one app process) -- without clearing it
        // first, a leftover value from an unrelated test class run earlier in the same suite can
        // flake an assertion here that passes fine in isolation.
        context.dataStore.edit { it.clear() }
        manager = SettingsDataManager(context)
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
        composeRule.onNodeWithText("Save").performClick()
        // Save runs several sequential DataStore writes before leaving Settings -- real disk I/O
        // that waitForIdle()'s Compose-only idling doesn't track -- so poll instead of asserting
        // immediately.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("switch_export_picture").fetchSemanticsNodes().isEmpty()
        }

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
        composeRule.onNodeWithText("Save").performClick()
        // Save runs ~20 sequential DataStore writes before leaving Settings -- real disk I/O
        // that waitForIdle()'s Compose-only idling doesn't track -- so poll instead of asserting
        // immediately.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("switch_manual_range").fetchSemanticsNodes().isEmpty()
        }

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
