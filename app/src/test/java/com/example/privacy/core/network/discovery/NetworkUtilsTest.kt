package com.example.privacy.core.network.discovery

import org.junit.Assert.*
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun getSubnetPrefix_validIp_returnsThreeOctets() {
        val prefix = NetworkUtils.getSubnetPrefix("192.168.1.72")
        assertEquals("192.168.1", prefix)
    }

    @Test
    fun getSubnetPrefix_invalidIp_returnsNull() {
        val prefix = NetworkUtils.getSubnetPrefix("192.168.1")
        assertNull(prefix)
    }
}
