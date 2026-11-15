package com.danjuliodesigns.tcamviewer2.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseStateTest {

    @Test
    fun isNewerGithubRelease_higherMajor_returnsTrue() {
        assertTrue(isNewerGithubRelease("v2.0", "1.1234.1511132030"))
    }

    @Test
    fun isNewerGithubRelease_sameMajor_returnsFalse() {
        assertFalse(isNewerGithubRelease("v1.0", "1.1234.1511132030"))
    }

    @Test
    fun isNewerGithubRelease_lowerMajor_returnsFalse() {
        assertFalse(isNewerGithubRelease("v1.0", "2.1.1511132030"))
    }

    @Test
    fun isNewerGithubRelease_toleratesUppercaseVPrefix() {
        assertTrue(isNewerGithubRelease("V3.0", "2.1.1511132030"))
    }

    @Test
    fun isNewerGithubRelease_noVPrefix_stillParses() {
        assertTrue(isNewerGithubRelease("3.0", "2.1.1511132030"))
    }

    @Test
    fun isNewerGithubRelease_unparsableTag_returnsFalse() {
        assertFalse(isNewerGithubRelease("latest", "1.1234.1511132030"))
    }

    @Test
    fun isNewerGithubRelease_unparsableCurrentVersion_returnsFalse() {
        assertFalse(isNewerGithubRelease("v2.0", "notaversion"))
    }
}
