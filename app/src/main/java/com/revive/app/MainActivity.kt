package com.revive.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revive.app.ui.screen.ChatDetailScreen
import com.revive.app.ui.screen.PermissionScreen
import com.revive.app.ui.screen.ThreadListScreen
import com.revive.app.ui.theme.ReviveTheme 
import com.revive.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val isPermissionGranted = mutableStateOf(false)

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as ReviveApp).database.messageDao())
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
        isPermissionGranted.value = isNotificationServiceEnabled(this)
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
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