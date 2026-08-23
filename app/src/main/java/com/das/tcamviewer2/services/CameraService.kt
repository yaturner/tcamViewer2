package com.das.tcamviewer2.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.das.tcamviewer2.constants.Constants
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class CameraService : Service() {
    companion object {
        // Bounds how long a read() call blocks so the listening loop stays responsive to
        // disconnect()/stopListening() instead of sitting in a blocking syscall indefinitely —
        // important on the flaky WiFi links this app talks to, where the camera can go quiet
        // for a while (modem-sleep) without that meaning the connection actually died.
        private const val SOCKET_READ_TIMEOUT_MS = 30_000
    }

    private var cameraSocket: Socket? = null
    private var isStreaming = false
    private var ipAddress: String? = null

    // java.net.Socket's own isConnected()/isClosed() only reflect whether connect()/close()
    // were ever called — they don't track whether the link is actually still alive. A silent
    // WiFi drop leaves isConnected()==true and isClosed()==false indefinitely, which is exactly
    // the "app thinks it's connected but every command times out" failure mode this app has
    // hit repeatedly. Track our own flag instead, flipped the moment any read/write actually
    // fails, and use it (not the Socket's) as the source of truth for isConnected.
    @Volatile private var connectedFlag = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null

    // --- NEW: A thread-safe map tracking pending requests awaiting responses ---
    // Key: Command Type/ID string, Value: The deferred handler wrapper returning a JSONObject
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    // Resolved with the next radiometric frame; used by getImageOnce() for time lapse capture
    @Volatile private var singleImageDeferred: CompletableDeferred<JSONObject>? = null

    // Volatile: read from the listening-loop coroutine, written from disconnect() on whatever
    // coroutine calls it — needs to be visible across threads so a read failure caused by our
    // own deliberate teardown isn't mistaken for an unexpected drop (see notifyConnectionLost).
    @Volatile private var running = false
    private var bytesRead = 0
    private var responsePos = 0

    private var inFromSocket: InputStream? = null
    private var outToSocket: OutputStream? = null

    private var readBuffer = ByteArray(Constants.BUFFER_LENGTH)
    private var response = ByteArray(Constants.BUFFER_LENGTH)
    private var startFound = false

    // Create the private pipeline where the socket loop dumps raw data
    private val imageChannel = PublishSubject.create<JSONObject>()

    // Emits when a previously-good connection dies on its own (read/write failure, camera-side
    // close) — as opposed to disconnect(), which is the user/ViewModel asking to disconnect.
    // Lets the ViewModel distinguish "the user meant to disconnect" from "we should try to
    // reconnect automatically" without polling isConnected.
    private val connectionLostSubject = PublishSubject.create<Unit>()

    fun getConnectionLostSignal(): Observable<Unit> = connectionLostSubject.hide()

    // Binder setup that gives the ViewModel access to this service instance
    private val binder = CameraServiceBinder()

    // EXPOSE THE METHOD HERE (This is what your ViewModel is calling!)
    fun getImageChannel(): Observable<JSONObject> {
        return imageChannel.hide()
        // .hide() is an RxJava best-practice that prevents external classes
        // from calling .onNext() and tampering with your stream directly.
    }

    inner class CameraServiceBinder : Binder() {
        val service: CameraService
            get() = this@CameraService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        readBuffer = ByteArray(Constants.BUFFER_LENGTH)
        response = ByteArray(Constants.BUFFER_LENGTH)
        resetBuffers()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // Fail any incomplete pending calls gracefully during termination
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        disconnect()
        super.onDestroy()
    }

    fun setIpAddress(address: String) {
        if (isConnected) disconnect()
        ipAddress = address
    }

    fun getIpAddress(): String? = ipAddress

    fun connect(): Boolean = runBlocking {
        // Tear down anything left over from a previous attempt first — reconnecting without
        // this risked leaking the old socket's file descriptor and leaving stale stream
        // references around if a prior connect/read had failed without fully cleaning up.
        // Closing the socket/streams is also what unblocks the old listening loop if it's
        // sitting in a blocking read() (cancellation alone is cooperative and won't interrupt
        // that); cancelAndJoin() then waits for it to actually exit before we reset the shared
        // parse buffers and start a new loop — otherwise the old and new loops could briefly
        // run concurrently and corrupt frame parsing by mutating those buffers together.
        running = false
        teardownConnection()
        listeningJob?.cancelAndJoin()
        listeningJob = null
        resetBuffers()
        running = true
        val connected =
            withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(java.net.InetSocketAddress(ipAddress, 5001), 5000)
                    socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                    socket.keepAlive = true
                    cameraSocket = socket
                    inFromSocket = socket.getInputStream()
                    outToSocket = socket.getOutputStream()
                    true
                } catch (e: Exception) {
                    Timber.e(e, "connect failed")
                    teardownConnection()
                    false
                }
            }

        connectedFlag = connected
        if (connected) startListening()
        connected
    }

    fun stopListening() {
        running = false
        listeningJob?.cancel()
    }

    fun disconnect() {
        stopStreaming()
        running = false
        // Closing the streams here (not just cancelling the listening coroutine) is what
        // actually unblocks a thread currently sitting in a blocking read() call — coroutine
        // cancellation alone is cooperative and won't interrupt that blocking JVM I/O.
        teardownConnection()
        listeningJob?.cancel()
        failPendingRequests("Disconnected")
    }

    /** Fully tears down the socket and its streams. Always safe to call — even if already
     *  torn down, mid-failure, or never fully connected — so every failure path can call it
     *  unconditionally instead of each needing its own "is there something to clean up" check. */
    private fun teardownConnection() {
        connectedFlag = false
        try {
            cameraSocket?.shutdownInput()
        } catch (_: Exception) {
        }
        try {
            cameraSocket?.shutdownOutput()
        } catch (_: Exception) {
        }
        try {
            inFromSocket?.close()
        } catch (_: Exception) {
        }
        try {
            outToSocket?.close()
        } catch (_: Exception) {
        }
        try {
            cameraSocket?.close()
        } catch (_: Exception) {
        }
        cameraSocket = null
        inFromSocket = null
        outToSocket = null
    }

    /** Immediately fails every in-flight request rather than leaving callers to wait out their
     *  own individual timeouts once we already know the connection is dead. */
    private fun failPendingRequests(reason: String) {
        val error = parseResponse(String.format(Constants.ERROR_RESPONSE, reason))
        pendingRequests.values.forEach { it.complete(error) }
        pendingRequests.clear()
        singleImageDeferred?.let { if (!it.isCompleted) it.complete(error) }
        singleImageDeferred = null
    }

    /** Centralizes the write-then-handle-failure path shared by every fire-and-forget command
     *  (getImage, setConfig, setWifi, ...) — on any write failure, tear the connection down and
     *  fail in-flight requests immediately instead of leaving stale state for the next attempt
     *  to trip over. */
    private fun writeCommand(bytes: ByteArray): Boolean {
        val out = outToSocket
        if (out == null) {
            teardownConnection()
            failPendingRequests("Socket not connected")
            return false
        }
        return try {
            out.write(bytes)
            out.flush()
            true
        } catch (e: IOException) {
            Timber.e(e, "Command write failed — tearing down connection")
            val wasConnected = connectedFlag
            teardownConnection()
            failPendingRequests("Socket write failed: ${e.message}")
            if (wasConnected) connectionLostSubject.onNext(Unit)
            false
        }
    }

    // A suspending function that executes a command AND awaits its response
    suspend fun sendCmd(
        cmd: String,
        expectedKey: String,
        timeoutMillis: Long = 5000L,
    ): JSONObject {
        if (!isConnected) {
            return parseResponse(String.format(Constants.ERROR_RESPONSE, "Socket disconnected"))
        }

        // 1. Create a deferred synchronization point
        val deferredResponse = CompletableDeferred<JSONObject>()
        pendingRequests[expectedKey] = deferredResponse

        return withContext(Dispatchers.IO) {
            try {
                // 2. Transmit bytes down the wire — on failure this tears the connection down
                // and completes deferredResponse (among all pending requests) with an error,
                // so the await() below resolves immediately instead of waiting out the timeout.
                writeCommand(cmd.toByteArray(StandardCharsets.UTF_8))

                // 3. Await resolution with a safety timeout guard wrapper
                withTimeout(timeoutMillis) {
                    deferredResponse.await()
                }
            } catch (e: TimeoutCancellationException) {
                parseResponse(String.format(Constants.ERROR_RESPONSE, "Request timed out matching key: $expectedKey"))
            } finally {
                // Remove the handler from execution scope map memory cleanly
                pendingRequests.remove(expectedKey)
            }
        }
    }

    val isConnected: Boolean
        get() = connectedFlag

    // Example updated caller logic
    fun startStreaming() {
        isStreaming = true
        val args = String.format(Constants.ARGS_SET_STREAM_ON, 0, 0)
        val command = String.format(Constants.CMD_SET_STREAM_ON, args)

        serviceScope.launch {
            // Fires command and catches the response directly inline!
            val response = sendCmd(command, expectedKey = "stream_status")
            Timber.d("Stream started response status: $response")
        }
    }

    fun stopStreaming() {
        isStreaming = false
        serviceScope.launch {
            sendCmd(Constants.CMD_SET_STREAM_OFF, expectedKey = "stream_status")
        }
    }

    fun getImage() {
        serviceScope.launch {
            writeCommand(Constants.CMD_GET_IMAGE.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun runFfc() {
        serviceScope.launch {
            writeCommand(Constants.CMD_RUN_FFC.toByteArray(StandardCharsets.UTF_8))
        }
    }

    suspend fun getImageOnce(timeoutMs: Long = 15_000L): JSONObject? {
        if (!isConnected) return null
        val deferred = CompletableDeferred<JSONObject>()
        singleImageDeferred = deferred
        val sent =
            withContext(Dispatchers.IO) {
                writeCommand(Constants.CMD_GET_IMAGE.toByteArray(StandardCharsets.UTF_8))
            }
        if (!sent) {
            singleImageDeferred = null
            return null
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            singleImageDeferred = null
            null
        }
    }

    fun setSpotmeter(
        c1: Int,
        c2: Int,
        r1: Int,
        r2: Int,
    ) {
        serviceScope.launch {
            val args = String.format(Constants.ARGS_SET_SPOTMETER, c1, c2, r1, r2)
            val cmd = String.format(Constants.CMD_SET_SPOTMETER, args)
            sendCmd(cmd, expectedKey = "set_spotmeter")
        }
    }

    suspend fun getConfig(): JSONObject = sendCmd(Constants.CMD_GET_CONFIG, expectedKey = "config")

    suspend fun getWifi(): JSONObject = sendCmd(Constants.CMD_GET_WIFI, expectedKey = "wifi")

    fun setConfig(
        agcEnabled: Boolean,
        emissivity: Int,
        gainMode: Int,
    ) {
        serviceScope.launch {
            val args = String.format(Constants.ARGS_SET_CONFIG, if (agcEnabled) 1 else 0, emissivity, gainMode)
            val cmd = String.format(Constants.CMD_SET_CONFIG, args)
            writeCommand(cmd.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun setWifi(argsJson: String) {
        serviceScope.launch {
            val cmd = String.format(Constants.CMD_SET_WIFI, argsJson)
            writeCommand(cmd.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun startListening() {
        running = true
        bytesRead = 0

        listeningJob =
            serviceScope.launch {
                val input = inFromSocket ?: return@launch
                while (isConnected && running) {
                    try {
                        bytesRead = input.read(readBuffer)
                    } catch (e: java.net.SocketTimeoutException) {
                        // Just an idle link (e.g. camera modem-sleep) — not necessarily dead.
                        // Looping back re-checks isConnected/running so a concurrent disconnect()
                        // is noticed promptly instead of blocking another full read() cycle.
                        continue
                    } catch (e: java.io.IOException) {
                        Timber.e(e, "Socket read error — tearing down connection")
                        val wasRunning = running
                        running = false
                        teardownConnection()
                        failPendingRequests("Socket read error: ${e.message}")
                        if (wasRunning) connectionLostSubject.onNext(Unit)
                        break
                    }
                    when {
                        bytesRead < 0 -> {
                            Timber.w("Socket read hit EOF — camera closed the connection")
                            val wasRunning = running
                            running = false
                            teardownConnection()
                            failPendingRequests("Camera closed the connection")
                            if (wasRunning) connectionLostSubject.onNext(Unit)
                            break
                        }

                        bytesRead == 0 -> {
                            delay(100)
                            continue
                        }
                    }
                    for (index in 0 until bytesRead) {
                        val b = readBuffer[index]
                        when {
                            b == 0x02.toByte() -> {
                                if (startFound) responsePos = 0 else startFound = true
                            }

                            startFound && b == 0x03.toByte() -> {
                                val parsedJson =
                                    parseResponse(
                                        String(response, 0, responsePos, StandardCharsets.UTF_8),
                                    )
                                if (!routeToPendingRequest(parsedJson)) {
                                    // Resolve a pending single-image capture (time lapse) if waiting
                                    val deferred = singleImageDeferred
                                    if (deferred != null && !deferred.isCompleted && parsedJson.has("radiometric")) {
                                        singleImageDeferred = null
                                        deferred.complete(parsedJson)
                                    }
                                    imageChannel.onNext(parsedJson)
                                }
                                resetBuffers()
                            }

                            startFound -> {
                                if (responsePos < response.size) {
                                    response[responsePos++] = b
                                } else {
                                    resetBuffers()
                                }
                            }
                        }
                    }
                }
            }
    }

    // Inspects JSON keys to resolve waiting requests
    private fun routeToPendingRequest(json: JSONObject): Boolean {
        // Customize this matching logic based on your camera hardware API signature layout.
        // For example, if your socket json includes a "cmd" or "type" indicator field:
        val cmdType = json.optString("cmd", json.optString("type", ""))

        if (pendingRequests.containsKey(cmdType)) {
            pendingRequests[cmdType]?.complete(json)
            return true
        }

        // Secondary fallback checking: Match against explicit keys
        for (key in pendingRequests.keys) {
            if (json.has(key)) {
                pendingRequests[key]?.complete(json)
                return true
            }
        }
        return false
    }

    private fun resetBuffers() {
        responsePos = 0
        startFound = false
    }

    private fun parseResponse(responseString: String?): JSONObject {
        if (responseString == null) return JSONObject()
        return try {
            JSONObject(responseString)
        } catch (e: JSONException) {
            handleError(e)
            JSONObject()
        }
    }

    private fun handleError(e: Exception) {
        // Sentry.captureException(e)
        // mainActivity.getExecutor().shutdown()
    }
}
