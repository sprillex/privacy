package com.example.privacy.core.database

import androidx.room.Embedded
import androidx.room.Relation

data class RokuDeviceWithChecks(
    @Embedded val device: RokuDeviceEntity,
    @Relation(
        parentColumn = "deviceId",
        entityColumn = "deviceId"
    )
    val checks: List<PrivacyCheckItemEntity>
)
