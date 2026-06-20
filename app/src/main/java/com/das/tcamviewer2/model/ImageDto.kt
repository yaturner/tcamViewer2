package com.das.tcamviewer2.model

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Pair
import com.das.tcamviewer2.cameraUtils
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.paletteFactory
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
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

    var jsonObject: JSONObject? = null
    var metadata: JSONObject? = null
    var filename: String? = ""
    var tjsnString: String? = null
    var palette: Array<IntArray?>? = null
    var imageData: IntArray? = null
    var paletteName: String? = null
    var bitmap: Bitmap? = null

    //Constructor from camera response
    constructor(jsonObject: JSONObject, paletteName: String?) {
        this.jsonObject = jsonObject
        init(paletteName)
    }

    //Constructor from file
    constructor(filename: String?, paletteName: String?) {
        this.filename = filename
        tjsnString = cameraUtils.readTjsnFile(filename, false)
        if (tjsnString != null && !tjsnString!!.isEmpty()) {
            try {
                jsonObject = JSONObject(tjsnString)
            } catch (e: JSONException) {
                //Sentry.captureException(e)
            }
        } else {
            //TODO Handle error
            return
        }
        init(paletteName)
    }

    private fun init(paletteName: String?) {
        try {
            //add the palette name to the metadata if it isn't there already
            metadata = jsonObject!!.getJSONObject("metadata")
            if (!metadata!!.has("palette")) {
                metadata!!.put("palette", paletteName)
                this.paletteName = paletteName
            } else {
                this.paletteName = metadata!!.getString("palette")
            }
            palette =
                paletteFactory.getPaletteByName(this.paletteName)
            if (bitmap != null) {
                bitmap!!.recycle()
            }
            cameraUtils.processImageResponse(this)
        } catch (e: JSONException) {
            //Sentry.captureException(e)
        }
        creationDate = Date()
    }

    fun parse(obj: JSONObject, PaletteName: String?) {
        jsonObject = obj
        init(paletteName)
    }

    fun getJsonObject(): JSONObject {
        return jsonObject!!
    }

    fun getPaletteName(): String? {
        return paletteName
    }

    fun setPaletteName(paletteName: String?) {
        this.paletteName = paletteName
        //change it in the jsonObject.metadata
        try {
            val meta = jsonObject!!.getJSONObject("metadata")
            meta.remove("paletteName")
            meta.put("palette", paletteName)
        } catch (e: JSONException) {
            //Sentry.captureException(e)
        }
    }

    /** */
    /*                                                                   */
    /*                      Extensions                                  */
    /*                                                                   */
    /** */
    fun convertToRadiometric(value: Float): Int {
        return cameraUtils.convertToRadiometric(this, value)
    }

    fun createColorBar(): Bitmap {
        return cameraUtils.createColorBar(this, Constants.COLORBAR_WIDTH)
    }

    fun remapImage() {
        cameraUtils.remapImage(this)
    }

    fun drawHotspot(): Bitmap {
        return cameraUtils.drawHotspot(this)
    }

    val radiometricTemperatures: android.util.Pair<Int?, Int?>
        get() = cameraUtils.getRadiometricTemperatures(this)

    val temperatures: Pair<Float?, Float?>
        get() = cameraUtils.getTemperatures(this)

    val meanTemperatureAtSpotmeter: Float
        get() = cameraUtils.getMeanTemperatureAtSpotmeter(this)

    fun createHistogram(): Bitmap {
        return cameraUtils.createHistogram(this)
    }

    @Throws(IOException::class)
    fun saveTjsn(): Boolean {
        return cameraUtils.saveTjsn(this)
    }

    @Throws(IOException::class)
    fun saveBitmapToFile(newFile: File?) {
        //TODO JMT cameraUtils.saveBitmapToFile(this, newFile)
    }


    fun rotateColormap(direction: Int) {
        cameraUtils.rotateColormap(this, direction)
    }
}
