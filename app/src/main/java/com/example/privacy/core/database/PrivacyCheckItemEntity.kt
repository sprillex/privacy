package com.example.privacy.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "privacy_check_items",
    foreignKeys = [
        ForeignKey(
            entity = RokuDeviceEntity::class,
            parentColumns = ["deviceId"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deviceId"])]
)
data class PrivacyCheckItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val targetKey: String, // e.g. "AD_TRACKING", "ACR"
    val isVerified: Boolean = false,
    val verifiedTimestamp: Long? = null
)
