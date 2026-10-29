package com.danjuliodesigns.tcamviewer2.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression tests for issue #56: a camera-downloaded file's SD-card name gets renamed, at
 *  download time, to match this app's own local naming convention. */
class CameraDownloadWindowHelpersTest {
    private fun metadata(date: String, time: String): JSONObject = JSONObject().put("Date", date).put("Time", time)

    @Test
    fun localStyleName_validMetadataProducesLocalFolderAndFilename() {
        val (folder, fileName) = localStyleName(
            metadata("10/12/26", "08:04:00"),
            "vid_",
            "tmjsn",
            "tcam_26_10_12",
            "mov_8_03_59.tmjsn",
        )

        assertEquals("10_12_2026", folder)
        assertEquals("vid_08_04_00.tmjsn", fileName)
    }

    @Test
    fun localStyleName_imagePrefixAndExtension() {
        val (folder, fileName) = localStyleName(
            metadata("01/05/26", "23:59:09"),
            "img_",
            "tjsn",
            "tcam_26_01_05",
            "img_23_58_55.tjsn",
        )

        assertEquals("01_05_2026", folder)
        assertEquals("img_23_59_09.tjsn", fileName)
    }

    @Test
    fun localStyleName_nullMetadataFallsBackToCameraName() {
        val (folder, fileName) = localStyleName(
            null,
            "vid_",
            "tmjsn",
            "tcam_26_10_12",
            "mov_8_03_59.tmjsn",
        )

        assertEquals("tcam_26_10_12", folder)
        assertEquals("mov_8_03_59.tmjsn", fileName)
    }

    @Test
    fun localStyleName_unparseableDateFallsBackToCameraName() {
        val (folder, fileName) = localStyleName(
            metadata("not-a-date", "nope"),
            "img_",
            "tjsn",
            "tcam_26_10_12",
            "img_01_02_03.tjsn",
        )

        assertEquals("tcam_26_10_12", folder)
        assertEquals("img_01_02_03.tjsn", fileName)
    }

    @Test
    fun localStyleName_missingDateTimeFieldsFallsBackToCameraName() {
        val (folder, fileName) = localStyleName(
            JSONObject(),
            "img_",
            "tjsn",
            "tcam_26_10_12",
            "img_01_02_03.tjsn",
        )

        assertEquals("tcam_26_10_12", folder)
        assertEquals("img_01_02_03.tjsn", fileName)
    }
}
