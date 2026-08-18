package com.das.tcamviewer2.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.das.tcamviewer2.BuildConfig
import com.das.tcamviewer2.SettingsDataManager
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.model.CameraViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dataManager = remember { SettingsDataManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val nsdManager = remember { context.getSystemService(NsdManager::class.java) }

    val isConnected by viewModel.isConnected.collectAsState()
    val currentMinTemp by viewModel.minTempValue.collectAsState()
    val currentMaxTemp by viewModel.maxTempValue.collectAsState()

    // Camera settings — persisted in DataStore. The ViewModel writes the camera's actual
    // reported config here once per connect, so these flows are always the source of truth.
    val savedCameraAgc by dataManager.cameraAgcFlow.collectAsState(initial = false)
    val savedCameraEmissivity by dataManager.cameraEmissivityFlow.collectAsState(initial = "90")
    val savedCameraGainMode by dataManager.cameraGainModeFlow.collectAsState(initial = Constants.GAIN_MODE_HIGH)

    var localAgc by remember(savedCameraAgc) { mutableStateOf(savedCameraAgc) }
    var localEmissivity by remember(savedCameraEmissivity) { mutableStateOf(savedCameraEmissivity) }
    var localGainMode by remember(savedCameraGainMode) { mutableStateOf(savedCameraGainMode) }

    var showDiscoveryDialog by remember { mutableStateOf(false) }
    val discoveredDevices = remember { mutableStateListOf<Pair<String, String>>() }
    var isDiscovering by remember { mutableStateOf(false) }
    var discoverySelectedDevice by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showIpChangeConfirm by remember { mutableStateOf(false) }

    var showPaletteDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var tempDialogPalette by remember { mutableStateOf("Rainbow") }
    var resMenuExpanded by remember { mutableStateOf(false) }

    val resolutions = listOf("160x120", "320x240", "480x360", "640x480")
    val paletteOptions = listOf(
        "Arctic", "Banded", "Blackhot", "DoubleRainbow", "Fusion",
        "Gray", "Ironblack", "Isotherm", "Rainbow", "Sepia",
    )

    // --- Saved DataStore values (source of truth) ---
    val savedIp by dataManager.cameraIpFlow.collectAsState(initial = "192.168.4.1")
    val savedExportPic by dataManager.exportPictureFlow.collectAsState(initial = false)
    val savedExportMeta by dataManager.exportMetadataFlow.collectAsState(initial = false)
    val savedExportRes by dataManager.exportResolutionFlow.collectAsState(initial = "320x240")
    val savedManualRange by dataManager.manualRangeFlow.collectAsState(initial = false)
    val savedMin by dataManager.minValueFlow.collectAsState(initial = "0")
    val savedMax by dataManager.maxValueFlow.collectAsState(initial = "100")
    val savedShutter by dataManager.shutterSoundFlow.collectAsState(initial = true)
    val savedSpotmeter by dataManager.spotmeterFlow.collectAsState(initial = true)
    val savedRegionMeasurement by dataManager.regionMeasurementFlow.collectAsState(initial = false)
    val savedAlertEnabled by dataManager.alertEnabledFlow.collectAsState(initial = false)
    val savedAlertMetric by dataManager.alertMetricFlow.collectAsState(initial = "Spot")
    val savedAlertComparison by dataManager.alertComparisonFlow.collectAsState(initial = "Above")
    val savedAlertThreshold by dataManager.alertThresholdFlow.collectAsState(initial = "100")
    val savedUnit by dataManager.temperatureUnitFlow.collectAsState(initial = "Celsius")
    val savedPalette by dataManager.selectedPaletteFlow.collectAsState(initial = "Rainbow")

    // Incrementing this forces every local state to reinitialize from saved values (Cancel)
    var resetKey by remember { mutableStateOf(0) }

    // --- Local (unsaved) working copies ---
    var localIp by remember(savedIp, resetKey) { mutableStateOf(savedIp) }
    var localExportPic by remember(savedExportPic, resetKey) { mutableStateOf(savedExportPic) }
    var localExportMeta by remember(savedExportMeta, resetKey) { mutableStateOf(savedExportMeta) }
    var localResolution by remember(savedExportRes, resetKey) { mutableStateOf(savedExportRes) }
    var localManualRange by remember(savedManualRange, resetKey) { mutableStateOf(savedManualRange) }
    var localMin by remember(savedMin, resetKey) { mutableStateOf(savedMin) }
    var localMax by remember(savedMax, resetKey) { mutableStateOf(savedMax) }
    var localShutter by remember(savedShutter, resetKey) { mutableStateOf(savedShutter) }
    var localSpotmeter by remember(savedSpotmeter, resetKey) { mutableStateOf(savedSpotmeter) }
    var localRegionMeasurement by remember(savedRegionMeasurement, resetKey) { mutableStateOf(savedRegionMeasurement) }
    var localAlertEnabled by remember(savedAlertEnabled, resetKey) { mutableStateOf(savedAlertEnabled) }
    var localAlertMetric by remember(savedAlertMetric, resetKey) { mutableStateOf(savedAlertMetric) }
    var localAlertComparison by remember(savedAlertComparison, resetKey) { mutableStateOf(savedAlertComparison) }
    var localAlertThreshold by remember(savedAlertThreshold, resetKey) { mutableStateOf(savedAlertThreshold) }
    var localUnit by remember(savedUnit, resetKey) { mutableStateOf(savedUnit) }
    var localPalette by remember(savedPalette, resetKey) { mutableStateOf(savedPalette) }

    suspend fun performSave(sendConfigIfConnected: Boolean) {
        dataManager.saveCameraIp(localIp)
        dataManager.saveExportPicture(localExportPic)
        dataManager.saveExportMetadata(localExportMeta)
        dataManager.saveExportResolution(localResolution)
        dataManager.saveManualRange(localManualRange)
        dataManager.saveMinValue(localMin)
        dataManager.saveMaxValue(localMax)
        dataManager.saveShutterSound(localShutter)
        dataManager.saveSpotmeter(localSpotmeter)
        dataManager.saveRegionMeasurement(localRegionMeasurement)
        dataManager.saveAlertEnabled(localAlertEnabled)
        dataManager.saveAlertMetric(localAlertMetric)
        dataManager.saveAlertComparison(localAlertComparison)
        dataManager.saveAlertThreshold(localAlertThreshold)
        dataManager.saveTemperatureUnit(localUnit)
        dataManager.saveSelectedPalette(localPalette)
        dataManager.saveCameraAgc(localAgc)
        dataManager.saveCameraEmissivity(localEmissivity)
        dataManager.saveCameraGainMode(localGainMode)
        if (sendConfigIfConnected && isConnected) {
            val emissivityPct = (localEmissivity.toIntOrNull() ?: 90).coerceIn(1, 100)
            viewModel.sendCameraConfig(localAgc, emissivityPct, localGainMode)
        }
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedbackTextButton(onClick = {
                    resetKey++
                    onNavigateBack()
                }) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FeedbackButton(onClick = {
                    if (isConnected && localIp != savedIp) {
                        showIpChangeConfirm = true
                    } else {
                        coroutineScope.launch { performSave(sendConfigIfConnected = true) }
                    }
                }) {
                    Text("Save")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Camera Settings — only shown when camera is connected
            if (isConnected) {
                CameraSettingsSection(
                    viewModel = viewModel,
                    localAgc = localAgc,
                    onAgcChange = {
                        localAgc = it
                        coroutineScope.launch { dataManager.saveCameraAgc(it) }
                    },
                    localEmissivity = localEmissivity,
                    onEmissivityChange = { localEmissivity = it },
                    onEmissivityConfirm = {
                        coroutineScope.launch { dataManager.saveCameraEmissivity(localEmissivity) }
                    },
                    localGainMode = localGainMode,
                    onGainModeChange = {
                        localGainMode = it
                        coroutineScope.launch { dataManager.saveCameraGainMode(it) }
                    },
                )
            }

            Text(
                text = "APPLICATION SETTINGS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
            )

            // Camera IP Address
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = localIp,
                    onValueChange = { localIp = it },
                    label = { Text("Camera IP Address") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() },
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    discoveredDevices.clear()
                    discoverySelectedDevice = null
                    showDiscoveryDialog = true
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Find cameras")
                }
            }

            // Export Picture on Save
            ListItem(
                headlineContent = { Text("Export Picture on Save") },
                supportingContent = { Text(if (localExportPic) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(
                        checked = localExportPic,
                        onCheckedChange = { localExportPic = it },
                        modifier = Modifier.testTag("switch_export_picture"),
                    )
                },
            )

            // Export Metadata
            ListItem(
                headlineContent = { Text("Export Metadata") },
                supportingContent = { Text(if (localExportMeta) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(
                        checked = localExportMeta,
                        onCheckedChange = { localExportMeta = it },
                        modifier = Modifier.testTag("switch_export_metadata"),
                    )
                },
            )

            // Export Resolution dropdown
            ExposedDropdownMenuBox(
                expanded = resMenuExpanded,
                onExpandedChange = { resMenuExpanded = it },
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    value = localResolution,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Export Resolution") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = resMenuExpanded,
                    onDismissRequest = { resMenuExpanded = false },
                ) {
                    resolutions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                localResolution = option
                                resMenuExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            // Manual Range toggle
            ListItem(
                headlineContent = { Text("Manual Range") },
                supportingContent = { Text(if (localManualRange) "Custom Bounds Active" else "Automatic Scaling") },
                trailingContent = {
                    Switch(
                        checked = localManualRange,
                        onCheckedChange = { enabled ->
                            localManualRange = enabled
                            // Freshly enabling — seed with a round-degree bound of the current
                            // image's actual range (in the current unit), like the desktop app,
                            // rather than leaving stale/default values from a different unit.
                            val min = currentMinTemp
                            val max = currentMaxTemp
                            if (enabled && min != null && max != null) {
                                localMin = floor(min).toInt().toString()
                                localMax = ceil(max).toInt().toString()
                            }
                        },
                        modifier = Modifier.testTag("switch_manual_range"),
                    )
                },
            )

            // Manual Range min/max fields
            if (localManualRange) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = localMin,
                        onValueChange = { localMin = it },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = localMax,
                        onValueChange = { localMax = it },
                        label = { Text("Max") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            // Palette
            ListItem(
                headlineContent = { Text("Palette") },
                supportingContent = { Text("Active: $localPalette") },
                trailingContent = {
                    IconButton(onClick = {
                        tempDialogPalette = localPalette
                        showPaletteDialog = true
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Open Palette Selection")
                    }
                },
            )
            if (showPaletteDialog) {
                AlertDialog(
                    onDismissRequest = { showPaletteDialog = false },
                    title = { Text("Select Palette") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            paletteOptions.forEach { palette ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = palette == tempDialogPalette,
                                            onClick = { tempDialogPalette = palette },
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = palette == tempDialogPalette,
                                        onClick = { tempDialogPalette = palette },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(palette, fontSize = 16.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            localPalette = tempDialogPalette
                            showPaletteDialog = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPaletteDialog = false }) { Text("Cancel") }
                    },
                )
            }

            // Shutter Sound
            ListItem(
                headlineContent = { Text("Shutter Sound") },
                supportingContent = { Text(if (localShutter) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(
                        checked = localShutter,
                        onCheckedChange = { localShutter = it },
                        modifier = Modifier.testTag("switch_shutter_sound"),
                    )
                },
            )

            // Spotmeter
            ListItem(
                headlineContent = { Text("Spotmeter") },
                supportingContent = { Text(if (localSpotmeter) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(
                        checked = localSpotmeter,
                        onCheckedChange = { localSpotmeter = it },
                        modifier = Modifier.testTag("switch_spotmeter"),
                    )
                },
            )

            // Region Measurement — mutually exclusive with the point spotmeter above; when on,
            // the Camera screen shows a resizable box (avg/min/max) instead of the single-point
            // reading. The box's own position is session-only regardless of this setting.
            ListItem(
                headlineContent = { Text("Region Measurement") },
                supportingContent = { Text(if (localRegionMeasurement) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(
                        checked = localRegionMeasurement,
                        onCheckedChange = { localRegionMeasurement = it },
                        modifier = Modifier.testTag("switch_region_measurement"),
                    )
                },
            )

            // Temperature Alert — fires an in-app message (Snackbar on the Camera screen) the
            // moment the selected metric crosses the threshold, then rearms once it crosses back.
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Temperature Alert") },
                    supportingContent = { Text(if (localAlertEnabled) "Enabled" else "Disabled") },
                    trailingContent = {
                        Switch(
                            checked = localAlertEnabled,
                            onCheckedChange = { localAlertEnabled = it },
                            modifier = Modifier.testTag("switch_alert_enabled"),
                        )
                    },
                )
                if (localAlertEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        listOf("Spot", "Max", "Min").forEach { metric ->
                            Row(
                                modifier = Modifier
                                    .selectable(
                                        selected = localAlertMetric == metric,
                                        onClick = { localAlertMetric = metric },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = localAlertMetric == metric,
                                    onClick = { localAlertMetric = metric },
                                )
                                Text(metric, fontSize = 14.sp)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        listOf("Above", "Below").forEach { comparison ->
                            Row(
                                modifier = Modifier
                                    .selectable(
                                        selected = localAlertComparison == comparison,
                                        onClick = { localAlertComparison = comparison },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = localAlertComparison == comparison,
                                    onClick = { localAlertComparison = comparison },
                                )
                                Text(comparison, fontSize = 14.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = localAlertThreshold,
                        onValueChange = { localAlertThreshold = it },
                        label = { Text(if (localUnit == "Celsius") "Threshold (°C)" else "Threshold (°F)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            // Temperature Units
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Units") },
                    supportingContent = { Text("Select your global temperature unit") },
                )
                val unitOptions = listOf("Celsius (°C)", "Fahrenheit (°F)")

                // Manual Range bounds are stored as plain unlabeled numbers — switching units
                // must translate them so they keep meaning the same physical temperature
                // instead of silently being reinterpreted under the new unit.
                fun selectUnit(unitName: String) {
                    if (unitName != localUnit) {
                        val toCelsius = unitName == "Celsius"
                        localMin = convertManualBound(localMin, toCelsius)
                        localMax = convertManualBound(localMax, toCelsius)
                        localUnit = unitName
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    unitOptions.forEach { option ->
                        val unitName = if (option.contains("Celsius")) "Celsius" else "Fahrenheit"
                        Row(
                            modifier = Modifier
                                .selectable(
                                    selected = localUnit == unitName,
                                    onClick = { selectUnit(unitName) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = localUnit == unitName,
                                onClick = { selectUnit(unitName) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Version
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
            )

            // Privacy Statement
            ListItem(
                headlineContent = { Text("Privacy Statement") },
                supportingContent = { Text("Tap to read") },
                trailingContent = {
                    IconButton(onClick = { showPrivacyDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Privacy Statement")
                    }
                },
            )
        }
    }

    // Privacy Statement Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Statement") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = """
tCam Viewer ("the App") is designed to connect to and display imagery from tCam thermal imaging cameras on your local network. We are committed to protecting your privacy.

Data Collection
The App does not collect, transmit, or share any personal information. All camera connections and image data remain entirely on your local device and local network.

Network Access
The App uses your device's Wi-Fi connection solely to communicate with tCam cameras on your local network. No data is sent to external servers or third parties.

Image Storage
Images saved through the App are stored locally on your device. The App does not upload images to any cloud service or remote server.

Camera & Network Permissions
The App requests network access permissions only to discover and connect to tCam cameras via mDNS and TCP on your local network.

Changes to This Statement
If this privacy statement is updated, the new version will be included in the next App release.

Contact
For questions about this privacy statement, please contact the developer through the app's distribution channel.
                        """.trimIndent(),
                        fontSize = 14.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
            },
        )
    }

    // --- Warn before changing the camera IP while connected — set_config/get_image etc. all
    // target the old address, so switching it out from under an active connection would just
    // leave the app pointed at a socket to nowhere. OK disconnects (synchronously — toggleConnection's
    // disconnect branch updates isConnected before returning) and then saves immediately, same as a
    // normal Save press. Cancel disconnects nothing and leaves the unsaved edits untouched. ---
    if (showIpChangeConfirm) {
        AlertDialog(
            onDismissRequest = { showIpChangeConfirm = false },
            title = { Text("Change IP Address") },
            text = { Text("This will disconnect the camera.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleConnection() // isConnected is true here, so this disconnects
                    showIpChangeConfirm = false
                    coroutineScope.launch { performSave(sendConfigIfConnected = true) }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showIpChangeConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // --- Camera Discovery Dialog ---
    if (showDiscoveryDialog) {
        LaunchedEffect(Unit) {
            isDiscovering = true
            val pendingResolves = Channel<NsdServiceInfo>(Channel.UNLIMITED)

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {
                    isDiscovering = false
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    isDiscovering = false
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceName.lowercase().startsWith("tcam")) {
                        pendingResolves.trySend(serviceInfo)
                    }
                }
            }

            // NsdManager is supposed to handle multicast reception on the app's behalf, but on
            // some devices/Android versions mDNS packets never reach the socket unless the app
            // holds its own multicast lock — a long-standing platform quirk, not something
            // NsdManager reliably covers on its own.
            val multicastWifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
            val multicastLock = multicastWifiManager?.createMulticastLock("tcamDiscovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            nsdManager.discoverServices(Constants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            try {
                withTimeoutOrNull(10_000L) {
                    for (serviceInfo in pendingResolves) {
                        val resolved = suspendCancellableCoroutine { cont ->
                            nsdManager.resolveService(
                                serviceInfo,
                                object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                        cont.resume(null)
                                    }
                                    override fun onServiceResolved(info: NsdServiceInfo) {
                                        cont.resume(info)
                                    }
                                },
                            )
                        }
                        resolved?.let { info ->
                            val ip = (info.host as? Inet4Address)?.hostAddress ?: return@let
                            val name = info.serviceName
                            if (discoveredDevices.none { it.first == name }) {
                                discoveredDevices.add(name to ip)
                            }
                        }
                    }
                }
            } finally {
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (_: Exception) {}
                try {
                    if (multicastLock?.isHeld == true) multicastLock.release()
                } catch (_: Exception) {}
                isDiscovering = false
            }
        }

        AlertDialog(
            onDismissRequest = { showDiscoveryDialog = false },
            title = { Text("Find tCam Devices") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isDiscovering) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                    }
                    if (discoveredDevices.isEmpty()) {
                        Text(if (isDiscovering) "Searching for cameras on your network…" else "No tCam devices found.")
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            discoveredDevices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = discoverySelectedDevice == device,
                                            onClick = { discoverySelectedDevice = device },
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = discoverySelectedDevice == device,
                                        onClick = { discoverySelectedDevice = device },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(device.first, fontWeight = FontWeight.SemiBold)
                                        Text(device.second, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = discoverySelectedDevice != null,
                    onClick = {
                        discoverySelectedDevice?.let { (_, ip) -> localIp = ip }
                        showDiscoveryDialog = false
                    },
                ) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscoveryDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private val EMISSIVITY_PRESETS = listOf(
    "Aluminum, polished........5" to 5,
    "Aluminum, oxidized........25" to 25,
    "Brass, tarnished..........22" to 22,
    "Brass, polished...........3" to 3,
    "Brick, common.............85" to 85,
    "Brick, plastered..........94" to 94,
    "Carbon....................96" to 96,
    "Chipboard, untreated......90" to 90,
    "Clay, fired...............91" to 91,
    "Concrete..................95" to 95,
    "Elec Tape, Black..........96" to 96,
    "Enamel....................90" to 90,
    "Formica.................. 93" to 93,
    "Soil......................93" to 93,
    "Glass Pane................97" to 97,
    "Granite.................. 86" to 86,
    "Iron, hot rolled..........77" to 77,
    "Iron sheet, galvanized....28" to 28,
    "Lacquer, black............97" to 97,
    "Lacquer, white............87" to 87,
    "Lead, oxidized............63" to 63,
    "Leather, tanned...........77" to 77,
    "Oil, thick................82" to 82,
    "Paint, oil, avg.......... 94" to 94,
    "Paper, white..............90" to 90,
    "Plasterboard..............90" to 90,
    "Plastic, PCB..............91" to 91,
    "Plastic, PVC..............93" to 93,
    "Porcelain, glazed.........92" to 92,
    "Rubber....................94" to 94,
    "Snow......................80" to 80,
    "Steel, rolled............ 50" to 50,
    "Tar Paper................ 92" to 92,
    "Varnish, oak floor........92" to 92,
    "Water.....................98" to 98,
    "Wood, plywood.............82" to 82,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraSettingsSection(
    viewModel: CameraViewModel,
    localAgc: Boolean,
    onAgcChange: (Boolean) -> Unit,
    localEmissivity: String,
    onEmissivityChange: (String) -> Unit,
    onEmissivityConfirm: () -> Unit,
    localGainMode: Int,
    onGainModeChange: (Int) -> Unit,
) {
    val wifiInfo by viewModel.wifiInfo.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    var showEmissivityDialog by remember { mutableStateOf(false) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var showSsidScanDialog by remember { mutableStateOf(false) }
    val ssidScanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showSsidScanDialog = true }

    Text(
        text = "CAMERA SETTINGS",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )

    // AGC
    ListItem(
        headlineContent = { Text("AGC") },
        supportingContent = { Text(if (localAgc) "Enabled" else "Disabled") },
        trailingContent = {
            Switch(checked = localAgc, onCheckedChange = { onAgcChange(it) })
        },
    )

    // Emissivity
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = localEmissivity,
            onValueChange = { v ->
                if (v.all { it.isDigit() } && v.length <= 3) onEmissivityChange(v)
            },
            label = { Text("Emissivity %") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                onEmissivityConfirm()
            }),
        )
        Spacer(modifier = Modifier.width(8.dp))
        FeedbackButton(
            onClick = { showEmissivityDialog = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) { Text("Choose") }
    }

    // Gain Mode
    ListItem(
        headlineContent = { Text("Gain Mode") },
        supportingContent = { Text("Controls sensor sensitivity range") },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "High" to Constants.GAIN_MODE_HIGH,
            "Low" to Constants.GAIN_MODE_LOW,
            "Auto" to Constants.GAIN_MODE_AUTO,
        ).forEach { (label, mode) ->
            Row(
                modifier = Modifier
                    .selectable(selected = localGainMode == mode, onClick = { onGainModeChange(mode) })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = localGainMode == mode, onClick = { onGainModeChange(mode) })
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }

    // WiFi / Network
    ListItem(
        headlineContent = { Text("WiFi / Network") },
        supportingContent = { Text("Configure the camera's WiFi connection") },
        trailingContent = {
            FeedbackButton(
                onClick = {
                    viewModel.fetchWifiInfo()
                    showWifiDialog = true
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) { Text("Edit", fontSize = 12.sp) }
        },
    )

    // --- Emissivity preset dialog ---
    if (showEmissivityDialog) {
        var selectedIndex by remember {
            mutableStateOf(
                EMISSIVITY_PRESETS.indexOfFirst { it.second == (localEmissivity.toIntOrNull() ?: 90) }
                    .coerceAtLeast(0),
            )
        }
        AlertDialog(
            onDismissRequest = { showEmissivityDialog = false },
            title = { Text("Select Emissivity") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    EMISSIVITY_PRESETS.forEachIndexed { index, (label, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedIndex == index, onClick = { selectedIndex = index })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedIndex == index, onClick = { selectedIndex = index })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selectedPct = EMISSIVITY_PRESETS[selectedIndex].second
                    onEmissivityChange(selectedPct.toString())
                    onEmissivityConfirm()
                    showEmissivityDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEmissivityDialog = false }) { Text("Cancel") }
            },
        )
    }

    // --- WiFi settings dialog — mirrors the fields/layout of the original tcamViewer's
    // fragment_wifi_settings.xml: an AP/client toggle, shared SSID/password fields (meaning
    // depends on that toggle), a static-IP toggle, and the static IP/netmask fields. Saves
    // straight to the camera on its own Save button, independent of the outer Save/Cancel —
    // a WiFi change is a deliberate, separate action, not something to bundle silently in.
    if (showWifiDialog) {
        var wifiIsAccessPoint by remember { mutableStateOf(true) }
        var wifiSsid by remember { mutableStateOf("") }
        var wifiPassword by remember { mutableStateOf("") }
        var wifiPasswordVisible by remember { mutableStateOf(false) }
        var wifiUseStaticIp by remember { mutableStateOf(false) }
        var wifiStaticIp by remember { mutableStateOf("") }
        var wifiStaticNetmask by remember { mutableStateOf("") }
        var showWifiSaveConfirm by remember { mutableStateOf(false) }

        // Seed the editable fields from the camera's current status once it loads.
        LaunchedEffect(wifiInfo) {
            val info = wifiInfo ?: return@LaunchedEffect
            val flags = info["flags"]?.toIntOrNull() ?: 0
            val isClientMode = (flags and Constants.WIFI_MASK_CLIENT_MODE) != 0
            wifiIsAccessPoint = !isClientMode
            wifiUseStaticIp = (flags and Constants.WIFI_MASK_STATIC_IP) != 0
            wifiSsid = if (isClientMode) info["sta_ssid"].orEmpty() else info["ap_ssid"].orEmpty()
            wifiStaticIp = info["sta_ip_addr"].orEmpty()
            wifiStaticNetmask = info["sta_netmask"].orEmpty()
        }

        AlertDialog(
            onDismissRequest = { showWifiDialog = false },
            title = { Text("WiFi Settings") },
            text = {
                if (wifiInfo == null) {
                    CircularProgressIndicator()
                } else {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Camera is Access Point",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = wifiIsAccessPoint,
                                onCheckedChange = { wifiIsAccessPoint = it },
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            OutlinedTextField(
                                value = wifiSsid,
                                onValueChange = { wifiSsid = it },
                                label = { Text("SSID") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    showSsidScanDialog = true
                                } else {
                                    ssidScanPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Scan for networks")
                            }
                        }

                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (wifiPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { wifiPasswordVisible = !wifiPasswordVisible }) {
                                    Icon(
                                        imageVector = if (wifiPasswordVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (wifiPasswordVisible) "Hide password" else "Show password",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Use Static IP when Client",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = wifiUseStaticIp,
                                onCheckedChange = { wifiUseStaticIp = it },
                            )
                        }

                        OutlinedTextField(
                            value = wifiStaticIp,
                            onValueChange = { wifiStaticIp = it },
                            label = { Text("Client Static IP Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )

                        OutlinedTextField(
                            value = wifiStaticNetmask,
                            onValueChange = { wifiStaticNetmask = it },
                            label = { Text("Client Static IP Netmask") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = wifiInfo != null,
                    onClick = { showWifiSaveConfirm = true },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWifiDialog = false }) { Text("Cancel") }
            },
        )

        // --- Warn before applying — set_wifi restarts the camera's WiFi subsystem, which
        // drops the current connection, so this shouldn't happen silently. ---
        if (showWifiSaveConfirm) {
            AlertDialog(
                onDismissRequest = { showWifiSaveConfirm = false },
                title = { Text("Save WiFi Settings?") },
                text = {
                    Text(
                        "This will disconnect the camera. It will attempt to reconnect " +
                            "on the new network.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.sendWifiConfig(
                            wifiIsAccessPoint,
                            wifiSsid,
                            wifiPassword,
                            wifiUseStaticIp,
                            wifiStaticIp,
                            wifiStaticNetmask,
                        )
                        val reconnectIp = when {
                            wifiIsAccessPoint -> wifiInfo?.get("ap_ip_addr") ?: "192.168.4.1"
                            wifiUseStaticIp -> wifiStaticIp
                            else -> null // DHCP client — new address unknown, best-effort retry
                        }
                        viewModel.reconnectAfterWifiChange(reconnectIp)
                        showWifiSaveConfirm = false
                        showWifiDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showWifiSaveConfirm = false }) { Text("Cancel") }
                },
            )
        }

        // --- SSID scan dialog — lists nearby networks the phone's own WiFi radio can see,
        // so the user can pick one instead of typing it by hand. ---
        if (showSsidScanDialog) {
            val scannedSsids = remember { mutableStateListOf<String>() }
            var isScanningSsids by remember { mutableStateOf(false) }
            var selectedScannedSsid by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                scannedSsids.clear()
                selectedScannedSsid = null
                isScanningSsids = true
                try {
                    withTimeoutOrNull(10_000L) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context, intent: Intent) {
                                    if (cont.isActive) cont.resume(Unit) {}
                                }
                            }
                            context.registerReceiver(
                                receiver,
                                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                            )
                            cont.invokeOnCancellation {
                                runCatching { context.unregisterReceiver(receiver) }
                            }
                            // startScan() returns false when throttled (e.g. too many recent
                            // scans) — fall back to whatever results are already cached.
                            @Suppress("DEPRECATION")
                            val started = wifiManager.startScan()
                            if (!started && cont.isActive) cont.resume(Unit) {}
                        }
                    }
                } catch (_: Exception) {
                    // Permission revoked mid-scan, etc. — fall through to whatever's cached.
                }
                val results = try {
                    @Suppress("DEPRECATION")
                    wifiManager.scanResults
                        .mapNotNull { it.SSID?.takeIf(String::isNotBlank) }
                        .distinct()
                        .sorted()
                } catch (e: SecurityException) {
                    emptyList()
                }
                scannedSsids.addAll(results)
                isScanningSsids = false
            }

            AlertDialog(
                onDismissRequest = { showSsidScanDialog = false },
                title = { Text("Available Networks") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isScanningSsids) {
                            CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                        }
                        if (scannedSsids.isEmpty()) {
                            Text(if (isScanningSsids) "Scanning for networks…" else "No networks found.")
                        } else {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                scannedSsids.forEach { ssid ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selectedScannedSsid == ssid,
                                                onClick = { selectedScannedSsid = ssid },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selectedScannedSsid == ssid,
                                            onClick = { selectedScannedSsid = ssid },
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(ssid)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedScannedSsid != null,
                        onClick = {
                            selectedScannedSsid?.let { wifiSsid = it }
                            showSsidScanDialog = false
                        },
                    ) { Text("Select") }
                },
                dismissButton = {
                    TextButton(onClick = { showSsidScanDialog = false }) { Text("Cancel") }
                },
            )
        }
    }
}

/** Converts a Manual Range bound string between °C and °F, preserving the physical temperature. */
private fun convertManualBound(value: String, toCelsius: Boolean): String {
    val v = value.toFloatOrNull() ?: return value
    val converted = if (toCelsius) (v - 32f) * 5f / 9f else v * 9f / 5f + 32f
    return converted.roundToInt().toString()
}
