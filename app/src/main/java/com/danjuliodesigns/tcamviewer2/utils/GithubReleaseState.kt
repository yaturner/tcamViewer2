package com.danjuliodesigns.tcamviewer2.utils

/**
 * Result of checking this repo's GitHub Releases for a newer version than this build (issue #36
 * follow-up: "full" / GitHub-distributed builds have no Play Store to check, so they check
 * GitHub Releases directly instead). See [GithubReleaseChecker] for the real network-backed
 * implementation, and the "full" flavor's ui/UpdateSection.kt for where it's surfaced in Settings.
 * Kept flavor-agnostic (primitives only) so the comparison logic below is unit testable without a
 * real network call.
 */
sealed class GithubReleaseState {
    data object UpToDate : GithubReleaseState()
    data class Available(val tagName: String, val htmlUrl: String) : GithubReleaseState()
    data class Error(val message: String) : GithubReleaseState()
}

/**
 * True if [releaseTag] (e.g. "v2.0") names a newer release than this build's own
 * [currentVersionName] (BuildConfig.VERSION_NAME, "major.minor.ddMMyyHHmm" -- see
 * app/build.gradle.kts). GitHub Releases only carry a tag, not this app's own versionCode, so the
 * comparison is deliberately narrow: it reads only the leading integer after an optional "v"/"V"
 * prefix and compares that against this build's own major version (versionName's first
 * dot-separated component). This matches version.properties' documented convention of bumping
 * `major` by hand only when a build is marked as a GitHub Release -- it does NOT detect a newer
 * commit within the same major version, by design, since GitHub Releases (unlike Play's
 * versionCode) don't carry a monotonic build number to compare against.
 */
fun isNewerGithubRelease(releaseTag: String, currentVersionName: String): Boolean {
    val releaseMajor = releaseTag.trim().removePrefix("v").removePrefix("V")
        .substringBefore('.').toIntOrNull() ?: return false
    val currentMajor = currentVersionName.trim().substringBefore('.').toIntOrNull() ?: return false
    return releaseMajor > currentMajor
}
