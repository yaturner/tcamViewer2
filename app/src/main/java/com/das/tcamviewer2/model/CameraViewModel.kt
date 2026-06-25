package com.das.tcamviewer2.model

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.das.tcamviewer2.cameraService
import com.das.tcamviewer2.settingsDataManager
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class CameraViewModel : ViewModel() {

    private val _spotmeterTemp = MutableStateFlow("--")
    val spotmeterTemp: StateFlow<String> = _spotmeterTemp.asStateFlow()

    private val _maxTemp = MutableStateFlow("--")
    val maxTemp: StateFlow<String> = _maxTemp.asStateFlow()

    private val _minTemp = MutableStateFlow("--")
    val minTemp: StateFlow<String> = _minTemp.asStateFlow()

    private val _fpsCounter = MutableStateFlow("0 fps")
    val fpsCounter: StateFlow<String> = _fpsCounter.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private var frameDisposable: Disposable? = null
    private var isCelsius = true
    private var selectedPalette = "Rainbow"
    private var frameCount = 0
    private var fpsWindowStart = SystemClock.elapsedRealtime()

    init {
        observeSettings()
        // Subscribe before connecting so no frames are missed
        frameDisposable = cameraService.getImageChannel()
            .subscribe(
                { json -> viewModelScope.launch(Dispatchers.Default) { processFrame(json) } },
                { error -> Timber.e(error, "Frame stream error") }
            )
        connectAndStream()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataManager.temperatureUnitFlow.collect { isCelsius = (it == "Celsius") }
        }
        viewModelScope.launch {
            settingsDataManager.selectedPaletteFlow.collect { selectedPalette = it }
        }
    }

    private fun connectAndStream() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = settingsDataManager.getCameraIp()
            cameraService.setIpAddress(ip)
            val connected = cameraService.connect()
            _isConnected.value = connected
            if (connected) cameraService.startStreaming()
        }
    }

    private suspend fun processFrame(json: JSONObject) {
        if (!json.has("radiometric")) return
        try {
            val dto = ImageDto.create(json, selectedPalette)
            if (dto.tLinearEnabled == 0) return
            val scale = if (dto.tLinearResolution == 0) 10f else 100f
            _currentBitmap.value = dto.bitmap
            _spotmeterTemp.value = formatTemp(dto.spotmeterMean, scale)
            _maxTemp.value = formatTemp(dto.maxTemperature, scale)
            _minTemp.value = formatTemp(dto.minTemperature, scale)
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
        frameCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000L) {
            _fpsCounter.value = "${(frameCount * 1000f / elapsed).toInt()} fps"
            frameCount = 0
            fpsWindowStart = now
        }
    }

    override fun onCleared() {
        super.onCleared()
        frameDisposable?.dispose()
        cameraService.disconnect()
    }
}
