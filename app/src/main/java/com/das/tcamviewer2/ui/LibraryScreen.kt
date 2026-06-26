package com.das.tcamviewer2.ui

import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.das.tcamviewer2.model.ImageDto
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

    // Scan on first composition
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

    // Re-sorted view derived from fileGroups + sort direction
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

    // First selected file in display order — used by Browse
    val firstSelected = remember(selectedPaths, displayGroups) {
        displayGroups.flatMap { it.second }.firstOrNull { it.absolutePath in selectedPaths }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val n = selectedPaths.size
                    Text(if (n == 0) "Library" else "$n selected")
                },
                actions = {
                    // Browse — visible when at least one item is selected
                    if (selectedPaths.isNotEmpty()) {
                        IconButton(onClick = { browseFile = firstSelected }) {
                            Icon(Icons.Default.Visibility, contentDescription = "Browse")
                        }
                        // Delete — removes files from disk and refreshes list
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
                    // Overflow dropdown
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
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
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

    // Full-screen browse window
    browseFile?.let { file ->
        BrowseWindow(file = file, onDismiss = { browseFile = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseWindow(file: File, onDismiss: () -> Unit) {
    var dto by remember { mutableStateOf<ImageDto?>(null) }

    LaunchedEffect(file) {
        dto = withContext(Dispatchers.Default) {
            runCatching { ImageDto.create(file.absolutePath, null) }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(formatFilename(file.name)) },
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
                    dto!!.bitmap != null -> Image(
                        bitmap = dto!!.bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    else -> Text("Could not load image", color = Color.White)
                }
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
