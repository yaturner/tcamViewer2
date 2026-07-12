package com.das.tcamviewer2.ui

import android.app.Activity
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.das.tcamviewer2.constants.Constants
import com.das.tcamviewer2.model.ImageDto
import com.das.tcamviewer2.paletteFactory
import com.das.tcamviewer2.settingsDataManager
import com.das.tcamviewer2.utils as globalUtils
import com.das.tcamviewer2.utils.VideoExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current

    var fileGroups by remember { mutableStateOf<List<Pair<String, List<File>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var sortAscending by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var browseFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: context.filesDir
            val folderMap = mutableMapOf<String, MutableList<File>>()
            for (rootDir in listOf(picturesDir, moviesDir)) {
                rootDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.forEach { dateDir ->
                        val files = dateDir.listFiles { f ->
                            f.extension == "tjsn" || f.extension == "mtjsn" || f.extension == "tltjsn"
                        } ?: return@forEach
                        if (files.isNotEmpty())
                            folderMap.getOrPut(dateDir.name) { mutableListOf() }.addAll(files)
                    }
            }
            fileGroups = folderMap.entries
                .sortedByDescending { it.key }
                .map { (folder, files) -> folder to files.sortedByDescending { it.name } }
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

    // Wrap in a Box so BrowseWindow can overlay as a sibling of the Scaffold
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val n = selectedPaths.size
                        Text(if (n == 0) "Library" else "$n selected")
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        if (selectedPaths.isNotEmpty()) {
                            IconButton(onClick = {
                                browseFiles = displayGroups
                                    .flatMap { it.second }
                                    .filter { it.absolutePath in selectedPaths }
                            }) {
                                Icon(Icons.Default.Visibility, contentDescription = "Browse")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
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
                    Text("No saved files", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    displayGroups.forEach { (folderName, files) ->
                        item(key = "header_$folderName", span = { GridItemSpan(2) }) {
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
                            ThumbnailGridCell(
                                file = file,
                                isSelected = isSelected,
                                onClick = {
                                    selectedPaths = if (isSelected)
                                        selectedPaths - file.absolutePath
                                    else
                                        selectedPaths + file.absolutePath
                                }
                            )
                        }
                    }
                }
            }
        }

        // Browse overlay — drawn on top of the Scaffold, fills the same area
        if (browseFiles.isNotEmpty()) {
            BrowseWindow(
                files = browseFiles,
                onDismiss = { browseFiles = emptyList() },
                onDelete = { deletedFile ->
                    deletedFile.delete()
                    fileGroups = fileGroups.mapNotNull { (folder, files) ->
                        val remaining = files.filter { it.absolutePath != deletedFile.absolutePath }
                        if (remaining.isNotEmpty()) folder to remaining else null
                    }
                    browseFiles = browseFiles.filter { it.absolutePath != deletedFile.absolutePath }
                }
            )
        }

        if (showDeleteConfirm) {
            val n = selectedPaths.size
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete $n file${if (n == 1) "" else "s"}?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedPaths.forEach { File(it).delete() }
                        fileGroups = fileGroups.mapNotNull { (folder, files) ->
                            val remaining = files.filter { it.absolutePath !in selectedPaths }
                            if (remaining.isNotEmpty()) folder to remaining else null
                        }
                        selectedPaths = emptySet()
                        showDeleteConfirm = false
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseWindow(
    files: List<File>,
    onDismiss: () -> Unit,
    onDelete: (File) -> Unit
) {
    BackHandler(onBack = onDismiss)

    var currentIndex by remember { mutableIntStateOf(0) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Keep index in bounds when the list shrinks after a delete
    LaunchedEffect(files.size) {
        if (files.isEmpty()) onDismiss()
        else currentIndex = currentIndex.coerceAtMost(files.size - 1)
    }

    val file = files.getOrNull(currentIndex) ?: return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dto by remember { mutableStateOf<ImageDto?>(null) }
    val tempUnit by settingsDataManager.temperatureUnitFlow.collectAsState(initial = "Celsius")
    val isCelsius = tempUnit == "Celsius"

    LaunchedEffect(file) {
        dto = null   // show spinner while loading the new image
        dto = withContext(Dispatchers.Default) {
            runCatching {
                if (file.extension == "mtjsn" || file.extension == "tltjsn") {
                    val json = readFirstMtjsnFrame(file) ?: return@runCatching null
                    ImageDto.create(json, null)
                } else {
                    ImageDto.create(file.absolutePath, null)
                }
            }.getOrNull()
        }
    }

    // dto.bitmap is already colour-mapped by processImageResponse using the palette from metadata
    val imageBitmap = remember(dto?.paletteName, dto?.bitmap) { dto?.bitmap?.asImageBitmap() }

    // Build the colour bar from the same palette name stored in the dto metadata
    val colorBarBitmap = remember(dto?.paletteName) {
        val palette = paletteFactory.getPaletteByName(dto?.paletteName) ?: return@remember null
        val pixels = IntArray(256) { i ->
            val rgb = palette[255 - i]
            val r = rgb?.get(0) ?: 0
            val g = rgb?.get(1) ?: 0
            val b = rgb?.get(2) ?: 0
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        createBitmap(1, 256).also { it.setPixels(pixels, 0, 1, 0, 0, 1, 256) }.asImageBitmap()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    // Play — opens video player for .mtjsn recordings and .tltjsn time lapses
                    if (file.extension == "mtjsn" || file.extension == "tltjsn") {
                        IconButton(onClick = { showVideoPlayer = true }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play video")
                        }
                    }
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
                    // Delete — removes the current file from the list
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                dto == null -> CircularProgressIndicator()
                imageBitmap != null -> {
                    val currentDto = dto!!
                    val hasThermal = currentDto.tLinearEnabled != 0
                    val scale = if (currentDto.tLinearResolution == 0) 10f else 100f
                    val colorBar = colorBarBitmap

                    val hasSidebar = hasThermal && colorBar != null
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val sidebarW = if (hasSidebar) 64.dp else 0.dp
                        val labelH = if (hasThermal) 26.dp else 0.dp
                        val availW = (maxWidth - sidebarW).coerceAtLeast(1.dp)
                        val availH = (maxHeight - labelH).coerceAtLeast(1.dp)
                        val fitScale = minOf(
                            availW.value / Constants.IMAGE_WIDTH,
                            availH.value / Constants.IMAGE_HEIGHT
                        )
                        val imgW = (Constants.IMAGE_WIDTH * fitScale).dp
                        val imgH = (Constants.IMAGE_HEIGHT * fitScale).dp

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Main image: spotmeter temp above, hotspot square drawn on the image.
                            // Header reserves the same labelH used for the sidebar's max-temp
                            // label, so the image and the color bar start at the same Y.
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (hasThermal) {
                                    Box(
                                        modifier = Modifier.width(imgW).height(labelH),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
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
                                Box(modifier = Modifier.size(width = imgW, height = imgH)) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.FillBounds
                                    )
                                    if (hasThermal) {
                                        SpotmeterOverlay(currentDto.spotmeterLocation)
                                    }
                                }
                            }

                            // Sidebar: max temp → color bar → min temp. The bar itself is
                            // exactly imgH tall; the labels add extra height above/below
                            // rather than shrinking it.
                            if (hasThermal && colorBar != null) {
                                Column(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier.height(labelH),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Text(
                                            text = formatTemp(
                                                currentDto.maxTemperature, scale, isCelsius
                                            ),
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Image(
                                        bitmap = colorBar,
                                        contentDescription = "Color scale",
                                        modifier = Modifier
                                            .height(imgH)
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
                }
                else -> Text("Could not load image", color = Color.White)
            }
        } // end image Box

        // Prev / Next navigation — only shown when browsing multiple selected images
        if (files.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { currentIndex-- },
                    enabled = currentIndex > 0
                ) {
                    Icon(
                        Icons.Default.NavigateBefore,
                        contentDescription = "Previous",
                        tint = Color.White
                    )
                }
                Text(
                    text = "${currentIndex + 1} / ${files.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    onClick = { currentIndex++ },
                    enabled = currentIndex < files.size - 1
                ) {
                    Icon(
                        Icons.Default.NavigateNext,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
            }
        }
        } // end Column
    }
    if (showVideoPlayer) {
        VideoPlayerWindow(file = file, onDismiss = { showVideoPlayer = false })
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this file?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(file)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
    } // end Box
}

@Composable
private fun ThumbnailGridCell(file: File, isSelected: Boolean, onClick: () -> Unit) {
    var thumbnail by remember(file) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file) {
        thumbnail = withContext(Dispatchers.Default) {
            runCatching {
                if (file.extension == "mtjsn" || file.extension == "tltjsn") {
                    val json = readFirstMtjsnFrame(file) ?: return@runCatching null
                    ImageDto.create(json, null).bitmap?.asImageBitmap()
                } else {
                    ImageDto.create(file.absolutePath, null).bitmap?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
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
            if (file.extension == "mtjsn") {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video recording",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(20.dp)
                )
            }
            if (file.extension == "tltjsn") {
                Icon(
                    imageVector = Icons.Default.Timelapse,
                    contentDescription = "Time lapse",
                    tint = Color.Yellow,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(20.dp)
                )
            }
        }
        Text(
            text = formatFilename(file.name),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        )
    }
}

internal fun formatTemp(rawValue: Int, scale: Float, isCelsius: Boolean): String {
    val tempC = rawValue / scale - 273.15f
    return if (isCelsius) "%.1f°C".format(tempC) else "%.1f°F".format(tempC * 9f / 5f + 32f)
}

/** MM_dd_yyyy → "June 25, 2026" */
internal fun formatDateFolder(name: String): String {
    val parts = name.split("_")
    if (parts.size != 3) return name
    val monthNames = arrayOf(
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val month = parts[0].toIntOrNull()?.let { monthNames.getOrNull(it) } ?: return name
    return "$month ${parts[1]}, ${parts[2]}"
}

/** img_HH_mm_ss.tjsn → "HH:mm:ss",  vid_HH_mm_ss.mtjsn → "HH:mm:ss",  tl_HH_mm_ss.tltjsn → "HH:mm:ss" */
internal fun formatFilename(name: String): String {
    val base = when {
        name.endsWith(".mtjsn")  -> name.removeSuffix(".mtjsn").removePrefix("vid_")
        name.endsWith(".tltjsn") -> name.removeSuffix(".tltjsn").removePrefix("tl_")
        else                     -> name.removeSuffix(".tjsn").removePrefix("img_")
    }
    val parts = base.split("_")
    return if (parts.size == 3) "${parts[0]}:${parts[1]}:${parts[2]}" else name
}

private data class VideoFrame(val bitmap: ImageBitmap, val dto: ImageDto, val timestampMs: Long)
private data class MtjsnContent(val frames: List<JSONObject>, val videoInfo: JSONObject?)

private suspend fun readMtjsnContent(file: File): MtjsnContent =
    withContext(Dispatchers.IO) {
        val frames = mutableListOf<JSONObject>()
        val sb = StringBuilder()
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                for (i in 0 until n) {
                    val b = buf[i].toInt() and 0xFF
                    if (b == 0x03) {
                        if (sb.isNotEmpty()) {
                            runCatching {
                                val json = JSONObject(sb.toString())
                                if (json.has("radiometric")) frames.add(json)
                            }
                            sb.clear()
                        }
                    } else {
                        sb.append(b.toChar())
                    }
                }
            }
        }
        // Content remaining after the last ETX (no trailing ETX) is the footer JSON
        val footer = if (sb.isNotEmpty())
            runCatching { JSONObject(sb.toString()).optJSONObject("video_info") }.getOrNull()
        else null
        MtjsnContent(frames, footer)
    }

internal fun calculateFrameInterval(videoInfo: JSONObject?, numFrames: Int): Long {
    if (videoInfo == null || numFrames <= 1) return 125L
    return runCatching {
        val startMs = parseVideoTimeMs(videoInfo.getString("start_time")) ?: return@runCatching 125L
        val endMs   = parseVideoTimeMs(videoInfo.getString("end_time"))   ?: return@runCatching 125L
        if (endMs > startMs) (endMs - startMs) / numFrames else 125L
    }.getOrElse { 125L }
}

internal fun parseVideoTimeMs(t: String): Long? = runCatching {
    val p = t.split(":")
    val ms = p[2].split(".").let { it.getOrNull(1)?.padEnd(3,'0')?.take(3)?.toLong() ?: 0L }
    p[0].toLong() * 3_600_000L + p[1].toLong() * 60_000L + p[2].split(".")[0].toLong() * 1_000L + ms
}.getOrNull()

internal fun parseFrameTimestampMs(json: JSONObject): Long =
    runCatching {
        val timeStr = json.getJSONObject("metadata").optString("Time", "")
        parseVideoTimeMs(timeStr) ?: 0L
    }.getOrElse { 0L }

private const val SKIP_FRAMES = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayerWindow(file: File, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    var videoFrames by remember { mutableStateOf<List<VideoFrame>>(emptyList()) }
    var frameIntervals by remember { mutableStateOf<List<Long>>(emptyList()) }
    var fallbackIntervalMs by remember { mutableStateOf(125L) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val tempUnit by settingsDataManager.temperatureUnitFlow.collectAsState(initial = "Celsius")
    val isCelsius = tempUnit == "Celsius"
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Exit fullscreen with back button before closing the player
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

    LaunchedEffect(file) {
        isLoading = true
        isPlaying = false
        currentIndex = 0
        val content = withContext(Dispatchers.IO) { readMtjsnContent(file) }
        fallbackIntervalMs = calculateFrameInterval(content.videoInfo, content.frames.size)
        val loaded = ArrayList<VideoFrame>(content.frames.size)
        withContext(Dispatchers.Default) {
            for (json in content.frames) {
                val dto = runCatching { ImageDto.create(json, null) }.getOrNull() ?: continue
                val bmp = dto.bitmap?.asImageBitmap() ?: continue
                loaded.add(VideoFrame(bmp, dto, parseFrameTimestampMs(json)))
            }
        }
        val intervals = ArrayList<Long>(loaded.size)
        if (file.extension == "tltjsn") {
            // Time lapse: ignore capture timestamps, play at smooth 8 fps
            repeat(loaded.size) { intervals.add(125L) }
        } else {
            // Compute per-frame display durations from consecutive metadata timestamps.
            // Fall back to the average interval for any gap that looks wrong (<10ms or >5s).
            val fb = fallbackIntervalMs
            for (i in 0 until loaded.size - 1) {
                val dt = loaded[i + 1].timestampMs - loaded[i].timestampMs
                intervals.add(if (dt in 10L..5_000L) dt else fb)
            }
            intervals.add(intervals.lastOrNull() ?: fb)  // last frame: same duration as preceding
        }
        videoFrames = loaded
        frameIntervals = intervals
        isLoading = false
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying || videoFrames.isEmpty()) return@LaunchedEffect
        while (isPlaying) {
            delay(frameIntervals.getOrElse(currentIndex) { fallbackIntervalMs })
            val next = currentIndex + 1
            if (next >= videoFrames.size) {
                isPlaying = false
            } else {
                currentIndex = next
            }
        }
    }

    val currentFrame = videoFrames.getOrNull(currentIndex)
    val hasThermal = currentFrame?.dto?.tLinearEnabled != 0
    val tempScale = if (currentFrame?.dto?.tLinearResolution == 0) 10f else 100f

    val colorBarBitmap = remember(currentFrame?.dto?.paletteName) {
        val palette = paletteFactory.getPaletteByName(currentFrame?.dto?.paletteName)
            ?: return@remember null
        val pix = IntArray(256) { i ->
            val rgb = palette[255 - i]
            (0xFF shl 24) or ((rgb?.get(0) ?: 0) shl 16) or ((rgb?.get(1) ?: 0) shl 8) or (rgb?.get(2) ?: 0)
        }
        createBitmap(1, 256).also { it.setPixels(pix, 0, 1, 0, 0, 1, 256) }.asImageBitmap()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    title = {
                        Column {
                            Text(formatFilename(file.name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (isLoading) "Loading…" else "${videoFrames.size} frames",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            // Share — encodes the frames to MP4 (correct per-frame timing) and shares it
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isExporting = true
                                        val mp4 = runCatching {
                                            exportVideoAsMp4(context, file, videoFrames, frameIntervals)
                                        }.getOrNull()
                                        isExporting = false
                                        if (mp4 == null) {
                                            Toast.makeText(context, "Export failed", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            mp4
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Share thermal video")
                                        )
                                    }
                                },
                                enabled = videoFrames.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            // Export — encodes the frames to MP4 (correct per-frame timing) and saves to gallery
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isExporting = true
                                        val mp4 = runCatching {
                                            exportVideoAsMp4(context, file, videoFrames, frameIntervals)
                                        }.getOrNull()
                                        val saved = mp4?.let {
                                            val folder = file.parentFile?.name ?: "tCam"
                                            val name = file.nameWithoutExtension
                                                .removePrefix("vid_").removePrefix("tl_")
                                            withContext(Dispatchers.IO) {
                                                globalUtils.saveVideo(it, folder, name) != null
                                            }
                                        } ?: false
                                        isExporting = false
                                        Toast.makeText(
                                            context,
                                            if (saved) "Saved to gallery" else "Export failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                enabled = videoFrames.isNotEmpty()
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = "Export to gallery")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { isFullscreen = !isFullscreen },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    videoFrames.isEmpty() -> Text("No frames to display", color = Color.White)
                    currentFrame != null -> {
                        val colorBar = colorBarBitmap
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val hasSidebar = hasThermal && colorBar != null
                            val sidebarW = if (hasSidebar) 64.dp else 0.dp
                            val labelH = if (hasThermal) 26.dp else 0.dp
                            val availW = (maxWidth - sidebarW).coerceAtLeast(1.dp)
                            val availH = (maxHeight - labelH).coerceAtLeast(1.dp)
                            val fitScale = minOf(
                                availW.value / Constants.IMAGE_WIDTH,
                                availH.value / Constants.IMAGE_HEIGHT
                            )
                            val imgW = (Constants.IMAGE_WIDTH * fitScale).dp
                            val imgH = (Constants.IMAGE_HEIGHT * fitScale).dp

                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Header reserves the same labelH used for the sidebar's max-temp
                                // label, so the image and the color bar start at the same Y.
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (hasThermal) {
                                        Box(
                                            modifier = Modifier.width(imgW).height(labelH),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Text(
                                                text = formatTemp(currentFrame.dto.spotmeterMean, tempScale, isCelsius),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.size(width = imgW, height = imgH)) {
                                        Image(
                                            bitmap = currentFrame.bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.FillBounds
                                        )
                                        if (hasThermal) {
                                            SpotmeterOverlay(currentFrame.dto.spotmeterLocation)
                                        }
                                    }
                                }
                                if (hasThermal && colorBar != null) {
                                    // Bar itself is exactly imgH tall; labels add extra height.
                                    Column(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .padding(horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier.height(labelH),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Text(formatTemp(currentFrame.dto.maxTemperature, tempScale, isCelsius),
                                                fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
                                        }
                                        Image(bitmap = colorBar, contentDescription = null,
                                            modifier = Modifier.height(imgH).width(28.dp).padding(vertical = 4.dp),
                                            contentScale = ContentScale.FillBounds)
                                        Text(formatTemp(currentFrame.dto.minTemperature, tempScale, isCelsius),
                                            fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isLoading && videoFrames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222222))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { currentIndex = (currentIndex - SKIP_FRAMES).coerceAtLeast(0) },
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Skip back", tint = Color.White)
                    }
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { currentIndex = (currentIndex + SKIP_FRAMES).coerceAtMost(videoFrames.size - 1) },
                        enabled = currentIndex < videoFrames.size - 1
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Skip forward", tint = Color.White)
                    }
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = {
                            isPlaying = false
                            currentIndex = it.toInt().coerceIn(0, videoFrames.size - 1)
                        },
                        valueRange = 0f..maxOf(videoFrames.size - 1, 0).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.Gray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${currentIndex + 1}/${videoFrames.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = androidx.compose.ui.Modifier.width(52.dp).padding(start = 4.dp)
                    )
                    IconButton(onClick = { isFullscreen = !isFullscreen }) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** Encodes [frames] to an MP4 in the app's share cache dir, using [intervals] for per-frame timing. */
private suspend fun exportVideoAsMp4(
    context: android.content.Context,
    sourceFile: File,
    frames: List<VideoFrame>,
    intervals: List<Long>
): File = withContext(Dispatchers.Default) {
    val (rawW, rawH) = parseResolution(settingsDataManager.getExportResolution()) ?: (320 to 240)
    val width = VideoExporter.align16(rawW)
    val height = VideoExporter.align16(rawH)
    val bitmaps = frames.map { it.dto.bitmap!! }
    val exportDir = File(context.cacheDir, "share").also { it.mkdirs() }
    val outputFile = File(exportDir, "${sourceFile.nameWithoutExtension}.mp4")
    VideoExporter.exportMp4(bitmaps, intervals, outputFile, width, height)
    outputFile
}

internal fun parseResolution(s: String): Pair<Int, Int>? {
    val parts = s.lowercase().split("x")
    if (parts.size != 2) return null
    val w = parts[0].trim().toIntOrNull() ?: return null
    val h = parts[1].trim().toIntOrNull() ?: return null
    return w to h
}

private suspend fun readFirstMtjsnFrame(file: File): JSONObject? =
    withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder()
            file.inputStream().use { stream ->
                val buf = ByteArray(8192)
                var done = false
                while (!done) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    for (i in 0 until n) {
                        val b = buf[i].toInt() and 0xFF
                        if (b == 0x03) { done = true; break }
                        sb.append(b.toChar())
                    }
                }
            }
            if (sb.isEmpty()) null else JSONObject(sb.toString())
        }.getOrNull()
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
