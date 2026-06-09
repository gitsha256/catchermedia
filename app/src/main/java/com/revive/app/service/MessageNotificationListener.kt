package com.revive.app.service

import android.app.Notification
import android.content.ContentValues
import android.os.Environment
import android.os.FileObserver
import android.provider.MediaStore
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

class MessageNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: MessageRepository by lazy {
        (application as ReviveApp).repository
    }
    private val observers = mutableListOf<FileObserver>()

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
        serviceScope.launch {
            startMediaTracking()
        }
    }

    private suspend fun startMediaTracking() {
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
            
            // Modern Scoped Storage compliant saving using MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "rescued_${System.currentTimeMillis()}_$fileName")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/${ReviveConstants.RESCUE_DIRECTORY}")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val contentResolver = applicationContext.contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            var finalPath = ""
            uri?.let { targetUri ->
                contentResolver.openOutputStream(targetUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(targetUri, contentValues, null, null)
                }
                finalPath = targetUri.toString()
            }

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
            Log.d("MediaRescue", "Successfully rescued incoming media file.")

        } catch (e: Exception) {
            Log.e("MediaRescue", "Error during file rescue", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (!ReviveConstants.TARGET_PACKAGES.contains(packageName)) return

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
        if (ReviveConstants.EXCLUDED_TEXTS.any { lowerText.contains(it) }) return
        
        val isSystemStatus = (title.equals("WhatsApp", true) || title.equals("Telegram", true)) &&
                (lowerText.contains("checking") || lowerText.contains("incoming") || lowerText.contains("running"))
        if (isSystemStatus) return

        val timestamp = sbn.postTime

        serviceScope.launch {
            try {
                val isDeletionMessage = ReviveConstants.DELETION_KEYWORDS.any { lowerText.contains(it) }

                if (isDeletionMessage) {
                    // Look back 5 minutes (300,000 ms) for the latest message in this thread
                    val timeLimit = timestamp - 300_000
                    val lastMsg = repository.findLastMessageForDeletion(
                        packageName = packageName,
                        senderName = title,
                        timeLimit = timeLimit
                    )

                    if (lastMsg != null) {
                        repository.markMessageAsDeleted(lastMsg.id)
                        Log.d("MessageNotification", "Flagged message as deleted (ID: ${lastMsg.id})")
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
