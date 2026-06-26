package com.das.tcamviewer2.ui

import android.os.Environment
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
import androidx.core.graphics.createBitmap
import com.das.tcamviewer2.model.ImageDto
import com.das.tcamviewer2.settingsDataManager
import java.io.File
import kotlinx.coroutines.Dispatchers
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
            BrowseWindow(file = file, onDismiss = { browseFile = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseWindow(file: File, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

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
