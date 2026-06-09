package com.revive.app

import android.app.Application
import com.revive.app.data.AppDatabase
import com.revive.app.data.MessageRepository

class ReviveApp : Application() {
    
    // Lazy initialization of database
    val database: AppDatabase by lazy { 
        AppDatabase.getDatabase(this) 
    }

    val repository: MessageRepository by lazy {
        MessageRepository(database.messageDao())
    }

    override fun onCreate() {
        super.onCreate()
    }
}
