package com.das.tcamviewer2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.das.tcamviewer2.ui.theme.TcamViewer2Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TcamViewer2Theme {
                MainScreen()
            }
        }
    }
}

enum class ScreenTab(val title: String, val icon: ImageVector) {
    Camera("Camera", Icons.Filled.CameraAlt),
    Settings("Settings", Icons.Filled.Settings),
    Library("Library", Icons.Filled.PhotoLibrary)
}


@Composable
fun MainScreen() {
    var selectedTabItem by remember { mutableIntStateOf(0) }
    val tabs = ScreenTab.entries.toTypedArray()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabItem == index,
                        onClick = { selectedTabItem = index },
                        label = { Text(tab.title) },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tabs[selectedTabItem]) {
                ScreenTab.Camera -> CameraScreen()
                ScreenTab.Settings -> SettingsScreen()
                ScreenTab.Library -> GenericScreen(name = "Library")
            }
        }
    }
}

/**
 * Camera Screen featuring an integrated Material 3 TopAppBar to display the Action Menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val paletteOptions = listOf("RGB", "Grayscale", "Thermal", "Sepia")

    // State to control visibility of the menus
    var mainMenuExpanded by remember { mutableStateOf(false) }
    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var selectedPalette by remember { mutableStateOf("Default") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera") },
                actions = {
                    // 1. Direct Actions on the Top Bar
                    TextButton(onClick = {
                        Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("SAVE")
                    }

                    TextButton(onClick = {
                        Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("CONNECT")
                    }

                    TextButton(onClick = {
                        Toast.makeText(context, "Fetching data...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("GET")
                    }

                    TextButton(onClick = {
                        Toast.makeText(context, "Starting stream...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("STREAM")
                    }

                    // 2. PALETTE Anchor Item directly on the Bar
                    Box {
                        TextButton(onClick = { paletteMenuExpanded = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("PALETTE")
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Open Palettes"
                                )
                            }
                        }

                        // Dropdown opens right beneath the PALETTE text button
                        DropdownMenu(
                            expanded = paletteMenuExpanded,
                            onDismissRequest = { paletteMenuExpanded = false }
                        ) {
                            paletteOptions.forEach { palette ->
                                DropdownMenuItem(
                                    text = { Text(palette) },
                                    onClick = {
                                        selectedPalette = palette
                                        paletteMenuExpanded = false
                                        Toast.makeText(
                                            context,
                                            "Palette: $palette",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Hello Camera Screen!", fontSize = 24.sp)
                Text(
                    text = "Active Palette: $selectedPalette",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/***********************************************************
 *             SettingsScreen
 ************************************************************/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val dataManager = remember { SettingsDataManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Local state tracking variables for Dropdown UI
    var resMenuExpanded by remember { mutableStateOf(false) }
    // List of resolutions for our Export Resolution
    val resolutions = listOf("160x120", "320x240", "480x360", "640x480")
    // List of palettes for our nested dropdown
    val paletteOptions = listOf("RGB", "Grayscale", "Thermal", "Sepia")
    val savedExportRes by dataManager.exportResolutionFlow.collectAsState(initial = "")

    // State initialization with your required default values
    val cameraIpAddress by dataManager.cameraIpFlow.collectAsState(initial = "192.168.4.1")
    val exportPictureOnSave by dataManager.exportPictureFlow.collectAsState(initial = false)
    val exportMetadata by dataManager.exportMetadataFlow.collectAsState(initial = false)
    val exportImageResolution by dataManager.exportResolutionFlow.collectAsState(initial = "320x240")
    val manualRangeEnabled by dataManager.manualRangeFlow.collectAsState(initial = false)
    val savedMinVal by dataManager.minValueFlow.collectAsState(initial = "0")
    val savedMaxVal by dataManager.maxValueFlow.collectAsState(initial = "100")
    val savedPalette by dataManager.selectedPaletteFlow.collectAsState(initial = "Default")

    //  LOCAL STATE PROXIES FOR INTUITIVE TYPING ---
    var localMin by remember(savedMinVal) { mutableStateOf(savedMinVal) }
    var localMax by remember(savedMaxVal) { mutableStateOf(savedMaxVal) }

    var resSelected by remember { mutableStateOf(resolutions[1]) }

    // This local variable bridges the async DataStore flow with the UI text wrapper
    var currentResSelection by remember(savedExportRes) { mutableStateOf(savedExportRes) }

    // States to handle Palette Popup Window
    var showPaletteDialog by remember { mutableStateOf(false) }
    val paletteChoices = listOf("Default", "RGB", "Grayscale", "Thermal", "Sepia")
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

            // Camera IP Address (TextEdit / TextField)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = cameraIpAddress,
                    onValueChange = { cameraIpAddress ->
                        coroutineScope.launch { dataManager.saveCameraIp(cameraIpAddress) }
                    },
                    label = { Text("Camera IP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = false,
                    // Optimizes the digital keyboard layout for typing numbers and dots
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
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


// --- 4. POPUP DIALOG WINDOW ---
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


            }

            // --- PALETTE SETTING ROW WITH PLAY BUTTON ---
            ListItem(
                headlineContent = { Text("Palette") },
                supportingContent = { Text("Active: $savedPalette") },
                trailingContent = {
                    IconButton(onClick = {
                        tempDialogSelection = savedPalette // Reset temporary selection to current value
                        showPaletteDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Open Palette Selection"
                        )
                    }
                }
            )
// --- POPUP DIALOG WINDOW ---
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

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                thickness = 1.dp, // Optional: customize the line thickness
                color = MaterialTheme.colorScheme.outlineVariant // Optional: customize the line color
            )
        }


    }

}


@Composable
fun GenericScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Hello $name Screen!", fontSize = 24.sp)
    }
}

