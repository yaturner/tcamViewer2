package com.das.tcamviewer2.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay

private val PALETTE_OPTIONS = listOf(
    "Arctic", "Banded", "Blackhot", "DoubleRainbow", "Fusion",
    "Gray", "Ironblack", "Isotherm", "Rainbow", "Sepia"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: CameraViewModel = viewModel()
) {
    val displayImageWidth = 320.dp
    val displayImageHeight = 240.dp
    val colorBarWidth = 32.dp
    val histogramWidth = 192.dp

    // Small phones in portrait are tight on width — drop the histogram to give the image more room.
    // Tablets/foldables (smallestScreenWidthDp >= 600, the Material breakpoint) keep it.
    val configuration = LocalConfiguration.current
    val isPhonePortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
        configuration.smallestScreenWidthDp < 600

    val spotmeterText by viewModel.spotmeterTemp.collectAsState()
    val maxTempText by viewModel.maxTemp.collectAsState()
    val minTempText by viewModel.minTemp.collectAsState()
    val fpsText by viewModel.fpsCounter.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isTimeLapsing by viewModel.isTimeLapsing.collectAsState()
    val isTimeLapseCapturing by viewModel.isTimeLapseCapturing.collectAsState()
    val bitmap by viewModel.currentBitmap.collectAsState()
    val currentPalette by viewModel.currentPalette.collectAsState()
    val histogram by viewModel.histogram.collectAsState()
    val currentImageDto by viewModel.currentImageDto.collectAsState()
    val spotmeterRect by viewModel.spotmeterRect.collectAsState()
    val showConnectError by viewModel.showConnectError.collectAsState()
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var streamMenuExpanded by remember { mutableStateOf(false) }
    var showTimeLapseDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.timeLapseMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Auto-dismiss the AGC hint after 10s; re-shows if AGC toggles off then on again.
    val isAGC = currentImageDto?.isAGC == true
    var showAgcHint by remember { mutableStateOf(false) }
    LaunchedEffect(isAGC) {
        if (isAGC) {
            showAgcHint = true
            delay(10_000)
            showAgcHint = false
        } else {
            showAgcHint = false
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        if (!isConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Open menu",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = if (isConnecting) "Thermal Viewer (Connecting...)"
                           else "Thermal Viewer (Disconnected)",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF80C0FF))
        ) {
            // Scale image to fit available space (important in landscape / windowed mode)
            val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val btnBarH = 56.dp + navBarInset
            val sidebarW = colorBarWidth + (if (isPhonePortrait) 0.dp else histogramWidth) + 30.dp
            val availW = maxWidth - sidebarW - 32.dp
            val availH = maxHeight - btnBarH - 16.dp
            val scale = minOf(
                availW.value / displayImageWidth.value,
                availH.value / displayImageHeight.value,
                1f
            ).coerceAtLeast(0.25f)
            val imgW = displayImageWidth * scale
            val imgH = displayImageHeight * scale

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            // --- Image + sidebar row (only when a frame is available) ---
            if (imageBitmap != null) Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 16.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // 1. MAIN PREVIEW AREA
                Column(
                    modifier = Modifier.padding(end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = spotmeterText,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .width(imgW)
                            .padding(bottom = 2.dp),
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier.size(width = imgW, height = imgH)
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

                        if (showAgcHint) {
                            Text(
                                text = "AGC on — temps unavailable",
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 2. DIAGNOSTICS & TEMPERATURE SIDEBAR
                Row(
                    modifier = Modifier.height(imgH),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color bar + its own max/min labels, grouped so the labels stay
                    // centered over the bar itself even when the histogram (much wider)
                    // sits alongside it.
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = maxTempText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 5.dp)
                        )

                        Image(
                            bitmap = colorBarBitmap,
                            contentDescription = "Color Bar Scale",
                            modifier = Modifier
                                .width(colorBarWidth)
                                .weight(1f)
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

                        Text(
                            text = minTempText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    }

                    if (histogram != null && !isPhonePortrait) {
                        val hist = histogram!!
                        val histPalette = paletteFactory.getPaletteByName(currentPalette)
                        Canvas(
                            modifier = Modifier
                                .width(histogramWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            val maxCount = hist.maxOrNull()?.coerceAtLeast(1) ?: 1
                            val rowHeight = size.height / 256f
                            for (row in 0 until 256) {
                                val idx = 255 - row
                                val barWidth = (hist[idx].toLong() * size.width / maxCount).toFloat()
                                if (barWidth > 0f) {
                                    val rgb = histPalette?.get(idx)
                                    val color = if (rgb != null)
                                        Color(red = rgb[0] / 255f, green = rgb[1] / 255f, blue = rgb[2] / 255f)
                                    else Color.Black
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(0f, row * rowHeight),
                                        size = Size(barWidth, rowHeight)
                                    )
                                }
                            }
                        }
                    }
                }
            }  // end if (imageBitmap != null)

            // 3. Menu button (top-left) — header row above is hidden once connected
            if (isConnected) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Open menu")
                }
            }

            // 3b. FPS counter (top-right) — only while streaming
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
                    .navigationBarsPadding()
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

                // Stop button (active) or Stream dropdown (idle)
                if (isStreaming || isRecording || isTimeLapsing) {
                    FeedbackButton(
                        onClick = {
                            if (isTimeLapsing) viewModel.stopTimeLapse()
                            else viewModel.toggleStreaming()
                        },
                        contentPadding = btnPadding
                    ) {
                        val label = when {
                            isTimeLapsing && isTimeLapseCapturing -> "Rec"
                            isTimeLapsing -> "Stream"
                            else -> "Stop"
                        }
                        Text(label, fontSize = 12.sp)
                    }
                } else {
                    Box {
                        FeedbackButton(
                            onClick = { streamMenuExpanded = true },
                            enabled = isConnected,
                            contentPadding = btnPadding
                        ) {
                            Text("Stream", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = streamMenuExpanded,
                            onDismissRequest = { streamMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Start") },
                                onClick = {
                                    viewModel.toggleStreaming()
                                    streamMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Record") },
                                enabled = isConnected,
                                onClick = {
                                    viewModel.toggleRecording()
                                    streamMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Time Lapse") },
                                enabled = isConnected,
                                onClick = {
                                    streamMenuExpanded = false
                                    showTimeLapseDialog = true
                                }
                            )
                        }
                    }
                }

                if (showTimeLapseDialog) {
                    TimeLapseDialog(
                        onConfirm = { intervalSec, durationSec ->
                            showTimeLapseDialog = false
                            viewModel.startTimeLapse(intervalSec, durationSec)
                        },
                        onDismiss = { showTimeLapseDialog = false }
                    )
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

private val TIMELAPSE_INTERVALS = listOf(
    1 to "1 second", 2 to "2 seconds", 5 to "5 seconds", 10 to "10 seconds",
    30 to "30 seconds", 60 to "1 minute", 120 to "2 minutes", 300 to "5 minutes"
)

private val TIMELAPSE_DURATIONS = listOf(
    30 to "30 seconds", 60 to "1 minute", 120 to "2 minutes", 300 to "5 minutes",
    600 to "10 minutes", 1800 to "30 minutes", 3600 to "1 hour", 7200 to "2 hours"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeLapseDialog(
    onConfirm: (intervalSec: Int, durationSec: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var intervalIndex by remember { mutableIntStateOf(2) }  // default: 5 seconds
    var durationIndex by remember { mutableIntStateOf(4) }  // default: 10 minutes
    var intervalExpanded by remember { mutableStateOf(false) }
    var durationExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time Lapse") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Capture one frame from the camera at the selected interval for the selected duration.")

                ExposedDropdownMenuBox(
                    expanded = intervalExpanded,
                    onExpandedChange = { intervalExpanded = it }
                ) {
                    OutlinedTextField(
                        value = TIMELAPSE_INTERVALS[intervalIndex].second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Interval") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false }
                    ) {
                        TIMELAPSE_INTERVALS.forEachIndexed { i, (_, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { intervalIndex = i; intervalExpanded = false }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = durationExpanded,
                    onExpandedChange = { durationExpanded = it }
                ) {
                    OutlinedTextField(
                        value = TIMELAPSE_DURATIONS[durationIndex].second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Duration") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = durationExpanded,
                        onDismissRequest = { durationExpanded = false }
                    ) {
                        TIMELAPSE_DURATIONS.forEachIndexed { i, (_, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { durationIndex = i; durationExpanded = false }
                            )
                        }
                    }
                }

                val frames = TIMELAPSE_DURATIONS[durationIndex].first / TIMELAPSE_INTERVALS[intervalIndex].first
                Text("$frames frames total", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    TIMELAPSE_INTERVALS[intervalIndex].first,
                    TIMELAPSE_DURATIONS[durationIndex].first
                )
            }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
