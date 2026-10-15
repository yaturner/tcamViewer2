package com.danjuliodesigns.tcamviewer2.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danjuliodesigns.tcamviewer2.model.CameraViewModel
import com.danjuliodesigns.tcamviewer2.model.FirmwareUpdateState
import com.danjuliodesigns.tcamviewer2.utils.Firmware

/** Check for new camera firmware and install it over the air — ported from Dan Julio's tCam
 *  desktop app's "Check for new Firmware" / "Update Firmware" Camera menu items (via
 *  tcamViewerDesktop), combined into one dialog. Opening it runs the check. */
@Composable
fun FirmwareUpdateDialog(viewModel: CameraViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val check by viewModel.firmwareCheck.collectAsState()
    val update by viewModel.firmwareUpdate.collectAsState()

    // What to install once the user OKs the warning: label + picked file (null = latest download)
    var pendingInstall by remember { mutableStateOf<Pair<String, Uri?>?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name =
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "selected file"
            pendingInstall = name to uri
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetFirmwareUpdate()
        viewModel.checkFirmware()
    }

    val busy =
        update is FirmwareUpdateState.Downloading ||
            update is FirmwareUpdateState.WaitingForConfirmation ||
            update is FirmwareUpdateState.Transferring

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Camera Firmware") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val c = check
                if (c == null || (c.checking && c.installedVersion.isEmpty())) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Reading camera status…")
                    }
                } else if (c.installedVersion.isEmpty()) {
                    Text(c.error ?: "Camera status unavailable", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Camera: ${c.cameraName} (${Firmware.cameraTypeName(c.model)})")
                    Text("Installed firmware: ${c.installedVersion}")
                    val latest = c.latest
                    when {
                        c.checking -> Text("Checking danjuliodesigns.com…")

                        latest != null -> {
                            val newer = Firmware.isNewer(latest.version, c.installedVersion)
                            Text(
                                "Latest firmware: ${latest.version}" + if (newer) " — update available" else " — up to date",
                                color = if (newer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (latest.description.isNotBlank()) {
                                Text(latest.description.trim(), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        c.error != null -> Text(c.error, color = MaterialTheme.colorScheme.error)
                    }
                    if (!Firmware.supportsOta(c.model)) {
                        Text(
                            "This camera's firmware does not support over-the-air updates (tCam-Mini needs FW 2.0+). " +
                                "Flash it over USB instead.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    val canInstall = !busy && !c.checking && Firmware.supportsOta(c.model)
                    if (canInstall) {
                        if (latest != null) {
                            OutlinedButton(
                                onClick = { pendingInstall = "firmware ${latest.version} from danjuliodesigns.com" to null },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Download & Install ${latest.version}") }
                        }
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Install from File…") }
                    }
                }

                if (update != FirmwareUpdateState.Idle) HorizontalDivider()
                when (val u = update) {
                    FirmwareUpdateState.Idle -> {}

                    FirmwareUpdateState.Downloading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Downloading firmware…")
                    }

                    is FirmwareUpdateState.WaitingForConfirmation -> Text(
                        if (u.isFullTcam) {
                            "Confirm the update to ${u.version} on the camera's screen (within 60 seconds)."
                        } else {
                            "The camera's LED is blinking red/green. Press its button to confirm the update to " +
                                "${u.version} (within 60 seconds)."
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )

                    is FirmwareUpdateState.Transferring -> {
                        LinearProgressIndicator(
                            progress = { u.bytesSent.toFloat() / u.totalBytes },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Sending firmware: ${u.bytesSent * 100 / u.totalBytes}% — don't power off the camera")
                    }

                    is FirmwareUpdateState.Succeeded -> Text(
                        "${u.message}. The camera is restarting with the new firmware; the app will reconnect.",
                        color = MaterialTheme.colorScheme.primary,
                    )

                    is FirmwareUpdateState.Failed -> Text(
                        "Update failed: ${u.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (busy) {
                TextButton(onClick = { viewModel.cancelFirmwareUpdate() }) { Text("Cancel Update") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )

    pendingInstall?.let { (label, uri) ->
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text("Update Firmware?") },
            text = {
                Text(
                    "Install $label on the camera? Streaming will stop, and you'll need to confirm the update " +
                        "on the camera itself. Keep the camera powered and in range until it restarts.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingInstall = null
                    if (uri == null) viewModel.installLatestFirmware() else viewModel.installFirmwareFile(uri, label)
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { pendingInstall = null }) { Text("Cancel") } },
        )
    }
}
