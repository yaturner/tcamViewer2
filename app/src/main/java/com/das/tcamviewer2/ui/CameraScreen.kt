package com.das.tcamviewer2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.das.tcamviewer2.model.CameraViewModel

private val PALETTE_OPTIONS = listOf(
    "Arctic", "Banded", "Blackhot", "DoubleRainbow", "Fusion",
    "Gray", "Ironblack", "Isotherm", "Rainbow", "Sepia"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val displayImageWidth = 320.dp
    val displayImageHeight = 240.dp
    val colorBarWidth = 32.dp
    val histogramWidth = 64.dp

    val spotmeterText by viewModel.spotmeterTemp.collectAsState()
    val maxTempText by viewModel.maxTemp.collectAsState()
    val minTempText by viewModel.minTemp.collectAsState()
    val fpsText by viewModel.fpsCounter.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val bitmap by viewModel.currentBitmap.collectAsState()
    val currentPalette by viewModel.currentPalette.collectAsState()
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    var paletteMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isConnected) "Thermal Viewer (Connected)" else "Thermal Viewer (Connecting...)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF80C0FF))
        ) {
            // --- Image + sidebar row ---
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 16.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // 1. MAIN PREVIEW AREA
                Box(
                    modifier = Modifier
                        .size(width = displayImageWidth, height = displayImageHeight)
                        .padding(end = 10.dp)
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Thermal Camera Feed",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        Image(
                            painter = painterResource(id = android.R.drawable.ic_menu_camera),
                            contentDescription = "Waiting for camera",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    Text(
                        text = spotmeterText,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(5.dp)
                    )
                }

                // 2. DIAGNOSTICS & TEMPERATURE SIDEBAR
                Column(
                    modifier = Modifier.height(displayImageHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = maxTempText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 5.dp)
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = android.R.drawable.button_onoff_indicator_on),
                            contentDescription = "Color Bar Scale",
                            modifier = Modifier
                                .width(colorBarWidth)
                                .fillMaxHeight()
                                .padding(end = 5.dp),
                            contentScale = ContentScale.FillBounds
                        )

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

                    Text(
                        text = minTempText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }

            // 3. FPS counter (top-right)
            Text(
                text = fpsText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )

            // 4. BUTTON BAR (bottom)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val btnPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

                // Connect / Disconnect
                Button(
                    onClick = { viewModel.toggleConnection() },
                    contentPadding = btnPadding
                ) {
                    Text(if (isConnected) "Disconnect" else "Connect", fontSize = 12.sp)
                }

                // Get — single frame capture; only meaningful when connected but not streaming
                Button(
                    onClick = { viewModel.getImage() },
                    enabled = isConnected && !isStreaming,
                    contentPadding = btnPadding
                ) {
                    Text("Get", fontSize = 12.sp)
                }

                // Save (stub)
                Button(
                    onClick = { /* TODO */ },
                    enabled = false,
                    contentPadding = btnPadding
                ) {
                    Text("Save", fontSize = 12.sp)
                }

                // Stream / Stop
                Button(
                    onClick = { viewModel.toggleStreaming() },
                    enabled = isConnected,
                    contentPadding = btnPadding
                ) {
                    Text(if (isStreaming) "Stop" else "Stream", fontSize = 12.sp)
                }

                // Palette dropdown
                Box {
                    Button(
                        onClick = { paletteMenuExpanded = true },
                        contentPadding = btnPadding
                    ) {
                        Text(currentPalette, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = paletteMenuExpanded,
                        onDismissRequest = { paletteMenuExpanded = false }
                    ) {
                        PALETTE_OPTIONS.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.setPalette(name)
                                    paletteMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
