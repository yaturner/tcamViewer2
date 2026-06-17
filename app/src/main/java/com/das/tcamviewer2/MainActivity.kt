package com.das.tcamviewer2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.das.tcamviewer2.ui.theme.TcamViewer2Theme

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
    Library("Library", Icons.Filled.PhotoLibrary),
    Settings("Settings", Icons.Filled.Settings)
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
                ScreenTab.Library -> GenericScreen(name = "Library")
                ScreenTab.Settings -> GenericScreen(name = "Settings")
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

    // State to control visibility of the menus
    var mainMenuExpanded by remember { mutableStateOf(false) }
    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var selectedPalette by remember { mutableStateOf("Default") }

    // List of palettes for our nested dropdown
    val paletteOptions = listOf("RGB", "Grayscale", "Thermal", "Sepia")

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
                                        Toast.makeText(context, "Palette: $palette", Toast.LENGTH_SHORT).show()
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
    }}

@Composable
fun GenericScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Hello $name Screen!", fontSize = 24.sp)
    }
}

