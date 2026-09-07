package com.example.privacy.core.network.ecp

import org.junit.Assert.*
import org.junit.Test

class RokuEcpClientTest {

    @Test
    fun parseDeviceInfoXml_rokuTv_parsesCorrectly() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <udn>1234-5678</udn>
                <serial-number>YN00000000</serial-number>
                <device-id>12345</device-id>
                <vendor-name>Roku</vendor-name>
                <model-number>7000X</model-number>
                <model-name>Roku TV</model-name>
                <friendly-device-name>55" TCL Roku TV</friendly-device-name>
                <is-tv>true</is-tv>
                <software-version>12.5.0</software-version>
            </device-info>
        """.trimIndent()

        val info = RokuEcpClient.parseDeviceInfoXml(xml)

        assertEquals("55\" TCL Roku TV", info.friendlyDeviceName)
        assertEquals("Roku TV", info.modelName)
        assertEquals("7000X", info.modelNumber)
        assertTrue(info.isTv)
        assertEquals("12.5.0", info.softwareVersion)
    }

    @Test
    fun parseDeviceInfoXml_rokuStreamingStick_parsesIsTvFalse() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <user-device-name>Living Room Stick</user-device-name>
                <friendly-device-name>Roku Streaming Stick 4K</friendly-device-name>
                <model-number>3820X</model-number>
                <model-name>Streaming Stick 4K</model-name>
                <is-tv>false</is-tv>
                <software-version>13.0.0</software-version>
            </device-info>
        """.trimIndent()

        val info = RokuEcpClient.parseDeviceInfoXml(xml)

        assertEquals("Living Room Stick", info.friendlyDeviceName)
        assertEquals("Streaming Stick 4K", info.modelName)
        assertEquals("3820X", info.modelNumber)
        assertFalse(info.isTv)
        assertEquals("13.0.0", info.softwareVersion)
    }

    @Test
    fun parseDeviceInfoXml_limitedEcpSetting_isLimitedModeTrue() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <friendly-device-name>TCL Roku TV</friendly-device-name>
                <ecp-setting-control>limited</ecp-setting-control>
            </device-info>
        """.trimIndent()

        val info = RokuEcpClient.parseDeviceInfoXml(xml)

        assertEquals("limited", info.ecpSettingControl)
        assertTrue(info.isLimitedMode)
    }

    @Test
    fun parseDeviceInfoXml_disabledEcpSetting_isLimitedModeTrue() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <friendly-device-name>Hisense Roku TV</friendly-device-name>
                <ecp-setting-control>disabled</ecp-setting-control>
            </device-info>
        """.trimIndent()

        val info = RokuEcpClient.parseDeviceInfoXml(xml)

        assertEquals("disabled", info.ecpSettingControl)
        assertTrue(info.isLimitedMode)
    }

    @Test
    fun parseDeviceInfoXml_permissiveEcpSetting_isLimitedModeFalse() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <friendly-device-name>onn. Roku TV</friendly-device-name>
                <ecp-setting-control>permissive</ecp-setting-control>
            </device-info>
        """.trimIndent()

        val info = RokuEcpClient.parseDeviceInfoXml(xml)

        assertEquals("permissive", info.ecpSettingControl)
        assertFalse(info.isLimitedMode)
    }
}
