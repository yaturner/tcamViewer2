package com.das.tcamviewer2.model

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.das.tcamviewer2.cameraService
import com.das.tcamviewer2.cameraUtils
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.paletteFactory
import com.das.tcamviewer2.settingsDataManager
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CameraConfig(
    val agcEnabled: Boolean = false,
    val emissivity: Int = 7700,
    val gainMode: Int = Constants.GAIN_MODE_HIGH
)

class CameraViewModel : ViewModel() {

    private val _spotmeterTemp = MutableStateFlow("--")
    val spotmeterTemp: StateFlow<String> = _spotmeterTemp.asStateFlow()

    private val _maxTemp = MutableStateFlow("--")
    val maxTemp: StateFlow<String> = _maxTemp.asStateFlow()

    private val _minTemp = MutableStateFlow("--")
    val minTemp: StateFlow<String> = _minTemp.asStateFlow()

    private val _fpsCounter = MutableStateFlow("-- fps")
    val fpsCounter: StateFlow<String> = _fpsCounter.asStateFlow()

    private val _showConnectError = MutableStateFlow(false)
    val showConnectError: StateFlow<Boolean> = _showConnectError.asStateFlow()

    private val _cameraConfig = MutableStateFlow<CameraConfig?>(null)
    val cameraConfig: StateFlow<CameraConfig?> = _cameraConfig.asStateFlow()

    private val _wifiInfo = MutableStateFlow<Map<String, String>?>(null)
    val wifiInfo: StateFlow<Map<String, String>?> = _wifiInfo.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @Volatile private var recordingStream: FileOutputStream? = null
    @Volatile private var recordingFrameCount: Int = 0
    private var recordingStartMs: Long = 0L
    private var startedStreamingForRecord = false

    private val _isTimeLapsing = MutableStateFlow(false)
    val isTimeLapsing: StateFlow<Boolean> = _isTimeLapsing.asStateFlow()

    private var timeLapseJob: Job? = null

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _currentPalette = MutableStateFlow("Rainbow")
    val currentPalette: StateFlow<String> = _currentPalette.asStateFlow()

    private val _histogram = MutableStateFlow<IntArray?>(null)
    val histogram: StateFlow<IntArray?> = _histogram.asStateFlow()

    private val _currentImageDto = MutableStateFlow<ImageDto?>(null)
    val currentImageDto: StateFlow<ImageDto?> = _currentImageDto.asStateFlow()

    private val _spotmeterRect = MutableStateFlow<Rect?>(null)
    val spotmeterRect: StateFlow<Rect?> = _spotmeterRect.asStateFlow()
    // Once the user manually moves the hotspot, telemetry no longer overwrites it;
    // reset to false on disconnect so the first new frame re-initialises the rect.
    @Volatile private var userMovedSpotmeter = false

    // CONFLATED: only keeps the latest frame; old frames are dropped when processing falls behind
    private val frameChannel = Channel<JSONObject>(Channel.CONFLATED)
    private var frameDisposable: Disposable? = null

    // selectedPalette drives currentPalette StateFlow and is passed as a hint to ImageDto.create
    // @Volatile ensures writes on Main are immediately visible to processFrame on Dispatchers.Default
    @Volatile private var selectedPalette = "Rainbow"

    private var frameCount = 0
    private var fpsWindowStart = -1L   // -1 = not yet started; initialised on first frame

    init {
        observeSettings()
        frameDisposable = cameraService.getImageChannel()
            .subscribe(
                { json -> frameChannel.trySend(json) },
                { error -> Timber.e(error, "Frame stream error") }
            )
        viewModelScope.launch(Dispatchers.Default) {
            for (json in frameChannel) processFrame(json)
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataManager.selectedPaletteFlow.collect { palette ->
                selectedPalette = palette
                _currentPalette.value = palette
                remapCurrentFrame(palette)
            }
        }
        viewModelScope.launch {
            settingsDataManager.manualRangeFlow.collect { v ->
                cameraUtils.settingIsManualRange = v
            }
        }
        viewModelScope.launch {
            settingsDataManager.minValueFlow.collect { v ->
                cameraUtils.settingManualMin = v.toFloatOrNull() ?: 0f
            }
        }
        viewModelScope.launch {
            settingsDataManager.maxValueFlow.collect { v ->
                cameraUtils.settingManualMax = v.toFloatOrNull() ?: 100f
            }
        }
        viewModelScope.launch {
            settingsDataManager.temperatureUnitFlow.collect { v ->
                cameraUtils.settingIsCelsius = (v == "Celsius")
            }
        }
    }

