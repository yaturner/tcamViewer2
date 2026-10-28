package com.danjuliodesigns.tcamviewer2.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for issues #52/#53: changing the camera's WiFi config silently failed
 *  because of two bugs in the JSON this builds -- an unedited password was always sent (blanking
 *  the real one), and the AP/client-mode toggle left the SSID field holding whichever mode's SSID
 *  was loaded when the dialog opened. This exercises every isAccessPoint/useStaticIp/password
 *  combination buildWifiConfigArgs() can be called with from the WiFi Settings dialog. */
class WifiConfigArgsTest {
    private fun args(
        isAccessPoint: Boolean = false,
        ssid: String = "laurie",
        password: String? = null,
        useStaticIp: Boolean = false,
        staticIp: String = "192.168.68.199",
        staticNetmask: String = "255.255.255.0",
    ): JSONObject = JSONObject(
        buildWifiConfigArgs(isAccessPoint, ssid, password, useStaticIp, staticIp, staticNetmask),
    )

    // --- Access Point mode ---

    @Test
    fun apModeWithEditedPassword_sendsApSsidAndPassword() {
        val json = args(isAccessPoint = true, ssid = "tCam-7F89", password = "newpassword")

        assertEquals("tCam-7F89", json.getString("ap_ssid"))
        assertEquals("newpassword", json.getString("ap_pw"))
        assertEquals(1, json.getInt("flags"))
        assertFalse("AP mode payload should never include client-mode fields", json.has("sta_ssid"))
    }

    @Test
    fun apModeWithUntouchedPassword_omitsPasswordField() {
        // issue #52/#53: the camera never returns its current password in get_wifi, so an
        // untouched Password field must omit ap_pw entirely rather than send "" and blank it.
        val json = args(isAccessPoint = true, ssid = "tCam-7F89", password = null)

        assertEquals("tCam-7F89", json.getString("ap_ssid"))
        assertFalse(json.has("ap_pw"))
        assertEquals(1, json.getInt("flags"))
    }

    // --- Client mode, static IP ---

    @Test
    fun staticIpWithEditedPassword_sendsAllStaticFieldsAndPassword() {
        val json = args(
            isAccessPoint = false,
            ssid = "laurie",
            password = "homepw123",
            useStaticIp = true,
            staticIp = "192.168.68.199",
            staticNetmask = "255.255.255.0",
        )

        assertEquals("laurie", json.getString("sta_ssid"))
        assertEquals("homepw123", json.getString("sta_pw"))
        assertEquals("192.168.68.199", json.getString("sta_ip_addr"))
        assertEquals("255.255.255.0", json.getString("sta_netmask"))
        assertEquals(145, json.getInt("flags")) // client(0x80) + static(0x10) + startup(0x01)
        assertFalse(json.has("ap_ssid"))
    }

    @Test
    fun staticIpWithUntouchedPassword_omitsPasswordButKeepsStaticFields() {
        val json = args(useStaticIp = true, staticIp = "192.168.68.199", staticNetmask = "255.255.255.0")

        assertFalse(json.has("sta_pw"))
        assertEquals("192.168.68.199", json.getString("sta_ip_addr"))
        assertEquals("255.255.255.0", json.getString("sta_netmask"))
        assertEquals(145, json.getInt("flags"))
    }

    // --- Client mode, DHCP ---

    @Test
    fun dhcpWithEditedPassword_sendsSsidAndPasswordNoStaticFields() {
        val json = args(useStaticIp = false, password = "homepw123")

        assertEquals("laurie", json.getString("sta_ssid"))
        assertEquals("homepw123", json.getString("sta_pw"))
        assertEquals(129, json.getInt("flags")) // client(0x80) + startup(0x01), no static bit
        assertFalse(json.has("sta_ip_addr"))
        assertFalse(json.has("sta_netmask"))
    }

    @Test
    fun dhcpWithUntouchedPassword_omitsPasswordField() {
        // issue #52's exact repro: disable static IP, leave password untouched, Save.
        val json = args(useStaticIp = false, password = null)

        assertEquals("laurie", json.getString("sta_ssid"))
        assertFalse(json.has("sta_pw"))
        assertEquals(129, json.getInt("flags"))
        assertFalse(json.has("sta_ip_addr"))
    }

    // --- SSID follows the AP/client toggle (not left stale from whichever mode loaded first) ---

    @Test
    fun ssidReflectsWhicheverModeWasPassedIn_apSsidWhenAccessPoint() {
        val json = args(isAccessPoint = true, ssid = "tCam-7F89")
        assertTrue(json.has("ap_ssid"))
        assertEquals("tCam-7F89", json.getString("ap_ssid"))
    }

    @Test
    fun ssidReflectsWhicheverModeWasPassedIn_staSsidWhenClient() {
        val json = args(isAccessPoint = false, ssid = "laurie")
        assertTrue(json.has("sta_ssid"))
        assertEquals("laurie", json.getString("sta_ssid"))
    }
}
