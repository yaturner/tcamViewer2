package com.das.tcamviewer2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

/**
 * Covers the delete-confirmation dialog added to the Library screen (both the multi-select
 * grid toolbar and the single-image browse view previously deleted with no confirmation at
 * all — the gap that caused a real accidental deletion during manual testing of this feature).
 *
 * A dedicated, deliberately-unrealistic date folder ("01_01_2099") and time ("23:59:59") are
 * used so this test's synthetic file can't collide with any real capture in the Library grid.
 */
@RunWith(AndroidJUnit4::class)
class LibraryDeleteConfirmationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var testFile: File
    private lateinit var testDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val picturesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)!!
        testDir = File(picturesDir, "01_01_2099")
        testDir.mkdirs()
        testFile = File(testDir, "img_23_59_59.tjsn")
        testFile.writeText(buildSyntheticFrame().toString())
    }

    @After
    fun tearDown() {
        testFile.delete()
        testDir.delete()
    }

    @Test
    fun cancelPreservesTheFile() {
        openLibraryAndSelectTestThumbnail()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete 1 file?").assertIsDisplayed()
        composeRule.onNodeWithText("This cannot be undone.").assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertTrue("File must still exist after Cancel", testFile.exists())
        composeRule.onNodeWithText("23:59:59").assertIsDisplayed()
    }

    @Test
    fun confirmingDeleteRemovesTheFile() {
        openLibraryAndSelectTestThumbnail()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete 1 file?").assertIsDisplayed()

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        assertFalse("File must be gone after confirming Delete", testFile.exists())
        composeRule.onNodeWithText("23:59:59").assertDoesNotExist()
    }

    private fun openLibraryAndSelectTestThumbnail() {
        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()

        // The thumbnail grid loads file list + decodes bitmaps asynchronously.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("23:59:59"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("23:59:59").performClick()
        composeRule.waitForIdle()
    }

    private fun buildSyntheticFrame(): JSONObject {
        val width = 160
        val height = 120
        val numPixels = width * height
        val radiometricBytes = ByteArray(numPixels * 2)
        for (i in 0 until numPixels) {
            radiometricBytes[i * 2] = 0x80.toByte()
            radiometricBytes[i * 2 + 1] = 0x0A
        }
        val telBytes = ByteArray(480)
        val metadata = JSONObject().apply {
            put("Date", "01/01/99")
            put("Time", "23:59:59.000")
        }
        return JSONObject().apply {
            put("radiometric", Base64.getEncoder().encodeToString(radiometricBytes))
            put("telemetry", Base64.getEncoder().encodeToString(telBytes))
            put("metadata", metadata)
        }
    }
}
