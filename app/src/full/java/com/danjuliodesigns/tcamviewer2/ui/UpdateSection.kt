package com.danjuliodesigns.tcamviewer2.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.danjuliodesigns.tcamviewer2.model.CameraViewModel
import com.danjuliodesigns.tcamviewer2.utils.GithubReleaseChecker
import com.danjuliodesigns.tcamviewer2.utils.GithubReleaseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Check for updates" Settings row for the GitHub (full) build (issue #36 follow-up) -- this
 * build has no Play Store relationship to query, so it checks this repo's GitHub Releases
 * instead. Tapping Update just opens the release page in the browser (Option A from the issue's
 * follow-up discussion): the user downloads and installs the APK themselves, same as installing
 * any sideloaded APK today. Downloading the APK and prompting an in-app install (Option B) was
 * deliberately deferred as higher-risk/more permission-hungry for the same end result. See the
 * "play" flavor's version of this file for the Play Store counterpart.
 */
@Composable
fun CheckForUpdatesSection(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<GithubReleaseState?>(null) }

    ListItem(
        headlineContent = { Text("Check for updates") },
        supportingContent = {
            Text(
                when (val s = state) {
                    null -> "Not checked yet"
                    is GithubReleaseState.UpToDate -> "Up to date"
                    is GithubReleaseState.Available -> "${s.tagName} available"
                    is GithubReleaseState.Error -> "Could not check: ${s.message}"
                },
            )
        },
        trailingContent = {
            when (val s = state) {
                is GithubReleaseState.Available -> FeedbackButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.htmlUrl)))
                }) { Text("Update") }

                else -> FeedbackButton(onClick = {
                    scope.launch {
                        state = withContext(Dispatchers.IO) { GithubReleaseChecker.checkForUpdate() }
                    }
                }) { Text("Check") }
            }
        },
    )
}
