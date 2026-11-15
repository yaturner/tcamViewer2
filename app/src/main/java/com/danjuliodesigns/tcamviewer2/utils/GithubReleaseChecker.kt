package com.danjuliodesigns.tcamviewer2.utils

import com.danjuliodesigns.tcamviewer2.BuildConfig
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Checks this repo's GitHub Releases for a newer version than this build (issue #36 follow-up).
 * Used by the "full" (GitHub-distributed) flavor's Settings row -- the "play" flavor checks
 * Play's In-App Updates API instead (see that flavor's own utils/AppUpdater.kt). Unauthenticated
 * GET, rate-limited to 60 requests/hour per IP by GitHub -- fine for a manual, user-initiated
 * check, so no auth token is needed or used.
 *
 * Plain [HttpURLConnection], same pattern as [Firmware]'s httpGet.
 */
object GithubReleaseChecker {
    private const val HTTP_TIMEOUT_MS = 10_000
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/yaturner/tcamViewer2/releases/latest"

    /** Blocking -- call from Dispatchers.IO. */
    fun checkForUpdate(): GithubReleaseState = try {
        val json = JSONObject(String(httpGet(LATEST_RELEASE_URL), Charsets.UTF_8))
        val tagName = json.getString("tag_name")
        val htmlUrl = json.getString("html_url")
        if (isNewerGithubRelease(tagName, BuildConfig.VERSION_NAME)) {
            GithubReleaseState.Available(tagName, htmlUrl)
        } else {
            GithubReleaseState.UpToDate
        }
    } catch (e: Exception) {
        Timber.e(e, "GithubReleaseChecker.checkForUpdate failed")
        GithubReleaseState.Error(e.message ?: "Update check failed")
    }

    private fun httpGet(url: String, redirectsLeft: Int = 5): ByteArray {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            // GitHub's REST API wants an explicit Accept header and this one specifically
            // (https://docs.github.com/en/rest/releases/releases#get-the-latest-release).
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            // HttpURLConnection won't follow a redirect that switches protocol (http <-> https)
            if (code in 300..399 && redirectsLeft > 0) {
                val next = conn.getHeaderField("Location") ?: throw IOException("HTTP $code without Location from $url")
                return httpGet(URI(url).resolve(next).toString(), redirectsLeft - 1)
            }
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code from $url")
            return conn.inputStream.use { input ->
                ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}
