package com.catcher.app

import android.app.Application
import com.catcher.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CatcherApp : Application() {
    
    // Lazy initialization of database
    val database: AppDatabase by lazy { 
        AppDatabase.getDatabase(this) 
    }

    override fun onCreate() {
        super.onCreate()
        // Pre-warm database instance in background to avoid skipping frames on startup
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(this@CatcherApp)
        }
    }
}
