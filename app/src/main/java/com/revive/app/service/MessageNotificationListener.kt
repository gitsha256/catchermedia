package com.revive.app.service

import android.app.Notification
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.revive.app.ReviveApp
import com.revive.app.data.MessageRepository
import com.revive.app.data.MessageLog
import com.revive.app.util.ReviveConstants
import com.revive.app.util.ReviveConstants.PKG_WHATSAPP
import com.revive.app.util.ReviveConstants.PKG_WHATSAPP_BUSINESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "Revive"

class MessageNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: MessageRepository by lazy {
        (application as ReviveApp).repository
    }
    private val observers = mutableListOf<FileObserver>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "[${Thread.currentThread().name}] Notification Listener Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "[${Thread.currentThread().name}] Notification Listener Disconnected")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[${Thread.currentThread().name}] MessageNotificationListener created")
        serviceScope.launch {
            startMediaTracking()
        }
    }

    private suspend fun startMediaTracking() {
        Log.d(TAG, "[${Thread.currentThread().name}] Initializing Media Tracking Paths")
        val root = Environment.getExternalStorageDirectory().absolutePath
        val basePackages = listOf(PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS)
        val discoveredPaths = mutableSetOf<String>()

        basePackages.forEach { pkg ->
            val isW4b = pkg.contains("w4b")
            val appFolderName = if (isW4b) "WhatsApp Business" else "WhatsApp"
            val pkgRoot = File("$root/Android/media/$pkg/$appFolderName")

            if (pkgRoot.exists()) {
                // 1. Standard/Legacy Path
                val standardPath = File(pkgRoot, "Media/${if (isW4b) "WhatsApp Business Images" else "WhatsApp Images"}")
                addPathIfValid(standardPath, discoveredPaths)

                // 2. Multi-account/Dual App Path: accounts/{id}/Media/WhatsApp Images
                val accountsDir = File(pkgRoot, "accounts")
                if (accountsDir.exists() && accountsDir.isDirectory) {
                    accountsDir.listFiles()?.filter { it.isDirectory }?.forEach { accountDir ->
                        val accountMediaPath = File(accountDir, "Media/${if (isW4b) "WhatsApp Business Images" else "WhatsApp Images"}")
                        addPathIfValid(accountMediaPath, discoveredPaths)
                    }
                }
            }
        }

        discoveredPaths.forEach { path ->
            Log.d(TAG, "[${Thread.currentThread().name}] Attaching FileObserver to: $path")
            setupObserver(path)
        }
    }

    private fun addPathIfValid(dir: File, set: MutableSet<String>) {
        if (dir.exists() && !dir.name.equals("Sent", ignoreCase = true)) {
            set.add(dir.absolutePath)
            val privateDir = File(dir, "Private")
            if (privateDir.exists()) set.add(privateDir.absolutePath)
        }
    }

    private fun setupObserver(path: String) {
        val mask = FileObserver.CLOSE_WRITE
        
        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(File(path), mask) {
                override fun onEvent(event: Int, fileName: String?) {
                    processFileEvent(path, event, fileName)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(path, mask) {
                override fun onEvent(event: Int, fileName: String?) {
                    processFileEvent(path, event, fileName)
                }
            }
        }
        
        observer.startWatching()
        observers.add(observer)
    }

    private fun processFileEvent(path: String, event: Int, fileName: String?) {
        // Strict guard: Ignore events if the directory being watched is a "Sent" folder
        if (File(path).name.equals("Sent", ignoreCase = true)) return

        if (fileName != null && !fileName.startsWith(".") &&
            (fileName.lowercase().endsWith(".jpg") ||
                    fileName.lowercase().endsWith(".jpeg") ||
                    fileName.lowercase().endsWith(".png"))) {

            Log.d(TAG, "[${Thread.currentThread().name}] Media detected! Event: $event, File: $fileName")
            serviceScope.launch {
                rescueFile(path, fileName)
            }
        }
    }

    private suspend fun rescueFile(parentPath: String, fileName: String) {
        try {
            val sourceFile = File(parentPath, fileName)
            val rescueFolder = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "RescuedMedia")
            
            if (!rescueFolder.exists()) {
                rescueFolder.mkdirs()
            }

        val destinationFile = File(rescueFolder, "rescued_${System.currentTimeMillis()}_$fileName")
        Log.d(TAG, "[${Thread.currentThread().name}] Media rescue started. Source: ${sourceFile.absolutePath} -> Target: ${destinationFile.absolutePath}")

            // Filter out 0-byte or tiny outgoing upload placeholders/thumbnails
            if (!sourceFile.exists() || sourceFile.length() < 1024) {
                Log.w(TAG, "[${Thread.currentThread().name}] Rescue aborted: File too small or missing (${sourceFile.length()} bytes)")
                return
            }
            
            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val finalPath = destinationFile.absolutePath

            // Identify source package based on path
            val sourcePkg = if (parentPath.contains("w4b")) PKG_WHATSAPP_BUSINESS else PKG_WHATSAPP

            // Heuristic: Associate this file with the most recent sender from this package
            val lastNotification = repository.findLatestMessageByPackage(sourcePkg)
            val probableSender = lastNotification?.senderName ?: "Media Interceptor"

            // Log to Room as a special media entry
            val mediaLog = MessageLog(
                packageName = sourcePkg,
                senderName = probableSender,
                messageText = "📷 Rescued Photo ($fileName)",
                timestamp = System.currentTimeMillis(),
                mediaPath = finalPath
            )
            repository.insertMessage(mediaLog)
            Log.d(TAG, "[${Thread.currentThread().name}] Media rescue successful. Size: ${destinationFile.length()} bytes")

        } catch (e: Exception) {
            Log.e(TAG, "[${Thread.currentThread().name}] CRITICAL: Media rescue failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) {
            Log.w(TAG, "[${Thread.currentThread().name}] onNotificationPosted: sbn is null")
            return
        }

        val packageName = sbn.packageName
        if (!ReviveConstants.TARGET_PACKAGES.contains(packageName)) {
            Log.d(TAG, "[${Thread.currentThread().name}] Ignored notification from: $packageName")
            return
        }

        val extras = sbn.notification.extras ?: return
        Log.d(TAG, "[${Thread.currentThread().name}] Received from $packageName. Extras keys: ${extras.keySet()}")

        // Skip ongoing/system notifications like calls, uploads, persistent notifications
        if (sbn.isOngoing) {
            Log.d(TAG, "[${Thread.currentThread().name}] Skipping ongoing notification")
            return
        }

        // Skip group summary notifications (e.g., "3 new messages") 
        // to avoid saving redundant or empty data.
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        // Validation safeguards
        if (title.isNullOrEmpty() || text.isNullOrEmpty()) return

        // Filter out system utility messages
        val lowerText = text.lowercase()
        if (ReviveConstants.EXCLUDED_TEXTS.any { lowerText.contains(it) }) {
            Log.d(TAG, "[${Thread.currentThread().name}] Filtered excluded utility text: $text")
            return
        }
        
        val isSystemStatus = (title.equals("WhatsApp", true) || title.equals("Telegram", true)) &&
                (lowerText.contains("checking") || lowerText.contains("incoming") || lowerText.contains("running"))
        if (isSystemStatus) return

        val timestamp = sbn.postTime

        serviceScope.launch {
            try {
                val isDeletionMessage = ReviveConstants.DELETION_KEYWORDS.any { lowerText.contains(it) }

                if (isDeletionMessage) {
                    Log.d(TAG, "[${Thread.currentThread().name}] Deletion detected in $packageName from $title")
                    // Look back 5 minutes (300,000 ms) for the latest message in this thread
                    val timeLimit = timestamp - 300_000
                    val lastMsg = repository.findLastMessageForDeletion(
                        packageName = packageName,
                        senderName = title,
                        timeLimit = timeLimit
                    )

                    if (lastMsg != null) {
                        repository.markMessageAsDeleted(lastMsg.id)
                    }
                } else {
                    // Check duplicate prevention within last 15 seconds
                    val latestMsg = repository.findLatestMessage(packageName, title)
                    val isDuplicate = latestMsg != null &&
                            latestMsg.messageText == text &&
                            (timestamp - latestMsg.timestamp) < 15_000

                    if (!isDuplicate) {
                        val messageLog = MessageLog(
                            packageName = packageName,
                            senderName = title,
                            messageText = text,
                            timestamp = timestamp,
                            isDeleted = false
                        )
                        repository.insertMessage(messageLog)
                    } else {
                        Log.d(TAG, "[${Thread.currentThread().name}] Duplicate notification ignored for $title")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[${Thread.currentThread().name}] Error processing notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "[${Thread.currentThread().name}] MessageNotificationListener onDestroy")
        observers.forEach { it.stopWatching() }
        observers.clear()
        serviceScope.cancel()
    }
}
