package com.danjuliodesigns.tcamviewer2.utils

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Helpers for files stored on a full tCam's micro-SD card (see issue #35). Names come from the
 * camera, so anything used as a path segment goes through [isSafeName] first.
 */
object DeviceFiles {
    const val IMAGE_EXTENSION = "tjsn"
    const val VIDEO_EXTENSION = "tmjsn"

    /** Splits the comma-separated `name_list` from a `filesystem_list` reply. The camera leaves a
     *  trailing comma, so empty entries are dropped. */
    fun parseNameList(nameList: String): List<String> = nameList.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** A name is usable as a single path segment: no separators and not "." or "..". */
    fun isSafeName(name: String): Boolean = name.isNotEmpty() &&
        name != "." &&
        name != ".." &&
        !name.contains('/') &&
        !name.contains('\\')

    fun isDownloadableImage(name: String): Boolean = isSafeName(name) && name.endsWith(".$IMAGE_EXTENSION")

    /** Videos (.tmjsn) — see [CameraService.fetchVideoFile] (issue #39). */
    fun isDownloadableVideo(name: String): Boolean = isSafeName(name) && name.endsWith(".$VIDEO_EXTENSION")

    fun isDownloadable(name: String): Boolean = isDownloadableImage(name) || isDownloadableVideo(name)

    /** Same root the Library scans, so imported folders show up beside captured ones. */
    fun picturesRoot(context: Context): File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir

    /**
     * Writes [content] to `root/folder/fileName`, keeping the camera's folder name. Writes to a
     * `.part` file first, so an interrupted download never leaves a half-written image that the
     * Library would try to open. Returns [SaveResult.ALREADY_PRESENT] without writing if the file
     * already exists (already imported).
     */
    @Throws(IOException::class)
    fun saveImage(root: File, folder: String, fileName: String, content: String): SaveResult {
        require(isSafeName(folder) && isSafeName(fileName)) { "Unsafe device path: $folder/$fileName" }
        val dir = File(root, folder)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.path}")

        val target = File(dir, fileName)
        if (target.exists()) return SaveResult.ALREADY_PRESENT

        val partial = File(dir, "$fileName.part")
        try {
            partial.writeText(content, Charsets.US_ASCII)
            if (!partial.renameTo(target)) throw IOException("Could not rename to ${target.name}")
        } catch (e: IOException) {
            partial.delete()
            throw e
        }
        return SaveResult.SAVED
    }

    /**
     * Writes a downloaded video's [frames] and [videoInfo] to `root/folder/fileName`, in the same
     * layout this app writes for its own local recordings (see CameraViewModel.processFrame /
     * buildFooterJson): each frame's JSON followed by one ETX (0x03) byte, then the video_info
     * JSON with no trailing ETX. Library's readMtjsnContent() reads this layout back. Same
     * `.part`-then-rename and already-imported handling as [saveImage].
     */
    @Throws(IOException::class)
    fun saveVideo(root: File, folder: String, fileName: String, frames: List<JSONObject>, videoInfo: JSONObject): SaveResult {
        require(isSafeName(folder) && isSafeName(fileName)) { "Unsafe device path: $folder/$fileName" }
        val dir = File(root, folder)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.path}")

        val target = File(dir, fileName)
        if (target.exists()) return SaveResult.ALREADY_PRESENT

        val partial = File(dir, "$fileName.part")
        try {
            partial.outputStream().use { out ->
                for (frame in frames) {
                    out.write(frame.toString().toByteArray(Charsets.US_ASCII))
                    out.write(0x03)
                }
                out.write(videoInfo.toString().toByteArray(Charsets.US_ASCII))
            }
            if (!partial.renameTo(target)) throw IOException("Could not rename to ${target.name}")
        } catch (e: IOException) {
            partial.delete()
            throw e
        }
        return SaveResult.SAVED
    }

    enum class SaveResult { SAVED, ALREADY_PRESENT }
}
