package com.example.privacy.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RokuDeviceEntity::class, PrivacyCheckItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RokuDatabase : RoomDatabase() {
    abstract fun rokuDao(): RokuDao

    companion object {
        @Volatile
        private var INSTANCE: RokuDatabase? = null

        fun getDatabase(context: Context): RokuDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RokuDatabase::class.java,
                    "roku_privacy.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
