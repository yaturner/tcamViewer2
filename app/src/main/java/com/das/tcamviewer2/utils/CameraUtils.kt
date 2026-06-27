package com.das.tcamviewer2.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Environment
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.model.ImageDto
import com.das.tcamviewer2.settingsDataManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class CameraUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Pre-allocated per-frame buffers — eliminates ~153 KB of heap allocation per frame
    private val pixels    = IntArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT)
    private val imageData = IntArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT)
    private val imageBytes = ByteArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT * 2)
    private val telData  = IntArray(3 * 80) // 3 Lepton telemetry rows × 80 words

    companion object {
        private const val offsetA: Int = 0
        private const val offsetB: Int = 80
        private const val offsetC: Int = 160

        private val paintBlack: Paint = Paint().apply {
            color = Color.BLACK; style = Paint.Style.FILL; strokeWidth = 1.0f
        }
        private val paintWhite: Paint = Paint().apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.0f
        }
        private val paint: Paint = Paint().apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.0f
        }

        val IP_PATTERN: Pattern = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
        )
        val sdf: SimpleDateFormat = SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.getDefault())
        val simpleDateFormatFolder: SimpleDateFormat = SimpleDateFormat("MM_dd_yyyy", Locale.getDefault())
        val simpleDateFormatFile: SimpleDateFormat = SimpleDateFormat("HH_mm_ss", Locale.getDefault())
    }

    @Throws(JSONException::class)
    suspend fun processImageResponse(
        imageDto: ImageDto) {
        val palette: Array<IntArray?>? = imageDto.palette
        val isManualRange: Boolean = settingsDataManager.isManualRange()
        val manualMin: Float = settingsDataManager.getManualMinTemperature()
        val manualMax: Float = settingsDataManager.getManualMaxTemperature()
        val isCelsius: Boolean = settingsDataManager.isUnitsCelsius()
        val metadata: JSONObject = imageDto.getJsonObject().getJSONObject("metadata")
        val radiometricString: String = imageDto.getJsonObject().getString("radiometric")
        val telemetryString: String = imageDto.getJsonObject().getString("telemetry")

        // Decode radiometric data into pre-allocated imageBytes
        Base64.getDecoder().decode(radiometricString.toByteArray(), imageBytes)

        imageDto.creationDate = try {
            sdf.parse(metadata.optString("Date") + " " + metadata.optString("Time"))
        } catch (e: Exception) {
            Date()
        }

        parseTelemetryData(telemetryString)

        val status = ((telData[4] and 0xffff) shl 16) or (telData[3] and 0xffff)
        imageDto.isAGC          = (status and Constants.TELEMETRY_MASK_AGC)      == Constants.TELEMETRY_MASK_AGC
        imageDto.isShutdown     = (status and Constants.TELEMETRY_MASK_SHUTDOWN) == Constants.TELEMETRY_MASK_SHUTDOWN
        imageDto.emissivity     = telData[offsetB + 19]
        imageDto.gainMode       = telData[offsetC + 5]
        imageDto.autoGainMode   = telData[offsetC + 6]
        imageDto.tLinearEnabled    = telData[offsetC + 48]
        imageDto.tLinearResolution = telData[offsetC + 49]
        imageDto.spotmeterMean  = telData[offsetC + 50]
        val x1 = telData[offsetC + 55] and 0xffff
        val y1 = telData[offsetC + 54] and 0xffff
        val x2 = telData[offsetC + 57] and 0xffff
        val y2 = telData[offsetC + 56] and 0xffff
        imageDto.spotmeterLocation = Rect(x1, y1, x2, y2)

        // Decode 16-bit little-endian pixel values into imageData
        var minTemp = Int.MAX_VALUE
        var maxTemp = Int.MIN_VALUE
        val nPixels = imageBytes.size / 2
        for (j in 0 until nPixels) {
            val i = j * 2
            val v = ((imageBytes[i + 1].toInt() and 0xff) shl 8) or (imageBytes[i].toInt() and 0xff)
            imageData[j] = v
            if (v < minTemp) minTemp = v
            if (v > maxTemp) maxTemp = v
        }
        imageDto.imageData      = imageData
        imageDto.minTemperature = minTemp
        imageDto.maxTemperature = maxTemp

        val histogram = IntArray(256)

        if (imageDto.isAGC) {
            for (i in pixels.indices) {
                val idx = imageData[i].coerceIn(0, 255)
                pixels[i] = rgbToPixel(palette?.get(idx))
                histogram[idx]++
            }
        } else {
            val (rangeMin, rangeMax) = getRadiometricTemperatures(
                imageDto, isManualRange, manualMin, manualMax, isCelsius
            )
            val diff = if (rangeMax > rangeMin) rangeMax - rangeMin else 1
            for (i in pixels.indices) {
                val v = if (isManualRange) imageData[i].coerceIn(rangeMin, rangeMax) else imageData[i]
                val idx = (((v - rangeMin) * 255) / diff).coerceIn(0, 255)
                pixels[i] = rgbToPixel(palette?.get(idx))
                histogram[idx]++
            }
        }

        imageDto.histogram = histogram

        val bmp = Bitmap.createBitmap(Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, Constants.IMAGE_WIDTH, 0, 0, Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT)
        imageDto.bitmap = bmp
    }

    private fun rgbToPixel(rgb: IntArray?): Int {
        val red   = (rgb?.get(0) ?: 0).coerceIn(0, 255)
        val green = (rgb?.get(1) ?: 0).coerceIn(0, 255)
        val blue  = (rgb?.get(2) ?: 0).coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    // Writes into pre-allocated telData field — no heap allocation
    private fun parseTelemetryData(telemetryString: String) {
        val telBytes = Base64.getDecoder().decode(telemetryString.toByteArray())
        val nWords = telBytes.size / 2
        for (j in 0 until nWords) {
            val i = j * 2
            telData[j] = ((telBytes[i + 1].toInt() and 0xff) shl 8) or (telBytes[i].toInt() and 0xff)
        }
    }

    fun remapWithPalette(
        dto: ImageDto,
        palette: Array<IntArray?>?,
        isManualRange: Boolean,
        manualMin: Float,
        manualMax: Float,
        isCelsius: Boolean
    ): Bitmap? {
        val data = dto.imageData?.copyOf() ?: return null
        val out = IntArray(data.size)
        if (dto.isAGC) {
            for (i in out.indices) {
                val idx = data[i].coerceIn(0, 255)
                out[i] = rgbToPixel(palette?.get(idx))
            }
        } else {
            val (rangeMin, rangeMax) = getRadiometricTemperatures(dto, isManualRange, manualMin, manualMax, isCelsius)
            val diff = if (rangeMax > rangeMin) rangeMax - rangeMin else 1
            for (i in out.indices) {
                val v = if (isManualRange) data[i].coerceIn(rangeMin, rangeMax) else data[i]
                val idx = (((v - rangeMin) * 255) / diff).coerceIn(0, 255)
                out[i] = rgbToPixel(palette?.get(idx))
            }
        }
        val bmp = Bitmap.createBitmap(Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, Constants.IMAGE_WIDTH, 0, 0, Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT)
        return bmp
    }

    fun getRadiometricTemperatures(
        imageDto: ImageDto,
        isManualRange: Boolean,
        manualMin: Float,
        manualMax: Float,
        isCelsius: Boolean
    ): kotlin.Pair<Int, Int> {
        return if (isManualRange) {
            kotlin.Pair(
                convertToRadiometric(imageDto, manualMin, isCelsius),
                convertToRadiometric(imageDto, manualMax, isCelsius)
            )
        } else {
            kotlin.Pair(imageDto.minTemperature, imageDto.maxTemperature)
        }
    }

    fun convertToRadiometric(imageDto: ImageDto, value: Float, isCelsius: Boolean): Int {
        val scale = if (imageDto.tLinearResolution == 0) 10f else 100f
        return if (isCelsius) {
            Math.round((value + 273.15f) * scale)
        } else {
            val c = (value - 32f) * .5556f
            Math.round((c + 273.15f) * scale)
        }
    }

    @Throws(IOException::class)
    fun saveTjsn(imageDto: ImageDto): Boolean {
        // getExternalFilesDir is app-private; no storage permission needed.
        // Fall back to internal filesDir if external storage is unavailable.
        val rootDir: File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir

        // mkdirs() creates the full path including any missing parents; mkdir() only creates one level.
        val dir = File(rootDir, generateNewPath())
        if (!dir.exists() && !dir.mkdirs()) return false

        val tjsn = File(dir, generateNewFilename() + ".tjsn")
        imageDto.filename = tjsn.name
        FileOutputStream(tjsn).use { stream ->
            stream.write(imageDto.getJsonObject().toString().toByteArray(StandardCharsets.US_ASCII))
        }
        return true
    }


    fun generateNewFilename(): String {
        val now = Date()
        return java.lang.String("img_" + simpleDateFormatFile.format(now)) as String
    }

    fun generateNewPath(): String {
        val now = Date()
        return simpleDateFormatFolder.format(now)
    }

    fun readTjsnFile(path: String): String {
        var json = ""
        var line: String?
        var bufferedReader: BufferedReader? = null
        var fileReader: FileReader? = null
        try {
            fileReader = FileReader(File(path))
            bufferedReader = BufferedReader(fileReader)
            do {
                line = bufferedReader.readLine()
                if (line != null) {
                    json = json + line
                }
            } while (line != null)
        } catch (e: IOException) {
            //TODO JMT Sentry.captureException(e)
            json = ""
        }

        if (bufferedReader != null) {
            try {
                fileReader!!.close()
                bufferedReader.close()
            } catch (e: java.lang.Exception) {
                //TODO JMT Sentry.captureException(e)
            }
        }
        return json
    }

}
