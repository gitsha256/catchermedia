package com.catcher.app

import android.app.Application
import com.catcher.app.data.AppDatabase

class CatcherApp : Application() {
    
    // Lazy initialization of database
    val database: AppDatabase by lazy { 
        AppDatabase.getDatabase(this) 
    }

    override fun onCreate() {
        super.onCreate()
        // Pre-warm database instance
        AppDatabase.getDatabase(this)
    }
}
