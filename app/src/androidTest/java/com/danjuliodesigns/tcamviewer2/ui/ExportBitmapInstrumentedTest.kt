package com.danjuliodesigns.tcamviewer2.ui

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danjuliodesigns.tcamviewer2.SettingsDataManager
import com.danjuliodesigns.tcamviewer2.cameraUtils
import com.danjuliodesigns.tcamviewer2.dataStore
import com.danjuliodesigns.tcamviewer2.factory.PaletteFactory
import com.danjuliodesigns.tcamviewer2.model.ImageDto
import com.danjuliodesigns.tcamviewer2.paletteFactory
import com.danjuliodesigns.tcamviewer2.settingsDataManager
import com.danjuliodesigns.tcamviewer2.utils.CameraUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * Regression tests for issue #18: with "Export Metadata" off, a saved export must be exactly
 * the plain colorized picture at the selected Export Resolution -- no spotmeter header, no
 * color-bar sidebar, no gain/emissivity/date-time footer. With it on, all of that chrome is
 * added around the image. buildShareBitmap() needs a real android.graphics.Bitmap/Canvas, so
 * this runs instrumented rather than as a plain JVM unit test (same constraint already
 * documented on CameraScreenHelpersTest's Rect usage).
 */
@RunWith(AndroidJUnit4::class)
class ExportBitmapInstrumentedTest {
    private lateinit var manager: SettingsDataManager

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Real on-device DataStore shared across the whole instrumented-test suite -- clear it
        // first so a leftover value from an unrelated test class can't flake these assertions.
        context.dataStore.edit { it.clear() }
        manager = SettingsDataManager(context)
        // buildShareBitmap() and ImageDto.create() both read the app's global singletons
        // directly rather than taking them as params, so this test must set them up itself
        // rather than relying on MainActivity's onCreate() having already run.
        settingsDataManager = manager
        paletteFactory = PaletteFactory()
        cameraUtils = CameraUtils(context)
        manager.saveExportResolution("640x480")
    }

    /** A minimal but realistic get_image-style response: a flat (all-zero) 160x120 radiometric
     *  frame plus a telemetry block with just the words buildShareBitmap's chrome depends on
     *  set -- tLinearEnabled (offset word 160+48) so the thermal/color-bar sidebar path is
     *  reachable, everything else left at zero. Telemetry words are 16-bit little-endian, same
     *  as CameraUtils.parseTelemetryData expects. */
    private fun syntheticFrame(tLinearEnabled: Int): JSONObject {
        val radiometricBytes = ByteArray(160 * 120 * 2)
        val words = IntArray(3 * 80)
        words[160 + 48] = tLinearEnabled // ImageDto.tLinearEnabled
        words[160 + 49] = 1 // tLinearResolution (0.01 scale) -- irrelevant to size, just realistic
        val telBytes = ByteArray(words.size * 2)
        for (j in words.indices) {
            telBytes[j * 2] = (words[j] and 0xFF).toByte()
            telBytes[j * 2 + 1] = ((words[j] shr 8) and 0xFF).toByte()
        }
        return JSONObject().apply {
            put("radiometric", Base64.getEncoder().encodeToString(radiometricBytes))
            put("telemetry", Base64.getEncoder().encodeToString(telBytes))
            put("metadata", JSONObject().put("Date", "01/01/26").put("Time", "12:00:00"))
        }
    }

    @Test
    fun exportMetadataOff_isExactlyThePlainImageAtExportResolution() = runBlocking {
        manager.saveExportMetadata(false)
        val dto = ImageDto.create(syntheticFrame(tLinearEnabled = 1), "Rainbow")

        val bitmap = buildShareBitmap(dto, isCelsius = true)

        assertEquals("No header/footer/sidebar chrome -- width must equal the plain image width", 640, bitmap.width)
        assertEquals("No header/footer/sidebar chrome -- height must equal the plain image height", 480, bitmap.height)
    }

    @Test
    fun exportMetadataOff_evenWithThermalDataPresent_stillOmitsTheSidebar() = runBlocking {
        // The literal #18 repro: a thermal (tLinear-enabled) frame with metadata export turned
        // off must still come out as the bare image -- the color-bar sidebar is gated on
        // exportMetadata too, not just on whether the frame has radiometric data.
        manager.saveExportMetadata(false)
        val dto = ImageDto.create(syntheticFrame(tLinearEnabled = 1), "Rainbow")

        val bitmap = buildShareBitmap(dto, isCelsius = true)

        assertEquals(640, bitmap.width)
        assertEquals(480, bitmap.height)
    }

    @Test
    fun exportMetadataOn_addsHeaderFooterAndColorBarSidebarAroundTheImage() = runBlocking {
        manager.saveExportMetadata(true)
        manager.saveSpotmeter(true)
        val dto = ImageDto.create(syntheticFrame(tLinearEnabled = 1), "Rainbow")

        val bitmap = buildShareBitmap(dto, isCelsius = true)

        assertTrue(
            "Color-bar sidebar must widen the export beyond the plain 640px image",
            bitmap.width > 640,
        )
        assertTrue(
            "Header + footer rows must heighten the export beyond the plain 480px image",
            bitmap.height > 480,
        )
    }

    @Test
    fun exportMetadataOn_withNonThermalFrame_stillAddsHeaderFooterButNoSidebar() = runBlocking {
        // hasThermal gates the color-bar sidebar specifically -- a non-radiometric (tLinear
        // disabled) frame with metadata export on should still get the gain/emissivity/date
        // footer (and header band), just without a color bar there's nothing useful to show.
        manager.saveExportMetadata(true)
        val dto = ImageDto.create(syntheticFrame(tLinearEnabled = 0), "Rainbow")

        val bitmap = buildShareBitmap(dto, isCelsius = true)

        assertEquals("No thermal data -- no color-bar sidebar to widen the export", 640, bitmap.width)
        assertTrue("Header + footer rows still heighten the export", bitmap.height > 480)
    }
}
