package com.revive.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revive.app.ui.screen.ChatDetailScreen
import com.revive.app.ui.screen.PermissionScreen
import com.revive.app.ui.screen.ThreadListScreen
import com.revive.app.ui.theme.ReviveTheme 
import com.revive.app.ui.viewmodel.MainViewModel
import com.revive.app.util.ReviveConstants

private const val TAG = "Revive"

class MainActivity : ComponentActivity() {

    private val isNotificationGranted = mutableStateOf(false)
    private val isStorageGranted = mutableStateOf(false)
    private var showBatteryRationale by mutableStateOf(false)

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as ReviveApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d(TAG, "[${Thread.currentThread().name}] MainActivity onCreate")

        val sharedPrefs = getSharedPreferences("revive_prefs", Context.MODE_PRIVATE)

        val requestMultiplePermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val imageGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
            val legacyGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            
            val systemCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

            val trackingAllowed = imageGranted || legacyGranted || systemCheck
            isStorageGranted.value = trackingAllowed
            
            sharedPrefs.edit().putBoolean("prefs_storage_passed", trackingAllowed).apply()
            Log.d(TAG, "Storage Request Launcher Callback. Allowed: $trackingAllowed")
        }

        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val hasPassedBefore = sharedPrefs.getBoolean("prefs_storage_passed", false)
        val systemVerification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            permissionsToRequest.all { 
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
            }
        }
        
        isStorageGranted.value = systemVerification || hasPassedBefore

        if (!systemVerification && !hasPassedBefore) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest)
        }

        setContent {
            ReviveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val notificationActive by isNotificationGranted
                    val storageActive by isStorageGranted

                    if (showBatteryRationale) {
                        AlertDialog(
                            onDismissRequest = { 
                                showBatteryRationale = false
                                sharedPrefs.edit().putBoolean("prefs_battery_prompt_dismissed", true).apply()
                            },
                            title = { Text("Background Durability") },
                            text = { Text("To ensure Revive catches deleted messages instantly, please set Battery Usage to 'Unrestricted'.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showBatteryRationale = false
                                    sharedPrefs.edit().putBoolean("prefs_battery_prompt_dismissed", true).apply()
                                    launchBatterySettings()
                                }) { Text("Settings") }
                            },
                            dismissButton = {
                                TextButton(onClick = { 
                                    showBatteryRationale = false 
                                    sharedPrefs.edit().putBoolean("prefs_battery_prompt_dismissed", true).apply()
                                }) {
                                    Text("Maybe Later")
                                }
                            }
                        )
                    }

                    if (!notificationActive || !storageActive) {
                        PermissionScreen()
                    } else {
                        val threads by viewModel.threads.collectAsStateWithLifecycle()
                        val threadState by viewModel.selectedThread.collectAsStateWithLifecycle()
                        val selected = threadState

                        if (selected != null) {
                            // BUG FIX: Ensure we only escape if the thread is truly gone (not just loading)
                            val currentThreadExists = remember(threads, selected) {
                                if (threads.isEmpty()) true // Don't escape while loading/updating
                                threads.any { it.packageName == selected.first && it.senderName == selected.second }
                            }

                            if (!currentThreadExists) {
                                // Only clear if the user deleted the thread or it was purged
                                viewModel.clearSelectedThread()
                            }

                            BackHandler(enabled = true) {
                                viewModel.clearSelectedThread()
                            }
                            
                            val messages by viewModel.messages.collectAsStateWithLifecycle()
                            ChatDetailScreen(
                                senderName = selected.second,
                                packageName = selected.first,
                                messages = messages,
                                onBackClick = { viewModel.clearSelectedThread() },
                                onDeleteMessages = { messagesToDelete ->
                                    viewModel.deleteMessages(messagesToDelete)
                                },
                                onDeleteAllMessages = {
                                    // Separates clearing individual logs from ripping out the core structural thread row
                                    viewModel.clearMessages(selected.first, selected.second)
                                    viewModel.clearSelectedThread()
                                }
                            )
                        } else {
                            // Wire the state properties straight to the persistent background Coroutine system
                            ThreadListScreen(
                                threads = threads,
                                onThreadClick = { packageName, senderName ->
                                    viewModel.selectThread(packageName, senderName)
                                },
                                onDeleteThreadsRequested = { threadsToDelete ->
                                    viewModel.deleteThreadsDeferred(threadsToDelete)
                                },
                                onUndoDeleteThreads = { threadsToRestore ->
                                    viewModel.undoDeleteThreads(threadsToRestore)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isNotificationGranted.value = isNotificationServiceEnabled(this)
        
        val sharedPrefs = getSharedPreferences("revive_prefs", Context.MODE_PRIVATE)
        val hasPassedBefore = sharedPrefs.getBoolean("prefs_storage_passed", false)

        val systemVerification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE).all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        isStorageGranted.value = systemVerification || hasPassedBefore
        checkBatteryOptimizations()
    }

    private fun checkBatteryOptimizations() {
        val isDismissed = getSharedPreferences("revive_prefs", Context.MODE_PRIVATE)
            .getBoolean("prefs_battery_prompt_dismissed", false)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)

        if (!isIgnoring && !showBatteryRationale && !isDismissed) {
            showBatteryRationale = true
        }
    }

    private fun launchBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, ReviveConstants.ENABLED_NOTIFICATION_LISTENERS)
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = android.content.ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }
}