package com.das.tcamviewer2.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.das.tcamviewer2.R
import com.das.tcamviewer2.cameraUtils
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.model.CameraViewModel
import com.das.tcamviewer2.model.MeasurementMode
import com.das.tcamviewer2.model.TempSample
import com.das.tcamviewer2.paletteFactory
import com.das.tcamviewer2.settingsDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import com.das.tcamviewer2.utils as globalUtils

private val PALETTE_OPTIONS = listOf(
    "Arctic", "Banded", "Blackhot", "DoubleRainbow", "Fusion",
    "Gray", "Ironblack", "Isotherm", "Rainbow", "Sepia",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: CameraViewModel = viewModel(),
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
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val spotmeterText by viewModel.spotmeterTemp.collectAsState()
    val maxTempText by viewModel.maxTemp.collectAsState()
    val minTempText by viewModel.minTemp.collectAsState()
    val spotmeterTempValue by viewModel.spotmeterTempValue.collectAsState()
    val maxTempValue by viewModel.maxTempValue.collectAsState()
    val minTempValue by viewModel.minTempValue.collectAsState()
    val spotmeterEnabled by viewModel.spotmeterEnabled.collectAsState()
    val measurementMode by viewModel.measurementMode.collectAsState()
    val measurementRegion by viewModel.measurementRegion.collectAsState()
    val regionAvgText by viewModel.regionAvgTemp.collectAsState()
    val regionMinText by viewModel.regionMinTemp.collectAsState()
    val regionMaxText by viewModel.regionMaxTemp.collectAsState()
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
    val tempHistory by viewModel.tempHistory.collectAsState()
    val isCelsius by viewModel.isCelsius.collectAsState()
    val exportPictureOnSave by settingsDataManager.exportPictureFlow.collectAsState(initial = false)
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var streamMenuExpanded by remember { mutableStateOf(false) }
    var showTimeLapseDialog by remember { mutableStateOf(false) }
    var showStopSaveDialog by remember { mutableStateOf(false) }
    var showTempChart by remember { mutableStateOf(false) }
    // rememberSaveable so fullscreen mode survives rotation instead of dropping back to
    // the normal view — the same class of bug as the tab reset fixed for issue #2.
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Exit fullscreen with back button before leaving the screen
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    // Hide/restore system bars when entering/exiting fullscreen
    val view = LocalView.current
    val window = remember { (view.context as Activity).window }
    DisposableEffect(isFullscreen) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(Unit) {
        viewModel.timeLapseMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.alertMessage.collect { msg ->
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

    // Fraction of the way down the color bar (0 = max/top, 1 = min/bottom) the current
    // spotmeter reading falls at, for the marker drawn beside the bar.
    val spotFraction = remember(spotmeterTempValue, maxTempValue, minTempValue) {
        val spot = spotmeterTempValue
        val max = maxTempValue
        val min = minTempValue
        if (spot != null && max != null && min != null && max != min) {
            ((max - spot) / (max - min)).coerceIn(0f, 1f)
        } else {
            null
        }
    }

    if (showConnectError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConnectError() },
            title = { Text("Connection Failed") },
            text = { Text("The camera failed to connect, please verify that the IP address is correct and the camera is turned on.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissConnectError() }) { Text("OK") }
            },
        )
    }

    if (showTempChart) {
        val chartPrimaryLabel = if (measurementMode == MeasurementMode.REGION) "Avg" else "Spot"
        TempHistoryDialog(
            samples = tempHistory,
            isCelsius = isCelsius,
            primaryLabel = chartPrimaryLabel,
            onSave = {
                if (cameraUtils.saveTempChart(tempHistory, isCelsius, chartPrimaryLabel)) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Chart saved") }
                }
            },
            onDismiss = { showTempChart = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (!isConnected && !isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Open menu",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = if (isConnecting) {
                        "Thermal Viewer (Connecting...)"
                    } else {
                        "Thermal Viewer (Disconnected)"
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF80C0FF))
                .then(if (isFullscreen && isLandscape) Modifier.padding(bottom = 24.dp) else Modifier),
        ) {
            // Scale image to fit available space (important in landscape / windowed mode)
            // Both header labels share this fixed height so the image and the color bar/
            // histogram below them start at the exact same Y, regardless of text metrics.
            val headerH = 24.dp
            val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val btnBarH = if (isFullscreen) 0.dp else 56.dp + navBarInset
            val sidebarW = colorBarWidth + (if (isPhonePortrait) 0.dp else histogramWidth) + 30.dp
            val availW = maxWidth - sidebarW - 32.dp
            // The color bar column is headerH (max label) + bar + headerH (min label) tall,
            // taller than the image alone — reserve that here or its bottom label collides
            // with the button bar in landscape, where height is the binding constraint.
            val availH = maxHeight - btnBarH - 16.dp - (headerH * 2)
            // Fullscreen is meant to maximize the image, so let it scale up past its
            // native 320x240dp size there; otherwise cap at 1x to avoid upscaling normally.
            val maxScale = if (isFullscreen) Float.MAX_VALUE else 1f
            val scale = minOf(
                availW.value / displayImageWidth.value,
                availH.value / displayImageHeight.value,
                maxScale,
            ).coerceAtLeast(0.25f)
            val imgW = displayImageWidth * scale
            val imgH = displayImageHeight * scale

            // Constrains centering to the region above the button bar — Alignment.Center on
            // a direct child would center within the *full* box, letting content bleed down
            // into the button bar whenever its height exceeds maxHeight - 2*btnBarH.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height((maxHeight - btnBarH).coerceAtLeast(0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // --- Image + sidebar row (only when a frame is available) ---
                if (imageBitmap != null) {
                    Row(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 5.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        // 1. MAIN PREVIEW AREA
                        Column(
                            modifier = Modifier.padding(end = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.width(imgW).height(headerH),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                if (measurementMode == MeasurementMode.REGION) {
                                    Text(
                                        text = "avg $regionAvgText  min $regionMinText  max $regionMaxText",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Text(
                                        text = spotmeterText,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.size(width = imgW, height = imgH),
                            ) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Thermal Camera Feed",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (measurementMode == MeasurementMode.POINT) {
                                                Modifier.pointerInput(isConnected) {
                                                    if (!isConnected) return@pointerInput
                                                    detectTapGestures { offset ->
                                                        val camX = (offset.x / size.width * Constants.IMAGE_WIDTH)
                                                            .toInt().coerceIn(0, Constants.IMAGE_WIDTH - 1)
                                                        val camY = (offset.y / size.height * Constants.IMAGE_HEIGHT)
                                                            .toInt().coerceIn(0, Constants.IMAGE_HEIGHT - 1)
                                                        viewModel.setSpotmeter(camX, camY)
                                                    }
                                                }
                                            } else {
                                                // Keyed only on isConnected/mode (not the region itself, which
                                                // changes every drag step) — always reads/writes the ViewModel's
                                                // StateFlow.value directly so the gesture never restarts mid-drag.
                                                Modifier.pointerInput(isConnected, measurementMode) {
                                                    if (!isConnected) return@pointerInput
                                                    var dragTarget = RegionDragTarget.NONE
                                                    detectDragGestures(
                                                        onDragStart = { start ->
                                                            val region = viewModel.measurementRegion.value
                                                                ?: return@detectDragGestures
                                                            val camX = start.x / size.width * Constants.IMAGE_WIDTH
                                                            val camY = start.y / size.height * Constants.IMAGE_HEIGHT
                                                            dragTarget = resolveRegionDragTarget(region, camX, camY)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            if (dragTarget == RegionDragTarget.NONE) return@detectDragGestures
                                                            val region = viewModel.measurementRegion.value
                                                                ?: return@detectDragGestures
                                                            val dCamX = dragAmount.x / size.width * Constants.IMAGE_WIDTH
                                                            val dCamY = dragAmount.y / size.height * Constants.IMAGE_HEIGHT
                                                            viewModel.setMeasurementRegion(
                                                                applyRegionDrag(region, dragTarget, dCamX, dCamY),
                                                            )
                                                        },
                                                    )
                                                }
                                            },
                                        ),
                                    contentScale = ContentScale.FillBounds,
                                )

                                // Spotmeter / region overlay — mutually exclusive with each other
                                if (measurementMode == MeasurementMode.REGION) {
                                    RegionOverlay(measurementRegion)
                                } else if (spotmeterEnabled) {
                                    SpotmeterOverlay(spotmeterRect)
                                }

                                if (showAgcHint) {
                                    Text(
                                        text = "AGC on — temps unavailable",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }

                        // 2. DIAGNOSTICS & TEMPERATURE SIDEBAR
                        Row(
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Color bar + its own max/min labels, grouped so the labels stay
                            // centered over the bar itself even when the histogram (much wider)
                            // sits alongside it. The bar itself is exactly imgH tall, starting at
                            // the same Y as the image (both headers share the fixed headerH height);
                            // the labels are allowed to add extra height rather than shrink the bar.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier.height(headerH),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Text(
                                        text = maxTempText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 5.dp),
                                    )
                                }

                                Box(modifier = Modifier.width(colorBarWidth).height(imgH)) {
                                    Image(
                                        bitmap = colorBarBitmap,
                                        contentDescription = "Color Bar Scale",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 6.dp)
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
                                        contentScale = ContentScale.FillBounds,
                                    )

                                    // Arrow marking where the current spotmeter reading falls on the bar —
                                    // on the left, in the gutter between the image and the bar itself
                                    if (spotmeterEnabled && spotFraction != null) {
                                        Canvas(modifier = Modifier.matchParentSize()) {
                                            val y = spotFraction * size.height
                                            val tipX = 6.dp.toPx()
                                            val halfHeight = 5.dp.toPx()
                                            val path = Path().apply {
                                                moveTo(tipX, y)
                                                lineTo(0f, y - halfHeight)
                                                lineTo(0f, y + halfHeight)
                                                close()
                                            }
                                            drawPath(path, color = Color.White)
                                            drawPath(path, color = Color.Black, style = Stroke(width = 1.dp.toPx()))
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier.height(headerH),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    Text(
                                        text = minTempText,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(start = 5.dp),
                                    )
                                }
                            }

                            if (histogram != null && !isPhonePortrait) {
                                val hist = histogram!!
                                val histPalette = paletteFactory.getPaletteByName(currentPalette)
                                // No label of its own — a matching spacer keeps its top aligned with
                                // the image/color bar above, which both reserve headerH for text.
                                Column {
                                    Spacer(modifier = Modifier.height(headerH))
                                    Canvas(
                                        modifier = Modifier
                                            .width(histogramWidth)
                                            .height(imgH)
                                            .padding(horizontal = 5.dp, vertical = 2.dp),
                                    ) {
                                        val maxCount = hist.maxOrNull()?.coerceAtLeast(1) ?: 1
                                        val rowHeight = size.height / 256f
                                        for (row in 0 until 256) {
                                            val idx = 255 - row
                                            val barWidth = (hist[idx].toLong() * size.width / maxCount).toFloat()
                                            if (barWidth > 0f) {
                                                val rgb = histPalette?.get(idx)
                                                val color = if (rgb != null) {
                                                    Color(red = rgb[0] / 255f, green = rgb[1] / 255f, blue = rgb[2] / 255f)
                                                } else {
                                                    Color.Black
                                                }
                                                drawRect(
                                                    color = color,
                                                    topLeft = Offset(0f, row * rowHeight),
                                                    size = Size(barWidth, rowHeight),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!isConnected) {
                    Image(
                        painter = painterResource(id = R.drawable.appicon),
                        contentDescription = "No camera image",
                        modifier = Modifier.size(width = imgW, height = imgH),
                        contentScale = ContentScale.Fit,
                    )
                } // end if (imageBitmap != null)
            } // end content-area Box

            // 3. Menu button (top-left) — header row above is hidden once connected
            if (isConnected && !isFullscreen) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Open menu")
                }
            }

            // 3b/3c. FPS counter + fullscreen toggle (top-right), stacked so neither
            // overlaps the bottom button bar (BottomEnd collided with it in portrait).
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.End,
            ) {
                if (isStreaming) {
                    Text(
                        text = fpsText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                if (imageBitmap != null && !isFullscreen) {
                    IconButton(
                        onClick = { showTempChart = true },
                        enabled = tempHistory.size > 2,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShowChart,
                            contentDescription = "Temperature history",
                        )
                    }
                }

                if (imageBitmap != null) {
                    IconButton(onClick = { isFullscreen = !isFullscreen }) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        )
                    }
                }
            }

            // 4. BUTTON BAR (bottom) — hidden in fullscreen
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val btnPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

                    // Connect / Disconnect
                    FeedbackButton(
                        onClick = { viewModel.toggleConnection() },
                        enabled = !isConnecting,
                        contentPadding = btnPadding,
                    ) {
                        Text(
                            when {
                                isConnected -> "Disconnect"
                                isConnecting -> "Connecting..."
                                else -> "Connect"
                            },
                            fontSize = 12.sp,
                        )
                    }

                    // Get — single frame capture; only meaningful when connected but not streaming
                    FeedbackButton(
                        onClick = { viewModel.getImage() },
                        enabled = isConnected && !isStreaming,
                        contentPadding = btnPadding,
                    ) {
                        Text("Get", fontSize = 12.sp)
                    }

                    // Save — only meaningful once a frame has actually been captured
                    FeedbackButton(
                        onClick = {
                            currentImageDto?.let { dto ->
                                if (cameraUtils.saveTjsn(dto)) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Image saved as ${dto.filename}")
                                    }
                                    if (exportPictureOnSave) {
                                        coroutineScope.launch {
                                            val exportBitmap = withContext(Dispatchers.Default) {
                                                buildShareBitmap(dto, isCelsius)
                                            }
                                            val folder = cameraUtils.generateNewPath()
                                            val name = dto.filename.orEmpty().removeSuffix(".tjsn").removePrefix("img_")
                                            val saved = withContext(Dispatchers.IO) {
                                                globalUtils.saveBitmap(exportBitmap, folder, name) != null
                                            }
                                            if (saved) {
                                                snackbarHostState.showSnackbar("Exported to gallery")
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = currentImageDto != null,
                        contentPadding = btnPadding,
                    ) {
                        Text("Save", fontSize = 12.sp)
                    }

                    // Stop button (active) or Stream dropdown (idle)
                    if (isStreaming || isRecording || isTimeLapsing) {
                        FeedbackButton(
                            onClick = {
                                if (isTimeLapsing || isRecording) {
                                    showStopSaveDialog = true
                                } else {
                                    viewModel.toggleStreaming()
                                }
                            },
                            contentPadding = btnPadding,
                        ) {
                            val label = when {
                                isTimeLapsing && isTimeLapseCapturing -> "Rec"
                                isTimeLapsing -> "Stream"
                                else -> "Stop"
                            }
                            Text(label, fontSize = 12.sp)
                        }
                    } else {
                        val canStream = isConnected && currentImageDto != null
                        Box {
                            FeedbackButton(
                                onClick = { streamMenuExpanded = true },
                                enabled = canStream,
                                contentPadding = btnPadding,
                            ) {
                                Text("Stream", fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = streamMenuExpanded,
                                onDismissRequest = { streamMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Start") },
                                    enabled = canStream,
                                    onClick = {
                                        viewModel.toggleStreaming()
                                        streamMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Record") },
                                    enabled = canStream,
                                    onClick = {
                                        viewModel.toggleRecording()
                                        streamMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Time Lapse") },
                                    enabled = canStream,
                                    onClick = {
                                        streamMenuExpanded = false
                                        showTimeLapseDialog = true
                                    },
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
                            onDismiss = { showTimeLapseDialog = false },
                        )
                    }

                    if (showStopSaveDialog) {
                        val label = if (isTimeLapsing) "time lapse" else "recording"
                        AlertDialog(
                            onDismissRequest = { showStopSaveDialog = false },
                            title = { Text("Save $label?") },
                            text = { Text("Do you want to save the $label, or discard it?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showStopSaveDialog = false
                                    if (isTimeLapsing) {
                                        viewModel.stopTimeLapse(save = true)
                                    } else {
                                        viewModel.stopRecording(save = true)
                                    }
                                }) { Text("Yes") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showStopSaveDialog = false
                                    if (isTimeLapsing) {
                                        viewModel.stopTimeLapse(save = false)
                                    } else {
                                        viewModel.stopRecording(save = false)
                                    }
                                }) { Text("No") }
                            },
                        )
                    }

                    // Palette dropdown — only meaningful once a frame has actually been captured
                    Box {
                        FeedbackButton(
                            onClick = { paletteMenuExpanded = true },
                            enabled = currentImageDto != null,
                            contentPadding = btnPadding,
                        ) {
                            Text(currentPalette, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = paletteMenuExpanded,
                            onDismissRequest = { paletteMenuExpanded = false },
                        ) {
                            PALETTE_OPTIONS.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.setPalette(name)
                                        paletteMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Drawn last so it renders on top of the image/buttons instead of behind them.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private val TIMELAPSE_INTERVALS = listOf(
    1 to "1 second",
    2 to "2 seconds",
    5 to "5 seconds",
    10 to "10 seconds",
    30 to "30 seconds",
    60 to "1 minute",
    120 to "2 minutes",
    300 to "5 minutes",
)

private val TIMELAPSE_DURATIONS = listOf(
    30 to "30 seconds",
    60 to "1 minute",
    120 to "2 minutes",
    300 to "5 minutes",
    600 to "10 minutes",
    1800 to "30 minutes",
    3600 to "1 hour",
    7200 to "2 hours",
    14400 to "4 hours",
    28800 to "8 hours",
    43200 to "12 hours",
    86400 to "24 hours",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeLapseDialog(
    onConfirm: (intervalSec: Int, durationSec: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var intervalIndex by remember { mutableIntStateOf(2) } // default: 5 seconds
    var durationIndex by remember { mutableIntStateOf(4) } // default: 10 minutes
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
                    onExpandedChange = { intervalExpanded = it },
                ) {
                    OutlinedTextField(
                        value = TIMELAPSE_INTERVALS[intervalIndex].second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Interval") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false },
                    ) {
                        TIMELAPSE_INTERVALS.forEachIndexed { i, (_, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    intervalIndex = i
                                    intervalExpanded = false
                                },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = durationExpanded,
                    onExpandedChange = { durationExpanded = it },
                ) {
                    OutlinedTextField(
                        value = TIMELAPSE_DURATIONS[durationIndex].second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Duration") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = durationExpanded,
                        onDismissRequest = { durationExpanded = false },
                    ) {
                        TIMELAPSE_DURATIONS.forEachIndexed { i, (_, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    durationIndex = i
                                    durationExpanded = false
                                },
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
                    TIMELAPSE_DURATIONS[durationIndex].first,
                )
            }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TempHistoryDialog(
    samples: List<TempSample>,
    isCelsius: Boolean,
    primaryLabel: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temperature History") },
        text = { TemperatureHistoryChart(samples = samples, isCelsius = isCelsius, primaryLabel = primaryLabel) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onSave()
                    onDismiss()
                },
                enabled = samples.size >= 2,
            ) { Text("Save") }
        },
    )
}

/** Rounds a raw axis range up to a "nice" tick step (1/2/5 × a power of ten) so gridlines land on
 *  round numbers instead of the raw sample min/max — same trick desktop charting tools use. */
private fun niceAxisStep(rawRange: Float, targetTicks: Int = 5): Float {
    val range = rawRange.takeIf { it > 0.01f } ?: 1f
    val rawStep = range / targetTicks
    val magnitude = 10.0.pow(floor(log10(rawStep.toDouble())))
    val residual = rawStep / magnitude
    val niceResidual = when {
        residual <= 1 -> 1.0
        residual <= 2 -> 2.0
        residual <= 5 -> 5.0
        else -> 10.0
    }
    return (niceResidual * magnitude).toFloat()
}

/** Rolling line chart of spot/max/min temperature over the last few minutes, styled after
 *  desktop thermal-camera charting tools: dark background, gridlines, and labeled axes. Max is
 *  red, the primary metric is green (bolder, the primary signal to watch), min is blue. Values
 *  are plotted exactly as recorded (whatever unit was active at sample time); only the axis
 *  suffix reflects the *current* unit, so a mid-session unit change won't retroactively relabel
 *  older samples. [primaryLabel] names the green line/field — "Spot" in Point mode, "Avg" in
 *  Region mode (max/min mean the region's own max/min in that case, not the whole frame's).
 *  Not private — reused by ChartsScreen to render saved charts identically. */
@Composable
fun TemperatureHistoryChart(
    samples: List<TempSample>,
    isCelsius: Boolean,
    primaryLabel: String = "Spot",
) {
    val unitSuffix = if (isCelsius) "°C" else "°F"
    val chartBackground = Color(0xFF1C1C1E)
    val gridColor = Color(0xFF7A7A7E)
    val axisTextColor = Color(0xFFAEAEB2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(chartBackground, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        if (samples.size < 2) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Collecting data…", color = Color.Gray)
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LegendEntry(Color(0xFFE53935), "Max")
            Spacer(modifier = Modifier.width(14.dp))
            LegendEntry(Color(0xFF43A047), primaryLabel)
            Spacer(modifier = Modifier.width(14.dp))
            LegendEntry(Color(0xFF1E88E5), "Min")
        }

        val rawYMin = samples.minOf { minOf(it.spot, it.max, it.min) }
        val rawYMax = samples.maxOf { maxOf(it.spot, it.max, it.min) }
        val yStep = niceAxisStep(rawYMax - rawYMin)
        val yMin = floor(rawYMin / yStep) * yStep
        val yMax = ceil(rawYMax / yStep) * yStep
        val yRange = (yMax - yMin).takeIf { it > 0.01f } ?: 1f
        val yTicks = ((yMax - yMin) / yStep).roundToInt().coerceAtLeast(1)

        val tStart = samples.first().timestampMs
        val tEnd = samples.last().timestampMs
        val tRange = (tEnd - tStart).takeIf { it > 0L } ?: 1L
        val totalMinutes = tRange / 60_000f
        val xStep = niceAxisStep(totalMinutes)
        val xTicks = (totalMinutes / xStep).roundToInt().coerceAtLeast(1)
        val xDecimals = when {
            xStep >= 1f -> 0
            xStep >= 0.1f -> 1
            else -> 2
        }

        val density = LocalDensity.current
        val axisTextSizePx = with(density) { 10.sp.toPx() }
        val axisTextColorArgb = axisTextColor.toArgb()

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(top = 6.dp),
        ) {
            val leftMargin = 34.dp.toPx()
            val bottomMargin = 40.dp.toPx()
            val plotWidth = size.width - leftMargin
            val plotHeight = size.height - bottomMargin

            fun xOf(t: Long) = leftMargin + (t - tStart).toFloat() / tRange * plotWidth
            fun yOf(v: Float) = plotHeight - ((v - yMin) / yRange * plotHeight)

            val axisPaint = Paint().apply {
                color = axisTextColorArgb
                textSize = axisTextSizePx
                isAntiAlias = true
            }

            // Horizontal gridlines + y-axis labels.
            for (i in 0..yTicks) {
                val v = yMin + i * yStep
                val y = yOf(v)
                drawLine(gridColor, Offset(leftMargin, y), Offset(size.width, y), strokeWidth = 1f)
                val label = "%.0f".format(v)
                val labelWidth = axisPaint.measureText(label)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    leftMargin - labelWidth - 6.dp.toPx(),
                    y + axisTextSizePx / 3,
                    axisPaint,
                )
            }

            // Vertical gridlines + x-axis (minutes) labels.
            for (i in 0..xTicks) {
                val minutesAt = i * xStep
                val tAt = tStart + (minutesAt * 60_000f).toLong()
                if (tAt > tEnd) break
                val x = xOf(tAt)
                drawLine(gridColor, Offset(x, 0f), Offset(x, plotHeight), strokeWidth = 1f)
                val label = "%.${xDecimals}f".format(minutesAt)
                val labelWidth = axisPaint.measureText(label)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x - labelWidth / 2,
                    plotHeight + axisTextSizePx + 6.dp.toPx(),
                    axisPaint,
                )
            }

            val axisTitle = "Minutes"
            val titleWidth = axisPaint.measureText(axisTitle)
            drawContext.canvas.nativeCanvas.drawText(
                axisTitle,
                leftMargin + (plotWidth - titleWidth) / 2,
                size.height - 2.dp.toPx(),
                axisPaint,
            )

            drawContext.canvas.nativeCanvas.drawText(unitSuffix, 0f, axisTextSizePx, axisPaint)

            // Samples can arrive once per frame (many per second), far denser than a marker every
            // few pixels would allow — so markers are thinned to whichever samples land at least
            // markerSpacing apart, always including the first and last point, rather than one per
            // sample. This keeps the "where a reading was taken" cue readable instead of a
            // solid smear of overlapping dots.
            val markerSpacing = 18.dp.toPx()
            val markerRadius = 2.5.dp.toPx()

            fun drawSeries(pick: (TempSample) -> Float, color: Color, strokeWidth: Float) {
                val path = Path()
                var lastMarkerX = Float.NEGATIVE_INFINITY
                samples.forEachIndexed { i, s ->
                    val px = xOf(s.timestampMs)
                    val py = yOf(pick(s))
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    val isLast = i == samples.lastIndex
                    if (px - lastMarkerX >= markerSpacing || isLast) {
                        drawCircle(color = color, radius = markerRadius, center = Offset(px, py))
                        lastMarkerX = px
                    }
                }
                drawPath(path, color = color, style = Stroke(width = strokeWidth))
            }
            drawSeries({ it.max }, Color(0xFFE53935), 3f)
            drawSeries({ it.spot }, Color(0xFF43A047), 5f)
            drawSeries({ it.min }, Color(0xFF1E88E5), 3f)
        }
    }
}

@Composable
fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 16.dp, height = 3.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = Color(0xFFAEAEB2))
    }
}

/** Which part of the region box a drag gesture is manipulating. */
private enum class RegionDragTarget { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

// Camera-pixel radius around each corner treated as a resize handle, rather than a plain move.
private const val REGION_HANDLE_HIT_PX = 10f

private fun resolveRegionDragTarget(region: Rect, camX: Float, camY: Float): RegionDragTarget {
    fun near(x: Int, y: Int) = hypot((camX - x).toDouble(), (camY - y).toDouble()) <= REGION_HANDLE_HIT_PX
    return when {
        near(region.left, region.top) -> RegionDragTarget.TOP_LEFT

        near(region.right, region.top) -> RegionDragTarget.TOP_RIGHT

        near(region.left, region.bottom) -> RegionDragTarget.BOTTOM_LEFT

        near(region.right, region.bottom) -> RegionDragTarget.BOTTOM_RIGHT

        camX >= region.left && camX <= region.right && camY >= region.top && camY <= region.bottom ->
            RegionDragTarget.MOVE

        else -> RegionDragTarget.NONE
    }
}

private fun applyRegionDrag(region: Rect, target: RegionDragTarget, dx: Float, dy: Float): Rect {
    var left = region.left
    var top = region.top
    var right = region.right
    var bottom = region.bottom
    when (target) {
        RegionDragTarget.MOVE -> {
            left += dx.roundToInt()
            right += dx.roundToInt()
            top += dy.roundToInt()
            bottom += dy.roundToInt()
        }

        RegionDragTarget.TOP_LEFT -> {
            left += dx.roundToInt()
            top += dy.roundToInt()
        }

        RegionDragTarget.TOP_RIGHT -> {
            right += dx.roundToInt()
            top += dy.roundToInt()
        }

        RegionDragTarget.BOTTOM_LEFT -> {
            left += dx.roundToInt()
            bottom += dy.roundToInt()
        }

        RegionDragTarget.BOTTOM_RIGHT -> {
            right += dx.roundToInt()
            bottom += dy.roundToInt()
        }

        RegionDragTarget.NONE -> {}
    }
    return Rect(left, top, right, bottom)
}

/** Draws the resizable region box — a hollow rectangle (same black+white outline style as
 *  SpotmeterOverlay) plus small corner handles marking the resize grab points. The image here is
 *  always shown with ContentScale.FillBounds inside a Box already sized to the image's aspect
 *  ratio, so no letterboxing correction is needed — size.width/height map directly to
 *  IMAGE_WIDTH/HEIGHT, the same assumption the drag gesture's own coordinate math uses. */
@Composable
private fun RegionOverlay(region: Rect?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val r = region ?: return@Canvas
        val sx = size.width / Constants.IMAGE_WIDTH
        val sy = size.height / Constants.IMAGE_HEIGHT
        val left = r.left * sx
        val top = r.top * sy
        val w = (r.right - r.left) * sx
        val h = (r.bottom - r.top) * sy
        val topLeft = Offset(left, top)
        val regionSize = Size(w, h)
        drawRect(color = Color.Black, topLeft = topLeft, size = regionSize, style = Stroke(width = 3.dp.toPx()))
        drawRect(color = Color.White, topLeft = topLeft, size = regionSize, style = Stroke(width = 1.dp.toPx()))

        val handleSize = 10.dp.toPx()
        listOf(
            Offset(left, top),
            Offset(left + w, top),
            Offset(left, top + h),
            Offset(left + w, top + h),
        ).forEach { c ->
            val handleTopLeft = Offset(c.x - handleSize / 2, c.y - handleSize / 2)
            val handleBoxSize = Size(handleSize, handleSize)
            drawRect(color = Color.White, topLeft = handleTopLeft, size = handleBoxSize)
            drawRect(
                color = Color.Black,
                topLeft = handleTopLeft,
                size = handleBoxSize,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
