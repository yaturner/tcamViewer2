package com.danjuliodesigns.tcamviewer2.utils

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps Play's In-App Updates API (flexible flow) for issue #36. This class only exists in the
 * "play" flavor's source set — the "full" (GitHub) build has no Play Core dependency and no
 * equivalent file, so none of this ships there (see ui/UpdateSection.kt in both flavors). The
 * actual state-mapping / restart-eligibility logic lives in utils/UpdateState.kt, flavor-agnostic
 * and unit tested; this class is a thin wrapper around the real Play Core calls.
 */
class AppUpdater(context: Context) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context.applicationContext)
    private var pendingInfo: AppUpdateInfo? = null
    private var listener: InstallStateUpdatedListener? = null

    private suspend fun fetchInfo(): AppUpdateInfo = suspendCancellableCoroutine { cont ->
        manager.appUpdateInfo
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun checkForUpdate(): UpdateState = try {
        val info = fetchInfo()
        pendingInfo = info
        mapUpdateState(
            updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
            flexibleUpdateAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            availableVersionCode = info.availableVersionCode(),
        )
    } catch (e: Exception) {
        Timber.e(e, "checkForUpdate failed")
        UpdateState.Error(e.message ?: "Update check failed")
    }

    /** Call this once when the Settings screen opens — a flexible update already downloaded in
     *  a previous session is otherwise only reported via [registerListener], which isn't
     *  registered yet the first time the user comes back after it finished. */
    suspend fun hasDownloadedUpdate(): Boolean = try {
        val info = fetchInfo()
        pendingInfo = info
        isUpdateReadyToInstall(info.installStatus(), InstallStatus.DOWNLOADED)
    } catch (e: Exception) {
        Timber.e(e, "hasDownloadedUpdate failed")
        false
    }

    /** No-op if [checkForUpdate] hasn't found an available update yet — each AppUpdateInfo can
     *  only start a flow once, so this always uses the most recently fetched one. */
    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = pendingInfo ?: return
        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
    }

    fun registerListener(onDownloaded: () -> Unit) {
        val l = InstallStateUpdatedListener { state ->
            if (isUpdateReadyToInstall(state.installStatus(), InstallStatus.DOWNLOADED)) onDownloaded()
        }
        listener = l
        manager.registerListener(l)
    }

    fun unregisterListener() {
        listener?.let { manager.unregisterListener(it) }
        listener = null
    }

    fun completeUpdate() = manager.completeUpdate()
}
