package com.revive.app.service

import android.app.Notification
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.FileObserver
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.revive.app.data.AppDatabase
import com.revive.app.data.MessageDao
import com.revive.app.data.MessageLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MessageNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database: AppDatabase by lazy { 
        AppDatabase.getDatabase(applicationContext) 
    }
    private val observers = mutableListOf<FileObserver>()

    private val targetPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.telegram.messenger",
        "org.thunderdog.challegram"
    )

    private val excludedTexts = listOf(
        "checking for new messages",
        "media downloading",
        "finished downloading",
        "whatsapp web is active",
        "backup in progress",
        "whatsapp web",
        "backup",
        "restoring chat history",
        "connecting...",
        "updating...",
        "telegram is running"
    )

    private val deletionKeywords = listOf(
        "this message was deleted",
        "message was deleted",
        "you deleted this message",
        "message deleted"
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("MessageNotification", "Notification Listener Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w("MessageNotification", "Notification Listener Disconnected")
    }

    override fun onCreate() {
        super.onCreate()
        startMediaTracking()
    }

    private fun startMediaTracking() {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val basePackages = listOf("com.whatsapp", "com.whatsapp.w4b")
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
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE
        val observer = object : FileObserver(File(path), mask) { // Refactor: Use File object for constructor
            override fun onEvent(event: Int, fileName: String?) {
                // Strict guard: Ignore events if the directory being watched is a "Sent" folder
                if (File(path).name.equals("Sent", ignoreCase = true)) return

                Log.v("MediaRescue", "Event detected: $event in monitored directory")
                if (fileName != null && !fileName.startsWith(".") &&
                    (fileName.lowercase().endsWith(".jpg") ||
                     fileName.lowercase().endsWith(".jpeg") ||
                     fileName.lowercase().endsWith(".png"))) {
                    
                    serviceScope.launch {
                        rescueFile(path, fileName)
                    }
                }
            }
        }
        observer.startWatching()
        observers.add(observer)
        Log.i("MediaRescue", "Watching directory: $path")
    }

    private suspend fun rescueFile(parentPath: String, fileName: String) {
        try {
            // Allow the file write to complete before attempting to copy
            delay(200)

            val sourceFile = File(parentPath, fileName)
            // Filter out 0-byte or tiny outgoing upload placeholders/thumbnails
            if (!sourceFile.exists() || sourceFile.length() < 1024) return

            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Revive").apply { mkdirs() }
            val destFile = File(publicDir, "rescued_${System.currentTimeMillis()}_$fileName")

            // Immediate copy to secure internal storage
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Trigger MediaScanner so the file appears in Gallery/Photos apps immediately
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(destFile.absolutePath),
                null
            ) { path, uri ->
                Log.d("MediaRescue", "Media scan finished. File successfully indexed.")
            }

            // Identify source package based on path
            val sourcePkg = if (parentPath.contains("w4b")) "com.whatsapp.w4b" else "com.whatsapp"

            // Heuristic: Associate this file with the most recent sender from this package
            val lastNotification = database.messageDao().findLatestMessageByPackage(sourcePkg)
            val probableSender = lastNotification?.senderName ?: "Media Interceptor"

            // Log to Room as a special media entry
            val mediaLog = MessageLog(
                packageName = sourcePkg,
                senderName = probableSender,
                messageText = "📷 Rescued Photo ($fileName)",
                timestamp = System.currentTimeMillis(),
                mediaPath = destFile.absolutePath
            )
            database.messageDao().insertMessage(mediaLog)
            Log.d("MediaRescue", "Successfully rescued incoming media file.")

        } catch (e: Exception) {
            Log.e("MediaRescue", "Error during file rescue", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (!targetPackages.contains(packageName)) return

        // Skip ongoing/system notifications like calls, uploads, persistent notifications
        if (sbn.isOngoing) return

        // Skip group summary notifications (e.g., "3 new messages") 
        // to avoid saving redundant or empty data.
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        // Validation safeguards
        if (title.isNullOrEmpty() || text.isNullOrEmpty()) return

        // Filter out system utility messages
        val lowerText = text.lowercase()
        if (excludedTexts.any { lowerText.contains(it) }) return
        
        val isSystemStatus = (title.equals("WhatsApp", true) || title.equals("Telegram", true)) &&
                (lowerText.contains("checking") || lowerText.contains("incoming") || lowerText.contains("running"))
        if (isSystemStatus) return

        val timestamp = sbn.postTime

        serviceScope.launch {
            try {
                val isDeletionMessage = deletionKeywords.any { lowerText.contains(it) }

                if (isDeletionMessage) {
                    // Look back 5 minutes (300,000 ms) for the latest message in this thread
                    val timeLimit = timestamp - 300_000
                    val lastMsg = database.messageDao().findLastMessageForDeletion(
                        packageName = packageName,
                        senderName = title,
                        timeLimit = timeLimit
                    )

                    if (lastMsg != null) {
                        database.messageDao().markMessageAsDeleted(lastMsg.id.toLong())
                        Log.d("MessageNotification", "Flagged message as deleted (ID: ${lastMsg.id})")
                    }
                } else {
                    // Check duplicate prevention within last 15 seconds
                    val latestMsg = database.messageDao().findLatestMessage(packageName, title)
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
                        database.messageDao().insertMessage(messageLog)
                        Log.i("MessageNotification", "New notification processed and stored locally.")
                    } else {
                        Log.d("MessageNotification", "Duplicate notification ignored.")
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageNotification", "Error processing notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        observers.clear()
        serviceScope.cancel()
    }
}
