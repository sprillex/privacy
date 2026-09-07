package com.example.privacy.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roku_devices")
data class RokuDeviceEntity(
    @PrimaryKey val deviceId: String,
    val ipAddress: String,
    val port: Int = 8060,
    val name: String,
    val modelName: String,
    val modelNumber: String,
    val isTv: Boolean,
    val softwareVersion: String,
    val lastAuditedAt: Long? = null
)
