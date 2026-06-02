package com.catcher.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore

@Entity(tableName = "message_logs")
data class MessageLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val isDeleted: Boolean = false,
    val mediaPath: String? = null
) {
    @Ignore
    var isSelected: Boolean = false
}
