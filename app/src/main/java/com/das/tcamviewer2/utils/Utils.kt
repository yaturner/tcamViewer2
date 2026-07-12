package com.das.tcamviewer2.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException


class Utils(context: Context) {
    private val appContext = context.applicationContext

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

    fun saveVideo(
        sourceFile: File,
        videoDirectory: String,
        videoName: String
    ): Uri? {
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, videoName)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        values.put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES + "/" + videoDirectory
        )

        val resolver: ContentResolver = appContext.contentResolver
        var uri: Uri? = null

        try {
            val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            uri = resolver.insert(contentUri, values)

            if (uri == null) throw IOException("Failed to create new MediaStore record.")

            resolver.openOutputStream(uri).use { stream ->
                if (stream == null) throw IOException("Failed to open output stream.")
                sourceFile.inputStream().use { it.copyTo(stream) }
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