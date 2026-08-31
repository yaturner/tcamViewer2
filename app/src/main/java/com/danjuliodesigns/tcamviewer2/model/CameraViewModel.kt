package com.danjuliodesigns.tcamviewer2.model

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danjuliodesigns.tcamviewer2.R
import com.danjuliodesigns.tcamviewer2.appContext
import com.danjuliodesigns.tcamviewer2.cameraService
import com.danjuliodesigns.tcamviewer2.cameraUtils
import com.danjuliodesigns.tcamviewer2.constants.Constants
import com.danjuliodesigns.tcamviewer2.paletteFactory
import com.danjuliodesigns.tcamviewer2.settingsDataManager
import com.danjuliodesigns.tcamviewer2.utils.discoverTcamCameras
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class CameraConfig(
    val agcEnabled: Boolean = false,
    val emissivity: Int = 90, // percentage 1-100, per tCam's set_config/get_config API
    val gainMode: Int = Constants.GAIN_MODE_HIGH,
)

/** One point in the rolling temperature-over-time history. Values are in whatever unit was
 *  currently selected when the sample was taken (same as the on-screen spot/max/min text). */
data class TempSample(
    val timestampMs: Long,
    val spot: Float,
    val max: Float,
    val min: Float,
)

/** POINT is today's single-pixel-neighborhood spotmeter; REGION is a user-resizable box showing
 *  avg/min/max within it. Mutually exclusive in the UI — only one overlay/readout at a time. */
enum class MeasurementMode { POINT, REGION }

class CameraViewModel : ViewModel() {
    private val _spotmeterTemp = MutableStateFlow("--")
    val spotmeterTemp: StateFlow<String> = _spotmeterTemp.asStateFlow()

    private val _maxTemp = MutableStateFlow("--")
    val maxTemp: StateFlow<String> = _maxTemp.asStateFlow()

    private val _minTemp = MutableStateFlow("--")
    val minTemp: StateFlow<String> = _minTemp.asStateFlow()

    // Raw values (same unit as the formatted text above) so the UI can position a
    // marker along the color bar without re-parsing the display strings.
    private val _spotmeterTempValue = MutableStateFlow<Float?>(null)
    val spotmeterTempValue: StateFlow<Float?> = _spotmeterTempValue.asStateFlow()

    private val _maxTempValue = MutableStateFlow<Float?>(null)
    val maxTempValue: StateFlow<Float?> = _maxTempValue.asStateFlow()

    private val _minTempValue = MutableStateFlow<Float?>(null)
    val minTempValue: StateFlow<Float?> = _minTempValue.asStateFlow()

    private val _spotmeterEnabled = MutableStateFlow(true)
    val spotmeterEnabled: StateFlow<Boolean> = _spotmeterEnabled.asStateFlow()

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

    @Volatile private var recordingFile: File? = null

    @Volatile private var recordingFrameCount: Int = 0
    private var recordingStartMs: Long = 0L
    private var startedStreamingForRecord = false

    private val _isTimeLapsing = MutableStateFlow(false)
    val isTimeLapsing: StateFlow<Boolean> = _isTimeLapsing.asStateFlow()

    private val _isTimeLapseCapturing = MutableStateFlow(false)
    val isTimeLapseCapturing: StateFlow<Boolean> = _isTimeLapseCapturing.asStateFlow()

    private val _timeLapseMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val timeLapseMessage: SharedFlow<String> = _timeLapseMessage.asSharedFlow()

    private val _alertMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val alertMessage: SharedFlow<String> = _alertMessage.asSharedFlow()

    // Temperature-alert settings, cached from DataStore the same way as the other per-frame
    // hot-path settings above (avoids a suspend read on every frame).
    @Volatile private var alertEnabled = false

    @Volatile private var alertMetric = "Spot"

    // "Spot" | "Max" | "Min"
    @Volatile private var alertComparison = "Above"

    // "Above" | "Below"
    @Volatile private var alertThreshold = 100f

    // Edge-triggered: fires once when the condition first becomes true, not on every frame
    // while it remains true, and rearms once the value crosses back.
    @Volatile private var alertCurrentlyTriggered = false

    private var timeLapseJob: Job? = null

    @Volatile private var discardTimeLapse = false

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

