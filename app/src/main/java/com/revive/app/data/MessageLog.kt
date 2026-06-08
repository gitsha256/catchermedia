package com.revive.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_logs")
data class MessageLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val isDeleted: Boolean = false,
    val mediaPath: String? = null
)
