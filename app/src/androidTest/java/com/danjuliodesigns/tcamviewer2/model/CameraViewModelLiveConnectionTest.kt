package com.danjuliodesigns.tcamviewer2.model

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danjuliodesigns.tcamviewer2.SettingsDataManager
import com.danjuliodesigns.tcamviewer2.appContext
import com.danjuliodesigns.tcamviewer2.cameraService
import com.danjuliodesigns.tcamviewer2.cameraUtils
import com.danjuliodesigns.tcamviewer2.dataStore
import com.danjuliodesigns.tcamviewer2.factory.PaletteFactory
import com.danjuliodesigns.tcamviewer2.paletteFactory
import com.danjuliodesigns.tcamviewer2.services.CameraService
import com.danjuliodesigns.tcamviewer2.settingsDataManager
import com.danjuliodesigns.tcamviewer2.utils
import com.danjuliodesigns.tcamviewer2.utils.CameraUtils
import com.danjuliodesigns.tcamviewer2.utils.Utils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// CameraService hardcodes port 5001 (see connect()'s InetSocketAddress) -- same environmental
// assumption as CameraServiceProtocolTest's JVM-side fake server, just running on-device here
// instead of on the host JVM, so there's no port conflict between the two.
private const val TEST_PORT = 5001

/** A minimal stand-in for a real tCam, run on a background thread for the lifetime of a test so
 *  it can auto-answer whatever CameraViewModel's connect sequence sends (set_time,
 *  get_filesystem_list, the initial auto-Get, get_config) while leaving get_image answerable (or
 *  not) under the test's own control via [respondToGetImage] -- issues #33/#34 are both about
 *  the app's own getImage()/isGetting state machine around that exact command. */
private class FakeCameraServer {
    private val serverSocket = ServerSocket(TEST_PORT, 1, InetAddress.getByName("127.0.0.1"))

    @Volatile private var clientSocket: Socket? = null
    private val acceptedLatch = CountDownLatch(1)

    @Volatile var respondToGetImage: Boolean = true
    val getImageCount = java.util.concurrent.atomic.AtomicInteger(0)
    val getImageTimestamps: MutableList<Long> = CopyOnWriteArrayList()

    private val running = AtomicBoolean(true)

    private val acceptThread = Thread {
        try {
            clientSocket = serverSocket.accept()
            acceptedLatch.countDown()
        } catch (_: Exception) {
            // ServerSocket closed while waiting to accept -- expected during teardown.
        }
    }.apply {
        isDaemon = true
        start()
    }

    private val responderThread = Thread {
        if (!acceptedLatch.await(5_000, TimeUnit.MILLISECONDS)) return@Thread
        while (running.get()) {
            val cmd = try {
                readNextCommand()
            } catch (_: Exception) {
                break
            }
            when {
                cmd.contains("get_config") ->
                    sendResponse("""{"config":{"agc_enabled":0,"emissivity":90,"gain_mode":0}}""")

                cmd.contains("get_filesystem_list") ->
                    sendResponse("""{"cam_info":{"ret_stat":false}}""")

                cmd.contains("get_image") -> {
                    getImageCount.incrementAndGet()
                    getImageTimestamps.add(System.currentTimeMillis())
                    if (respondToGetImage) sendResponse(syntheticFrameJson())
                }
                // set_time and anything else: no response expected.
            }
        }
    }.apply {
        isDaemon = true
        start()
    }

    fun awaitConnection(timeoutMs: Long = 5_000L): Boolean = acceptedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    private fun input(): InputStream = clientSocket!!.getInputStream()

    private fun output(): OutputStream = clientSocket!!.getOutputStream()

    private fun readNextCommand(): String {
        val input = input()
        val buffer = StringBuilder()
        var started = false
        while (true) {
            val b = input.read()
            if (b == -1) throw EOFException("Client closed before sending a full command")
            when (b) {
                0x02 -> {
                    started = true
                    buffer.clear()
                }

                0x03 -> if (started) return buffer.toString()

                else -> if (started) buffer.append(b.toChar())
            }
        }
    }

    @Synchronized
    private fun sendResponse(json: String) {
        val out = output()
        out.write(0x02)
        out.write(json.toByteArray(Charsets.UTF_8))
        out.write(0x03)
        out.flush()
    }

    fun close() {
        running.set(false)
        runCatching { clientSocket?.close() }
        runCatching { serverSocket.close() }
        acceptThread.interrupt()
        responderThread.interrupt()
    }
}

/** A flat, all-zero (non-AGC, non-thermal-flagged) 160x120 frame -- valid enough for
 *  ImageDto.create()/processFrame() to accept without throwing; these tests only care about
 *  the Get/isGetting state machine around its arrival, not its decoded content. */
