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

    private var cameraSocket: Socket? = null
    private var isStreaming = false
    private var ipAddress: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null

    // --- NEW: A thread-safe map tracking pending requests awaiting responses ---
    // Key: Command Type/ID string, Value: The deferred handler wrapper returning a JSONObject
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    // Resolved with the next radiometric frame; used by getImageOnce() for time lapse capture
    @Volatile private var singleImageDeferred: CompletableDeferred<JSONObject>? = null

    private var running = false
    private var bytesRead = 0
    private var responsePos = 0

    private var inFromSocket: InputStream? = null
    private var outToSocket: OutputStream? = null

    private var readBuffer = ByteArray(Constants.BUFFER_LENGTH)
    private var response = ByteArray(Constants.BUFFER_LENGTH)
    private var startFound = false
    // Create the private pipeline where the socket loop dumps raw data
    private val imageChannel = PublishSubject.create<JSONObject>()
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
        cameraSocket = Socket()
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
        running = true
        val connected = withContext(Dispatchers.IO) {
            try {
                cameraSocket = Socket().apply {
                    connect(java.net.InetSocketAddress(ipAddress, 5001), 5000)
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

    fun getImage() {
        serviceScope.launch {
            try {
                outToSocket?.write(Constants.CMD_GET_IMAGE.toByteArray(StandardCharsets.UTF_8))
                outToSocket?.flush()
            } catch (e: Exception) {
                Timber.e(e, "getImage failed")
            }
        }
    }

    suspend fun getImageOnce(timeoutMs: Long = 15_000L): JSONObject? {
        if (!isConnected) return null
        val deferred = CompletableDeferred<JSONObject>()
        singleImageDeferred = deferred
        return try {
            withContext(Dispatchers.IO) {
                outToSocket?.write(Constants.CMD_GET_IMAGE.toByteArray(StandardCharsets.UTF_8))
                outToSocket?.flush()
            }
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            singleImageDeferred = null
            null
        }
    }

    fun setSpotmeter(c1: Int, c2: Int, r1: Int, r2: Int) {
        serviceScope.launch {
            val args = String.format(Constants.ARGS_SET_SPOTMETER, c1, c2, r1, r2)
            val cmd = String.format(Constants.CMD_SET_SPOTMETER, args)
            sendCmd(cmd, expectedKey = "set_spotmeter")
        }
    }

    suspend fun getConfig(): JSONObject = sendCmd(Constants.CMD_GET_CONFIG, expectedKey = "config")

    suspend fun getWifi(): JSONObject = sendCmd(Constants.CMD_GET_WIFI, expectedKey = "wifi")

    fun setConfig(agcEnabled: Boolean, emissivity: Int, gainMode: Int) {
        serviceScope.launch {
            try {
                val args = String.format(Constants.ARGS_SET_CONFIG, if (agcEnabled) 1 else 0, emissivity, gainMode)
                val cmd = String.format(Constants.CMD_SET_CONFIG, args)
                outToSocket?.write(cmd.toByteArray(StandardCharsets.UTF_8))
                outToSocket?.flush()
            } catch (e: Exception) {
                Timber.e(e, "setConfig failed")
            }
        }
    }

    private fun startListening() {
        running = true
        bytesRead = 0

        listeningJob = serviceScope.launch {
            val input = inFromSocket ?: return@launch
            while (isConnected && running) {
                try {
                    bytesRead = input.read(readBuffer)
                } catch (e: java.io.IOException) {
                    Timber.e(e, "Socket read error — stopping listener")
                    running = false
                    try { cameraSocket?.close() } catch (_: Exception) {}
                    cameraSocket = null
                    break
                }
                when {
                    bytesRead < 0 -> { running = false; break }
                    bytesRead == 0 -> { delay(100); continue }
                }
                for (index in 0 until bytesRead) {
                    val b = readBuffer[index]
                    when {
                        b == 0x02.toByte() -> {
                            if (startFound) responsePos = 0 else startFound = true
                        }
                        startFound && b == 0x03.toByte() -> {
                            val parsedJson = parseResponse(
                                String(response, 0, responsePos, StandardCharsets.UTF_8)
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
                            if (responsePos < response.size) response[responsePos++] = b
                            else resetBuffers()
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
        //Sentry.captureException(e)
        //mainActivity.getExecutor().shutdown()
    }
}