    // Region measurement — mutually exclusive with the point spotmeter above (only one is shown/
    // interactive at a time in the UI); session-only, reset to POINT/null on manual disconnect.
    private val _measurementMode = MutableStateFlow(MeasurementMode.POINT)
    val measurementMode: StateFlow<MeasurementMode> = _measurementMode.asStateFlow()

    private val _measurementRegion = MutableStateFlow<Rect?>(null)
    val measurementRegion: StateFlow<Rect?> = _measurementRegion.asStateFlow()

    private val _regionAvgTemp = MutableStateFlow("--")
    val regionAvgTemp: StateFlow<String> = _regionAvgTemp.asStateFlow()

    private val _regionMinTemp = MutableStateFlow("--")
    val regionMinTemp: StateFlow<String> = _regionMinTemp.asStateFlow()

    private val _regionMaxTemp = MutableStateFlow("--")
    val regionMaxTemp: StateFlow<String> = _regionMaxTemp.asStateFlow()

    private val _isCelsius = MutableStateFlow(true)
    val isCelsius: StateFlow<Boolean> = _isCelsius.asStateFlow()

    // Rolling spot/max/min history for the temperature-over-time chart, trimmed to the last
    // TEMP_HISTORY_WINDOW_MS. Mutated from both the frame-processing dispatcher and the Main
    // dispatcher (a unit-change re-render also records a point), so access is synchronized.
    private val tempHistoryBuffer = ArrayDeque<TempSample>()
    private val _tempHistory = MutableStateFlow<List<TempSample>>(emptyList())
    val tempHistory: StateFlow<List<TempSample>> = _tempHistory.asStateFlow()

    // Once the user manually moves the hotspot, telemetry no longer overwrites it;
    // reset to false on disconnect so the first new frame re-initialises the rect.
    @Volatile private var userMovedSpotmeter = false

    // Tracks the in-flight connectToCamera() attempt so a disconnect (or a newer connect
    // request) can cancel a stale one instead of letting it resolve later and silently
    // resurrect a connection the user already tore down.
    private var connectJob: Job? = null

    // CONFLATED: only keeps the latest frame; old frames are dropped when processing falls behind
    private val frameChannel = Channel<JSONObject>(Channel.CONFLATED)
    private var frameDisposable: Disposable? = null
    private var connectionLostDisposable: Disposable? = null

    // selectedPalette drives currentPalette StateFlow and is passed as a hint to ImageDto.create
    // @Volatile ensures writes on Main are immediately visible to processFrame on Dispatchers.Default
    @Volatile private var selectedPalette = "Rainbow"

    private var frameCount = 0
    private var fpsWindowStart = -1L // -1 = not yet started; initialised on first frame

    private val tempHistoryWindowMs = 5 * 60_000L // keep the last 5 minutes of samples

    // Widened to the full requested duration while a time lapse is running (time lapses can now
    // run up to 24 hours), so the chart auto-saved on completion covers the whole capture instead
    // of just whatever's left in a 5-minute trailing window. Null outside of an active time lapse,
    // which restores the normal rolling-window behavior for live streaming.
    @Volatile private var tempHistoryWindowOverrideMs: Long? = null

    // 35mm-style shutter click — plays only when the user manually taps the "Get" button
    // (see getImage() below), not for the auto-Get on connect, the spotmeter-drag re-Get,
    // streaming/recording frames, or time-lapse captures — all of which would be constant
    // or surprising noise (https://github.com/yaturner/tcamViewer2/issues/15).
    //
    // Bundled as a PCM WAV (res/raw) and played via MediaPlayer rather than through
    // MediaActionSound/SoundPool: SoundPool has a long-standing bug decoding short OGG Vorbis
    // clips (mishandled codec pre-roll samples) that produced an audible click/thump artifact
    // instead of a clean sound — a plain PCM WAV through MediaPlayer's full decode pipeline
    // sidesteps that entirely.
    @Volatile private var shutterSoundEnabled = true

    // Set right before a manual Get request goes out, consumed by the next processFrame().
    @Volatile private var manualGetPending = false

    private fun playShutterSound() {
        try {
            val player = MediaPlayer.create(appContext, R.raw.camera_shutter)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.start()
        } catch (e: Exception) {
            Timber.e(e, "Shutter sound playback failed")
        }
    }

