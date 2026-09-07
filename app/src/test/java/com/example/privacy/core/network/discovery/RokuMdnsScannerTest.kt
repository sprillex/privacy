package com.example.privacy.core.network.discovery

import org.junit.Assert.*
import org.junit.Test

class RokuMdnsScannerTest {

    @Test
    fun serviceTypes_containsAirplayAndDisplay() {
        val types = RokuMdnsScanner.SERVICE_TYPES
        assertTrue(types.contains("_airplay._tcp."))
        assertTrue(types.contains("_display._tcp."))
    }
}
