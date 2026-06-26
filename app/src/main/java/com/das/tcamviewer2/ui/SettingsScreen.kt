package com.das.tcamviewer2.ui

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.das.tcamviewer2.SettingsDataManager
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.model.CameraViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val dataManager = remember { SettingsDataManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val nsdManager = remember { context.getSystemService(NsdManager::class.java) }

    val isConnected by viewModel.isConnected.collectAsState()
    val cameraConfig by viewModel.cameraConfig.collectAsState()

    // Camera settings — persisted in DataStore, used as initial values
    val savedCameraAgc       by dataManager.cameraAgcFlow.collectAsState(initial = false)
    val savedCameraEmissivity by dataManager.cameraEmissivityFlow.collectAsState(initial = "94")
    val savedCameraGainMode  by dataManager.cameraGainModeFlow.collectAsState(initial = Constants.GAIN_MODE_HIGH)

    var localAgc        by remember(savedCameraAgc)        { mutableStateOf(savedCameraAgc) }
    var localEmissivity by remember(savedCameraEmissivity) { mutableStateOf(savedCameraEmissivity) }
    var localGainMode   by remember(savedCameraGainMode)   { mutableStateOf(savedCameraGainMode) }

    // When live camera config arrives, override locals and persist
    LaunchedEffect(cameraConfig) {
        cameraConfig?.let {
            val pct = (it.emissivity * 100 / 8192).coerceIn(1, 100).toString()
            localAgc        = it.agcEnabled
            localEmissivity = pct
            localGainMode   = it.gainMode
            dataManager.saveCameraAgc(it.agcEnabled)
            dataManager.saveCameraEmissivity(pct)
            dataManager.saveCameraGainMode(it.gainMode)
        }
    }

    var showDiscoveryDialog by remember { mutableStateOf(false) }
    val discoveredDevices = remember { mutableStateListOf<Pair<String, String>>() }
    var isDiscovering by remember { mutableStateOf(false) }
    var discoverySelectedDevice by remember { mutableStateOf<Pair<String, String>?>(null) }

    var showPaletteDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var tempDialogPalette by remember { mutableStateOf("Rainbow") }
    var resMenuExpanded by remember { mutableStateOf(false) }

    val resolutions = listOf("160x120", "320x240", "480x360", "640x480")
    val paletteOptions = listOf(
        "Arctic", "Banded", "Blackhot", "DoubleRainbow", "Fusion",
        "Gray", "Ironblack", "Isotherm", "Rainbow", "Sepia"
    )

    // --- Saved DataStore values (source of truth) ---
    val savedIp           by dataManager.cameraIpFlow.collectAsState(initial = "192.168.4.1")
    val savedExportPic    by dataManager.exportPictureFlow.collectAsState(initial = false)
    val savedExportMeta   by dataManager.exportMetadataFlow.collectAsState(initial = false)
    val savedExportRes    by dataManager.exportResolutionFlow.collectAsState(initial = "320x240")
    val savedManualRange  by dataManager.manualRangeFlow.collectAsState(initial = false)
    val savedMin          by dataManager.minValueFlow.collectAsState(initial = "0")
    val savedMax          by dataManager.maxValueFlow.collectAsState(initial = "100")
    val savedShutter      by dataManager.shutterSoundFlow.collectAsState(initial = true)
    val savedSpotmeter    by dataManager.spotmeterFlow.collectAsState(initial = true)
    val savedUnit         by dataManager.temperatureUnitFlow.collectAsState(initial = "Celsius")
    val savedPalette      by dataManager.selectedPaletteFlow.collectAsState(initial = "Rainbow")

    // Incrementing this forces every local state to reinitialize from saved values (Cancel)
    var resetKey by remember { mutableStateOf(0) }

    // --- Local (unsaved) working copies ---
    var localIp          by remember(savedIp, resetKey)          { mutableStateOf(savedIp) }
    var localExportPic   by remember(savedExportPic, resetKey)   { mutableStateOf(savedExportPic) }
    var localExportMeta  by remember(savedExportMeta, resetKey)  { mutableStateOf(savedExportMeta) }
    var localResolution  by remember(savedExportRes, resetKey)   { mutableStateOf(savedExportRes) }
    var localManualRange by remember(savedManualRange, resetKey) { mutableStateOf(savedManualRange) }
    var localMin         by remember(savedMin, resetKey)         { mutableStateOf(savedMin) }
    var localMax         by remember(savedMax, resetKey)         { mutableStateOf(savedMax) }
    var localShutter     by remember(savedShutter, resetKey)     { mutableStateOf(savedShutter) }
    var localSpotmeter   by remember(savedSpotmeter, resetKey)   { mutableStateOf(savedSpotmeter) }
    var localUnit        by remember(savedUnit, resetKey)        { mutableStateOf(savedUnit) }
    var localPalette     by remember(savedPalette, resetKey)     { mutableStateOf(savedPalette) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeedbackTextButton(onClick = { resetKey++; onNavigateBack() }) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FeedbackButton(onClick = {
                    coroutineScope.launch {
                        dataManager.saveCameraIp(localIp)
                        dataManager.saveExportPicture(localExportPic)
                        dataManager.saveExportMetadata(localExportMeta)
                        dataManager.saveExportResolution(localResolution)
                        dataManager.saveManualRange(localManualRange)
                        dataManager.saveMinValue(localMin)
                        dataManager.saveMaxValue(localMax)
                        dataManager.saveShutterSound(localShutter)
                        dataManager.saveSpotmeter(localSpotmeter)
                        dataManager.saveTemperatureUnit(localUnit)
                        dataManager.saveSelectedPalette(localPalette)
                        onNavigateBack()
                    }
                }) {
                    Text("Done")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Camera Settings — only shown when camera is connected
            if (isConnected) {
                CameraSettingsSection(
                    viewModel          = viewModel,
                    localAgc           = localAgc,
                    onAgcChange        = {
                        localAgc = it
                        coroutineScope.launch { dataManager.saveCameraAgc(it) }
                    },
                    localEmissivity    = localEmissivity,
                    onEmissivityChange = { localEmissivity = it },
                    onEmissivityConfirm = {
                        coroutineScope.launch { dataManager.saveCameraEmissivity(localEmissivity) }
                    },
                    localGainMode      = localGainMode,
                    onGainModeChange   = {
                        localGainMode = it
                        coroutineScope.launch { dataManager.saveCameraGainMode(it) }
                    }
                )
            }

            Text(
                text = "APPLICATION SETTINGS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
            )

            // Camera IP Address
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = localIp,
                    onValueChange = { localIp = it },
                    label = { Text("Camera IP Address") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() }
                    )
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
                    Switch(checked = localExportPic, onCheckedChange = { localExportPic = it })
                }
            )

            // Export Metadata
            ListItem(
                headlineContent = { Text("Export Metadata") },
                supportingContent = { Text(if (localExportMeta) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(checked = localExportMeta, onCheckedChange = { localExportMeta = it })
                }
            )

            // Export Resolution dropdown
            ExposedDropdownMenuBox(
                expanded = resMenuExpanded,
                onExpandedChange = { resMenuExpanded = it }
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    value = localResolution,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Export Resolution") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = resMenuExpanded,
                    onDismissRequest = { resMenuExpanded = false }
                ) {
                    resolutions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                localResolution = option
                                resMenuExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            // Manual Range toggle
            ListItem(
                headlineContent = { Text("Manual Range") },
                supportingContent = { Text(if (localManualRange) "Custom Bounds Active" else "Automatic Scaling") },
                trailingContent = {
                    Switch(checked = localManualRange, onCheckedChange = { localManualRange = it })
                }
            )

            // Manual Range min/max fields
            if (localManualRange) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = localMin,
                        onValueChange = { localMin = it },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = localMax,
                        onValueChange = { localMax = it },
                        label = { Text("Max") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                }
            )
            if (showPaletteDialog) {
                AlertDialog(
                    onDismissRequest = { showPaletteDialog = false },
                    title = { Text("Select Palette") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            paletteOptions.forEach { palette ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = palette == tempDialogPalette,
                                            onClick = { tempDialogPalette = palette }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = palette == tempDialogPalette,
                                        onClick = { tempDialogPalette = palette }
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
                    }
                )
            }

            // Shutter Sound
            ListItem(
                headlineContent = { Text("Shutter Sound") },
                supportingContent = { Text(if (localShutter) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(checked = localShutter, onCheckedChange = { localShutter = it })
                }
            )

            // Spotmeter
            ListItem(
                headlineContent = { Text("Spotmeter") },
                supportingContent = { Text(if (localSpotmeter) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(checked = localSpotmeter, onCheckedChange = { localSpotmeter = it })
                }
            )

            // Temperature Units
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Units") },
                    supportingContent = { Text("Select your global temperature unit") }
                )
                val unitOptions = listOf("Celsius (°C)", "Fahrenheit (°F)")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    unitOptions.forEach { option ->
                        val unitName = if (option.contains("Celsius")) "Celsius" else "Fahrenheit"
                        Row(
                            modifier = Modifier
                                .selectable(
                                    selected = localUnit == unitName,
                                    onClick = { localUnit = unitName }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = localUnit == unitName,
                                onClick = { localUnit = unitName }
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
                supportingContent = { Text("1.0 (1)") }
            )

            // Privacy Statement
            ListItem(
                headlineContent = { Text("Privacy Statement") },
                supportingContent = { Text("Tap to read") },
                trailingContent = {
                    IconButton(onClick = { showPrivacyDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Privacy Statement")
                    }
                }
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
                        .verticalScroll(rememberScrollState())
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
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
            }
        )
    }

    // --- Camera Discovery Dialog ---
    if (showDiscoveryDialog) {
        LaunchedEffect(Unit) {
            isDiscovering = true
            val pendingResolves = Channel<NsdServiceInfo>(Channel.UNLIMITED)

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) { isDiscovering = false }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { isDiscovering = false }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceName.lowercase().startsWith("tcam")) {
                        pendingResolves.trySend(serviceInfo)
                    }
                }
            }

            nsdManager.discoverServices(Constants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            try {
                withTimeoutOrNull(10_000L) {
                    for (serviceInfo in pendingResolves) {
                        val resolved = suspendCancellableCoroutine { cont ->
                            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { cont.resume(null) }
                                override fun onServiceResolved(info: NsdServiceInfo) { cont.resume(info) }
                            })
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
                try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
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
                                            onClick = { discoverySelectedDevice = device }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = discoverySelectedDevice == device,
                                        onClick = { discoverySelectedDevice = device }
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
                    }
                ) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscoveryDialog = false }) { Text("Cancel") }
            }
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
"Wood, plywood.............82" to 82
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
    onGainModeChange: (Int) -> Unit
) {
    val wifiInfo by viewModel.wifiInfo.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var showEmissivityDialog by remember { mutableStateOf(false) }
    var showWifiDialog by remember { mutableStateOf(false) }

    fun emissivityRaw() = (localEmissivity.toIntOrNull() ?: 94).coerceIn(1, 100) * 8192 / 100

    Text(
        text = "CAMERA SETTINGS",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp)
    )

    // AGC
    ListItem(
        headlineContent = { Text("AGC") },
        supportingContent = { Text(if (localAgc) "Enabled" else "Disabled") },
        trailingContent = {
            Switch(checked = localAgc, onCheckedChange = {
                onAgcChange(it)
                viewModel.sendCameraConfig(it, emissivityRaw(), localGainMode)
            })
        }
    )

    // Emissivity
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
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
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                viewModel.sendCameraConfig(localAgc, emissivityRaw(), localGainMode)
                onEmissivityConfirm()
            })
        )
        Spacer(modifier = Modifier.width(8.dp))
        FeedbackButton(
            onClick = { showEmissivityDialog = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) { Text("Choose") }
    }

    // Gain Mode
    ListItem(
        headlineContent = { Text("Gain Mode") },
        supportingContent = { Text("Controls sensor sensitivity range") }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("High" to Constants.GAIN_MODE_HIGH,
            "Low" to Constants.GAIN_MODE_LOW,
            "Auto" to Constants.GAIN_MODE_AUTO).forEach { (label, mode) ->
            Row(
                modifier = Modifier
                    .selectable(selected = localGainMode == mode, onClick = {
                        onGainModeChange(mode)
                        viewModel.sendCameraConfig(localAgc, emissivityRaw(), mode)
                    })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = localGainMode == mode, onClick = {
                    onGainModeChange(mode)
                    viewModel.sendCameraConfig(localAgc, emissivityRaw(), mode)
                })
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }

    // WiFi / Network
    ListItem(
        headlineContent = { Text("WiFi / Network") },
        supportingContent = { Text("View camera network status") },
        trailingContent = {
            FeedbackButton(
                onClick = {
                    viewModel.fetchWifiInfo()
                    showWifiDialog = true
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) { Text("Show", fontSize = 12.sp) }
        }
    )

    // --- Emissivity preset dialog ---
    if (showEmissivityDialog) {
        var selectedPct by remember { mutableStateOf(localEmissivity.toIntOrNull() ?: 94) }
        AlertDialog(
            onDismissRequest = { showEmissivityDialog = false },
            title = { Text("Select Emissivity") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    EMISSIVITY_PRESETS.forEach { (label, pct) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedPct == pct, onClick = { selectedPct = pct })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedPct == pct, onClick = { selectedPct = pct })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onEmissivityChange(selectedPct.toString())
                    viewModel.sendCameraConfig(localAgc, selectedPct * 8192 / 100, localGainMode)
                    onEmissivityConfirm()
                    showEmissivityDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEmissivityDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- WiFi info dialog ---
    if (showWifiDialog) {
        AlertDialog(
            onDismissRequest = { showWifiDialog = false },
            title = { Text("Network Status") },
            text = {
                when (wifiInfo) {
                    null -> CircularProgressIndicator()
                    emptyMap<String, String>() -> Text("Could not retrieve network information.")
                    else -> Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        wifiInfo!!.forEach { (key, value) ->
                            val display = if (key.contains("pw", ignoreCase = true)) "••••••" else value
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "$key:",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.45f)
                                )
                                Text(text = display, fontSize = 13.sp, modifier = Modifier.weight(0.55f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWifiDialog = false }) { Text("Close") }
            }
        )
    }
}
