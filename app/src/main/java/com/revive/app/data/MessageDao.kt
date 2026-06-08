package com.revive.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageLog)

    // Finds the most recent non-deleted message in a thread within a specific timeframe (e.g., 5 mins)
    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName AND timestamp >= :timeLimit AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLastMessageForDeletion(packageName: String, senderName: String, timeLimit: Long): MessageLog?

    @Query("UPDATE message_logs SET isDeleted = 1 WHERE id = :id")
    suspend fun markMessageAsDeleted(id: Long)

    @Query("SELECT * FROM message_logs WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestMessageByPackage(packageName: String): MessageLog?

    // Used for duplicate prevention
    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestMessage(packageName: String, senderName: String): MessageLog?

    // Optimized: Returns the latest message for every unique chat thread
    @Query("SELECT * FROM message_logs WHERE id IN (SELECT MAX(id) FROM message_logs GROUP BY packageName, senderName) ORDER BY timestamp DESC")
    fun getAllThreads(): Flow<List<MessageLog>>

    // Returns the full conversation for a specific sender
    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName ORDER BY timestamp DESC")
    fun getMessagesForThread(packageName: String, senderName: String): Flow<List<MessageLog>>

    @Query("DELETE FROM message_logs WHERE packageName = :packageName AND senderName = :senderName")
    suspend fun deleteThread(packageName: String, senderName: String)

    @Delete
    suspend fun deleteMessages(messages: List<MessageLog>)

    @Query("DELETE FROM message_logs")
    suspend fun nukeTable()
}