package com.catcher.app.service

import android.app.Notification
import android.os.Environment
import android.os.FileObserver
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.catcher.app.data.AppDatabase
import com.catcher.app.data.MessageDao
import com.catcher.app.data.MessageLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        if (dir.exists()) {
            set.add(dir.absolutePath)
            val privateDir = File(dir, "Private")
            if (privateDir.exists()) set.add(privateDir.absolutePath)
        }
    }

    private fun setupObserver(path: String) {
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE
        val observer = object : FileObserver(path, mask) {
            override fun onEvent(event: Int, fileName: String?) {
                Log.v("MediaRescue", "Event detected: $event on file: $fileName in $path")
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
            val sourceFile = File(parentPath, fileName)
            // Small safety check: WhatsApp sometimes creates empty temp files
            if (!sourceFile.exists() || sourceFile.length() < 100) return

            val internalDir = File(applicationContext.filesDir, "rescued_media").apply { mkdirs() }
            val destFile = File(internalDir, "rescued_${System.currentTimeMillis()}_$fileName")

            // Immediate copy to secure internal storage
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Identify source package based on path
            val sourcePkg = if (parentPath.contains("w4b")) "com.whatsapp.w4b" else "com.whatsapp"

            // Log to Room as a special media entry
            val mediaLog = MessageLog(
                packageName = sourcePkg,
                senderName = "Media Interceptor",
                messageText = "Image rescued: $fileName",
                timestamp = System.currentTimeMillis(),
                mediaPath = destFile.absolutePath
            )
            database.messageDao().insertMessage(mediaLog)
            Log.d("MediaRescue", "Successfully rescued image to ${destFile.absolutePath}")

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
                        database.messageDao().markMessageAsDeleted(lastMsg.id)
                        Log.d("MessageNotification", "Successfully flagged deleted message id=${lastMsg.id} from $title")
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
                        Log.i("MessageNotification", "Saved: [$title] $text")
                    } else {
                        Log.d("MessageNotification", "Skipped duplicate notification from $title")
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
