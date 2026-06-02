package com.catcher.app

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catcher.app.ui.screen.ChatDetailScreen
import com.catcher.app.ui.screen.PermissionScreen
import com.catcher.app.ui.screen.ThreadListScreen
import com.catcher.app.ui.theme.CatcherTheme
import com.catcher.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val isPermissionGranted = mutableStateOf(false)

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as CatcherApp).database.messageDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge display to allow content to flow under system bars.
        // This automatically sets Window.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        
        super.onCreate(savedInstanceState)

        setContent {
            CatcherTheme {
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
                                onThreadDelete = { packageName, senderName ->
                                    viewModel.deleteThread(packageName, senderName)
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
