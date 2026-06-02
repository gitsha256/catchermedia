package com.catcher.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageLog): Long

    @Delete
    suspend fun deleteMessages(messages: List<MessageLog>)

    @Query("DELETE FROM message_logs WHERE packageName = :packageName AND senderName = :senderName")
    suspend fun deleteMessagesBySender(packageName: String, senderName: String)

    @Query("SELECT * FROM message_logs WHERE id IN (SELECT MAX(id) FROM message_logs GROUP BY packageName, senderName) ORDER BY timestamp DESC")
    fun getUniqueChatThreads(): Flow<List<MessageLog>>

    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName ORDER BY timestamp DESC")
    fun getMessagesForThread(packageName: String, senderName: String): Flow<List<MessageLog>>

    @Query("UPDATE message_logs SET isDeleted = 1 WHERE id = :messageId")
    suspend fun markMessageAsDeleted(messageId: Int)

    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName AND timestamp >= :timeLimit AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLastMessageForDeletion(packageName: String, senderName: String, timeLimit: Long): MessageLog?

    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestMessage(packageName: String, senderName: String): MessageLog?
}
