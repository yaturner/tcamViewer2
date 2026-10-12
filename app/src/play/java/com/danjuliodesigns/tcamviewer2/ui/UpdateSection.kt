package com.danjuliodesigns.tcamviewer2.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.danjuliodesigns.tcamviewer2.model.CameraViewModel
import com.danjuliodesigns.tcamviewer2.utils.AppUpdater
import com.danjuliodesigns.tcamviewer2.utils.UpdateState
import com.danjuliodesigns.tcamviewer2.utils.canCompleteUpdateNow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * "Check for updates" Settings row for the Play build (issue #36), using Play's In-App Updates
 * API (flexible flow). See the "full" flavor's version of this file for the no-op GitHub-build
 * counterpart — the row is absent there, not just hidden, since that build has no Play Core
 * dependency to call into.
 */
@Composable
fun CheckForUpdatesSection(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appUpdater = remember { AppUpdater(context) }
    var state by remember { mutableStateOf<UpdateState?>(null) }
    var readyToInstall by remember { mutableStateOf(false) }
    val isRecording by viewModel.isRecording.collectAsState()
    val isTimeLapsing by viewModel.isTimeLapsing.collectAsState()

    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Timber.w("In-app update flow did not complete: resultCode=${result.resultCode}")
        }
    }

    // The install listener only reports a download finishing while this screen is open — a
    // flexible update that finished in a previous session is caught by the one-shot check below.
    DisposableEffect(appUpdater) {
        appUpdater.registerListener { readyToInstall = true }
        onDispose { appUpdater.unregisterListener() }
    }

    LaunchedEffect(appUpdater) {
        if (appUpdater.hasDownloadedUpdate()) readyToInstall = true
    }

    ListItem(
        headlineContent = { Text("Check for updates") },
        supportingContent = {
            Text(
                when {
                    readyToInstall -> "Update downloaded"

                    else -> when (val s = state) {
                        null -> "Not checked yet"
                        is UpdateState.UpToDate -> "Up to date"
                        is UpdateState.Available -> "Version code ${s.availableVersionCode} available"
                        is UpdateState.Error -> "Could not check: ${s.message}"
                    }
                },
            )
        },
        trailingContent = {
            when {
                readyToInstall -> FeedbackButton(onClick = {
                    if (canCompleteUpdateNow(isRecording, isTimeLapsing)) {
                        appUpdater.completeUpdate()
                    } else {
                        Toast.makeText(
                            context,
                            "Finish the current recording or time lapse before restarting to update",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Restart") }

                state is UpdateState.Available -> FeedbackButton(onClick = {
                    appUpdater.startFlexibleUpdate(updateLauncher)
                }) { Text("Update") }

                else -> FeedbackButton(onClick = {
                    scope.launch { state = appUpdater.checkForUpdate() }
                }) { Text("Check") }
            }
        },
    )
}
