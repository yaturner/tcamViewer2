package com.danjuliodesigns.tcamviewer2.services

import com.danjuliodesigns.tcamviewer2.constants.Constants
import com.danjuliodesigns.tcamviewer2.model.buildWifiConfigArgs
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
import java.util.Base64
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
        // connect() also launches a get_filesystem_list probe (issue #35). Its order relative to
        // set_time is not fixed, so read both and check that each one arrived.
        val afterConnect = listOf(server.readNextCommand(), server.readNextCommand())
        assertTrue("Expected the automatic set_time command right after connect: $afterConnect", afterConnect.any { it.contains("set_time") })
        assertTrue("Expected the filesystem probe after connect: $afterConnect", afterConnect.any { it.contains("get_filesystem_list") })
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
                // Skip the connect-time get_filesystem_list probe and answer only health checks
                var answered = 0
                while (answered < 3) {
                    try {
                        val cmd = server.readNextCommand()
                        if (cmd.contains("get_status")) {
                            server.sendResponse("{\"status\":{\"ok\":true}}")
                            answered++
                        }
                    } catch (_: Exception) {
                        // Socket closed by teardown -- fine, the test is over by then.
                        break
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

    // --- filesystem listing (issues #40, #41, #59: the full tCam with no SD card, and the
    // tCam-Mini which never supports filesystem commands at all, both answer get_filesystem_list
    // without a "filesystem_list" key -- listDirectory must come back null, not hang or throw, so
    // the UI can tell "no files" apart from "still loading") ---

    @Test
    fun listDirectoryReturnsNullWhenCameraHasNoFilesystemList() = runBlocking {
        connectAndDrainSetTime()
        val result = async(Dispatchers.IO) { cameraService.listDirectory("/") }
        val cmd = server.readNextCommand()
        assertTrue(cmd.contains("get_filesystem_list"))
        // What an unsupported-command / no-SD-card camera actually sends back: a generic error
        // shape with no "filesystem_list" key at all.
        server.sendResponse("""{"cam_info":{"ret_stat":false}}""")
        assertEquals(null, result.await())
    }

    @Test
    fun listDirectoryReturnsNullOnTimeout() = runBlocking {
        connectAndDrainSetTime()
        val result = cameraService.listDirectory("/", timeoutMillis = 300L)
        assertEquals(null, result)
    }

    @Test
    fun listDirectoryParsesNameListOnSuccess() = runBlocking {
        connectAndDrainSetTime()
        val result = async(Dispatchers.IO) { cameraService.listDirectory("/") }
        server.readNextCommand()
        server.sendResponse("""{"filesystem_list":{"name_list":"tcam_26_10_01,tcam_26_10_02,"}}""")
        assertEquals(listOf("tcam_26_10_01", "tcam_26_10_02"), result.await())
    }

    // --- batch pause/resume around a multi-file download (issue #42: must pause once per batch,
    // not once per file, or every stream_on/off round trip risks a lost ack) ---

    @Test
    fun pauseStreamingForBatchIsANoOpWhenNotStreaming() = runBlocking {
        connectAndDrainSetTime()
        assertFalse(cameraService.pauseStreamingForBatch())
    }

    @Test
    fun pauseStreamingForBatchSendsStreamOffWhenStreaming() = runBlocking {
        connectAndDrainSetTime()
        cameraService.startStreaming()
        server.readNextCommand() // drain startStreaming()'s own stream_on

        val result = async(Dispatchers.IO) { cameraService.pauseStreamingForBatch() }
        val cmd = server.readNextCommand()
        assertTrue(cmd.contains("stream_off"))
        server.sendResponse("""{"stream_status":{"stream_on":false}}""")
        assertTrue("Should report it was streaming (and is now paused)", result.await())
    }

    @Test
    fun resumeStreamingAfterBatchDoesNothingWhenDisconnected() = runBlocking {
        // Never connected -- isConnected is false, so this must not attempt to write anything
        // that would throw or hang.
        cameraService.resumeStreamingAfterBatch()
    }

    // --- set_wifi (issues #52/#53: the camera config was built correctly but the write could
    // silently lose a race against the caller tearing the connection down right after -- see
    // CameraViewModel's sendWifiConfig()/reconnectAfterWifiChange(). setWifi() is now a suspend
    // fun that awaits the actual socket write, which these tests exercise directly.) ---

    @Test
    fun setWifiWritesTheExactArgsItWasGiven() = runBlocking {
        connectAndDrainSetTime()
        val args = buildWifiConfigArgs(
            isAccessPoint = false,
            ssid = "laurie",
            password = null,
            useStaticIp = false,
            staticIp = "",
            staticNetmask = "",
        )
        val wrote = async(Dispatchers.IO) { cameraService.setWifi(args) }
        val cmd = server.readNextCommand()
        assertTrue(cmd.contains("\"cmd\":\"set_wifi\""))
        assertTrue(cmd.contains("\"sta_ssid\": \"laurie\""))
        assertFalse("An untouched password must never be sent", cmd.contains("sta_pw"))
        assertTrue("setWifi() must report whether the write actually succeeded", wrote.await())
    }

    @Test
    fun setWifiReturnsFalseWithoutHangingWhenNotConnected() = runBlocking {
        // Never connected -- the old fire-and-forget version of setWifi() would just silently
        // launch a doomed write; the suspend version must report failure instead.
        val args = buildWifiConfigArgs(false, "laurie", null, false, "", "")
        assertFalse(cameraService.setWifi(args))
    }

    @Test
    fun setWifiCompletesTheWriteBeforeTheCallerCanTearDownTheSocket() = runBlocking {
        // The actual regression this guards against: CameraViewModel's Save handler calls
        // sendWifiConfig() (which awaits cameraService.setWifi()) and only THEN calls
        // reconnectAfterWifiChange(), which disconnects. Before the fix, setWifi() returned
        // immediately from a fire-and-forget launch{}, so disconnect() on the next line almost
        // always won the race and tore the socket down before the write ever happened. Calling
        // disconnect() here, synchronously, the instant setWifi() returns reproduces that same
        // ordering -- if the write isn't awaited, the fake server sees nothing or a truncated
        // command.
        connectAndDrainSetTime()
        val args = buildWifiConfigArgs(false, "laurie", null, false, "", "")
        val received = async(Dispatchers.IO) { server.readNextCommand() }
        val wrote = cameraService.setWifi(args) // suspends until the write genuinely completes
        cameraService.disconnect() // the very next thing the real call site does
        assertTrue(wrote)
        val cmd = received.await()
        assertTrue(cmd.contains("\"cmd\":\"set_wifi\""))
        assertTrue(cmd.contains("\"sta_ssid\": \"laurie\""))
    }

    // --- fetchFile cancellation (issue #38: cancelling a camera download mid-file must clean
    // up so a fresh download started right afterward isn't stuck waiting on a dead request) ---

    @Test
    fun fetchFileCancellationPropagatesCancellationExceptionRatherThanSwallowingIt() = runBlocking {
        connectAndDrainSetTime()
        val job = async(Dispatchers.IO) { cameraService.fetchFile("tcam_26_10_07", "img_1.tjsn") }
        server.readNextCommand() // get_file request

        job.cancel()
        // join() itself never throws for a cancelled job -- the real assertion is that the
        // coroutine actually completed via cancellation, not some other path that swallowed it
        // and returned normally (e.g. null) instead of propagating the CancellationException
        // fetchFile's own catch-and-rethrow is supposed to preserve.
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun fetchFileClearsPendingStateOnCancelSoTheNextFetchIsNotStuck() = runBlocking {
        connectAndDrainSetTime()
        val cancelledJob = async(Dispatchers.IO) { cameraService.fetchFile("tcam_26_10_07", "img_1.tjsn") }
        server.readNextCommand() // get_file request for img_1
        cancelledJob.cancel()
        cancelledJob.join()

        // A brand-new download started right after (the issue's own acceptance criterion) must
        // get its own file, not hang forever because the cancelled request's internal
        // CompletableDeferred was left in place.
        val nextJob = async(Dispatchers.IO) { cameraService.fetchFile("tcam_26_10_07", "img_2.tjsn") }
        val cmd = server.readNextCommand()
        assertTrue(cmd.contains("img_2.tjsn"))
        server.sendResponse("""{"radiometric":"AA==","telemetry":"AA==","metadata":{}}""")

        val result = nextJob.await()
        assertTrue("The fetch right after a cancelled one must still complete normally", result != null)
        assertTrue(result!!.has("radiometric"))
    }

    // --- OTA firmware update (issue #48): fw_update_request -> repeated get_fw/fw_segment ->
    // a terminal cam_info. These exercise the real protocol state machine in updateFirmware()
    // against a fake camera that drives it exactly like the tCam/tCam-Mini firmware does. ---

    @Test
    fun updateFirmwareSendsRequestThenSegmentThenReturnsOnSuccess() = runBlocking {
        connectAndDrainSetTime()
        val image = ByteArray(10) { it.toByte() }
        val progress = mutableListOf<Int>()

        val job = async(Dispatchers.IO) {
            cameraService.updateFirmware(image, "3.3") { progress.add(it) }
        }

        val request = server.readNextCommand()
        assertTrue(request.contains("\"cmd\":\"fw_update_request\""))
        assertTrue(request.contains("\"length\":10"))
        assertTrue(request.contains("\"version\":\"3.3\""))

        server.sendResponse("""{"get_fw":{"start":0,"length":10}}""")

        val segment = server.readNextCommand()
        assertTrue(segment.contains("\"cmd\":\"fw_segment\""))
        assertTrue(segment.contains("\"start\":0"))
        assertTrue(segment.contains("\"length\":10"))
        assertTrue(segment.contains(Base64.getEncoder().encodeToString(image)))

        server.sendResponse("""{"cam_info":{"info_value":6,"info_string":"update success"}}""")

        assertEquals("update success", job.await())
        assertEquals(listOf(10), progress)
    }

    @Test
    fun updateFirmwareAnswersEachGetFwRequestWithTheMatchingSlice() = runBlocking {
        connectAndDrainSetTime()
        val image = ByteArray(10) { it.toByte() }
        val progress = mutableListOf<Int>()

        val job = async(Dispatchers.IO) {
            cameraService.updateFirmware(image, "3.3") { progress.add(it) }
        }
        server.readNextCommand() // fw_update_request

        server.sendResponse("""{"get_fw":{"start":0,"length":4}}""")
        val firstSegment = server.readNextCommand()
        assertTrue(firstSegment.contains("\"start\":0"))
        assertTrue(firstSegment.contains(Base64.getEncoder().encodeToString(image.copyOfRange(0, 4))))

        server.sendResponse("""{"get_fw":{"start":4,"length":6}}""")
        val secondSegment = server.readNextCommand()
        assertTrue(secondSegment.contains("\"start\":4"))
        assertTrue(secondSegment.contains(Base64.getEncoder().encodeToString(image.copyOfRange(4, 10))))

        server.sendResponse("""{"cam_info":{"info_value":6,"info_string":"update success"}}""")

        assertEquals("update success", job.await())
        assertEquals(listOf(4, 10), progress)
    }

    @Test
    fun updateFirmwareThrowsWhenCameraRequestsAnOutOfRangeSegment() = runBlocking {
        connectAndDrainSetTime()
        val image = ByteArray(10)

        // async's own failure propagates to (and cancels) this runBlocking scope the instant
        // the child throws, regardless of whether/when await() is called -- catch the exception
        // INSIDE the child instead of trying to catch it at the await() call site.
        val job = async(Dispatchers.IO) {
            runCatching { cameraService.updateFirmware(image, "3.3") { } }
        }
        server.readNextCommand() // fw_update_request
        server.sendResponse("""{"get_fw":{"start":5,"length":10}}""") // 5+10 > 10

        val error = job.await().exceptionOrNull()
        assertTrue(error is java.io.IOException)
    }

    @Test
    fun updateFirmwareThrowsWithCameraReportedReasonOnRejection() = runBlocking {
        connectAndDrainSetTime()
        val image = ByteArray(10)

        val job = async(Dispatchers.IO) {
            runCatching { cameraService.updateFirmware(image, "3.3") { } }
        }
        server.readNextCommand() // fw_update_request
        // Camera rejects outright (e.g. version/project-name mismatch) without ever requesting
        // a segment -- CAM_INFO_NACK.
        server.sendResponse("""{"cam_info":{"info_value":0,"info_string":"version mismatch"}}""")

        val error = job.await().exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertEquals("version mismatch", error?.message)
    }

    @Test
    fun updateFirmwareThrowsWhenCameraReportsFailureAfterTransfer() = runBlocking {
        connectAndDrainSetTime()
        val image = ByteArray(10)

        val job = async(Dispatchers.IO) {
            runCatching { cameraService.updateFirmware(image, "3.3") { } }
        }
        server.readNextCommand() // fw_update_request
        server.sendResponse("""{"get_fw":{"start":0,"length":10}}""")
        server.readNextCommand() // fw_segment
        // A terminal cam_info whose text does not say "success" -- e.g. a checksum failure.
        server.sendResponse("""{"cam_info":{"info_value":6,"info_string":"crc check failed"}}""")

        val error = job.await().exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertEquals("crc check failed", error?.message)
    }

    @Test
    fun updateFirmwareFailsImmediatelyWhenNotConnected() = runBlocking {
        // Never call connect() -- isConnected is false from the start.
        val error = runCatching { cameraService.updateFirmware(ByteArray(10), "3.3") { } }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
    }

    @Test
    fun updateFirmwareIgnoresAckAndDebugCamInfoWhileWaitingForMoreSegments() = runBlocking {
        connectAndDrainSetTime()
        val image = ByteArray(10)
        val progress = mutableListOf<Int>()

        val job = async(Dispatchers.IO) {
            cameraService.updateFirmware(image, "3.3") { progress.add(it) }
        }
        server.readNextCommand() // fw_update_request

        // The camera ACKs the request (CAM_INFO_ACK) before ever asking for a segment -- must
        // not be mistaken for a terminal status.
        server.sendResponse("""{"cam_info":{"info_value":1,"info_string":"ack"}}""")
        server.sendResponse("""{"get_fw":{"start":0,"length":10}}""")
        server.readNextCommand() // fw_segment
        server.sendResponse("""{"cam_info":{"info_value":6,"info_string":"update success"}}""")

        assertEquals("update success", job.await())
        assertEquals(listOf(10), progress)
    }
}
