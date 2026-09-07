package com.example.privacy.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RokuDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: RokuDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckItems(checks: List<PrivacyCheckItemEntity>)

    @Update
    suspend fun updateCheckItem(checkItem: PrivacyCheckItemEntity)

    @Query("SELECT * FROM privacy_check_items WHERE deviceId = :deviceId AND targetKey = :targetKey LIMIT 1")
    suspend fun getCheckItem(deviceId: String, targetKey: String): PrivacyCheckItemEntity?

    @Transaction
    @Query("SELECT * FROM roku_devices")
    fun getAllDevicesWithChecks(): Flow<List<RokuDeviceWithChecks>>

    @Transaction
    @Query("SELECT * FROM roku_devices WHERE deviceId = :deviceId")
    fun getDeviceWithChecks(deviceId: String): Flow<RokuDeviceWithChecks?>

    @Query("UPDATE roku_devices SET lastAuditedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastAuditedAt(deviceId: String, timestamp: Long)
}
