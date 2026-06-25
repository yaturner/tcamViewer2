package com.das.tcamviewer2.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Pair
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.model.ImageDto
import com.das.tcamviewer2.settingsDataManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min


@Singleton
class CameraUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private lateinit var pixels: IntArray
    private lateinit var imageBytes: ByteArray
    private var imageLen = 0

    companion object {
        private const val offsetA: Int = 0
        private const val offsetB: Int = 80
        private const val offsetC: Int = 160

        private val paintBlack: Paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            strokeWidth = 1.0f
        }
        private val paintWhite: Paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }

        private val paint: Paint? = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }

        val IP_PATTERN: Pattern = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
        )
        val sdf: SimpleDateFormat = SimpleDateFormat("MM/dd/yy HH:mm:ss")
        val simpleDateFormatFolder: SimpleDateFormat = SimpleDateFormat("MM_dd_yyyy")
        val simpleDateFormatFile: SimpleDateFormat = SimpleDateFormat("HH_mm_ss")
    }

    @Throws(JSONException::class)
    suspend fun processImageResponse(imageDto: ImageDto) {
        val palette: Array<IntArray?>? = imageDto.palette
        var diff = 0

        imageDto.maxTemperature = Int.Companion.MIN_VALUE
        imageDto.minTemperature = Int.Companion.MAX_VALUE

        val metadata: JSONObject = imageDto.getJsonObject().getJSONObject("metadata")
        val radiometricString: String = imageDto.getJsonObject().getString("radiometric")
        val telemetryString: String = imageDto.getJsonObject().getString("telemetry")
        imageBytes = Base64.getDecoder().decode(radiometricString.toByteArray())
        var date: Date?
        try {
            date = sdf.parse(
                metadata.getString("Date") +
                        " " +
                        metadata.getString("Time")
            )
        } catch (e: ParseException) {
            //TODO JMT Sentry.captureException(e)
            date = Date()
        }
        imageDto.creationDate = date

        imageLen = imageBytes.size
        val imageData = IntArray(imageLen / 2)
        pixels = IntArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT)
        val telemetryData: IntArray?

        telemetryData = parseTelemetryData(telemetryString)
        val status = ((telemetryData!![4] and 0xffff) shl 16) or (telemetryData[3] and 0xffff)
        imageDto.isAGC      = (status and Constants.TELEMETRY_MASK_AGC) == Constants.TELEMETRY_MASK_AGC
        imageDto.isShutdown = (status and Constants.TELEMETRY_MASK_SHUTDOWN) == Constants.TELEMETRY_MASK_SHUTDOWN
        imageDto.emissivity = (telemetryData[offsetB + 19])
        imageDto.gainMode = (telemetryData[offsetC + 5])
        imageDto.autoGainMode = (telemetryData[offsetC + 6])
        imageDto.tLinearEnabled = (telemetryData[offsetC + 48])
        imageDto.tLinearResolution = (telemetryData[offsetC + 49])
        imageDto.spotmeterMean = (telemetryData[offsetC + 50])
        val x1 = telemetryData[offsetC + 55] and 0xffff
        val y1 = telemetryData[offsetC + 54] and 0xffff
        val x2 = telemetryData[offsetC + 57] and 0xffff
        val y2 = telemetryData[offsetC + 56] and 0xffff
        imageDto.spotmeterLocation = (Rect(x1, y1, x2, y2))

        var minTemperature = Int.Companion.MAX_VALUE
        var maxTemperature = Int.Companion.MIN_VALUE
        var i = 0
        var j = 0
        while (i < imageLen) {
            imageData[j] =
                ((imageBytes[i + 1].toInt() and 0xff) shl 8) or (imageBytes[i].toInt() and 0xff)
            minTemperature = min(imageData[j].toDouble(), minTemperature.toDouble()).toInt()
            maxTemperature = max(imageData[j].toDouble(), maxTemperature.toDouble()).toInt()
            i = i + 2
            j++
        }
        imageDto.imageData = (imageData)
        imageDto.minTemperature = (minTemperature)
        imageDto.maxTemperature = (maxTemperature)

        if (imageDto.isAGC) {
            for (i in pixels.indices) {
                pixels[i] = rgbToPixel(palette?.get(imageData[i]))
            }
        } else {
            val min: Int
            val max: Int
            val temps: Pair<Int?, Int?> = getRadiometricTemperatures(imageDto)
            min = temps.first!!
            max = temps.second!!
            diff = max - min
            for (i in imageData.indices) {
                var v = imageData[i]
                val value: Int
                if (settingsDataManager.isManualRange()) {
                    if (v < min) {
                        v = min
                    } else if (v > max) {
                        v = max
                    }
                    value = ((v - min) * 255) / diff
                } else {
                    value = ((v - imageDto.minTemperature) * 255) / diff
                }

                pixels[i] = rgbToPixel(palette?.get(min(max(value.toDouble(), 0.0), 255.0).toInt()))
            }
        }
    }

    private fun rgbToPixel(rgb: IntArray?): Int {
        // Fallback to 0 if null, and clamp between 0 and 255
        val red = (rgb?.get(0) ?: 0).coerceIn(0, 255)
        val green = (rgb?.get(1) ?: 0).coerceIn(0, 255)
        val blue = (rgb?.get(2) ?: 0).coerceIn(0, 255)

        return (red shl 16) or (green shl 8) or blue
    }

    private fun parseTelemetryData(telemetryString: String): IntArray {
        val telemetryBytes = Base64.getDecoder().decode(telemetryString.toByteArray())
        val telemetryData: IntArray?

        val len = telemetryBytes.size
        telemetryData = IntArray(len / 2)

        var i = 0
        var j = 0
        while (i < len) {
            telemetryData[j] =
                ((telemetryBytes[i + 1].toInt() and 0xff) shl 8) or (telemetryBytes[i].toInt() and 0xff)
            i = i + 2
            j++
        }

        return telemetryData
    }

    /**
     * getRadiometricTemperatures
     *
     * @return min, max temperatures in radiometric values
     */
    suspend fun getRadiometricTemperatures(imageDto: ImageDto): Pair<Int?, Int?> {
        if (settingsDataManager.isManualRange()) {
            return Pair<Int?, Int?>(
                convertToRadiometric(imageDto, settingsDataManager.getManualMinTemperature()),
                convertToRadiometric(imageDto, settingsDataManager.getManualMaxTemperature())
            )
        } else {
            return Pair<Int?, Int?>(imageDto.minTemperature, imageDto.maxTemperature)
        }
    }

    //Convert Celsius/Fahrenheit to radiometric data
    suspend fun convertToRadiometric(imageDto: ImageDto, value: Float): Int {
        val scale = if (imageDto.tLinearResolution == 0) 10f else 100f
        if (settingsDataManager.isUnitsCelsius()) {
            return Math.round((value + 273.15f) * scale)
        } else {
            val c = (value - 32f) * .5556f
            return Math.round((c + 273.15f) * scale)
        }
    }


}