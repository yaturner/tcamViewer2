package com.das.tcamviewer2.model

import android.graphics.Bitmap
import android.graphics.Rect
import com.das.tcamviewer2.cameraUtils
import com.das.tcamviewer2.paletteFactory
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

class ImageDto {
    var isAGC: Boolean = false
    var isShutdown: Boolean = false
    var emissivity: Int = 0
    var tLinearEnabled: Int = 0
    var tLinearResolution: Int = 0 // 0 = 0.1, 1 = 0.01
    var spotmeterMean: Int = 0
    var spotmeterLocation: Rect? = null
    var isShutterLockout: Boolean = false
    var fFCState: Int = 0
    var fFCDesired: Int = 0
    var gainMode: Int = 0
    var autoGainMode: Int = 0
    var maxTemperature: Int = 0
    var minTemperature: Int = 0
    var creationDate: Date? = null

    @get:JvmName("getJsonObjectNullable")
    var jsonObject: JSONObject? = null
    var metadata: JSONObject? = null
    var filename: String? = ""
    var tjsnString: String? = null
    var palette: Array<IntArray?>? = null
    var imageData: IntArray? = null
    var paletteName: String? = null
        set(value) {
            field = value
            try {
                val meta = jsonObject!!.getJSONObject("metadata")
                meta.remove("paletteName")
                meta.put("palette", value)
            } catch (_: JSONException) {}
        }
    var bitmap: Bitmap? = null
    var histogram: IntArray? = null

    //Constructor from camera response

    //Constructor from file
    suspend fun initFromFile(filename: String?, paletteName: String?) {
        this.filename = filename
        tjsnString = cameraUtils.readTjsnFile(filename!!)
        if (tjsnString != null && !tjsnString!!.isEmpty()) {
            try {
                jsonObject = JSONObject(tjsnString!!)
            } catch (e: JSONException) {
                //Sentry.captureException(e)
            }
        } else {
            //TODO Handle error
            return
        }
        init(paletteName)
    }

    companion object {
        suspend fun create(
            jsonObject: JSONObject,
            paletteName: String?
        ): ImageDto {
            val imageDto = ImageDto()
            imageDto.jsonObject = jsonObject
            imageDto.init(paletteName)
            return imageDto
        }

        suspend fun create(filename: String, paletteName: String?): ImageDto {
            val imageDto = ImageDto()
            imageDto.initFromFile(filename, paletteName)
            return imageDto
        }
    }

    private suspend fun init(paletteName: String?) {
        try {
            metadata = jsonObject!!.getJSONObject("metadata")
            if (!metadata!!.has("palette")) {
                metadata!!.put("palette", paletteName)
                this.paletteName = paletteName
            } else {
                this.paletteName = metadata!!.getString("palette")
            }
            palette = paletteFactory.getPaletteByName(this.paletteName)
            if (bitmap != null) {
                bitmap!!.recycle()
                bitmap = null
            }
            cameraUtils.processImageResponse(this)
        } catch (e: JSONException) {
        }
        creationDate = Date()
    }

    suspend fun parse(obj: JSONObject, PaletteName: String?) {
        jsonObject = obj
        create(obj, paletteName)
    }

    fun getJsonObject(): JSONObject = jsonObject!!

    /**********************************************************************/
    /*                                                                    */
    /*                      Extensions                                    */
    /*                                                                    */
    /**********************************************************************/
    //TODO JMT
//    fun convertToRadiometric(value: Float): Int {
//        return cameraUtils.convertToRadiometric(this, value)
//    }
//
//    fun createColorBar(): Bitmap {
//        return cameraUtils.createColorBar(this, Constants.COLORBAR_WIDTH)
//    }
//
//    fun remapImage() {
//        cameraUtils.remapImage(this)
//    }
//
//    fun drawHotspot(): Bitmap {
//        return cameraUtils.drawHotspot(this)
//    }
//
//    val radiometricTemperatures: android.util.Pair<Int?, Int?>
//        get() = cameraUtils.getRadiometricTemperatures(this)
//
//    val temperatures: Pair<Float?, Float?>
//        get() = cameraUtils.getTemperatures(this)
//
//    val meanTemperatureAtSpotmeter: Float
//        get() = cameraUtils.getMeanTemperatureAtSpotmeter(this)
//
//    fun createHistogram(): Bitmap {
//        return cameraUtils.createHistogram(this)
//    }
//
//    @Throws(IOException::class)
//    fun saveTjsn(): Boolean {
//        return cameraUtils.saveTjsn(this)
//    }
//
//    @Throws(IOException::class)
//    fun saveBitmapToFile(newFile: File?) {
//        //TODO JMT cameraUtils.saveBitmapToFile(this, newFile)
//    }
//
//
//    fun rotateColormap(direction: Int) {
//        cameraUtils.rotateColormap(this, direction)
//    }
}
