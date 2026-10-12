package com.danjuliodesigns.tcamviewer2.utils

/**
 * Result of checking Play for a newer version (issue #36). See the "play" flavor's
 * utils/AppUpdater.kt for the real Play Core-backed implementation, and ui/UpdateSection.kt
 * (separate "play" and "full" versions) for where it's surfaced in Settings. Kept flavor-agnostic
 * (primitives only, no Play Core types) so this — and the mapping/restart logic below — can be
 * unit tested without Play Core on the test classpath, and so the "full" (GitHub) build never
 * needs to reference Play Core at all.
 */
sealed class UpdateState {
    data object UpToDate : UpdateState()
    data class Available(val availableVersionCode: Int) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/** Maps Play's AppUpdateInfo fields (passed as primitives rather than importing the Play Core
 *  type here) to [UpdateState]. An update only counts as available if the flexible flow is
 *  actually allowed for it — otherwise there's nothing this app's UI can offer. */
fun mapUpdateState(
    updateAvailable: Boolean,
    flexibleUpdateAllowed: Boolean,
    availableVersionCode: Int,
): UpdateState = if (updateAvailable && flexibleUpdateAllowed) {
    UpdateState.Available(availableVersionCode)
} else {
    UpdateState.UpToDate
}

/** True once a flexible update has finished downloading and is only waiting on the user to
 *  confirm the restart — the point at which the Settings row should offer "Restart" instead of
 *  "Update"/"Check". [installStatus] and [downloadedStatus] are raw ints (Play Core's
 *  InstallStatus.DOWNLOADED constant, passed in by the caller) rather than the Play Core type. */
fun isUpdateReadyToInstall(installStatus: Int, downloadedStatus: Int): Boolean = installStatus == downloadedStatus

/** completeUpdate() restarts the app — must not happen mid-recording or mid-time-lapse (issue
 *  #36's explicit requirement). The caller shows a message instead when this is false. */
fun canCompleteUpdateNow(isRecording: Boolean, isTimeLapsing: Boolean): Boolean = !isRecording && !isTimeLapsing
