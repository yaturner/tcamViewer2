package com.das.tcamviewer2.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.das.tcamviewer2.cameraUtils
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.model.CameraViewModel
import com.das.tcamviewer2.paletteFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val histogramWidth = 192.dp

    val spotmeterText by viewModel.spotmeterTemp.collectAsState()
    val maxTempText by viewModel.maxTemp.collectAsState()
    val minTempText by viewModel.minTemp.collectAsState()
    val fpsText by viewModel.fpsCounter.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val bitmap by viewModel.currentBitmap.collectAsState()
    val currentPalette by viewModel.currentPalette.collectAsState()
    val histogram by viewModel.histogram.collectAsState()
    val currentImageDto by viewModel.currentImageDto.collectAsState()
    val spotmeterRect by viewModel.spotmeterRect.collectAsState()
    val showConnectError by viewModel.showConnectError.collectAsState()
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var streamMenuExpanded by remember { mutableStateOf(false) }

    // Histogram bitmap: built off-thread whenever frame data or palette changes
    var histogramBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(histogram, currentPalette) {
        val hist = histogram ?: return@LaunchedEffect
        histogramBitmap = withContext(Dispatchers.Default) {
            val palette = paletteFactory.getPaletteByName(currentPalette)
            val bmpWidth = 128
            val maxCount = hist.max().coerceAtLeast(1)
            val pixels = IntArray(bmpWidth * 256)
            for (row in 0 until 256) {
                val idx = 255 - row          // index 255 (hottest) at top, 0 at bottom
                val barWidth = (hist[idx].toLong() * bmpWidth / maxCount).toInt()
                val rgb = palette?.get(idx)
                val color = if (rgb != null)
                    (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
                else
                    0xFF000000.toInt()
                for (col in 0 until bmpWidth) {
                    pixels[row * bmpWidth + col] = if (col < barWidth) color else 0xFF000000.toInt()
                }
            }
            createBitmap(bmpWidth, 256).also {
                it.setPixels(pixels, 0, bmpWidth, 0, 0, bmpWidth, 256)
            }.asImageBitmap()
        }
    }

    // Build a 1×256 bitmap from palette entries: index 255 at top, index 0 at bottom
    val colorBarBitmap = remember(currentPalette) {
        val palette = paletteFactory.getPaletteByName(currentPalette)
        val pixels = IntArray(256) { i ->
            val rgb = palette?.get(255 - i)
            val r = rgb?.get(0) ?: 0
            val g = rgb?.get(1) ?: 0
            val b = rgb?.get(2) ?: 0
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        createBitmap(1, 256).also {
            it.setPixels(pixels, 0, 1, 0, 0, 1, 256)
        }.asImageBitmap()
    }

    if (showConnectError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConnectError() },
            title = { Text("Connection Failed") },
            text = { Text("The camera failed to connect, please verify that the IP address is correct and the camera is turned on.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissConnectError() }) { Text("OK") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isConnected) {
            TopAppBar(
                title = {
                    Text(
                        text = if (isConnecting) "Thermal Viewer (Connecting...)"
                               else "Thermal Viewer (Disconnected)",
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

        Box(
            modifier = Modifier
                .fillMaxSize()
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
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isConnected) {
                                    if (!isConnected) return@pointerInput
                                    detectTapGestures { offset ->
                                        val camX = (offset.x / size.width * Constants.IMAGE_WIDTH)
                                            .toInt().coerceIn(0, Constants.IMAGE_WIDTH - 1)
                                        val camY = (offset.y / size.height * Constants.IMAGE_HEIGHT)
                                            .toInt().coerceIn(0, Constants.IMAGE_HEIGHT - 1)
                                        viewModel.setSpotmeter(camX, camY)
                                    }
                                },
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

                    // Spotmeter rectangle overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        spotmeterRect?.let { rect ->
                            val sx = size.width / Constants.IMAGE_WIDTH
                            val sy = size.height / Constants.IMAGE_HEIGHT
                            val left = rect.left * sx
                            val top = rect.top * sy
                            val w = (rect.width() + 1) * sx
                            val h = (rect.height() + 1) * sy
                            // Black shadow for visibility on any palette
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(left - 1f, top - 1f),
                                size = Size(w + 2f, h + 2f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
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
                            bitmap = colorBarBitmap,
                            contentDescription = "Color Bar Scale",
                            modifier = Modifier
                                .width(colorBarWidth)
                                .fillMaxHeight()
                                .padding(end = 5.dp)
                                .pointerInput(currentPalette) {
                                    detectTapGestures { offset ->
                                        val idx = PALETTE_OPTIONS.indexOf(currentPalette)
                                        when {
                                            offset.y < size.height / 3f ->
                                                viewModel.setPalette(PALETTE_OPTIONS[(idx - 1 + PALETTE_OPTIONS.size) % PALETTE_OPTIONS.size])
                                            offset.y > size.height * 2f / 3f ->
                                                viewModel.setPalette(PALETTE_OPTIONS[(idx + 1) % PALETTE_OPTIONS.size])
                                        }
                                    }
                                },
                            contentScale = ContentScale.FillBounds
                        )

                        if (histogramBitmap != null) {
                            Image(
                                bitmap = histogramBitmap!!,
                                contentDescription = "Histogram Chart",
                                modifier = Modifier
                                    .width(histogramWidth)
                                    .fillMaxHeight()
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }

                    Text(
                        text = minTempText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }

            // 3. FPS counter (top-right) — only while streaming
            if (isStreaming) {
                Text(
                    text = fpsText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }

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
                FeedbackButton(
                    onClick = { viewModel.toggleConnection() },
                    enabled = !isConnecting,
                    contentPadding = btnPadding
                ) {
                    Text(
                        when {
                            isConnected -> "Disconnect"
                            isConnecting -> "Connecting..."
                            else -> "Connect"
                        },
                        fontSize = 12.sp
                    )
                }

                // Get — single frame capture; only meaningful when connected but not streaming
                FeedbackButton(
                    onClick = { viewModel.getImage() },
                    enabled = isConnected && !isStreaming,
                    contentPadding = btnPadding
                ) {
                    Text("Get", fontSize = 12.sp)
                }

                // Save (stub)
                FeedbackButton(
                    onClick = { cameraUtils.saveTjsn(currentImageDto!!)},
                    enabled = true,
                    contentPadding = btnPadding
                ) {
                    Text("Save", fontSize = 12.sp)
                }

                // Stream dropdown: Start/Stop + Record
                Box {
                    FeedbackButton(
                        onClick = { streamMenuExpanded = true },
                        enabled = isConnected,
                        contentPadding = btnPadding
                    ) {
                        Text(
                            if (isStreaming || isRecording) "Stop" else "Stream",
                            fontSize = 12.sp
                        )
                    }
                    DropdownMenu(
                        expanded = streamMenuExpanded,
                        onDismissRequest = { streamMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isStreaming) "Stop" else "Start") },
                            onClick = {
                                viewModel.toggleStreaming()
                                streamMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isRecording) "Stop Recording" else "Record") },
                            enabled = isConnected,
                            onClick = {
                                viewModel.toggleRecording()
                                streamMenuExpanded = false
                            }
                        )
                    }
                }

                // Palette dropdown
                Box {
                    FeedbackButton(
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
