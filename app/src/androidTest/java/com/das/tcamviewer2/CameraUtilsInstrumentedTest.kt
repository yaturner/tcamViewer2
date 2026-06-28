package com.das.tcamviewer2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.factory.PaletteFactory
import com.das.tcamviewer2.model.ImageDto
import com.das.tcamviewer2.services.CameraService
import com.das.tcamviewer2.utils.CameraUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class CameraUtilsInstrumentedTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        settingsDataManager = SettingsDataManager(context)
        paletteFactory = PaletteFactory()
        cameraUtils = CameraUtils(context)
        cameraService = CameraService()
    }

    // --- convertToRadiometric ---

    @Test
    fun convertZeroCelsiusLowResolution() {
        val dto = ImageDto()
        dto.tLinearResolution = 0
        // (0 + 273.15) * 10 = 2731.5 → Math.round → 2732
        assertEquals(2732, cameraUtils.convertToRadiometric(dto, 0f, isCelsius = true))
    }

    @Test
    fun convertOneHundredCelsiusLowResolution() {
        val dto = ImageDto()
        dto.tLinearResolution = 0
        // (100 + 273.15) * 10 = 3731.5 → 3732
        assertEquals(3732, cameraUtils.convertToRadiometric(dto, 100f, isCelsius = true))
    }

    @Test
    fun convertZeroCelsiusHighResolution() {
        val dto = ImageDto()
        dto.tLinearResolution = 1
        // (0 + 273.15) * 100 = 27315
        assertEquals(27315, cameraUtils.convertToRadiometric(dto, 0f, isCelsius = true))
    }

    @Test
    fun convertFreezingPointFahrenheit() {
        val dto = ImageDto()
        dto.tLinearResolution = 0
        // 32°F = 0°C → 2732
        assertEquals(2732, cameraUtils.convertToRadiometric(dto, 32f, isCelsius = false))
    }

    @Test
    fun convertBoilingPointFahrenheit() {
        val dto = ImageDto()
        dto.tLinearResolution = 0
        // 212°F = 100°C → 3732
        assertEquals(3732, cameraUtils.convertToRadiometric(dto, 212f, isCelsius = false))
    }

    // --- processImageResponse via ImageDto.create ---

    @Test
    fun radiometricFrameProducesCorrectSizedBitmap() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 0x0A80, agc = false)
        val dto = ImageDto.create(json, "Rainbow")
        assertNotNull("Bitmap should be produced", dto.bitmap)
        assertEquals(Constants.IMAGE_WIDTH, dto.bitmap!!.width)
        assertEquals(Constants.IMAGE_HEIGHT, dto.bitmap!!.height)
    }

    @Test
    fun agcFrameProducesCorrectSizedBitmap() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 128, agc = true)
        val dto = ImageDto.create(json, "Rainbow")
        assertNotNull("AGC bitmap should be produced", dto.bitmap)
        assertEquals(Constants.IMAGE_WIDTH, dto.bitmap!!.width)
        assertEquals(Constants.IMAGE_HEIGHT, dto.bitmap!!.height)
    }

    @Test
    fun uniformFrameProducesHistogramWithOneBin() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 0x0A80, agc = false)
        val dto = ImageDto.create(json, "Rainbow")
        assertNotNull("Histogram should be produced", dto.histogram)
        assertEquals(256, dto.histogram!!.size)
        val nonZeroBins = dto.histogram!!.count { it > 0 }
        assertEquals("All pixels identical → exactly 1 non-zero histogram bin", 1, nonZeroBins)
    }

    @Test
    fun uniformFrameHistogramSumsToTotalPixelCount() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 0x0A80, agc = false)
        val dto = ImageDto.create(json, "Rainbow")
        val total = dto.histogram!!.sum()
        assertEquals(
            "Histogram bin total must equal total pixel count",
            Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT,
            total
        )
    }

    @Test
    fun agcFrameIsDetectedCorrectly() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 128, agc = true)
        val dto = ImageDto.create(json, "Rainbow")
        assertTrue("AGC flag must be set for AGC frame", dto.isAGC)
    }

    @Test
    fun radiometricFrameIsNotDetectedAsAgc() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 0x0A80, agc = false)
        val dto = ImageDto.create(json, "Rainbow")
        assertTrue("Radiometric frame must not have AGC flag", !dto.isAGC)
    }

    @Test
    fun paletteName_isSetOnCreatedDto() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 0x0A80, agc = false)
        val dto = ImageDto.create(json, "Arctic")
        assertEquals("Arctic", dto.paletteName)
    }

    @Test
    fun minAndMaxTemperaturesPopulatedForUniformFrame() = runBlocking {
        val pixelValue = 0x0A80
        val json = buildSyntheticFrame(pixelValue = pixelValue, agc = false)
        val dto = ImageDto.create(json, "Rainbow")
        assertEquals(
            "All pixels equal → minTemperature == pixelValue",
            pixelValue, dto.minTemperature
        )
        assertEquals(
            "All pixels equal → maxTemperature == pixelValue",
            pixelValue, dto.maxTemperature
        )
    }

    // Builds a synthetic tCam JSON frame: all pixels set to pixelValue.
    // agc = true sets the AGC status bit in the telemetry.
    private fun buildSyntheticFrame(pixelValue: Int, agc: Boolean): JSONObject {
        val numPixels = Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT

        // Radiometric: 16-bit little-endian, all pixels = pixelValue
        val radiometricBytes = ByteArray(numPixels * 2)
        for (i in 0 until numPixels) {
            radiometricBytes[i * 2]     = (pixelValue and 0xFF).toByte()
            radiometricBytes[i * 2 + 1] = ((pixelValue shr 8) and 0xFF).toByte()
        }

        // Telemetry: 3 rows × 80 words × 2 bytes = 480 bytes, all zero by default.
        // AGC bit is bit 12 of status (low word = telData[3]).
        // telData[3] = (telBytes[7] << 8) | telBytes[6] (little-endian word at byte offset 6)
        val telBytes = ByteArray(480)
        if (agc) {
            val agcMask = Constants.TELEMETRY_MASK_AGC  // 0x1000
            telBytes[6] = (agcMask and 0xFF).toByte()
            telBytes[7] = ((agcMask shr 8) and 0xFF).toByte()
        }

        val metadata = JSONObject().apply {
            put("Date", "06/27/26")
            put("Time", "12:00:00.000")
        }

        return JSONObject().apply {
            put("radiometric", Base64.getEncoder().encodeToString(radiometricBytes))
            put("telemetry", Base64.getEncoder().encodeToString(telBytes))
            put("metadata", metadata)
        }
    }
}
