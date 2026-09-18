package com.danjuliodesigns.tcamviewer2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danjuliodesigns.tcamviewer2.constants.Constants
import com.danjuliodesigns.tcamviewer2.factory.PaletteFactory
import com.danjuliodesigns.tcamviewer2.model.ImageDto
import com.danjuliodesigns.tcamviewer2.services.CameraService
import com.danjuliodesigns.tcamviewer2.utils.CameraUtils
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
            total,
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
            pixelValue,
            dto.minTemperature,
        )
        assertEquals(
            "All pixels equal → maxTemperature == pixelValue",
            pixelValue,
            dto.maxTemperature,
        )
    }

    // --- getRadiometricTemperatures (Manual Range) ---
    // This is the exact mechanism behind issue #29 (Manual Range not applying to the colorbar/
    // pixel mapping) fixed this session — previously untested despite being the core of that fix.

    @Test
    fun getRadiometricTemperaturesUsesSceneMinMaxWhenManualRangeDisabled() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 0x0A80, agc = false)
        val dto = ImageDto.create(json, "Rainbow")
        val (rangeMin, rangeMax) =
            cameraUtils.getRadiometricTemperatures(
                dto,
                isManualRange = false,
                manualMin = 999f,
                manualMax = 999f,
                isCelsius = true,
            )
        assertEquals("Auto range must ignore manualMin/Max entirely", dto.minTemperature, rangeMin)
        assertEquals("Auto range must ignore manualMin/Max entirely", dto.maxTemperature, rangeMax)
    }

    @Test
    fun getRadiometricTemperaturesUsesManualBoundsWhenEnabled() = runBlocking {
        val json = buildSyntheticFrame(pixelValue = 0x0A80, agc = false)
        val dto = ImageDto.create(json, "Rainbow")
        val (rangeMin, rangeMax) =
            cameraUtils.getRadiometricTemperatures(
                dto,
                isManualRange = true,
                manualMin = 20f,
                manualMax = 40f,
                isCelsius = true,
            )
        assertEquals(cameraUtils.convertToRadiometric(dto, 20f, isCelsius = true), rangeMin)
        assertEquals(cameraUtils.convertToRadiometric(dto, 40f, isCelsius = true), rangeMax)
        // Manual bounds are unrelated to the scene's own min/max -- for this uniform test frame
        // they must NOT coincidentally equal dto.minTemperature/maxTemperature.
        assertTrue(rangeMin != dto.minTemperature || rangeMax != dto.maxTemperature)
    }

    @Test
    fun manualRangeClampsPixelsOutsideBoundsWhenRemapping() = runBlocking {
        // Half the frame is cold (0°C), half is hot (200°C) -- well outside a 20-40°C manual
        // range -- so every pixel must clamp to one of the two palette extremes rather than the
        // mid-range colors an auto (scene min/max) mapping would produce. buildSplitFrame's
        // telemetry bytes are all zero, so tLinearResolution reads as 0 (scale=10) -- match that
        // here rather than assuming resolution=1.
        val coldRaw = cameraUtils.convertToRadiometric(ImageDto().apply { tLinearResolution = 0 }, 0f, isCelsius = true)
        val hotRaw = cameraUtils.convertToRadiometric(ImageDto().apply { tLinearResolution = 0 }, 200f, isCelsius = true)
        val json = buildSplitFrame(coldRaw, hotRaw)
        val dto = ImageDto.create(json, "Gray")
        val palette = paletteFactory.getPaletteByName("Gray")!!

        val bmp =
            cameraUtils.remapWithPalette(
                dto,
                palette,
                isManualRange = true,
                manualMin = 20f,
                manualMax = 40f,
                isCelsius = true,
            )
        assertNotNull(bmp)
        val topLeftPixel = bmp!!.getPixel(0, 0) // cold half -> clamped to palette index 0
        val bottomRightPixel = bmp.getPixel(Constants.IMAGE_WIDTH - 1, Constants.IMAGE_HEIGHT - 1) // hot half -> index 255
        val expectedColdArgb = paletteRgbToArgb(palette[0])
        val expectedHotArgb = paletteRgbToArgb(palette[255])
        assertEquals("Cold half must clamp to the bottom of the manual range", expectedColdArgb, topLeftPixel)
        assertEquals("Hot half must clamp to the top of the manual range", expectedHotArgb, bottomRightPixel)
    }

    private fun paletteRgbToArgb(rgb: IntArray?): Int {
        val r = (rgb?.get(0) ?: 0).coerceIn(0, 255)
        val g = (rgb?.get(1) ?: 0).coerceIn(0, 255)
        val b = (rgb?.get(2) ?: 0).coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // Top half of the frame = topValue, bottom half = bottomValue -- (0,0) always lands in the
    // top half and (WIDTH-1, HEIGHT-1) always lands in the bottom half.
    private fun buildSplitFrame(
        topValue: Int,
        bottomValue: Int,
    ): JSONObject {
        val numPixels = Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT
        val radiometricBytes = ByteArray(numPixels * 2)
        for (row in 0 until Constants.IMAGE_HEIGHT) {
            val value = if (row < Constants.IMAGE_HEIGHT / 2) topValue else bottomValue
            for (col in 0 until Constants.IMAGE_WIDTH) {
                val i = row * Constants.IMAGE_WIDTH + col
                radiometricBytes[i * 2] = (value and 0xFF).toByte()
                radiometricBytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
        }
        val telBytes = ByteArray(480) // AGC bit left off -> radiometric frame
        val metadata =
            JSONObject().apply {
                put("Date", "06/27/26")
                put("Time", "12:00:00.000")
            }
        return JSONObject().apply {
            put("radiometric", Base64.getEncoder().encodeToString(radiometricBytes))
            put("telemetry", Base64.getEncoder().encodeToString(telBytes))
            put("metadata", metadata)
        }
    }

    // Builds a synthetic tCam JSON frame: all pixels set to pixelValue.
    // agc = true sets the AGC status bit in the telemetry.
    private fun buildSyntheticFrame(
        pixelValue: Int,
        agc: Boolean,
    ): JSONObject {
        val numPixels = Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT

        // Radiometric: 16-bit little-endian, all pixels = pixelValue
        val radiometricBytes = ByteArray(numPixels * 2)
        for (i in 0 until numPixels) {
            radiometricBytes[i * 2] = (pixelValue and 0xFF).toByte()
            radiometricBytes[i * 2 + 1] = ((pixelValue shr 8) and 0xFF).toByte()
        }

        // Telemetry: 3 rows × 80 words × 2 bytes = 480 bytes, all zero by default.
        // AGC bit is bit 12 of status (low word = telData[3]).
        // telData[3] = (telBytes[7] << 8) | telBytes[6] (little-endian word at byte offset 6)
        val telBytes = ByteArray(480)
        if (agc) {
            val agcMask = Constants.TELEMETRY_MASK_AGC // 0x1000
            telBytes[6] = (agcMask and 0xFF).toByte()
            telBytes[7] = ((agcMask shr 8) and 0xFF).toByte()
        }

        val metadata =
            JSONObject().apply {
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
