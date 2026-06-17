package com.das.tcamviewer2.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var selectedPalette by remember { mutableStateOf("Rainbow") }

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
