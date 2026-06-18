package com.das.tcamviewer2.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.das.tcamviewer2.constants.Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    private val binder: IBinder = CameraServiceBinder()
    private var cameraSocket: Socket? = null
    private var isStreaming = false
    private var ipAddress: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null

    // --- NEW: A thread-safe map tracking pending requests awaiting responses ---
    // Key: Command Type/ID string, Value: The deferred handler wrapper returning a JSONObject
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private var running = false
    private var totalBytesRead = 0
    private var bytesRead = 0
    private var responsePos = 0

    private var inFromSocket: InputStream? = null
    private var outToSocket: OutputStream? = null

    private lateinit var readBuffer: ByteArray
    private lateinit var response: CharArray
    private var startFound = false
    private var endFound = false
    private var prevTime = 0L
    private val _imageFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_LATEST)
    val imageFlow = _imageFlow.asSharedFlow()

    inner class CameraServiceBinder : Binder() {
        val service: CameraService
            get() = this@CameraService
    }

    override fun onCreate() {
        super.onCreate()

        readBuffer = ByteArray(Constants.BUFFER_LENGTH)
        response = CharArray(Constants.BUFFER_LENGTH)
        cameraSocket = Socket()
        resetBuffers()
    }

    override fun onBind(intent: Intent?): IBinder = binder

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
        running = true
        val connected = withContext(Dispatchers.IO) {
            try {
                cameraSocket = Socket(ipAddress, 5001).apply {
                    inFromSocket = getInputStream()
                    outToSocket = getOutputStream()
                }
                true
            } catch (e: Exception) {
                //Sentry.captureException(e)
                cameraSocket = null
                false
            }
        }

        if (connected && isConnected) {
            startListening()
            true
        } else {
            false
        }
    }

    fun stopListening() {
        running = false
        listeningJob?.cancel()
    }

    fun disconnect() {
        if (isConnected) {
            stopStreaming()
            stopListening()
            try {
                cameraSocket?.close()
            } catch (e: IOException) {
                //Sentry.captureException(e)
            } finally {
                cameraSocket = null
            }
        }
    }

    // --- MODIFIED: A suspending function that executes a command AND awaits its response ---
    suspend fun sendCmd(cmd: String, expectedKey: String, timeoutMillis: Long = 5000L): JSONObject {
        if (!isConnected) {
            return parseResponse(String.format(Constants.ERROR_RESPONSE, "Socket disconnected"))
        }

        // 1. Create a deferred synchronization point
        val deferredResponse = CompletableDeferred<JSONObject>()
        pendingRequests[expectedKey] = deferredResponse

        return withContext(Dispatchers.IO) {
            try {
                // 2. Transmit bytes down the wire
                outToSocket?.write(cmd.toByteArray(StandardCharsets.UTF_8))
                outToSocket?.flush()

                // 3. Await resolution with a safety timeout guard wrapper
                withTimeout(timeoutMillis) {
                    deferredResponse.await()
                }
            } catch (e: TimeoutCancellationException) {
                parseResponse(String.format(Constants.ERROR_RESPONSE, "Request timed out matching key: $expectedKey"))
            } catch (e: Exception) {
                cameraSocket = null
                //Sentry.captureException(e)
                parseResponse(String.format(Constants.ERROR_RESPONSE, e.toString()))
            } finally {
                // Remove the handler from execution scope map memory cleanly
                pendingRequests.remove(expectedKey)
            }
        }
    }

    val isConnected: Boolean
        get() = cameraSocket?.let { !it.isClosed && it.isConnected } ?: false

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

    private fun startListening() {
        running = true
        totalBytesRead = 0
        bytesRead = 0

        listeningJob = serviceScope.launch {
            while (isConnected && running) {
                prevTime = SystemClock.elapsedRealtime()
                try {
                    bytesRead = inFromSocket?.read(readBuffer) ?: -1
                } catch (e: java.io.IOException) {
                    if (e.toString().contains("Socket closed", ignoreCase = true)) {
                        running = false
                    }
                    val jsonString = String.format(Constants.ERROR_RESPONSE, e.toString())
                    _imageFlow.tryEmit(parseResponse(jsonString))
                    continue
                }

                if (bytesRead <= 0) {
                    delay(100)
                    continue
                }

                for (index in 0 until bytesRead) {
                    val c = readBuffer[index].toInt().toChar()
                    if (c == '\u0002') {
                        if (startFound) responsePos = 0 else startFound = true
                    } else if (startFound && !endFound && c == '\u0003') {
                        endFound = true
                        if (responsePos < response.size) response[responsePos] = '\u0000'

                        val rawResponseText = String(response, 0, responsePos)
                        val parsedJson = parseResponse(rawResponseText)

                        // --- NEW: ROUTING THE RECEIVED RESPONSE ---
                        // Inspect the JSON keys to match the pending request handler
                        val routed = routeToPendingRequest(parsedJson)

                        if (!routed) {
                            // Fallback: If it's a generic frame or unrequested telemetry, route to Rx image stream
                            _imageFlow.tryEmit(parsedJson)
                        }

                        resetBuffers()
                    } else {
                        if (startFound && !endFound) {
                            if (responsePos < response.size) {
                                response[responsePos] = c
                                responsePos += 1
                            } else {
                                resetBuffers()
                            }
                        }
                        totalBytesRead++
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
        endFound = false
        startFound = false
        totalBytesRead = 0
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
        //Sentry.captureException(e)
        //mainActivity.getExecutor().shutdown()
    }
}