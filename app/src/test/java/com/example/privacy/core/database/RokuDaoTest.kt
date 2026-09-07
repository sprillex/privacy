package com.example.privacy.core.database

import org.junit.Assert.*
import org.junit.Test

class RokuDatabaseEntitiesTest {

    @Test
    fun rokuDeviceEntity_creation_hasCorrectValues() {
        val device = RokuDeviceEntity(
            deviceId = "roku-123",
            ipAddress = "192.168.1.100",
            port = 8060,
            name = "Living Room TV",
            modelName = "Roku TV",
            modelNumber = "7000X",
            isTv = true,
            softwareVersion = "12.5.0",
            lastAuditedAt = 100000L
        )

        assertEquals("roku-123", device.deviceId)
        assertEquals("192.168.1.100", device.ipAddress)
        assertEquals(8060, device.port)
        assertEquals("Living Room TV", device.name)
        assertTrue(device.isTv)
        assertEquals(100000L, device.lastAuditedAt)
    }

    @Test
    fun privacyCheckItemEntity_creation_hasCorrectValues() {
        val check = PrivacyCheckItemEntity(
            id = 1L,
            deviceId = "roku-123",
            targetKey = "AD_TRACKING",
            isVerified = true,
            verifiedTimestamp = 200000L
        )

        assertEquals(1L, check.id)
        assertEquals("roku-123", check.deviceId)
        assertEquals("AD_TRACKING", check.targetKey)
        assertTrue(check.isVerified)
        assertEquals(200000L, check.verifiedTimestamp)
    }

    @Test
    fun rokuDeviceWithChecks_relation_holdsData() {
        val device = RokuDeviceEntity(
            deviceId = "roku-123",
            ipAddress = "192.168.1.100",
            name = "Living Room TV",
            modelName = "Roku TV",
            modelNumber = "7000X",
            isTv = true,
            softwareVersion = "12.5.0"
        )
        val checks = listOf(
            PrivacyCheckItemEntity(id = 1L, deviceId = "roku-123", targetKey = "AD_TRACKING", isVerified = false),
            PrivacyCheckItemEntity(id = 2L, deviceId = "roku-123", targetKey = "ACR", isVerified = true)
        )

        val relation = RokuDeviceWithChecks(device = device, checks = checks)

        assertEquals("roku-123", relation.device.deviceId)
        assertEquals(2, relation.checks.size)
        assertFalse(relation.checks[0].isVerified)
        assertTrue(relation.checks[1].isVerified)
    }
}
