package com.das.tcamviewer2

import com.das.tcamviewer2.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantsTest {

    // --- IP regex ---

    @Test
    fun validIpAddressesMatch() {
        val valid = listOf(
            "192.168.4.1",
            "0.0.0.0",
            "255.255.255.255",
            "10.0.0.1",
            "172.16.0.1",
            "1.2.3.4",
            "192.168.100.200"
        )
        for (ip in valid) {
            assertTrue("Expected '$ip' to be a valid IP", Constants.IP_PATTERN.matcher(ip).matches())
        }
    }

    @Test
    fun invalidIpAddressesDoNotMatch() {
        val invalid = listOf(
            "256.0.0.1",
            "192.168.1",
            "192.168.1.1.1",
            "",
            "abc.def.ghi.jkl",
            "192.168.1.",
            ".168.1.1",
            "192..1.1",
            " 192.168.1.1",
            "192.168.1.1 "
        )
        for (ip in invalid) {
            assertFalse("Expected '$ip' to be invalid", Constants.IP_PATTERN.matcher(ip).matches())
        }
    }

    // --- Camera command STX/ETX framing ---

    @Test
    fun simpleCommandsHaveStxEtxFraming() {
        val stx = ''
        val etx = ''
        val commands = listOf(
            Constants.CMD_GET_STATUS,
            Constants.CMD_GET_CONFIG,
            Constants.CMD_GET_WIFI,
            Constants.CMD_GET_IMAGE,
            Constants.CMD_SET_STREAM_OFF
        )
        for (cmd in commands) {
            assertEquals("'$cmd' should start with STX", stx, cmd.first())
            assertEquals("'$cmd' should end with ETX", etx, cmd.last())
        }
    }

    @Test
    fun templateCommandsHaveStxEtxFraming() {
        val stx = ''
        val etx = ''
        val templates = listOf(
            Constants.CMD_SET_TIME,
            Constants.CMD_SET_CONFIG,
            Constants.CMD_SET_SPOTMETER,
            Constants.CMD_SET_STREAM_ON,
            Constants.CMD_SET_WIFI
        )
        for (template in templates) {
            assertEquals("Template should start with STX: $template", stx, template.first())
            assertEquals("Template should end with ETX: $template", etx, template.last())
        }
    }

    // --- Image dimensions ---

    @Test
    fun imageDimensionsAreCorrect() {
        assertEquals(160, Constants.IMAGE_WIDTH)
        assertEquals(120, Constants.IMAGE_HEIGHT)
    }

    // --- Gain mode constants ---

    @Test
    fun gainModeConstantsAreDistinct() {
        val modes = setOf(Constants.GAIN_MODE_HIGH, Constants.GAIN_MODE_LOW, Constants.GAIN_MODE_AUTO)
        assertEquals("All 3 gain modes should be distinct values", 3, modes.size)
        assertEquals(0, Constants.GAIN_MODE_HIGH)
        assertEquals(1, Constants.GAIN_MODE_LOW)
        assertEquals(2, Constants.GAIN_MODE_AUTO)
    }

    // --- Telemetry bit masks ---

    @Test
    fun telemetryMaskAgcIsBit12() {
        assertEquals(1 shl 12, Constants.TELEMETRY_MASK_AGC)
    }

    @Test
    fun telemetryMaskShutdownIsBit20() {
        assertEquals(1 shl 20, Constants.TELEMETRY_MASK_SHUTDOWN)
    }

    @Test
    fun telemetryMasksDoNotOverlap() {
        assertEquals(0, Constants.TELEMETRY_MASK_AGC and Constants.TELEMETRY_MASK_SHUTDOWN)
    }

    // --- Service type constant ---

    @Test
    fun mdnsServiceTypeIsCorrect() {
        assertEquals("_tcam-socket._tcp.", Constants.SERVICE_TYPE)
    }
}