private fun syntheticFrameJson(): String {
    val radiometricBytes = ByteArray(160 * 120 * 2)
    val telBytes = ByteArray(480)
    return """{"radiometric":"${Base64.getEncoder().encodeToString(radiometricBytes)}",""" +
        """"telemetry":"${Base64.getEncoder().encodeToString(telBytes)}",""" +
        """"metadata":{"Date":"01/01/26","Time":"12:00:00.000"}}"""
}

/** Covers issues #33 (AGC change auto-refreshes the displayed frame) and #34 (a spinner-backing
 *  isGetting flag while a manual Get is in flight) against a REAL camera connection -- a fake
 *  server on loopback TCP, same approach as CameraServiceProtocolTest, but driving the full
 *  CameraViewModel rather than CameraService alone, since both issues are about ViewModel-level
 *  state (isGetting, refreshAfterConfigChange's delayed getImage()) that only exists once a
 *  connection is live. Needs a real Context (SettingsDataManager/CameraUtils), so this runs
 *  instrumented, following the same global-singleton setup as CameraViewModelInstrumentedTest. */
@RunWith(AndroidJUnit4::class)
class CameraViewModelLiveConnectionTest {
    private lateinit var viewModel: CameraViewModel
    private lateinit var server: FakeCameraServer

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.dataStore.edit { it.clear() }

        cameraService = CameraService()
        settingsDataManager = SettingsDataManager(context)
        cameraUtils = CameraUtils(context)
        paletteFactory = PaletteFactory()
        utils = Utils(context)
        appContext = context

        viewModel = CameraViewModel()
        server = FakeCameraServer()
    }

    @After
    fun tearDown() {
        cameraService.disconnect()
        server.close()
    }

    private fun connectAndWait() = runBlocking {
        settingsDataManager.saveCameraIp("127.0.0.1")
        viewModel.toggleConnection()
        awaitValue(5_000) { viewModel.isConnected.value }
        assertTrue("Test setup: must actually be connected before exercising Get/AGC behavior", viewModel.isConnected.value)
    }

    // --- issue #34: a spinner-backing isGetting flag while a manual Get is in flight ---

    @Test
    fun getImage_setsIsGettingUntilTheFrameArrives() = runBlocking {
        connectAndWait()
        assertFalse(viewModel.isGetting.value)

        viewModel.getImage()
        assertTrue("isGetting must flip true the instant Get is requested", viewModel.isGetting.value)

        awaitValue(5_000) { !viewModel.isGetting.value }
        assertFalse("isGetting must clear once the frame answering this Get arrives", viewModel.isGetting.value)
    }

    @Test
    fun getImage_clearsIsGettingAfterTimeoutWhenCameraNeverAnswers() = runBlocking {
        connectAndWait()
        server.respondToGetImage = false

        viewModel.getImage()
        assertTrue(viewModel.isGetting.value)

        // GET_TIMEOUT_MS is 5s -- wait past it rather than asserting instantly false.
        awaitValue(7_000) { !viewModel.isGetting.value }
        assertFalse(
            "A Get the camera never answers must not leave the spinner up forever",
            viewModel.isGetting.value,
        )
    }

    // --- issue #33: an AGC change auto-refreshes the displayed frame without needing Get ---

    @Test
    fun refreshAfterConfigChange_requestsANewFrameShortlyAfter() = runBlocking {
        connectAndWait()
        // Let connectToCamera()'s own initial auto-Get (and its near-instant loopback round
        // trip) fully settle before snapshotting the counter this test diffs against.
        kotlinx.coroutines.delay(300L)
        val before = System.currentTimeMillis()
        val countBefore = server.getImageCount.get()

        viewModel.sendCameraConfig(agcEnabled = true, emissivity = 90, gainMode = 0)
        viewModel.refreshAfterConfigChange()

        awaitValue(3_000) { server.getImageCount.get() > countBefore }
        assertTrue(
            "Changing AGC and refreshing must request a new frame automatically",
            server.getImageCount.get() > countBefore,
        )
        val elapsed = server.getImageTimestamps.last() - before
        assertTrue(
            "Must wait briefly before re-requesting -- the camera's set_config hasn't applied " +
                "instantly, so an immediate Get could still return a frame in the old mode " +
                "(elapsed=${elapsed}ms)",
            elapsed >= 400L,
        )
    }

    @Test
    fun refreshAfterConfigChange_theRequestedFrameClearsIsGettingLikeAnyOtherGet() = runBlocking {
        // refreshAfterConfigChange() calls the same getImage() a manual Get uses -- the spinner
        // state machine from #34 must behave identically here, not just "a command gets sent".
        connectAndWait()
        viewModel.sendCameraConfig(agcEnabled = true, emissivity = 90, gainMode = 0)
        viewModel.refreshAfterConfigChange()

        awaitValue(3_000) { viewModel.isGetting.value }
        assertTrue(viewModel.isGetting.value)

        awaitValue(5_000) { !viewModel.isGetting.value }
        assertFalse(viewModel.isGetting.value)
    }

    private suspend fun awaitValue(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(20L)
        }
    }
}
