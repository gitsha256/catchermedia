package com.revive.app.service

import android.app.Notification
import android.content.Context
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.absoluteValue

private const val TAG = "Revive"

class MessageNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: MessageRepository by lazy {
        (application as ReviveApp).repository
    }

    /**
     * Lock striping pool to prevent concurrent duplicate checks and database mutations
     * for the same conversation partition. Uses a fixed size to avoid memory leaks
     * while providing high concurrency across different chats.
     */
    private val locks = Array(16) { Mutex() }
    private fun getLock(key: String) = locks[key.hashCode().absoluteValue % locks.size]
    
    private var mediaObserver: ContentObserver? = null

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
        setupMediaStoreObserver()
    }

    private fun setupMediaStoreObserver() {
        Log.d(TAG, "[${Thread.currentThread().name}] Initializing MediaStore ContentObserver Engine")
        
        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri != null) {
                    serviceScope.launch {
                        rescueMediaFromUri(uri)
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver!!
        )
    }

    private suspend fun rescueMediaFromUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media.DATA, 
                    MediaStore.Images.Media.DISPLAY_NAME
                )
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val filePathIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        val fileNameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        
                        val absolutePath = cursor.getString(filePathIdx) ?: return@use
                        val fileName = cursor.getString(fileNameIdx) ?: "unknown_media.jpg"

                        if (absolutePath.contains("Sent", ignoreCase = true)) return@use

                        // CRITICAL: Ignore processing events triggered by our own saves into RescuedMedia
                        if (absolutePath.contains("RescuedMedia", ignoreCase = true)) {
                            return@use
                        }

                        val isWhatsapp = absolutePath.contains("com.whatsapp", ignoreCase = true)
                        val isWhatsappBiz = absolutePath.contains("com.whatsapp.w4b", ignoreCase = true)

                        if (isWhatsapp || isWhatsappBiz) {
                            rescueFilePayloadViaUri(
                                uri, 
                                fileName, 
                                if (isWhatsappBiz) PKG_WHATSAPP_BUSINESS else PKG_WHATSAPP
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing MediaStore content stream", e)
            }
        }
    }

    private suspend fun rescueFilePayloadViaUri(sourceUri: Uri, fileName: String, sourcePkg: String) {
        var pfd: ParcelFileDescriptor? = null
        try {
            // Target Shared Public Storage Path directory layout tree
            val publicPicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val rescueFolder = File(publicPicturesDir, "RescuedMedia")
            if (!rescueFolder.exists()) {
                rescueFolder.mkdirs()
            }

            val destinationFile = File(rescueFolder, "rescued_${fileName}")
            
            // Deduplication Guard: Terminate process if duplicate item mapping is tracked on disk
            if (destinationFile.exists() && destinationFile.length() > 0) {
                return
            }

            var retries = 0
            var openedSuccessfully = false
            
            while (retries < 6 && !openedSuccessfully) {
                try {
                    pfd = contentResolver.openFileDescriptor(sourceUri, "r")
                    if (pfd != null && pfd.statSize > 1024) {
                        openedSuccessfully = true
                    } else {
                        pfd?.close()
                        delay(250)
                        retries++
                    }
                } catch (e: Exception) {
                    pfd?.close()
                    delay(300)
                    retries++
                }
            }

            if (!openedSuccessfully || pfd == null) {
                return
            }

            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            val finalPath = destinationFile.absolutePath
            Log.d(TAG, "File safely cloned to public directory: $finalPath")

            // Broadcast asset information update directly to system index frameworks
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(finalPath),
                arrayOf("image/jpeg"),
                object : MediaScannerConnection.OnScanCompletedListener {
                    override fun onScanCompleted(path: String?, uri: Uri?) {
                        Log.d(TAG, "MediaScanner complete: indexed entry -> $uri")
                    }
                }
            )

            // ATOMIC RESOLUTION: Resolve sender with DB polling and real-time notification fallback
            val resolvedSender = resolveSenderWithRetry(sourcePkg)

            val mediaLog = MessageLog(
                packageName = sourcePkg.trim(),
                senderName = resolvedSender.trim(),
                messageText = "📷 Rescued Photo ($fileName)",
                timestamp = System.currentTimeMillis(), // Enforce fresh clock for media
                mediaPath = finalPath,
                isDeleted = false
            )
            repository.insertMessage(mediaLog)

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Media stream cloning engine pipeline crash", e)
        } finally {
            try { pfd?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Implements a non-blocking retry loop to wait for the notification system to catch up.
     * This prevents media from being assigned to "Unknown Sender" when the file observer
     * triggers slightly before the text notification is persisted.
     */
    private suspend fun resolveSenderWithRetry(packageName: String): String {
        val cleanPkg = packageName.trim()
        repeat(6) { attempt ->
            // 1. Search database for the most recent sender in this app
            val sender = repository.getMostRecentSenderForPackage(cleanPkg)
            if (sender != null) return sender
            
            // 2. Real-time fallback: Inspect current active notifications to find the sender
            val activeSender = try {
                getActiveNotifications()?.firstOrNull { 
                    it.packageName == cleanPkg && 
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 
                }?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            } catch (e: Exception) { null }

            if (!activeSender.isNullOrEmpty() && 
                !activeSender.equals("WhatsApp", true) && 
                !activeSender.equals("Telegram", true)) {
                return activeSender
            }

            Log.d(TAG, "Media Intercept: Resolving sender identity (Attempt ${attempt + 1}/6)")
            delay(500) // 500ms intervals * 6 = 3.0s window
        }
        Log.w(TAG, "Media Intercept: No matching sender found in 3s window. Falling back to Unknown.")
        return "Unknown Sender"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName.trim()
        if (!ReviveConstants.TARGET_PACKAGES.contains(packageName)) return

        val extras = sbn.notification.extras ?: return
        if (sbn.isOngoing) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        if (title.isNullOrEmpty() || text.isNullOrEmpty()) return

        val lowerText = text.lowercase()
        if (ReviveConstants.EXCLUDED_TEXTS.any { lowerText.contains(it) }) return
        
        if ((title.equals("WhatsApp", true) || title.equals("Telegram", true)) &&
            (lowerText.contains("checking") || lowerText.contains("incoming") || lowerText.contains("running"))) return

        val timestamp = sbn.postTime
        val lockKey = "$packageName:${title.lowercase()}"
        val mutex = getLock(lockKey)

        serviceScope.launch {
            // THREAD-SAFE SYNC: Use a mutex to ensure duplicate checks and inserts are atomic per chat
            mutex.withLock {
                try {
                val isDeletionMessage = ReviveConstants.DELETION_KEYWORDS.any { lowerText.contains(it) }

                if (isDeletionMessage) {
                    val timeLimit = timestamp - 300_000
                    val lastMsg = repository.findLastMessageForDeletion(
                        packageName = packageName,
                        senderName = title.trim(),
                        timeLimit = timeLimit
                    )

                    if (lastMsg != null) {
                        Log.d(TAG, "Thread-Safe: Marking message as deleted for $title")
                        repository.markMessageAsDeleted(lastMsg.id)
                    }
                } else {
                    val cleanTitle = title.trim()
                    val latestMsg = repository.findLatestMessage(packageName, cleanTitle)
                    // DUPLICATE SUPPRESSION: Removed isPhotoAlert exception to prevent burst logging.
                    // Also checks if current text is already contained in the last message (e.g., "Photo" vs "Rescued Photo").
                    val isDuplicate = latestMsg != null &&
                            (latestMsg.messageText == text || latestMsg.messageText.contains(text, ignoreCase = true)) &&
                            (timestamp - latestMsg.timestamp) < 15_000

                    if (!isDuplicate) {
                        val messageLog = MessageLog(
                            packageName = packageName,
                            senderName = title,
                            messageText = text,
                            timestamp = timestamp,
                            isDeleted = false
                        )
                        Log.d(TAG, "Thread-Safe: Inserting unique message from $title")
                        repository.insertMessage(messageLog)
                    } else {
                        Log.d(TAG, "Thread-Safe: Suppressed duplicate notification for $title")
                    }
                }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling logging data injection", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaObserver?.let { contentResolver.unregisterContentObserver(it) }
        serviceScope.cancel()
    }
}