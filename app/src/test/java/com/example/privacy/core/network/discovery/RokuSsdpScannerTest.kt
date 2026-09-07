package com.example.privacy.core.network.discovery

import org.junit.Assert.*
import org.junit.Test

class RokuSsdpScannerTest {

    @Test
    fun parseSsdpResponse_validResponse_parsesCorrectly() {
        val rawResponse = """
            HTTP/1.1 200 OK
            Cache-Control: max-age=3600
            ST: roku:ecp
            Location: http://192.168.1.120:8060/
            USN: uuid:roku:ecp:123456789
            Server: Roku UPnP/1.0 MiniUPnPd/1.4
        """.trimIndent()

        val device = RokuSsdpScanner.parseSsdpResponse(rawResponse)

        assertNotNull(device)
        assertEquals("http://192.168.1.120:8060/", device?.location)
        assertEquals("192.168.1.120", device?.ipAddress)
        assertEquals(8060, device?.port)
        assertEquals("uuid:roku:ecp:123456789", device?.usn)
    }

    @Test
    fun parseSsdpResponse_caseInsensitiveHeaderNames_parsesCorrectly() {
        val rawResponse = """
            HTTP/1.1 200 OK
            location: http://10.0.0.5:8060/
            usn: uuid:roku:ecp:987654321
        """.trimIndent()

        val device = RokuSsdpScanner.parseSsdpResponse(rawResponse)

        assertNotNull(device)
        assertEquals("10.0.0.5", device?.ipAddress)
        assertEquals(8060, device?.port)
        assertEquals("uuid:roku:ecp:987654321", device?.usn)
    }

    @Test
    fun parseSsdpResponse_missingLocationHeader_returnsNull() {
        val rawResponse = """
            HTTP/1.1 200 OK
            USN: uuid:roku:ecp:123456789
        """.trimIndent()

        val device = RokuSsdpScanner.parseSsdpResponse(rawResponse)

        assertNull(device)
    }

    @Test
    fun parseSsdpResponse_fallbackUsn_whenUsnHeaderIsMissing() {
        val rawResponse = """
            HTTP/1.1 200 OK
            Location: http://192.168.1.100:8060/
        """.trimIndent()

        val device = RokuSsdpScanner.parseSsdpResponse(rawResponse)

        assertNotNull(device)
        assertEquals("usn:192.168.1.100", device?.usn)
    }

    @Test
    fun parseSsdpResponse_dialSt_parsesCorrectly() {
        val rawResponse = """
            HTTP/1.1 200 OK
            ST: urn:dial-multiscreen-org:service:dial:1
            Location: http://192.168.1.72:8060/
            USN: uuid:roku:ecp:dial123
        """.trimIndent()

        val device = RokuSsdpScanner.parseSsdpResponse(rawResponse)

        assertNotNull(device)
        assertEquals("192.168.1.72", device?.ipAddress)
        assertEquals(8060, device?.port)
        assertEquals("uuid:roku:ecp:dial123", device?.usn)
    }
}
