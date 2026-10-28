package com.danjuliodesigns.tcamviewer2.utils

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DeviceFilesTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("device-files-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun parseNameList_dropsTrailingComma() {
        assertEquals(
            listOf("img_18_52_20.tjsn", "img_18_53_01.tjsn"),
            DeviceFiles.parseNameList("img_18_52_20.tjsn,img_18_53_01.tjsn,"),
        )
    }

    @Test
    fun parseNameList_emptyListGivesNoEntries() {
        assertEquals(emptyList<String>(), DeviceFiles.parseNameList(""))
        assertEquals(emptyList<String>(), DeviceFiles.parseNameList(","))
    }

    @Test
    fun parseNameList_trimsWhitespace() {
        assertEquals(listOf("a.tjsn", "b.tjsn"), DeviceFiles.parseNameList(" a.tjsn , b.tjsn ,"))
    }

    @Test
    fun isSafeName_rejectsPathTricks() {
        assertFalse(DeviceFiles.isSafeName(""))
        assertFalse(DeviceFiles.isSafeName("."))
        assertFalse(DeviceFiles.isSafeName(".."))
        assertFalse(DeviceFiles.isSafeName("../secret.tjsn"))
        assertFalse(DeviceFiles.isSafeName("dir/file.tjsn"))
        assertFalse(DeviceFiles.isSafeName("dir\\file.tjsn"))
        assertTrue(DeviceFiles.isSafeName("tcam_26_10_01"))
        assertTrue(DeviceFiles.isSafeName("img_18_52_20.tjsn"))
    }

    @Test
    fun isDownloadableImage_onlyTjsnFiles() {
        assertTrue(DeviceFiles.isDownloadableImage("img_18_52_20.tjsn"))
        assertFalse(DeviceFiles.isDownloadableImage("vid_18_52_20.tmjsn"))
        assertFalse(DeviceFiles.isDownloadableImage("tl_18_52_20.tltjsn"))
        assertFalse(DeviceFiles.isDownloadableImage("../img.tjsn"))
    }

    @Test
    fun saveImage_keepsCameraFolderName() {
        val result = DeviceFiles.saveImage(root, "tcam_26_10_01", "img_18_52_20.tjsn", "{}")

        assertEquals(DeviceFiles.SaveResult.SAVED, result)
        val saved = File(root, "tcam_26_10_01/img_18_52_20.tjsn")
        assertTrue(saved.exists())
        assertEquals("{}", saved.readText())
    }

    @Test
    fun saveImage_existingFileIsNotOverwritten() {
        DeviceFiles.saveImage(root, "tcam_26_10_01", "img_18_52_20.tjsn", "first")

        val result = DeviceFiles.saveImage(root, "tcam_26_10_01", "img_18_52_20.tjsn", "second")

        assertEquals(DeviceFiles.SaveResult.ALREADY_PRESENT, result)
        assertEquals("first", File(root, "tcam_26_10_01/img_18_52_20.tjsn").readText())
    }

    @Test
    fun saveImage_leavesNoPartialFileBehind() {
        DeviceFiles.saveImage(root, "tcam_26_10_01", "img_18_52_20.tjsn", "{}")

        val leftovers = File(root, "tcam_26_10_01").listFiles()!!.map { it.name }
        assertEquals(listOf("img_18_52_20.tjsn"), leftovers)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveImage_rejectsUnsafeFolder() {
        DeviceFiles.saveImage(root, "../escape", "img.tjsn", "{}")
    }

    @Test(expected = IOException::class)
    fun saveImage_failsWhenFolderCannotBeCreated() {
        // A regular file where the folder should go makes mkdirs fail
        File(root, "tcam_26_10_01").writeText("not a folder")
        DeviceFiles.saveImage(root, "tcam_26_10_01", "img.tjsn", "{}")
    }

    // --- saveVideo (issue #39: video files, not just stills, are downloadable) ---

    @Test
    fun saveVideo_keepsCameraFolderName() {
        val frames = listOf(JSONObject().put("radiometric", "a"), JSONObject().put("radiometric", "b"))
        val videoInfo = JSONObject().put("video_info", true)

        val result = DeviceFiles.saveVideo(root, "tcam_26_10_01", "mov_18_52_20.tmjsn", frames, videoInfo)

        assertEquals(DeviceFiles.SaveResult.SAVED, result)
        val saved = File(root, "tcam_26_10_01/mov_18_52_20.tmjsn")
        assertTrue(saved.exists())
    }

    @Test
    fun saveVideo_writesEachFrameFollowedByEtxThenVideoInfoWithNoTrailingEtx() {
        val frames = listOf(JSONObject().put("radiometric", "a"), JSONObject().put("radiometric", "b"))
        val videoInfo = JSONObject().put("video_info", true)

        DeviceFiles.saveVideo(root, "tcam_26_10_01", "mov_18_52_20.tmjsn", frames, videoInfo)

        val bytes = File(root, "tcam_26_10_01/mov_18_52_20.tmjsn").readBytes()
        val expected =
            frames[0].toString().toByteArray(Charsets.US_ASCII) + byteArrayOf(0x03) +
                frames[1].toString().toByteArray(Charsets.US_ASCII) + byteArrayOf(0x03) +
                videoInfo.toString().toByteArray(Charsets.US_ASCII)
        assertTrue(bytes.contentEquals(expected))
        // No trailing ETX after video_info -- readMtjsnContent relies on this to know where the
        // frame stream ends and the trailing video_info object begins.
        assertFalse(bytes.isEmpty())
        assertEquals(0x7d, bytes.last().toInt()) // '}' -- the JSON object's own closing brace, not 0x03
    }

    @Test
    fun saveVideo_existingFileIsNotOverwritten() {
        val videoInfo = JSONObject().put("video_info", true)
        DeviceFiles.saveVideo(root, "tcam_26_10_01", "mov_18_52_20.tmjsn", emptyList(), videoInfo)
        val firstBytes = File(root, "tcam_26_10_01/mov_18_52_20.tmjsn").readBytes()

        val result = DeviceFiles.saveVideo(
            root,
            "tcam_26_10_01",
            "mov_18_52_20.tmjsn",
            listOf(JSONObject().put("radiometric", "new")),
            JSONObject().put("video_info", "different"),
        )

        assertEquals(DeviceFiles.SaveResult.ALREADY_PRESENT, result)
        assertTrue(firstBytes.contentEquals(File(root, "tcam_26_10_01/mov_18_52_20.tmjsn").readBytes()))
    }

    @Test
    fun saveVideo_leavesNoPartialFileBehind() {
        DeviceFiles.saveVideo(root, "tcam_26_10_01", "mov_18_52_20.tmjsn", emptyList(), JSONObject())

        val leftovers = File(root, "tcam_26_10_01").listFiles()!!.map { it.name }
        assertEquals(listOf("mov_18_52_20.tmjsn"), leftovers)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveVideo_rejectsUnsafeFolder() {
        DeviceFiles.saveVideo(root, "../escape", "mov.tmjsn", emptyList(), JSONObject())
    }

    @Test(expected = IOException::class)
    fun saveVideo_failsWhenFolderCannotBeCreated() {
        File(root, "tcam_26_10_01").writeText("not a folder")
        DeviceFiles.saveVideo(root, "tcam_26_10_01", "mov.tmjsn", emptyList(), JSONObject())
    }

    @Test
    fun isDownloadableVideo_onlyTmjsnFiles() {
        assertTrue(DeviceFiles.isDownloadableVideo("vid_18_52_20.tmjsn"))
        assertFalse(DeviceFiles.isDownloadableVideo("img_18_52_20.tjsn"))
        assertFalse(DeviceFiles.isDownloadableVideo("tl_18_52_20.tltjsn"))
        assertFalse(DeviceFiles.isDownloadableVideo("../vid.tmjsn"))
    }
}
