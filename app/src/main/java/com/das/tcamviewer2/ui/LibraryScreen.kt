package com.das.tcamviewer2.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.das.tcamviewer2.model.ImageDto
import com.das.tcamviewer2.settingsDataManager
import com.das.tcamviewer2.utils as globalUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current

    var fileGroups by remember { mutableStateOf<List<Pair<String, List<File>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var sortAscending by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var browseFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val rootDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            val groups = rootDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.mapNotNull { dateDir ->
                    val files = dateDir.listFiles { f -> f.extension == "tjsn" }
                        ?.sortedByDescending { it.name }
                        ?: emptyList()
                    if (files.isNotEmpty()) dateDir.name to files else null
                }
                ?: emptyList()
            fileGroups = groups
            isLoading = false
        }
    }

    val displayGroups = remember(fileGroups, sortAscending) {
        val sortedFolders = if (sortAscending) fileGroups.sortedBy { it.first }
                            else fileGroups.sortedByDescending { it.first }
        sortedFolders.map { (folder, files) ->
            folder to if (sortAscending) files.sortedBy { it.name }
                      else files.sortedByDescending { it.name }
        }
    }

    val allPaths = remember(fileGroups) {
        fileGroups.flatMap { it.second }.map { it.absolutePath }.toSet()
    }

    val firstSelected = remember(selectedPaths, displayGroups) {
        displayGroups.flatMap { it.second }.firstOrNull { it.absolutePath in selectedPaths }
    }

    // Wrap in a Box so BrowseWindow can overlay as a sibling of the Scaffold
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val n = selectedPaths.size
                        Text(if (n == 0) "Library" else "$n selected")
                    },
                    actions = {
                        if (selectedPaths.isNotEmpty()) {
                            IconButton(onClick = { browseFile = firstSelected }) {
                                Icon(Icons.Default.Visibility, contentDescription = "Browse")
                            }
                            IconButton(onClick = {
                                selectedPaths.forEach { File(it).delete() }
                                fileGroups = fileGroups.mapNotNull { (folder, files) ->
                                    val remaining = files.filter { it.absolutePath !in selectedPaths }
                                    if (remaining.isNotEmpty()) folder to remaining else null
                                }
                                selectedPaths = emptySet()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Select all") },
                                    onClick = { selectedPaths = allPaths; menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear selections") },
                                    onClick = { selectedPaths = emptySet(); menuExpanded = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Sort ascending") },
                                    onClick = { sortAscending = true; menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort descending") },
                                    onClick = { sortAscending = false; menuExpanded = false }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                displayGroups.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No saved images", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    displayGroups.forEach { (folderName, files) ->
                        item(key = folderName) {
                            Text(
                                text = formatDateFolder(folderName),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    start = 16.dp, top = 16.dp, bottom = 4.dp
                                )
                            )
                        }
                        items(files, key = { it.absolutePath }) { file ->
                            val isSelected = file.absolutePath in selectedPaths
                            ThumbnailListItem(
                                file = file,
                                isSelected = isSelected,
                                onClick = {
                                    selectedPaths = if (isSelected)
                                        selectedPaths - file.absolutePath
                                    else
                                        selectedPaths + file.absolutePath
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Browse overlay — drawn on top of the Scaffold, fills the same area
        browseFile?.let { file ->
            BrowseWindow(
                file = file,
                onDismiss = { browseFile = null },
                onDelete = {
                    file.delete()
                    fileGroups = fileGroups.mapNotNull { (folder, files) ->
                        val remaining = files.filter { it.absolutePath != file.absolutePath }
                        if (remaining.isNotEmpty()) folder to remaining else null
                    }
                    browseFile = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseWindow(file: File, onDismiss: () -> Unit, onDelete: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dto by remember { mutableStateOf<ImageDto?>(null) }
    val tempUnit by settingsDataManager.temperatureUnitFlow.collectAsState(initial = "Celsius")
    val isCelsius = tempUnit == "Celsius"

    LaunchedEffect(file) {
        dto = withContext(Dispatchers.Default) {
            runCatching { ImageDto.create(file.absolutePath, null) }.getOrNull()
        }
    }

    val imageBitmap = remember(dto?.bitmap) { dto?.bitmap?.asImageBitmap() }

    val colorBarBitmap = remember(dto?.palette) {
        val palette = dto?.palette ?: return@remember null
        val pixels = IntArray(256) { i ->
            val rgb = palette[255 - i]
            val r = rgb?.get(0) ?: 0
            val g = rgb?.get(1) ?: 0
            val b = rgb?.get(2) ?: 0
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        createBitmap(1, 256).also { it.setPixels(pixels, 0, 1, 0, 0, 1, 256) }.asImageBitmap()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            formatFilename(file.name),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    // Share — composites the full screen (image + colorbar + temps) and shares as PNG
                    IconButton(
                        onClick = {
                            val currentDto = dto ?: return@IconButton
                            coroutineScope.launch {
                                val shareBitmap = withContext(Dispatchers.Default) {
                                    buildShareBitmap(currentDto, file, isCelsius)
                                }
                                val shareDir = File(context.cacheDir, "share")
                                    .also { it.mkdirs() }
                                val shareFile = File(shareDir, "${file.nameWithoutExtension}.png")
                                withContext(Dispatchers.IO) {
                                    shareFile.outputStream().use {
                                        shareBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    shareFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "Share thermal image")
                                )
                            }
                        },
                        enabled = dto?.bitmap != null
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    // Export — saves composite image (bitmap + colorbar + temps) to gallery
                    IconButton(
                        onClick = {
                            val currentDto = dto ?: return@IconButton
                            coroutineScope.launch {
                                val exportBitmap = withContext(Dispatchers.Default) {
                                    buildShareBitmap(currentDto, file, isCelsius)
                                }
                                val folder = file.parentFile?.name ?: "tCam"
                                val name = file.nameWithoutExtension.removePrefix("img_")
                                val saved = withContext(Dispatchers.IO) {
                                    globalUtils.saveBitmap(exportBitmap, folder, name) != null
                                }
                                Toast.makeText(
                                    context,
                                    if (saved) "Saved to gallery: $name" else "Export failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = dto?.bitmap != null
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Export to gallery")
                    }
                    // Delete — removes the file and updates the library list
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                dto == null -> CircularProgressIndicator()
                imageBitmap != null -> {
                    val currentDto = dto!!
                    val hasThermal = currentDto.tLinearEnabled != 0
                    val scale = if (currentDto.tLinearResolution == 0) 10f else 100f

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Main image with spotmeter temp overlaid at centre
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            if (hasThermal) {
                                Text(
                                    text = formatTemp(
                                        currentDto.spotmeterMean, scale, isCelsius
                                    ),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Sidebar: max temp → color bar → min temp
                        if (hasThermal && colorBarBitmap != null) {
                            Column(
                                modifier = Modifier
                                    .width(64.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 16.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatTemp(
                                        currentDto.maxTemperature, scale, isCelsius
                                    ),
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Image(
                                    bitmap = colorBarBitmap,
                                    contentDescription = "Color scale",
                                    modifier = Modifier
                                        .weight(1f)
                                        .width(28.dp)
                                        .padding(vertical = 4.dp),
                                    contentScale = ContentScale.FillBounds
                                )
                                Text(
                                    text = formatTemp(
                                        currentDto.minTemperature, scale, isCelsius
                                    ),
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                else -> Text("Could not load image", color = Color.White)
            }
        }
    }
}

@Composable
private fun ThumbnailListItem(file: File, isSelected: Boolean, onClick: () -> Unit) {
    var thumbnail by remember(file) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file) {
        thumbnail = withContext(Dispatchers.Default) {
            runCatching {
                ImageDto.create(file.absolutePath, null).bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }

    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            Box(modifier = Modifier.size(width = 80.dp, height = 60.dp)) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                    )
                }
            }
        },
        headlineContent = { Text(formatFilename(file.name)) },
        supportingContent = { Text(file.name) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatTemp(rawValue: Int, scale: Float, isCelsius: Boolean): String {
    val tempC = rawValue / scale - 273.15f
    return if (isCelsius) "%.1f°C".format(tempC) else "%.1f°F".format(tempC * 9f / 5f + 32f)
}

/** MM_dd_yyyy → "June 25, 2026" */
private fun formatDateFolder(name: String): String {
    val parts = name.split("_")
    if (parts.size != 3) return name
    val monthNames = arrayOf(
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val month = parts[0].toIntOrNull()?.let { monthNames.getOrNull(it) } ?: return name
    return "$month ${parts[1]}, ${parts[2]}"
}

/** img_HH_mm_ss.tjsn → "HH:mm:ss" */
private fun formatFilename(name: String): String {
    val base = name.removeSuffix(".tjsn").removePrefix("img_")
    val parts = base.split("_")
    return if (parts.size == 3) "${parts[0]}:${parts[1]}:${parts[2]}" else name
}

/**
 * Builds a composite share image: scaled thermal image + colour bar sidebar + temperature labels
 * + spotmeter overlay + filename header. All rendering via Android Canvas (no Compose layer).
 */
private fun buildShareBitmap(dto: ImageDto, file: File, isCelsius: Boolean): Bitmap {
    val tempScale = if (dto.tLinearResolution == 0) 10f else 100f
    val hasThermal = dto.tLinearEnabled != 0

    // All dimensions in px (off-screen bitmap — not dp)
    val imgW     = 640          // 160 × 4
    val imgH     = 480          // 120 × 4
    val headerH  = 72
    val bottomPad = 28          // keeps image/colorbar bottom away from the bitmap edge
    val sidebarW = if (hasThermal) 120 else 0
    val totalW   = imgW + sidebarW
    val totalH   = headerH + imgH + bottomPad   // 580

    val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    canvas.drawColor(android.graphics.Color.BLACK)

    // ── Header ───────────────────────────────────────────────────────────────
    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val subPaint = android.graphics.Paint().apply {
        color = 0xFFAAAAAA.toInt()
        textSize = 30f
        isAntiAlias = true
    }
    val timeLabel = formatFilename(file.name)
    canvas.drawText(timeLabel, 16f, 50f, titlePaint)
    canvas.drawText(
        file.name,
        16f + titlePaint.measureText(timeLabel) + 20f,
        50f,
        subPaint
    )

    // ── Thermal image (scaled 4×, starts at y = headerH) ────────────────────
    dto.bitmap?.let { src ->
        val scaled = Bitmap.createScaledBitmap(src, imgW, imgH, true)
        canvas.drawBitmap(scaled, 0f, headerH.toFloat(), null)
        scaled.recycle()
    }

    if (hasThermal) {
        // ── Spotmeter temp (centred on image, stroke + fill for contrast) ────
        val spotText = formatTemp(dto.spotmeterMean, tempScale, isCelsius)
        val spotStroke = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 56f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
        }
        val spotFill = android.graphics.Paint(spotStroke).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val cx = imgW / 2f
        val cy = headerH + imgH / 2f + 20f
        canvas.drawText(spotText, cx, cy, spotStroke)
        canvas.drawText(spotText, cx, cy, spotFill)

        // ── Colour bar: centred in sidebar, padded so labels fit above/below ─
        // cbPad is the gap reserved above and below the bar for the temp labels
        val cbPad = 50
        val cbW   = 44
        val cbH   = imgH - cbPad * 2         // 380 px tall
        val cbX   = imgW + (sidebarW - cbW) / 2
        val cbY   = headerH + cbPad          // 122

        val cbPixels = IntArray(256) { i ->
            val rgb = dto.palette?.get(255 - i)
            val r = rgb?.get(0) ?: 0; val g = rgb?.get(1) ?: 0; val b = rgb?.get(2) ?: 0
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val cbSrc = Bitmap.createBitmap(1, 256, Bitmap.Config.ARGB_8888)
        cbSrc.setPixels(cbPixels, 0, 1, 0, 0, 1, 256)
        val cbScaled = Bitmap.createScaledBitmap(cbSrc, cbW, cbH, true)
        canvas.drawBitmap(cbScaled, cbX.toFloat(), cbY.toFloat(), null)
        cbSrc.recycle(); cbScaled.recycle()

        // ── Max / min labels (above and below the colour bar) ────────────────
        val tempPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val sidebarCx = imgW + sidebarW / 2f
        // Max: baseline sits cbPad-10 px below the image top → above the bar
        canvas.drawText(
            formatTemp(dto.maxTemperature, tempScale, isCelsius),
            sidebarCx,
            (headerH + cbPad - 12).toFloat(),   // y ≈ 110
            tempPaint
        )
        // Min: baseline sits cbPad-12 px above the bottom of the image area
        canvas.drawText(
            formatTemp(dto.minTemperature, tempScale, isCelsius),
            sidebarCx,
            (headerH + imgH - cbPad + 34).toFloat(),  // y ≈ 504
            tempPaint
        )
    }

    return result
}
