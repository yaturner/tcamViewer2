package com.danjuliodesigns.tcamviewer2.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danjuliodesigns.tcamviewer2.cameraService
import com.danjuliodesigns.tcamviewer2.utils.DeviceFiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Full-screen browser for the files on a full tCam's micro-SD card (issue #35). Shows the folder
 * list first, then the files in the chosen folder. Downloads go to the Library's Pictures folder,
 * under a folder with the camera's name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDownloadWindow(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { DeviceFiles.picturesRoot(context) }

    var folders by remember { mutableStateOf<List<String>?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<String>?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun closeFolder() {
        openFolder = null
        files = null
        selected = emptySet()
        status = null
    }

    fun loadFolder(name: String) {
        openFolder = name
        files = null
        selected = emptySet()
        status = null
        scope.launch {
            files = cameraService.listDirectory(name)?.filter(DeviceFiles::isSafeName)?.sorted() ?: emptyList()
        }
    }

    /** Downloads [names] from [folder]. Already-imported files are skipped, not re-downloaded. */
    fun download(folder: String, names: List<String>) {
        job = scope.launch {
            var saved = 0
            var skipped = 0
            var failed = 0
            try {
                names.forEachIndexed { index, name ->
                    if (!cameraService.isConnected) {
                        failed += names.size - index
                        return@forEachIndexed
                    }
                    progress = index to names.size
                    try {
                        val json = cameraService.fetchFile(folder, name)
                        if (json == null || !json.has("radiometric")) {
                            failed++
                        } else if (DeviceFiles.saveImage(root, folder, name, json.toString()) ==
                            DeviceFiles.SaveResult.SAVED
                        ) {
                            saved++
                        } else {
                            skipped++
                        }
                    } catch (e: IOException) {
                        failed++
                    }
                }
            } finally {
                progress = null
                job = null
                status = "Saved $saved, already imported $skipped, failed $failed"
                selected = emptySet()
                if (saved > 0) onSaved()
            }
        }
    }

    fun downloadFolder(name: String) {
        scope.launch {
            val names = cameraService.listDirectory(name)
                ?.filter(DeviceFiles::isDownloadableImage)
                ?.sorted()
                .orEmpty()
            if (names.isEmpty()) {
                Toast.makeText(context, "No images in $name", Toast.LENGTH_SHORT).show()
            } else {
                download(name, names)
            }
        }
    }

    // Back steps out of a folder, or closes the window from the folder list. While downloading,
    // back cancels the download instead of leaving partway through.
    BackHandler {
        when {
            job != null -> job?.cancel()
            openFolder != null -> closeFolder()
            else -> onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        folders = cameraService.listDirectory("/")?.filter(DeviceFiles::isSafeName)?.sortedDescending()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(openFolder ?: "Camera files") },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (openFolder != null) closeFolder() else onDismiss()
                            }) {
                                if (openFolder != null) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                } else {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    if (openFolder != null) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            progress?.let { (done, total) ->
                                LinearProgressIndicator(
                                    progress = { if (total == 0) 0f else done.toFloat() / total },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("Downloading ${done + 1} of $total")
                            }
                            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (job != null) {
                                    TextButton(onClick = { job?.cancel() }) { Text("Cancel") }
                                } else {
                                    Button(
                                        enabled = selected.isNotEmpty(),
                                        onClick = { download(openFolder!!, selected.sorted()) },
                                    ) { Text("Download ${selected.size}") }
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                val current = openFolder
                if (current == null) {
                    FolderList(
                        folders = folders,
                        contentPadding = padding,
                        onOpen = { loadFolder(it) },
                        onDownloadAll = { downloadFolder(it) },
                    )
                } else {
                    FileList(
                        files = files,
                        selected = selected,
                        contentPadding = padding,
                        onToggle = { name ->
                            selected = if (name in selected) selected - name else selected + name
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderList(
    folders: List<String>?,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onDownloadAll: (String) -> Unit,
) {
    when {
        folders == null -> Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) {
            CircularProgressIndicator()
        }

        folders.isEmpty() -> Text(
            "No folders found. Check that the camera has a micro-SD card inserted.",
            modifier = Modifier.padding(contentPadding).padding(16.dp),
        )

        else -> LazyColumn(contentPadding = contentPadding) {
            items(folders) { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(name) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Text(name, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    IconButton(onClick = { onDownloadAll(name) }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Download all images in $name")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<String>?,
    selected: Set<String>,
    contentPadding: PaddingValues,
    onToggle: (String) -> Unit,
) {
    when {
        files == null -> Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) {
            CircularProgressIndicator()
        }

        files.isEmpty() -> Text(
            "This folder is empty.",
            modifier = Modifier.padding(contentPadding).padding(16.dp),
        )

        else -> LazyColumn(contentPadding = contentPadding) {
            items(files) { name ->
                val downloadable = DeviceFiles.isDownloadableImage(name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = downloadable) { onToggle(name) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = name in selected,
                        onCheckedChange = null,
                        enabled = downloadable,
                    )
                    Text(
                        if (downloadable) name else "$name (video, not supported yet)",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
