package com.das.tcamviewer2.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.das.tcamviewer2.SettingsDataManager
import kotlinx.coroutines.launch

/***********************************************************
 *             SettingsScreen
 ************************************************************/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val dataManager = remember { SettingsDataManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Local state tracking variables for Dropdown UI
    var resMenuExpanded by remember { mutableStateOf(false) }
    // List of resolutions for our Export Resolution
    val resolutions = listOf("160x120", "320x240", "480x360", "640x480")
    // List of palettes for our nested dropdown
    val paletteOptions = listOf(
        "Arctic", "Banded", "DoubleRainbow",
        "Fusion", "Grey", "Ironblack", "Rainbow", "Sepia"
    )
    val savedExportRes by dataManager.exportResolutionFlow.collectAsState(initial = "")

    val cameraIpAddress by dataManager.cameraIpFlow.collectAsState(initial = "192.168.4.1")
    var localIp by remember(cameraIpAddress) { mutableStateOf(cameraIpAddress) }
    val exportPictureOnSave by dataManager.exportPictureFlow.collectAsState(initial = false)
    val exportMetadata by dataManager.exportMetadataFlow.collectAsState(initial = false)
    val exportImageResolution by dataManager.exportResolutionFlow.collectAsState(initial = "320x240")
    val shutterSound by dataManager.shutterSoundFlow.collectAsState(initial = true)
    val spotmeter by dataManager.spotmeterFlow.collectAsState(initial = true)
    val manualRangeEnabled by dataManager.manualRangeFlow.collectAsState(initial = false)
    val savedMinVal by dataManager.minValueFlow.collectAsState(initial = "0")
    val savedMaxVal by dataManager.maxValueFlow.collectAsState(initial = "100")
    val savedUnitsF by dataManager.minValueFlow.collectAsState(initial = true)
    val savedUnitsC by dataManager.maxValueFlow.collectAsState(initial = false)
    val savedPalette by dataManager.selectedPaletteFlow.collectAsState(initial = "Default")

    //  LOCAL STATE PROXIES FOR INTUITIVE TYPING ---
    var localMin by remember(savedMinVal) { mutableStateOf(savedMinVal) }
    var localMax by remember(savedMaxVal) { mutableStateOf(savedMaxVal) }
    var localUnitsF by remember(savedUnitsF) { mutableStateOf(savedUnitsF) }
    var localUnitsC by remember(savedUnitsC) { mutableStateOf(savedUnitsC) }

    var resSelected by remember { mutableStateOf(resolutions[1]) }

    // This local variable bridges the async DataStore flow with the UI text wrapper
    var currentResSelection by remember(savedExportRes) { mutableStateOf(savedExportRes) }

    // States to handle Palette Popup Window
    var showPaletteDialog by remember { mutableStateOf(false) }
    val paletteChoices = paletteOptions
    // Tracks temporary dialog choice before user clicks "OK"
    var tempDialogSelection by remember(savedPalette) { mutableStateOf(savedPalette) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {

            // --- Group Header: APPLICATION SETTINGS ---
            Text(
                text = "APPLICATION SETTINGS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
            )

            // Camera IP Address
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = localIp,
                    onValueChange = { localIp = it },
                    label = { Text("Camera IP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            coroutineScope.launch { dataManager.saveCameraIp(localIp) }
                            keyboardController?.hide()
                        }
                    )
                )
            }

            // Export Picture on Save (On/Off Switch)
            ListItem(
                headlineContent = { Text("Export Picture on Save") },
                supportingContent = {
                    Text(if (exportPictureOnSave) "Enabled" else "Disabled")
                },
                trailingContent = {
                    Switch(
                        checked = exportPictureOnSave,
                        onCheckedChange = { isChecked ->
                            coroutineScope.launch {
                                dataManager.saveExportPicture(isChecked)
                            }
                        }
                    )
                }
            )

            // Export Meta Data (On/Off Switch)
            ListItem(
                headlineContent = { Text("Export Metadata") },
                supportingContent = {
                    Text(if (exportMetadata) "Enabled" else "Disabled")
                },
                trailingContent = {
                    Switch(
                        checked = exportMetadata,
                        onCheckedChange = { isChecked ->
                            coroutineScope.launch { dataManager.saveExportMetadata(isChecked) }
                        }
                    )
                }
            )

            //Export Resolution (drop down)
            ExposedDropdownMenuBox(
                expanded = resMenuExpanded,
                onExpandedChange = { resMenuExpanded = it }

            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(), // Anchors the menu dropdown to this layout bounds
                    value = currentResSelection,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Export Resolution") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                // The actual hidden menu that floats open when clicked
                ExposedDropdownMenu(
                    expanded = resMenuExpanded,
                    onDismissRequest = { resMenuExpanded = false }
                ) {
                    resolutions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                currentResSelection = option
                                coroutineScope.launch {
                                    dataManager.saveExportResolution(option)
                                }
                                resMenuExpanded =
                                    false // Hide menu after selectionexpanded = false // Hide menu after selection
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            // MANUAL RANGE SETTING TOGGLE ---
            ListItem(
                headlineContent = { Text("Manual Range") },
                supportingContent = { Text(if (manualRangeEnabled) "Custom Bounds Active" else "Automatic Scaling") },
                trailingContent = {
                    Switch(
                        checked = manualRangeEnabled,
                        onCheckedChange = { isChecked ->
                            coroutineScope.launch { dataManager.saveManualRange(isChecked) }
                        }
                    )
                }
            )

            // Manual Range Settings
            // When manualRangeEnabled is true, the layout engine inserts this Row block
            if (manualRangeEnabled) {
                androidx.compose.animation.AnimatedVisibility(visible = manualRangeEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                            16.dp
                        )
                    ) {
                        // Min Text Field
                        OutlinedTextField(
                            value = localMin,
                            onValueChange = { nextMin ->
                                localMin = nextMin
                                coroutineScope.launch { dataManager.saveMinValue(nextMin) }
                            },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        // Max Text Field
                        OutlinedTextField(
                            value = localMax,
                            onValueChange = { nextMax ->
                                localMax = nextMax
                                coroutineScope.launch { dataManager.saveMaxValue(nextMax) }
                            },
                            label = { Text("Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }


                // ---POPUP DIALOG WINDOW ---
                if (showPaletteDialog) {
                    AlertDialog(
                        onDismissRequest = { showPaletteDialog = false },
                        title = { Text(text = "Select Palette") },
                        text = {
                            Column {
                                paletteChoices.forEach { palette ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = (palette == tempDialogSelection),
                                                onClick = { tempDialogSelection = palette }
                                            )
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = (palette == tempDialogSelection),
                                            onClick = { tempDialogSelection = palette }
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = palette, fontSize = 16.sp)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPaletteDialog = false
                                    // Persistent save to Datastore occurs here
                                    coroutineScope.launch {
                                        dataManager.saveSelectedPalette(tempDialogSelection)
                                    }
                                }
                            ) {
                                Text("OK")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPaletteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            // --- PALETTE SETTING ROW WITH PLAY BUTTON ---
            ListItem(
                headlineContent = { Text("Palette") },
                supportingContent = { Text("Active: $savedPalette") },
                trailingContent = {
                    IconButton(onClick = {
                        tempDialogSelection =
                            savedPalette // Reset temporary selection to current value
                        showPaletteDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Open Palette Selection"
                        )
                    }
                }
            )
            // --- Palette Selection Dialog ---
            if (showPaletteDialog) {
                AlertDialog(
                    onDismissRequest = { showPaletteDialog = false },
                    title = { Text(text = "Select Palette") },
                    text = {
                        // Adding verticalScroll ensures items never clip off-screen
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()) // Makes the inner dialog scrollable
                        ) {
                            paletteChoices.forEach { palette ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = (palette == tempDialogSelection),
                                            onClick = { tempDialogSelection = palette }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (palette == tempDialogSelection),
                                        onClick = { tempDialogSelection = palette }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = palette, fontSize = 16.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showPaletteDialog = false
                                // Persistent save to Datastore occurs here
                                coroutineScope.launch {
                                    dataManager.saveSelectedPalette(tempDialogSelection)
                                }
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPaletteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Shutter Sound (On/Off Switch)
            ListItem(
                headlineContent = { Text("Shutter Sound") },
                supportingContent = {
                    Text(if (shutterSound) "Enabled" else "Disabled")
                },
                trailingContent = {
                    Switch(
                        checked = shutterSound,
                        onCheckedChange = { isChecked ->
                            coroutineScope.launch { dataManager.saveShutterSound(isChecked) }
                        }
                    )
                }
            )

            // SpotMeter (On/Off Switch)
            ListItem(
                headlineContent = { Text("Spotmeter") },
                supportingContent = {
                    Text(if (spotmeter) "Enabled" else "Disabled")
                },
                trailingContent = {
                    Switch(
                        checked = spotmeter,
                        onCheckedChange = { isChecked ->
                            coroutineScope.launch { dataManager.saveSpotmeter(isChecked) }
                        }
                    )
                }
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Units") },
                    supportingContent = { Text("Select your global temperature unitd") }
                )

                // Collect the stream state from your DataStore
                val selectedUnit by dataManager.temperatureUnitFlow.collectAsState(initial = "Celsius")
                val unitOptions = listOf("Celsius (°C)", "Fahrenheit (°F)")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)
                ) {
                    unitOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .selectable(
                                    selected = (option.startsWith(selectedUnit)),
                                    onClick = {
                                        // Extract just the name ("Celsius" or "Fahrenheit") to save to disk
                                        val unitName = if (option.contains("Celsius")) "Celsius" else "Fahrenheit"
                                        coroutineScope.launch {
                                            dataManager.saveTemperatureUnit(unitName)
                                        }
                                    }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option.startsWith(selectedUnit)),
                                onClick = {
                                    val unitName = if (option.contains("Celsius")) "Celsius" else "Fahrenheit"
                                    coroutineScope.launch {
                                        dataManager.saveTemperatureUnit(unitName)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, fontSize = 16.sp)
                        }
                    }
                }
            }
            /**** INSERT NEW COMPSABLES HERE ****/
        }
    }
}