    private suspend fun remapCurrentFrame(paletteName: String) {
        val dto = _currentImageDto.value ?: return
        val palette = paletteFactory.getPaletteByName(paletteName)
        val isManualRange = cameraUtils.settingIsManualRange
        val manualMin = if (isManualRange) cameraUtils.settingManualMin else 0f
        val manualMax = if (isManualRange) cameraUtils.settingManualMax else 0f
        val isCelsius = cameraUtils.settingIsCelsius
        val bmp = withContext(Dispatchers.Default) {
            cameraUtils.remapWithPalette(dto, palette, isManualRange, manualMin, manualMax, isCelsius)
        }
        if (bmp != null) {
            // Write palette name into dto so saveTjsn (which saves dto.getJsonObject()) captures it
            dto.paletteName = paletteName
            _currentBitmap.value = bmp
        }
    }

    private suspend fun connectToCamera(ip: String) {
        Timber.d("connectToCamera ip=$ip")
        _isConnecting.value = true
        try {
            cameraService.disconnect()
            cameraService.setIpAddress(ip)
            val connected = cameraService.connect()
            Timber.d("connectToCamera result=$connected")
            _isConnected.value = connected
            _isStreaming.value = false
            if (connected) {
                cameraService.getImage()
                loadCameraConfig()
            } else _showConnectError.value = true
        } finally {
            _isConnecting.value = false
        }
    }

    // --- Public actions called from the UI ---

