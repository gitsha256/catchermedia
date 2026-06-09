package com.revive.app.util

object ReviveConstants {
    // System constants
    /**
     * Explicitly defined as a string literal to bypass Android SDK 
     * stub visibility compilation issues.
     */
    const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

    // Supported Packages
    const val PKG_WHATSAPP = "com.whatsapp"
    const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val PKG_TELEGRAM = "com.telegram.messenger"
    const val PKG_TELEGRAM_X = "org.thunderdog.challegram"

    val TARGET_PACKAGES = setOf(
        PKG_WHATSAPP,
        PKG_WHATSAPP_BUSINESS,
        PKG_TELEGRAM,
        PKG_TELEGRAM_X
    )

    // Filters
    val EXCLUDED_TEXTS = listOf(
        "checking for new messages", "media downloading", "finished downloading",
        "whatsapp web is active", "backup in progress", "whatsapp web", "backup",
        "restoring chat history", "connecting...", "updating...", "telegram is running"
    )

    val DELETION_KEYWORDS = listOf(
        "this message was deleted", "message was deleted", 
        "you deleted this message", "message deleted"
    )

    // Storage
    const val RESCUE_DIRECTORY = "Revive"
    const val MIME_TYPE_IMAGE = "image/jpeg"
    val SUPPORTED_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
    const val MIN_FILE_SIZE_BYTES = 1024L
}