    init {
        observeSettings()
        frameDisposable =
            cameraService
                .getImageChannel()
                .subscribe(
                    { json -> frameChannel.trySend(json) },
                    { error -> Timber.e(error, "Frame stream error") },
                )
        connectionLostDisposable =
            cameraService
                .getConnectionLostSignal()
                .subscribe(
                    { onConnectionLost() },
                    { error -> Timber.e(error, "Connection-lost signal error") },
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
                _isCelsius.value = (v == "Celsius")
                cameraUtils.settingIsCelsius = (v == "Celsius")
                // Re-render the already-displayed frame in the new unit immediately, rather
                // than waiting for the next Get/stream frame to happen to pick it up.
                refreshTempDisplays()
                remapCurrentFrame(selectedPalette)
            }
        }
        viewModelScope.launch {
            settingsDataManager.spotmeterFlow.collect { v ->
                _spotmeterEnabled.value = v
            }
        }
        viewModelScope.launch {
            settingsDataManager.shutterSoundFlow.collect { v ->
                shutterSoundEnabled = v
            }
        }
        // Region-vs-point mode is a persisted Settings preference rather than a Camera-screen
        // toggle; the region box's own position stays session-only regardless (reset on
        // disconnect, seeded fresh here the first time this session it's needed).
        viewModelScope.launch {
            settingsDataManager.regionMeasurementFlow.collect { enabled ->
                _measurementMode.value = if (enabled) MeasurementMode.REGION else MeasurementMode.POINT
                seedDefaultRegionIfNeeded()
                // Point and region samples aren't comparable on the same chart (whole-frame
                // spot/max/min vs. a box's own avg/min/max), so switching modes starts the
                // rolling history fresh rather than plotting both halves as one continuous line.
                clearTempHistory()
                refreshTempDisplays()
            }
        }
        viewModelScope.launch {
            settingsDataManager.alertEnabledFlow.collect { enabled ->
                alertEnabled = enabled
                alertCurrentlyTriggered = false // re-arm whenever the feature is toggled
            }
        }
        viewModelScope.launch {
            settingsDataManager.alertMetricFlow.collect { alertMetric = it }
        }
        viewModelScope.launch {
            settingsDataManager.alertComparisonFlow.collect { alertComparison = it }
        }
        viewModelScope.launch {
            settingsDataManager.alertThresholdFlow.collect { v ->
                alertThreshold = v.toFloatOrNull() ?: 100f
            }
        }
    }

    /** Seeds a default centered region box the first time it's needed in a session — either
     *  because the setting just turned on, or a fresh connection cleared the previous session's
     *  box while the setting was already on. No-op if a box already exists or REGION isn't active. */
    private fun seedDefaultRegionIfNeeded() {
        if (_measurementMode.value != MeasurementMode.REGION || _measurementRegion.value != null) return
        val w = Constants.IMAGE_WIDTH / 4
        val h = Constants.IMAGE_HEIGHT / 4
        val cx = Constants.IMAGE_WIDTH / 2
        val cy = Constants.IMAGE_HEIGHT / 2
        _measurementRegion.value = Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    private suspend fun remapCurrentFrame(paletteName: String) {
        val dto = _currentImageDto.value ?: return
        val palette = paletteFactory.getPaletteByName(paletteName)
        val isManualRange = cameraUtils.settingIsManualRange
        val manualMin = if (isManualRange) cameraUtils.settingManualMin else 0f
        val manualMax = if (isManualRange) cameraUtils.settingManualMax else 0f
        val isCelsius = cameraUtils.settingIsCelsius
        val bmp =
            withContext(Dispatchers.Default) {
                cameraUtils.remapWithPalette(dto, palette, isManualRange, manualMin, manualMax, isCelsius)
            }
        if (bmp != null) {
            // Write palette name into dto so saveTjsn (which saves dto.getJsonObject()) captures it
            dto.paletteName = paletteName
            _currentBitmap.value = bmp
        }
    }

    /** Reformats the currently-displayed frame's spot/max/min temperature text and values
     *  from its raw radiometric data, using whatever unit is currently selected — used both
     *  when a frame first arrives and to re-render an already-displayed frame immediately
     *  when the user changes units, rather than waiting for the next frame to reflect it. */
    private fun refreshTempDisplays() {
        val dto = _currentImageDto.value ?: return
        if (dto.tLinearEnabled == 0) return
        val celsius = cameraUtils.settingIsCelsius
        val scale = if (dto.tLinearResolution == 0) 10f else 100f
        val rect = _spotmeterRect.value
        val (spotValue, spotText) =
            if (rect != null && dto.imageData != null) {
                val cx = (rect.left + rect.right) / 2
                val cy = (rect.top + rect.bottom) / 2
                calcSpotTemp(dto.imageData!!, cx, cy, scale, celsius)
            } else {
                formatTemp(dto.spotmeterMean, scale, celsius)
            }
        val (maxValue, maxText) = formatTemp(dto.maxTemperature, scale, celsius)
        val (minValue, minText) = formatTemp(dto.minTemperature, scale, celsius)
        _spotmeterTemp.value = spotText
        _maxTemp.value = maxText
        _minTemp.value = minText
        _spotmeterTempValue.value = spotValue
        _maxTempValue.value = maxValue
        _minTempValue.value = minValue
        checkTemperatureAlert(spotValue, maxValue, minValue, celsius)

        // In Region mode the chart tracks the box's own avg/min/max instead of the whole-frame
        // spot/max/min — reusing the same TempSample fields (avg standing in for "spot") rather
        // than adding mode-specific ones, since [clearTempHistory] above already guarantees a
        // chart never mixes samples from both modes.
        val region = _measurementRegion.value
        if (_measurementMode.value == MeasurementMode.REGION && region != null && dto.imageData != null) {
            val (avg, regionMin, regionMax) = calcRegionStats(dto.imageData!!, region, scale, celsius)
            _regionAvgTemp.value = avg.second
            _regionMinTemp.value = regionMin.second
            _regionMaxTemp.value = regionMax.second
            recordTempSample(avg.first, regionMax.first, regionMin.first)
        } else {
            recordTempSample(spotValue, maxValue, minValue)
        }
    }

    /** Edge-triggered temperature alert: fires an in-app message the moment the selected metric
     *  crosses the threshold, then stays quiet until it crosses back — otherwise it would fire
     *  on every single frame while the condition holds. */
    private fun checkTemperatureAlert(
        spotValue: Float,
        maxValue: Float,
        minValue: Float,
        isCelsius: Boolean,
    ) {
        // "Spot" only fires while the point hotspot marker is actually visible — Spotmeter
        // disabled or Region Measurement mode active both mean nothing is drawn at that
        // location, even though the value itself is still being computed underneath. Max/Min
        // aren't tied to a marker at all, so they're unaffected.
        val spotVisible = spotmeterEnabled.value && measurementMode.value == MeasurementMode.POINT
        if (!alertEnabled || (alertMetric == "Spot" && !spotVisible)) {
            alertCurrentlyTriggered = false
            return
        }
        val value = when (alertMetric) {
            "Max" -> maxValue
            "Min" -> minValue
            else -> spotValue
        }
        val crossed = if (alertComparison == "Above") value > alertThreshold else value < alertThreshold
        if (crossed && !alertCurrentlyTriggered) {
            alertCurrentlyTriggered = true
            val unit = if (isCelsius) "°C" else "°F"
            val comparisonWord = if (alertComparison == "Above") "above" else "below"
            _alertMessage.tryEmit(
                "$alertMetric temperature $comparisonWord threshold: %.1f%s".format(value, unit),
            )
        } else if (!crossed) {
            alertCurrentlyTriggered = false
        }
    }

    /** Updates the region box (from drag/resize gestures), clamped to the frame bounds with a
     *  minimum 4×4-camera-pixel size so it can never collapse to nothing. */
    fun setMeasurementRegion(rect: Rect) {
        val left = rect.left.coerceIn(0, Constants.IMAGE_WIDTH - 4)
        val top = rect.top.coerceIn(0, Constants.IMAGE_HEIGHT - 4)
        val right = rect.right.coerceIn(left + 4, Constants.IMAGE_WIDTH)
        val bottom = rect.bottom.coerceIn(top + 4, Constants.IMAGE_HEIGHT)
        _measurementRegion.value = Rect(left, top, right, bottom)
        refreshTempDisplays()
    }

    /** Appends a temperature-over-time sample and trims anything older than
     *  [tempHistoryWindowMs] (or [tempHistoryWindowOverrideMs] during a time lapse). Called from
     *  both the frame-processing dispatcher and Main (a unit change re-renders and records
     *  immediately), so the buffer mutation is synchronized. */
    private fun recordTempSample(
        spot: Float,
        max: Float,
        min: Float,
    ) {
        val now = System.currentTimeMillis()
        val snapshot = synchronized(tempHistoryBuffer) {
            tempHistoryBuffer.addLast(TempSample(now, spot, max, min))
            val cutoff = now - (tempHistoryWindowOverrideMs ?: tempHistoryWindowMs)
            while (tempHistoryBuffer.isNotEmpty() && tempHistoryBuffer.first().timestampMs < cutoff) {
                tempHistoryBuffer.removeFirst()
            }
            tempHistoryBuffer.toList()
        }
        _tempHistory.value = snapshot
    }

    private fun clearTempHistory() {
        synchronized(tempHistoryBuffer) { tempHistoryBuffer.clear() }
        _tempHistory.value = emptyList()
    }

    /** User-triggered reset from the Temperature History dialog's Clear button — the buffer
     *  otherwise just keeps rolling, with no way to start a fresh window short of toggling
     *  Region Measurement (which resets it as a side effect). */
    fun clearChartHistory() = clearTempHistory()

    private suspend fun connectToCamera(ip: String, showErrorOnFailure: Boolean = true) {
        Timber.d("connectToCamera ip=$ip")
        _isConnecting.value = true
        try {
            cameraService.disconnect()
            cameraService.setIpAddress(ip)
            val connected = cameraService.connect()
            Timber.d("connectToCamera result=$connected")
            if (!coroutineContext.isActive) {
                // Superseded by a disconnect or a newer connect attempt while this one was in
                // flight — don't resurrect a connection the user already moved past.
                if (connected) cameraService.disconnect()
                return
            }
            _isConnected.value = connected
            _isStreaming.value = false
            if (connected) {
                cameraService.getImage()
                loadCameraConfig()
                seedDefaultRegionIfNeeded()
            } else if (showErrorOnFailure) {
                _showConnectError.value = true
            }
        } finally {
            _isConnecting.value = false
        }
    }

    /** Called when CameraService reports a previously-good connection died on its own (as
     *  opposed to the user disconnecting). Only meaningful if we still think we're connected —
     *  guards against a signal arriving after the user already disconnected/reconnected. */
    private fun onConnectionLost() {
        if (!_isConnected.value) return
        _isConnected.value = false
        _isStreaming.value = false
        startAutoReconnect()
    }

    /** Retries the last-known address a few times first — most drops are transient (WiFi
     *  hiccup, camera modem-sleep) and clear up without the address changing. If those all
     *  fail, falls back to mDNS discovery in case the camera's DHCP lease handed out a new
     *  address, and retries once more against whatever it finds. Suppresses the normal
     *  connect-error dialog for every attempt but the last, so a background retry loop doesn't
     *  pop it repeatedly. */
    private fun startAutoReconnect() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            val lastIp = settingsDataManager.getCameraIp()
            repeat(3) { attempt ->
                delay(3_000L * (attempt + 1))
                if (!isActive) return@launch
                connectToCamera(lastIp, showErrorOnFailure = false)
                if (_isConnected.value) return@launch
            }
            if (!isActive) return@launch
            val camera = discoverTcamCameras(appContext, timeoutMs = 8_000L).firstOrNull()
            if (!isActive) return@launch
            if (camera != null) {
                if (camera.ip != lastIp) settingsDataManager.saveCameraIp(camera.ip)
                connectToCamera(camera.ip, showErrorOnFailure = false)
            }
            if (!_isConnected.value && isActive) _showConnectError.value = true
        }
    }

    // --- Public actions called from the UI ---

    fun toggleConnection() {
        if (_isConnected.value || _isConnecting.value) {
            connectJob?.cancel()
            connectJob = null
            cameraService.disconnect()
            _isConnected.value = false
            _isConnecting.value = false
            _isStreaming.value = false
            _spotmeterRect.value = null
            _cameraConfig.value = null
            userMovedSpotmeter = false
            clearTempHistory()
            // Region ON/OFF is a persisted Settings preference and stays as-is; only the box's
            // own position is session-only.
            _measurementRegion.value = null
            alertCurrentlyTriggered = false
        } else {
            connectJob?.cancel()
            connectJob = viewModelScope.launch(Dispatchers.IO) {
                connectToCamera(settingsDataManager.getCameraIp())
            }
        }
    }

    fun dismissConnectError() {
        _showConnectError.value = false
    }

    /** Disconnects immediately (the set_wifi command that must have just been sent restarts the
     *  camera's WiFi subsystem on its own), then waits for it to rejoin the network and tries
     *  once. [newIp] is the address to try — known up front for AP mode (its fixed address) and
     *  static-IP client mode (what the user typed); null for DHCP client mode, where the new
     *  address can't be known in advance, so the existing configured IP is retried as a
     *  best-effort guess (DHCP lease stickiness often reassigns the same address). Failure
     *  surfaces through the normal _showConnectError flow — the user can retry via Connect or
     *  "Find cameras" (mDNS) once the camera settles on its new network. */
    fun reconnectAfterWifiChange(newIp: String?) {
        connectJob?.cancel()
        cameraService.disconnect()
        _isConnected.value = false
        _isStreaming.value = false
        _spotmeterRect.value = null
        _cameraConfig.value = null
        userMovedSpotmeter = false
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            val ip =
                if (newIp != null) {
                    settingsDataManager.saveCameraIp(newIp)
                    newIp
                } else {
                    settingsDataManager.getCameraIp()
                }
            delay(8_000L)
            connectToCamera(ip)
        }
    }

    private suspend fun loadCameraConfig() {
        try {
            val response = cameraService.getConfig()
            val config = response.optJSONObject("config") ?: return
            val agcEnabled = config.optInt("agc_enabled") != 0
            // The set_config/get_config API takes emissivity as a plain 1-100 percentage
            // (see tCam firmware json_utilities.c: json_parse_set_config clamps to 1..100) —
            // distinct from the 0-8192 scale used by Lepton telemetry elsewhere in the app.
            val emissivity = config.optInt("emissivity", 90).coerceIn(1, 100)
            val gainMode = config.optInt("gain_mode", Constants.GAIN_MODE_HIGH)
            _cameraConfig.value = CameraConfig(agcEnabled, emissivity, gainMode)

            // Persist the camera's actual reported config once per connect — the Settings
            // screen reads these as its source of truth and shouldn't re-sync on every visit.
            settingsDataManager.saveCameraAgc(agcEnabled)
            settingsDataManager.saveCameraEmissivity(emissivity.toString())
            settingsDataManager.saveCameraGainMode(gainMode)
        } catch (e: Exception) {
            Timber.e(e, "loadCameraConfig failed")
        }
    }

    fun sendCameraConfig(
        agcEnabled: Boolean,
        emissivity: Int,
        gainMode: Int,
    ) {
        cameraService.setConfig(agcEnabled, emissivity, gainMode)
    }

    fun sendWifiConfig(
        isAccessPoint: Boolean,
        ssid: String,
        password: String,
        useStaticIp: Boolean,
        staticIp: String,
        staticNetmask: String,
    ) {
        val args =
            when {
                isAccessPoint -> {
                    String.format(Constants.ARGS_SET_WIFI_AP, ssid, password)
                }

                useStaticIp -> {
                    String.format(
                        Constants.ARGS_SET_WIFI_STATIC,
                        ssid,
                        password,
                        staticIp,
                        staticNetmask,
                    )
                }

                else -> {
                    String.format(Constants.ARGS_SET_WIFI_NOT_STATIC, ssid, password)
                }
            }
        cameraService.setWifi(args)
    }

    fun fetchWifiInfo() {
        _wifiInfo.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = cameraService.getWifi()
                val wifi =
                    response.optJSONObject("wifi") ?: run {
                        _wifiInfo.value = emptyMap()
                        return@launch
                    }
                _wifiInfo.value = wifi.keys().asSequence().associateWith { wifi.optString(it) }
            } catch (e: Exception) {
                Timber.e(e, "fetchWifiInfo failed")
                _wifiInfo.value = emptyMap()
            }
        }
    }

    fun getImage() {
        if (!_isConnected.value) return
        manualGetPending = true
        cameraService.getImage()
    }

    fun runFfc() {
        if (!_isConnected.value) return
        cameraService.runFfc()
    }

    fun startTimeLapse(
        intervalSec: Int,
        durationSec: Int,
    ) {
        if (!_isConnected.value || _isTimeLapsing.value) return
        val intervalMs = intervalSec * 1000L
        val durationMs = durationSec * 1000L
        _isTimeLapsing.value = true
        tempHistoryWindowOverrideMs = durationMs
        timeLapseJob =
            viewModelScope.launch(Dispatchers.IO) {
                var stream: FileOutputStream? = null
                var frameCount = 0
                var naturalCompletion = false
                val startMs = System.currentTimeMillis()
                var file: File? = null
                try {
                    val handle = cameraUtils.openTimeLapseFile()
                    stream = handle.stream
                    file = handle.file
                    val endTime = startMs + durationMs
                    while (isActive && System.currentTimeMillis() < endTime) {
                        val frameStart = System.currentTimeMillis()
                        _isTimeLapseCapturing.value = true
                        val json = cameraService.getImageOnce() ?: break
                        _isTimeLapseCapturing.value = false
                        stream.write(json.toString().toByteArray(Charsets.US_ASCII))
                        stream.write(0x03)
                        frameCount++
                        val remaining = intervalMs - (System.currentTimeMillis() - frameStart)
                        if (remaining > 0 && isActive) kotlinx.coroutines.delay(remaining)
                    }
                    naturalCompletion = isActive
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Time lapse error")
                } finally {
                    val endMs = System.currentTimeMillis()
                    if (discardTimeLapse) {
                        runCatching { stream?.close() }
                        file?.delete()
                    } else {
                        runCatching {
                            stream?.write(buildFooterJson(startMs, endMs, frameCount).toByteArray(Charsets.US_ASCII))
                            stream?.close()
                        }
                    }
                    discardTimeLapse = false
                    _isTimeLapseCapturing.value = false
                    _isTimeLapsing.value = false
                    if (naturalCompletion) {
                        val samples = _tempHistory.value
                        val primaryLabel = if (_measurementMode.value == MeasurementMode.REGION) "Avg" else "Spot"
                        val chartSaved =
                            samples.size >= 2 &&
                                cameraUtils.saveTempChart(samples, cameraUtils.settingIsCelsius, primaryLabel)
                        val suffix = if (chartSaved) ", chart saved" else ""
                        _timeLapseMessage.tryEmit("Time lapse complete — $frameCount frames captured$suffix")
                    }
                    // Restore the normal 5-minute rolling window for whatever streaming/viewing
                    // happens next, now that the full-duration chart has been read and saved.
                    tempHistoryWindowOverrideMs = null
                }
            }
    }

    fun stopTimeLapse(save: Boolean) {
        if (!save) discardTimeLapse = true
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
                val handle = cameraUtils.openRecordingFile()
                recordingStream = handle.stream
                recordingFile = handle.file
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

    // Stops the current recording from the "Stop" button, after the user has chosen whether
    // to keep or discard it via the save/discard confirmation dialog.
    fun stopRecording(save: Boolean) {
        finishRecording(save = save, stopStreamIfAutoStarted = false)
        toggleStreaming()
    }

    private fun finishRecording(
        save: Boolean = true,
        stopStreamIfAutoStarted: Boolean = true,
    ) {
        val stream = recordingStream
        val file = recordingFile
        recordingStream = null
        recordingFile = null
        val count = recordingFrameCount
        val endMs = System.currentTimeMillis()
        _isRecording.value = false
        viewModelScope.launch(Dispatchers.IO) {
            if (stream != null) {
                try {
                    if (save) {
                        stream.write(buildFooterJson(recordingStartMs, endMs, count).toByteArray(Charsets.US_ASCII))
                        stream.close()
                    } else {
                        stream.close()
                        file?.delete()
                    }
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

    private fun buildFooterJson(
        startMs: Long,
        endMs: Long,
        numFrames: Int,
    ): String {
        val timeFmt = SimpleDateFormat("H:mm:ss.SSS", Locale.US)
        val dateFmt = SimpleDateFormat("M/d/yy", Locale.US)
        val start = Date(startMs)
        val end = Date(endMs)
        return """{"video_info":{"start_time":"${timeFmt.format(
            start,
        )}","start_date":"${dateFmt.format(
            start,
        )}","end_time":"${timeFmt.format(end)}","end_date":"${dateFmt.format(end)}","num_frames":$numFrames,"version":1}}"""
    }

    fun setPalette(name: String) {
        viewModelScope.launch { settingsDataManager.saveSelectedPalette(name) }
    }

    fun setSpotmeter(
        camX: Int,
        camY: Int,
    ) {
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
                val (spotValue, spotText) =
                    calcSpotTemp(
                        dto.imageData!!,
                        camX,
                        camY,
                        scale,
                        settingsDataManager.isUnitsCelsius(),
                    )
                _spotmeterTemp.value = spotText
                _spotmeterTempValue.value = spotValue
            }
        }

        cameraService.setSpotmeter(c1, c2, r1, r2)
        if (!_isStreaming.value && _isConnected.value) cameraService.getImage()
    }

    // --- Frame processing ---

    private suspend fun processFrame(json: JSONObject) {
        if (!json.has("radiometric")) return
        // Only the frame that answers a manually-tapped Get clicks — not the auto-Get on
        // connect, the spotmeter-drag re-Get, streaming/recording frames, or time-lapse captures.
        if (manualGetPending) {
            manualGetPending = false
            if (shutterSoundEnabled) playShutterSound()
        }
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
            _currentImageDto.value = dto
            _currentBitmap.value = dto.bitmap
            _histogram.value = dto.histogram
            if (!userMovedSpotmeter) dto.spotmeterLocation?.let { _spotmeterRect.value = it }
            if (dto.tLinearEnabled != 0) {
                refreshTempDisplays()
            } else {
                _spotmeterTemp.value = "--"
                _maxTemp.value = "--"
                _minTemp.value = "--"
                _spotmeterTempValue.value = null
                _maxTempValue.value = null
                _minTempValue.value = null
            }
            updateFps()
        } catch (e: Exception) {
            Timber.e(e, "Frame processing error")
        }
    }

    /** Returns the temperature in the currently-selected display unit, alongside its formatted text. */
    private fun formatTemp(
        rawValue: Int,
        scale: Float,
        isCelsius: Boolean,
    ): Pair<Float, String> {
        val tempC = rawValue / scale - 273.15f
        val value = if (isCelsius) tempC else tempC * 9f / 5f + 32f
        val text = if (isCelsius) "%.1f°C".format(value) else "%.1f°F".format(value)
        return value to text
    }

    private fun calcSpotTemp(
        imageData: IntArray,
        cx: Int,
        cy: Int,
        scale: Float,
        isCelsius: Boolean,
    ): Pair<Float, String> {
        val c1 = cx.coerceIn(0, Constants.IMAGE_WIDTH - 1)
        val c2 = (cx + 1).coerceAtMost(Constants.IMAGE_WIDTH - 1)
        val r1 = cy.coerceIn(0, Constants.IMAGE_HEIGHT - 1)
        val r2 = (cy + 1).coerceAtMost(Constants.IMAGE_HEIGHT - 1)
        var sum = 0L
        var count = 0
        for (row in r1..r2) {
            for (col in c1..c2) {
                sum += imageData[row * Constants.IMAGE_WIDTH + col]
                count++
            }
        }
        return formatTemp(if (count > 0) (sum / count).toInt() else 0, scale, isCelsius)
    }

    /** Average/min/max temperature (value + formatted text) over an arbitrary region, computed
     *  client-side from the raw per-frame imageData — the camera's own set_spotmeter command only
     *  reports a mean for whatever region it was last told about, not min/max, so those need
     *  local math. Raw values (not just text) are returned so callers can feed them into the
     *  temperature-history chart alongside the whole-frame spot/max/min. */
    private fun calcRegionStats(
        imageData: IntArray,
        rect: Rect,
        scale: Float,
        isCelsius: Boolean,
    ): Triple<Pair<Float, String>, Pair<Float, String>, Pair<Float, String>> {
        val c1 = rect.left.coerceIn(0, Constants.IMAGE_WIDTH - 1)
        val c2 = rect.right.coerceIn(c1, Constants.IMAGE_WIDTH - 1)
        val r1 = rect.top.coerceIn(0, Constants.IMAGE_HEIGHT - 1)
        val r2 = rect.bottom.coerceIn(r1, Constants.IMAGE_HEIGHT - 1)
        var sum = 0L
        var count = 0
        var minRaw = Int.MAX_VALUE
        var maxRaw = Int.MIN_VALUE
        for (row in r1..r2) {
            for (col in c1..c2) {
                val v = imageData[row * Constants.IMAGE_WIDTH + col]
                sum += v
                count++
                if (v < minRaw) minRaw = v
                if (v > maxRaw) maxRaw = v
            }
        }
        if (count == 0) return Triple(0f to "--", 0f to "--", 0f to "--")
        return Triple(
            formatTemp((sum / count).toInt(), scale, isCelsius),
            formatTemp(minRaw, scale, isCelsius),
            formatTemp(maxRaw, scale, isCelsius),
        )
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
        connectionLostDisposable?.dispose()
        cameraService.disconnect()
    }
}
