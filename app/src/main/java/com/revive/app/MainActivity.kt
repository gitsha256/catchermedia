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

class MainActivity : ComponentActivity() {

    private val isPermissionGranted = mutableStateOf(false)
    private var showBatteryRationale by mutableStateOf(false)

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as ReviveApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Modern request for Media Permission
        val requestMultiplePermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (!granted) {
                // Optional: Inform user that media rescue features may be limited
            }
        }

        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        if (permissionsToRequest.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest)
        }

        setContent {
            // Using uppercase ReviveTheme here to match your custom theme wrapper
            ReviveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isGranted by isPermissionGranted

                    if (showBatteryRationale) {
                        AlertDialog(
                            onDismissRequest = { showBatteryRationale = false },
                            title = { Text("Background Durability") },
                            text = { Text("To ensure Revive catches deleted messages instantly on this device, please set Battery Usage to 'Unrestricted'.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showBatteryRationale = false
                                    launchBatterySettings()
                                }) { Text("Settings") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBatteryRationale = false }) {
                                    Text("Maybe Later")
                                }
                            }
                        )
                    }

                    if (!isGranted) {
                        PermissionScreen()
                    } else {
                        val threadState by viewModel.selectedThread.collectAsStateWithLifecycle()
                        val selected = threadState
                        if (selected != null) {
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
                                    viewModel.clearMessages(selected.first, selected.second)
                                }
                            )
                        } else {
                            val threads by viewModel.threads.collectAsStateWithLifecycle()
                            ThreadListScreen(
                                threads = threads,
                                onThreadClick = { packageName, senderName ->
                                    viewModel.selectThread(packageName, senderName)
                                },
                                onDeleteThreads = { threadsToDelete ->
                                    threadsToDelete.forEach { (pkg, sender) ->
                                        viewModel.deleteThread(pkg, sender)
                                    }
                                },
                                onHideThreads = { viewModel.hideThreads(it) },
                                onUnhideThreads = { viewModel.unhideThreads(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isPermissionGranted.value = isNotificationServiceEnabled(this)
        checkBatteryOptimizations()
    }

    private fun checkBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName) && !showBatteryRationale) {
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