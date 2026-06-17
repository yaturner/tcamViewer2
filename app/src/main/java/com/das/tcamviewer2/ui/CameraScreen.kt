package com.das.tcamviewer2.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // Hardcoded layout dimensions matching your custom XML @dimen requirements
    // Adjust these sizes to fit your specific device screens or business rules!
    val displayImageWidth = 280.dp
    val displayImageHeight = 350.dp
    val colorBarWidth = 32.dp
    val histogramWidth = 64.dp

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
// Inner padding handles top bar boundaries automatically

        // Root layout container inheriting safe bounds padding window
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF80C0FF)) // maps android:color/holo_blue_bright
        ) {
            // Main Horizontal Row splitting Camera Preview window from scale sidebars
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 16.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {

                // 1. LEFT BOX CONTAINER: Main camera stream frame + Spotmeter overlay
                Box(
                    modifier = Modifier
                        .size(width = displayImageWidth, height = displayImageHeight)
                        .padding(end = 10.dp)
                ) {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_menu_camera),
                        contentDescription = "Main Camera Feed",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    // Spotmeter overlay text layout bounds
                    Text(
                        text = "24.5 °C",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(5.dp)
                    )
                }

                // 2. RIGHT SIDEBAR COLUMN: Temperatures, ColorBar, and Histograms
                Column(
                    modifier = Modifier.height(displayImageHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Max Temperature Text Label
                    Text(
                        text = "Max: 45°C",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 5.dp)
                    )

                    // Side-by-side split row matching colorbar and histogram
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vertical Thermal Color Bar
                        Image(
                            painter = painterResource(id = android.R.drawable.button_onoff_indicator_on),
                            contentDescription = "Color Bar Scale",
                            modifier = Modifier
                                .width(colorBarWidth)
                                .fillMaxHeight()
                                .padding(end = 5.dp),
                            contentScale = ContentScale.FillBounds
                        )

                        // Diagnostic Data Histogram Chart
                        Image(
                            painter = painterResource(id = android.R.drawable.ic_menu_report_image),
                            contentDescription = "Histogram Chart",
                            modifier = Modifier
                                .width(histogramWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    // Min Temperature Text Label
                    Text(
                        text = "Min: 12°C",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }

            // 3. TOP-RIGHT OVERLAY LAYER: Framerate diagnostics info text counters
            Text(
                text = "30 FPS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}
