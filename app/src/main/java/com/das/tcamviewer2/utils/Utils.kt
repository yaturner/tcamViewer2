package com.das.tcamviewer2.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException


class Utils(context: Context) {
    private val appContext = context.applicationContext

//    @Throws(FileNotFoundException::class)
//    fun exportImage(imageDto: ImageDto) {
//        var imageFilename: String?
//        val imageDirectory: String?
//        val imageName: String?
//        val bitmap: Bitmap? = createExportImage(imageDto)
//        val word: Array<String?> =
//            imageDto.filename!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//        val nWords = word.size
//        //if there is only one word, then it is the filename and take the folder from the CreationDate
//        if (nWords == 1) {
//            imageName = imageDto.filename!!.replace("img_", "").replace(".tjsn", "")
//            imageDirectory = CameraUtils.simpleDateFormatFolder.format(imageDto.creationDate)
//        } else {
//            imageDirectory = word[nWords - 2]
//            imageName = word[nWords - 1]!!.replace("img_", "").replace(".tjsn", "")
//        }
//        val widths: IntArray = appContext.getResources().getIntArray(R.array.resolution_widths)
//        val heights: IntArray = appContext.getResources().getIntArray(R.array.resolution_heights)
//        saveBitmap(bitmap!!, imageDirectory!!, imageName)
//        Toast.makeText(appContext, "Image exported as " + imageName, Toast.LENGTH_LONG).show()
//    }

    fun saveBitmap(
    bitmap: Bitmap,
    imageDirectory: String,
    imageName: String
    ): Uri? {
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        values.put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DCIM + "/" + imageDirectory
        )

        val resolver: ContentResolver = appContext.contentResolver
        var uri: Uri? = null

        try {
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            uri = resolver.insert(contentUri, values)

            if (uri == null) throw IOException("Failed to create new MediaStore record.")

            resolver.openOutputStream(uri).use { stream ->
                if (stream == null) throw IOException("Failed to open output stream.")
                if (!bitmap!!.compress(
                        Bitmap.CompressFormat.PNG,
                        95,
                        stream
                    )
                ) throw IOException("Failed to save bitmap.")
            }
            return uri
        } catch (e: IOException) {
            if (uri != null) {
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(uri, null, null)
            }
        }
        return null
    }
}