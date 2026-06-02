package com.catcher.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.catcher.app.data.AppDatabase
import com.catcher.app.data.MessageLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MessageNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database: AppDatabase by lazy { 
        AppDatabase.getDatabase(applicationContext) 
    }

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
        serviceScope.cancel()
    }
}
