package com.danjuliodesigns.tcamviewer2.services

import com.danjuliodesigns.tcamviewer2.constants.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// CameraService hardcodes port 5001 (see connect()'s InetSocketAddress), so the fake server has
// no choice but to bind that exact port on loopback -- there's no way to parameterize it without
// changing production code. This is the one real environmental assumption these tests make: the
// machine running them must have 127.0.0.1:5001 free.
private const val TEST_PORT = 5001

/** A minimal stand-in for a real tCam camera speaking the STX(0x02)/ETX(0x03)-framed JSON
 *  protocol over a loopback TCP socket. Exists so CameraServiceProtocolTest can exercise the
 *  REAL connect/sendCmd/disconnect-detection code paths end-to-end against real socket I/O,
 *  rather than mocking Socket/InputStream away and only testing what the mocks are told to do. */
private class FakeCameraServer {
    private val serverSocket = ServerSocket(TEST_PORT, 1, InetAddress.getByName("127.0.0.1"))

    @Volatile private var clientSocket: Socket? = null
    private val acceptedLatch = CountDownLatch(1)

    private val acceptThread =
        Thread {
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

    fun awaitConnection(timeoutMs: Long = 5_000L): Boolean = acceptedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun input(): InputStream = clientSocket!!.getInputStream()

    fun output(): OutputStream = clientSocket!!.getOutputStream()

    /** Blocks until one full STX...ETX-framed command arrives, returning its JSON body (framing
     *  bytes stripped). Byte-at-a-time on purpose -- this is a test double, not production code,
     *  and simplicity here matters more than throughput. */
    fun readNextCommand(): String {
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

    fun sendResponse(json: String) {
        val out = output()
        out.write(0x02)
        out.write(json.toByteArray(Charsets.UTF_8))
        out.write(0x03)
        out.flush()
    }

    /** Simulates the camera cleanly closing its side of the connection -- CameraService should
     *  see this as EOF (read() returning -1), not an exception. */
    fun closeConnection() {
        clientSocket?.close()
    }

    fun close() {
        runCatching { clientSocket?.close() }
        runCatching { serverSocket.close() }
        acceptThread.interrupt()
    }
}

class CameraServiceProtocolTest {
    private lateinit var server: FakeCameraServer
    private lateinit var cameraService: CameraService

    // The overridable timeout fields live on CameraService's companion object, so they're
    // process-global -- save/restore them per test in case Gradle reuses one JVM across test
    // classes, so a short override here can't leak into an unrelated test class.
    private var savedReadTimeout = 0
    private var savedMaxTimeouts = 0
    private var savedIdleInterval = 0L
    private var savedIdleTimeout = 0L

    @Before
    fun setUp() {
        savedReadTimeout = CameraService.SOCKET_READ_TIMEOUT_MS
        savedMaxTimeouts = CameraService.MAX_CONSECUTIVE_READ_TIMEOUTS_WHILE_STREAMING
        savedIdleInterval = CameraService.IDLE_HEALTH_CHECK_INTERVAL_MS
        savedIdleTimeout = CameraService.IDLE_HEALTH_CHECK_TIMEOUT_MS
        server = FakeCameraServer()
        cameraService = CameraService()
        cameraService.setIpAddress("127.0.0.1")
    }

    @After
    fun tearDown() {
        cameraService.disconnect()
        server.close()
        CameraService.SOCKET_READ_TIMEOUT_MS = savedReadTimeout
        CameraService.MAX_CONSECUTIVE_READ_TIMEOUTS_WHILE_STREAMING = savedMaxTimeouts
        CameraService.IDLE_HEALTH_CHECK_INTERVAL_MS = savedIdleInterval
        CameraService.IDLE_HEALTH_CHECK_TIMEOUT_MS = savedIdleTimeout
    }

    // --- connect / disconnect ---

    @Test
    fun connectSucceedsWhenServerAccepts() = runBlocking {
        val connected = cameraService.connect()
        assertTrue(connected)
        assertTrue(cameraService.isConnected)
        assertTrue(server.awaitConnection(2_000))
    }

    @Test
    fun connectFailsWhenNothingIsListening() = runBlocking {
        server.close() // free port 5001 before this test's own connect attempt
        val connected = cameraService.connect()
        assertFalse(connected)
        assertFalse(cameraService.isConnected)
    }

    @Test
    fun disconnectDoesNotEmitConnectionLostSignal() = runBlocking {
        cameraService.connect()
        server.awaitConnection()
        var lostSignalFired = false
        val disposable = cameraService.getConnectionLostSignal().subscribe { lostSignalFired = true }
        cameraService.disconnect()
        delay(300L)
        assertFalse(cameraService.isConnected)
        // A user-initiated disconnect is a DIFFERENT signal from an unexpected drop -- the
        // ViewModel relies on this distinction to decide whether to auto-reconnect.
        assertFalse("A user-initiated disconnect() must not fire connectionLostSubject", lostSignalFired)
        disposable.dispose()
    }

    // connect() unconditionally fires a `set_time` command in the background right after a
    // successful connect (real cameras just re-accept the phone's clock and ignore it) — the
    // fake server's single-command-at-a-time readNextCommand() needs that drained out of the
    // way first, or it can race ahead of whatever command a test actually cares about.
    private fun connectAndDrainSetTime(): Boolean {
        // connect() is itself implemented via runBlocking (it's a plain blocking function, not
        // suspend) -- call it directly rather than wrapping in another runBlocking, since nested
        // runBlocking layers on the same thread are how the earlier version of this helper ended
        // up pathologically slow (60s+ per test) instead of failing fast or succeeding promptly.
        val connected = cameraService.connect()
        server.awaitConnection()
        val setTimeCmd = server.readNextCommand()
        assertTrue("Expected the automatic set_time command right after connect: $setTimeCmd", setTimeCmd.contains("set_time"))
        return connected
    }

    // --- sendCmd / protocol framing ---

    @Test
    fun sendCmdResolvesOnMatchingResponse() = runBlocking {
        connectAndDrainSetTime()
        val response =
            async(Dispatchers.IO) {
                cameraService.sendCmd(Constants.CMD_GET_STATUS, expectedKey = "status", timeoutMillis = 3_000L)
            }
        val cmd = server.readNextCommand()
        assertTrue(cmd.contains("get_status"))
        server.sendResponse("""{"status":{"connected":true}}""")
        val result = response.await()
        assertFalse(result.has("error"))
        assertTrue(result.getJSONObject("status").getBoolean("connected"))
    }

    @Test
    fun sendCmdTimesOutWhenNoResponseArrives() = runBlocking {
        cameraService.connect()
        server.awaitConnection()
        val result = cameraService.sendCmd(Constants.CMD_GET_STATUS, expectedKey = "status", timeoutMillis = 300L)
        assertTrue(result.has("error"))
    }

    @Test
    fun sendCmdImmediatelyFailsWhenNotConnected() = runBlocking {
        // Never call connect() -- isConnected is false from the start.
        val result = cameraService.sendCmd(Constants.CMD_GET_STATUS, expectedKey = "status", timeoutMillis = 3_000L)
        assertTrue(result.has("error"))
    }

    @Test
    fun parsesResponseSplitAcrossMultipleSocketWrites() = runBlocking {
        connectAndDrainSetTime()
        val response =
            async(Dispatchers.IO) {
                cameraService.sendCmd(Constants.CMD_GET_STATUS, expectedKey = "status", timeoutMillis = 3_000L)
            }
        server.readNextCommand()
        val full = "{\"status\":{\"ok\":true}}"
        val bytes = full.toByteArray(Charsets.UTF_8)
        val splitPoint = bytes.size / 2
        val out = server.output()
        out.write(bytes, 0, splitPoint)
        out.flush()
        delay(150L) // ensure the first chunk is read before the rest arrives
        out.write(bytes, splitPoint, bytes.size - splitPoint)
        out.flush()
        val result = response.await()
        assertFalse(result.has("error"))
        assertTrue(result.getJSONObject("status").getBoolean("ok"))
    }

    @Test
    fun parsesMultipleFramesConcatenatedInOneWrite() = runBlocking {
        connectAndDrainSetTime()

        val imageFrames = mutableListOf<org.json.JSONObject>()
        val imageDisposable = cameraService.getImageChannel().subscribe { imageFrames.add(it) }

        val response =
            async(Dispatchers.IO) {
                cameraService.sendCmd(Constants.CMD_GET_STATUS, expectedKey = "status", timeoutMillis = 3_000L)
            }
        server.readNextCommand()

        // An unsolicited streaming frame immediately followed by the command's real response,
        // both delivered in a SINGLE write -- verifies the frame-boundary parsing (STX/ETX
        // scan + buffer reset) correctly separates them rather than corrupting the second.
        val imageJson = "{\"radiometric\":\"AA==\",\"telemetry\":\"AA==\",\"metadata\":{}}"
        val statusJson = "{\"status\":{\"ok\":true}}"
        val combined = "$imageJson$statusJson"
        server.output().write(combined.toByteArray(Charsets.UTF_8))
        server.output().flush()

        val result = response.await()
        assertFalse(result.has("error"))
        assertTrue(result.getJSONObject("status").getBoolean("ok"))

        // RxJava's onNext dispatch happens on whatever thread called it (here, the socket-
        // reading coroutine) but the subscriber above runs synchronously on that same
        // callback -- still, give it a brief moment in case of scheduling jitter.
        delay(200L)
        assertEquals(1, imageFrames.size)
        assertTrue(imageFrames[0].has("radiometric"))
        imageDisposable.dispose()
    }

    // --- unexpected-disconnect detection ---

    @Test
    fun eofFromCameraTearsDownAndEmitsConnectionLost() = runBlocking {
        cameraService.connect()
        server.awaitConnection()
        var lostSignalFired = false
        val disposable = cameraService.getConnectionLostSignal().subscribe { lostSignalFired = true }
        server.closeConnection() // simulate the camera cleanly hanging up
        delay(500L)
        assertFalse(cameraService.isConnected)
        assertTrue("EOF from the camera should be treated as an unexpected disconnect", lostSignalFired)
        disposable.dispose()
    }

    @Test
    fun disconnectsAfterConsecutiveReadTimeoutsWhileStreaming() = runBlocking {
        // Short-circuit the production 12s/2-timeout (24s) wait down to well under a second
        // so this test exercises the REAL issue #26 logic instead of a slow approximation of
        // it. soTimeout is read from this field inside connect(), so it must be set first.
        CameraService.SOCKET_READ_TIMEOUT_MS = 200
        CameraService.MAX_CONSECUTIVE_READ_TIMEOUTS_WHILE_STREAMING = 2

        cameraService.connect()
        server.awaitConnection()

        var lostSignalFired = false
        val disposable = cameraService.getConnectionLostSignal().subscribe { lostSignalFired = true }

        // startStreaming() sets the isStreaming flag (the only thing this path checks) as a
        // side effect; its own stream_on command is left unanswered, which is fine -- it
        // times out independently on sendCmd's own default timeout and doesn't affect this
        // read-timeout-counting path.
        cameraService.startStreaming()

        // The fake server never sends anything else, so every read() times out. Wait long
        // enough for 2 consecutive 200ms timeouts (400ms) plus scheduling slack.
        delay(1_500L)

        assertFalse(cameraService.isConnected)
        assertTrue(
            "Repeated read timeouts while streaming should be treated as a dead connection",
            lostSignalFired,
        )
        disposable.dispose()
    }

    @Test
    fun readTimeoutsWhileNotStreamingDoNotDisconnect() = runBlocking {
        // Same short timeout as above, but WITHOUT calling startStreaming() -- an idle link
        // going quiet (e.g. camera modem-sleep) must not be treated as dead by this path;
        // that's what the separate idle health check below is for.
        CameraService.SOCKET_READ_TIMEOUT_MS = 200
        CameraService.MAX_CONSECUTIVE_READ_TIMEOUTS_WHILE_STREAMING = 2
        CameraService.IDLE_HEALTH_CHECK_INTERVAL_MS = 60_000L // keep the idle check out of this test's way

        cameraService.connect()
        server.awaitConnection()

        var lostSignalFired = false
        val disposable = cameraService.getConnectionLostSignal().subscribe { lostSignalFired = true }

        delay(1_500L) // well past several read-timeout cycles

        assertTrue(cameraService.isConnected)
        assertFalse(lostSignalFired)
        disposable.dispose()
    }

    // --- idle health check ---

    @Test
    fun idleHealthCheckDisconnectsWhenCameraStopsResponding() = runBlocking {
        CameraService.IDLE_HEALTH_CHECK_INTERVAL_MS = 200L
        CameraService.IDLE_HEALTH_CHECK_TIMEOUT_MS = 200L

        cameraService.connect()
        server.awaitConnection()

        var lostSignalFired = false
        val disposable = cameraService.getConnectionLostSignal().subscribe { lostSignalFired = true }

        // Never respond to the health check's get_status -- simulates a silently dead link
        // (no FIN/RST) on an otherwise-idle connection.
        delay(1_000L)

        assertFalse(cameraService.isConnected)
        assertTrue(
            "An unanswered idle health check should be treated as a dead connection",
            lostSignalFired,
        )
        disposable.dispose()
    }

    @Test
    fun idleHealthCheckDoesNotDisconnectWhenCameraResponds() = runBlocking {
        CameraService.IDLE_HEALTH_CHECK_INTERVAL_MS = 200L
        CameraService.IDLE_HEALTH_CHECK_TIMEOUT_MS = 500L

        cameraService.connect()
        server.awaitConnection()

        var lostSignalFired = false
        val disposable = cameraService.getConnectionLostSignal().subscribe { lostSignalFired = true }

        val responderThread =
            Thread {
                repeat(3) {
                    try {
                        val cmd = server.readNextCommand()
                        if (cmd.contains("get_status")) server.sendResponse("{\"status\":{\"ok\":true}}")
                    } catch (_: Exception) {
                        // Socket closed by teardown -- fine, the test is over by then.
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

        delay(900L) // spans several health-check intervals

        assertTrue(cameraService.isConnected)
        assertFalse(lostSignalFired)
        responderThread.interrupt()
        disposable.dispose()
    }

    @Test
    fun idleHealthCheckDoesNotRunWhileStreaming() = runBlocking {
        // If the idle health check fired while streaming, its unanswered get_status would
        // eventually disconnect a perfectly healthy streaming link -- it must no-op instead
        // and let the streaming-timeout path (tested above) own disconnect detection there.
        CameraService.IDLE_HEALTH_CHECK_INTERVAL_MS = 200L
        CameraService.IDLE_HEALTH_CHECK_TIMEOUT_MS = 200L
        CameraService.SOCKET_READ_TIMEOUT_MS = 5_000 // keep the streaming-timeout path out of this test's way

        cameraService.connect()
        server.awaitConnection()
        cameraService.startStreaming()

        var lostSignalFired = false
        val disposable = cameraService.getConnectionLostSignal().subscribe { lostSignalFired = true }

        delay(1_000L) // several idle-health-check intervals' worth, all skipped while streaming

        assertTrue(cameraService.isConnected)
        assertFalse(lostSignalFired)
        disposable.dispose()
    }
}
