package com.das.tcamviewer2.model

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.das.tcamviewer2.cameraService
import com.das.tcamviewer2.settingsDataManager
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

class CameraViewModel : ViewModel() {

    private val _spotmeterTemp = MutableStateFlow("--")
    val spotmeterTemp: StateFlow<String> = _spotmeterTemp.asStateFlow()

    private val _maxTemp = MutableStateFlow("--")
    val maxTemp: StateFlow<String> = _maxTemp.asStateFlow()

    private val _minTemp = MutableStateFlow("--")
    val minTemp: StateFlow<String> = _minTemp.asStateFlow()

    private val _fpsCounter = MutableStateFlow("-- fps")
    val fpsCounter: StateFlow<String> = _fpsCounter.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _currentPalette = MutableStateFlow("Rainbow")
    val currentPalette: StateFlow<String> = _currentPalette.asStateFlow()

    private val _histogram = MutableStateFlow<IntArray?>(null)
    val histogram: StateFlow<IntArray?> = _histogram.asStateFlow()

    private val _currentImageDto = MutableStateFlow<ImageDto?>(null)
    val currentImageDto: StateFlow<ImageDto?> = _currentImageDto.asStateFlow()

    // CONFLATED: only keeps the latest frame; old frames are dropped when processing falls behind
    private val frameChannel = Channel<JSONObject>(Channel.CONFLATED)
    private var frameDisposable: Disposable? = null

    // Cached settings — updated reactively, read synchronously during frame processing
    private var isCelsius = true
    private var selectedPalette = "Rainbow"
    private var isManualRange = false
    private var manualMin = 0f
    private var manualMax = 100f

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
        connectAndStream()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataManager.temperatureUnitFlow.collect { isCelsius = (it == "Celsius") }
        }
        viewModelScope.launch {
            settingsDataManager.selectedPaletteFlow.collect {
                selectedPalette = it
                _currentPalette.value = it
            }
        }
        viewModelScope.launch { settingsDataManager.manualRangeFlow.collect { isManualRange = it } }
        viewModelScope.launch { settingsDataManager.minValueFlow.collect { manualMin = it.toFloatOrNull() ?: 0f } }
        viewModelScope.launch { settingsDataManager.maxValueFlow.collect { manualMax = it.toFloatOrNull() ?: 100f } }
    }

    private fun connectAndStream() {
        viewModelScope.launch(Dispatchers.IO) {
            connectToCamera(settingsDataManager.getCameraIp())
        }
        viewModelScope.launch {
            settingsDataManager.cameraIpFlow
                .drop(1)
                .distinctUntilChanged()
                .collect { ip -> withContext(Dispatchers.IO) { connectToCamera(ip) } }
        }
    }

    private fun connectToCamera(ip: String) {
        cameraService.disconnect()
        cameraService.setIpAddress(ip)
        val connected = cameraService.connect()
        _isConnected.value = connected
        _isStreaming.value = false
        if (connected) cameraService.getImage()
    }

    // --- Public actions called from the UI ---

    fun toggleConnection() {
        if (_isConnected.value) {
            cameraService.disconnect()
            _isConnected.value = false
            _isStreaming.value = false
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                connectToCamera(settingsDataManager.getCameraIp())
            }
        }
    }

    fun getImage() {
        if (_isConnected.value) cameraService.getImage()
    }

    fun toggleStreaming() {
        if (_isStreaming.value) {
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

    fun setPalette(name: String) {
        viewModelScope.launch { settingsDataManager.saveSelectedPalette(name) }
    }

    // --- Frame processing ---

    private suspend fun processFrame(json: JSONObject) {
        if (!json.has("radiometric")) return
        try {
            val dto = ImageDto.create(json, selectedPalette, isManualRange, manualMin, manualMax, isCelsius)
            _currentImageDto.value = dto
            _currentBitmap.value = dto.bitmap
            _histogram.value = dto.histogram
            if (dto.tLinearEnabled != 0) {
                val scale = if (dto.tLinearResolution == 0) 10f else 100f
                _spotmeterTemp.value = formatTemp(dto.spotmeterMean, scale)
                _maxTemp.value = formatTemp(dto.maxTemperature, scale)
                _minTemp.value = formatTemp(dto.minTemperature, scale)
            }
            updateFps()
        } catch (e: Exception) {
            Timber.e(e, "Frame processing error")
        }
    }

    private fun formatTemp(rawValue: Int, scale: Float): String {
        val tempC = rawValue / scale - 273.15f
        return if (isCelsius) "%.1f°C".format(tempC)
        else "%.1f°F".format(tempC * 9f / 5f + 32f)
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
