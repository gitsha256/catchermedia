package com.revive.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.util.Log

private const val TAG = "Revive"

@Database(entities = [MessageLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // If another thread initialized it while we were waiting for the lock, return it
                INSTANCE ?: run {
                    Log.d(TAG, "[${Thread.currentThread().name}] Initializing Room Database instance")
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "revive_database"
                    )
                    // 🔒 Removed .fallbackToDestructiveMigration() to protect  data history
                    .build()
                    
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}