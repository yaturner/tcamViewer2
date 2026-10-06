package com.danjuliodesigns.tcamviewer2.utils

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
}