    fun toggleConnection() {
        if (_isConnected.value || _isConnecting.value) {
            cameraService.disconnect()
            _isConnected.value = false
            _isConnecting.value = false
            _isStreaming.value = false
            _spotmeterRect.value = null
            _cameraConfig.value = null
            userMovedSpotmeter = false
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                connectToCamera(settingsDataManager.getCameraIp())
            }
        }
    }

    fun dismissConnectError() { _showConnectError.value = false }

    private suspend fun loadCameraConfig() {
        try {
            val response = cameraService.getConfig()
            val config = response.optJSONObject("config") ?: return
            _cameraConfig.value = CameraConfig(
                agcEnabled = config.optInt("agc_enabled") != 0,
                emissivity = config.optInt("emissivity", 7700),
                gainMode = config.optInt("gain_mode", Constants.GAIN_MODE_HIGH)
            )
        } catch (e: Exception) {
            Timber.e(e, "loadCameraConfig failed")
        }
    }

    fun sendCameraConfig(agcEnabled: Boolean, emissivity: Int, gainMode: Int) {
        cameraService.setConfig(agcEnabled, emissivity, gainMode)
    }

    fun fetchWifiInfo() {
        _wifiInfo.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = cameraService.getWifi()
                val wifi = response.optJSONObject("wifi") ?: run {
                    _wifiInfo.value = emptyMap(); return@launch
                }
                _wifiInfo.value = wifi.keys().asSequence().associateWith { wifi.optString(it) }
            } catch (e: Exception) {
                Timber.e(e, "fetchWifiInfo failed")
                _wifiInfo.value = emptyMap()
            }
        }
    }

    fun getImage() {
        if (_isConnected.value) cameraService.getImage()
    }

    fun startTimeLapse(intervalSec: Int, durationSec: Int) {
        if (!_isConnected.value || _isTimeLapsing.value) return
        val intervalMs = intervalSec * 1000L
        val durationMs = durationSec * 1000L
        _isTimeLapsing.value = true
        timeLapseJob = viewModelScope.launch(Dispatchers.IO) {
            var stream: FileOutputStream? = null
            var frameCount = 0
            val startMs = System.currentTimeMillis()
            try {
                stream = cameraUtils.openTimeLapseFile()
                val endTime = startMs + durationMs
                while (isActive && System.currentTimeMillis() < endTime) {
                    val frameStart = System.currentTimeMillis()
                    val json = cameraService.getImageOnce() ?: break
                    stream.write(json.toString().toByteArray(Charsets.US_ASCII))
                    stream.write(0x03)
                    frameCount++
                    val remaining = intervalMs - (System.currentTimeMillis() - frameStart)
                    if (remaining > 0 && isActive) kotlinx.coroutines.delay(remaining)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Time lapse error")
            } finally {
                val endMs = System.currentTimeMillis()
                runCatching {
                    stream?.write(buildFooterJson(startMs, endMs, frameCount).toByteArray(Charsets.US_ASCII))
                    stream?.close()
                }
                _isTimeLapsing.value = false
            }
        }
    }

    fun stopTimeLapse() {
        timeLapseJob?.cancel()
    }

    fun toggleStreaming() {
        if (_isStreaming.value) {
            if (_isRecording.value) finishRecording(stopStreamIfAutoStarted = false)
            cameraService.stopStreaming()
            _isStreaming.value = false
            frameCount = 0
            fpsWindowStart = -1L
            _fpsCounter.value = "-- fps"
        } else {
            frameCount = 0
            fpsWindowStart = -1L
            cameraService.startStreaming()
            _isStreaming.value = true
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            finishRecording()
            return
        }
        if (!_isConnected.value) return
        if (!_isStreaming.value) {
            startedStreamingForRecord = true
            frameCount = 0
            fpsWindowStart = -1L
            cameraService.startStreaming()
            _isStreaming.value = true
        } else {
            startedStreamingForRecord = false
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recordingStream = cameraUtils.openRecordingFile()
                recordingStartMs = System.currentTimeMillis()
                recordingFrameCount = 0
                _isRecording.value = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to open recording file")
                if (startedStreamingForRecord) {
                    cameraService.stopStreaming()
                    _isStreaming.value = false
                    startedStreamingForRecord = false
                }
            }
        }
    }

    private fun finishRecording(stopStreamIfAutoStarted: Boolean = true) {
        val stream = recordingStream
        recordingStream = null
        val count = recordingFrameCount
        val endMs = System.currentTimeMillis()
        _isRecording.value = false
        viewModelScope.launch(Dispatchers.IO) {
            if (stream != null) {
                try {
                    stream.write(buildFooterJson(recordingStartMs, endMs, count).toByteArray(Charsets.US_ASCII))
                    stream.close()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to write recording footer")
                }
            }
            if (stopStreamIfAutoStarted && startedStreamingForRecord) {
                startedStreamingForRecord = false
                cameraService.stopStreaming()
                _isStreaming.value = false
                frameCount = 0
                fpsWindowStart = -1L
                _fpsCounter.value = "-- fps"
            }
        }
    }

    private fun buildFooterJson(startMs: Long, endMs: Long, numFrames: Int): String {
        val timeFmt = SimpleDateFormat("H:mm:ss.SSS", Locale.US)
        val dateFmt = SimpleDateFormat("M/d/yy", Locale.US)
        val start = Date(startMs)
        val end = Date(endMs)
        return """{"video_info":{"start_time":"${timeFmt.format(start)}","start_date":"${dateFmt.format(start)}","end_time":"${timeFmt.format(end)}","end_date":"${dateFmt.format(end)}","num_frames":$numFrames,"version":1}}"""
    }

    fun setPalette(name: String) {
        viewModelScope.launch { settingsDataManager.saveSelectedPalette(name) }
    }

    fun setSpotmeter(camX: Int, camY: Int) {
        val c1 = (camX - 2).coerceAtLeast(0)
        val c2 = (camX + 2).coerceAtMost(Constants.IMAGE_WIDTH - 1)
        val r1 = (camY - 2).coerceAtLeast(0)
        val r2 = (camY + 2).coerceAtMost(Constants.IMAGE_HEIGHT - 1)

        userMovedSpotmeter = true
        _spotmeterRect.value = Rect(c1, r1, c2, r2)

        // Recalculate spotmeter temperature immediately from current imageData
        val dto = _currentImageDto.value
        if (dto?.imageData != null && dto.tLinearEnabled != 0) {
            viewModelScope.launch {
                val scale = if (dto.tLinearResolution == 0) 10f else 100f
                _spotmeterTemp.value = calcSpotTemp(
                    dto.imageData!!, camX, camY, scale, settingsDataManager.isUnitsCelsius()
                )
            }
        }

        cameraService.setSpotmeter(c1, c2, r1, r2)
        if (!_isStreaming.value && _isConnected.value) cameraService.getImage()
    }

    // --- Frame processing ---

    private suspend fun processFrame(json: JSONObject) {
        if (!json.has("radiometric")) return
        try {
            val stream = recordingStream
            if (stream != null) {
                withContext(Dispatchers.IO) {
                    stream.write(json.toString().toByteArray(Charsets.US_ASCII))
                    stream.write(0x03)
                }
                recordingFrameCount++
            }
            val dto = ImageDto.create(json, selectedPalette)
            val celsius = cameraUtils.settingIsCelsius
            _currentImageDto.value = dto
            _currentBitmap.value = dto.bitmap
            _histogram.value = dto.histogram
            if (!userMovedSpotmeter) dto.spotmeterLocation?.let { _spotmeterRect.value = it }
            if (dto.tLinearEnabled != 0) {
                val scale = if (dto.tLinearResolution == 0) 10f else 100f
                val rect = _spotmeterRect.value
                _spotmeterTemp.value = if (rect != null && dto.imageData != null) {
                    val cx = (rect.left + rect.right) / 2
                    val cy = (rect.top + rect.bottom) / 2
                    calcSpotTemp(dto.imageData!!, cx, cy, scale, celsius)
                } else {
                    formatTemp(dto.spotmeterMean, scale, celsius)
                }
                _maxTemp.value = formatTemp(dto.maxTemperature, scale, celsius)
                _minTemp.value = formatTemp(dto.minTemperature, scale, celsius)
            }
            updateFps()
        } catch (e: Exception) {
            Timber.e(e, "Frame processing error")
        }
    }

    private fun formatTemp(rawValue: Int, scale: Float, isCelsius: Boolean): String {
        val tempC = rawValue / scale - 273.15f
        return if (isCelsius) "%.1f°C".format(tempC)
        else "%.1f°F".format(tempC * 9f / 5f + 32f)
    }

    private fun calcSpotTemp(imageData: IntArray, cx: Int, cy: Int, scale: Float, isCelsius: Boolean): String {
        val c1 = cx.coerceIn(0, Constants.IMAGE_WIDTH - 1)
        val c2 = (cx + 1).coerceAtMost(Constants.IMAGE_WIDTH - 1)
        val r1 = cy.coerceIn(0, Constants.IMAGE_HEIGHT - 1)
        val r2 = (cy + 1).coerceAtMost(Constants.IMAGE_HEIGHT - 1)
        var sum = 0L; var count = 0
        for (row in r1..r2) for (col in c1..c2) {
            sum += imageData[row * Constants.IMAGE_WIDTH + col]; count++
        }
        return formatTemp(if (count > 0) (sum / count).toInt() else 0, scale, isCelsius)
    }

    private fun updateFps() {
        val now = SystemClock.elapsedRealtime()
        if (fpsWindowStart < 0L) {
            // First frame after reset: anchor the window here, don't publish yet
            fpsWindowStart = now
            frameCount = 0
            return
        }
        frameCount++
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000L) {
            _fpsCounter.value = "${(frameCount * 1000f / elapsed).toInt()} fps"
            frameCount = 0
            fpsWindowStart = now
        }
    }

    override fun onCleared() {
        super.onCleared()
        frameChannel.close()
        frameDisposable?.dispose()
        cameraService.disconnect()
    }
}
