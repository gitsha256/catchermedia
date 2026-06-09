package com.revive.app

import android.app.Application
import com.revive.app.data.AppDatabase
import com.revive.app.data.MessageRepository
import android.util.Log

private const val TAG = "Revive"

class ReviveApp : Application() {
    
    /**
     * Single source of truth for the database and repository.
     * Lazy initialization ensures resources are only consumed when needed.
     */
    val database: AppDatabase by lazy { 
        AppDatabase.getDatabase(this) 
    }

    val repository: MessageRepository by lazy {
        MessageRepository(database.messageDao())
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[${Thread.currentThread().name}] ReviveApp initialized")
    }
}
