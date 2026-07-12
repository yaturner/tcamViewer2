package com.das.tcamviewer2.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Directory used for every file this test creates in the shared DCIM/Movies collections, so
 * cleanup can find and remove exactly what it made without touching anything else on the device.
 */
private const val TEST_DIR = "tcamviewer2_instrumented_test"

@RunWith(AndroidJUnit4::class)
class UtilsInstrumentedTest {

    private lateinit var utils: Utils
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        utils = Utils(context)
    }

    @After
    fun tearDown() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        createdUris.forEach { resolver.delete(it, null, null) }
        createdUris.clear()
    }

    // --- saveBitmap ---

    @Test
    fun saveBitmapReturnsAReadableUri() {
        val bitmap = createBitmap(4, 4).apply { eraseColor(Color.RED) }
        val name = "test_${System.currentTimeMillis()}"

        val uri = utils.saveBitmap(bitmap, TEST_DIR, name)
        assertNotNull("saveBitmap should return a non-null Uri on success", uri)
        createdUris.add(uri!!)

        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val readBack = resolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) }
        assertNotNull("Saved bitmap should be readable back", readBack)
        assertEquals(4, readBack!!.width)
        assertEquals(4, readBack.height)
    }

    @Test
    fun saveBitmapSetsExpectedMediaStoreColumns() {
        val bitmap = createBitmap(2, 2).apply { eraseColor(Color.BLUE) }
        val name = "test_columns_${System.currentTimeMillis()}"

        val uri = utils.saveBitmap(bitmap, TEST_DIR, name)
        assertNotNull(uri)
        createdUris.add(uri!!)

        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        resolver.query(uri!!, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE), null, null, null)
            .use { cursor ->
                assertNotNull(cursor)
                assertTrue(cursor!!.moveToFirst())
                // MediaStore appends the extension implied by MIME_TYPE to DISPLAY_NAME itself.
                assertEquals("$name.png", cursor.getString(0))
                assertEquals("image/png", cursor.getString(1))
            }
    }

    // --- saveVideo ---

    @Test
    fun saveVideoCopiesSourceBytesAndReturnsAReadableUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val sourceFile = File(context.cacheDir, "fake_video_${System.currentTimeMillis()}.mp4")
        sourceFile.writeBytes(sourceBytes)
        val name = "test_video_${System.currentTimeMillis()}"

        try {
            val uri = utils.saveVideo(sourceFile, TEST_DIR, name)
            assertNotNull("saveVideo should return a non-null Uri on success", uri)
            createdUris.add(uri!!)

            val resolver = context.contentResolver
            val readBack = resolver.openInputStream(uri).use { it?.readBytes() }
            assertNotNull(readBack)
            assertTrue("Copied bytes should match source file", sourceBytes.contentEquals(readBack))
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun saveVideoSetsExpectedMediaStoreColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.cacheDir, "fake_video_columns_${System.currentTimeMillis()}.mp4")
        sourceFile.writeBytes(byteArrayOf(9, 9, 9))
        val name = "test_video_columns_${System.currentTimeMillis()}"

        try {
            val uri = utils.saveVideo(sourceFile, TEST_DIR, name)
            assertNotNull(uri)
            createdUris.add(uri!!)

            val resolver = context.contentResolver
            resolver.query(uri!!, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE), null, null, null)
                .use { cursor ->
                    assertNotNull(cursor)
                    assertTrue(cursor!!.moveToFirst())
                    // MediaStore appends the extension implied by MIME_TYPE to DISPLAY_NAME itself.
                    assertEquals("$name.mp4", cursor.getString(0))
                    assertEquals("video/mp4", cursor.getString(1))
                }
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun saveVideoReturnsNullWhenSourceFileDoesNotExist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missingFile = File(context.cacheDir, "does_not_exist_${System.currentTimeMillis()}.mp4")
        val uri = utils.saveVideo(missingFile, TEST_DIR, "should_not_be_created")
        assertNull("saveVideo should return null and clean up the MediaStore row when the source is unreadable", uri)
    }
}
