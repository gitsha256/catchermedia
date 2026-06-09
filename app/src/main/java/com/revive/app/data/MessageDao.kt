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

    @Query("SELECT senderName FROM message_logs WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentSenderForPackage(packageName: String): String?

    // Used for duplicate prevention
    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestMessage(packageName: String, senderName: String): MessageLog?

    // BUG-05 Fix: Use Inner Join to retrieve the full row for the latest message per thread
    @Query("SELECT m1.* FROM message_logs m1 INNER JOIN (SELECT packageName, senderName, MAX(timestamp) as maxTs FROM message_logs GROUP BY packageName, senderName) m2 ON m1.packageName = m2.packageName AND m1.senderName = m2.senderName AND m1.timestamp = m2.maxTs ORDER BY m1.timestamp DESC")
    fun getAllThreads(): Flow<List<MessageLog>>

    // BUG-13 Fix: Order by ASC to ensure chronological stacking in LazyColumn (reverseLayout = true)
    @Query("SELECT * FROM message_logs WHERE packageName = :packageName AND senderName = :senderName ORDER BY timestamp ASC")
    fun getMessagesForThread(packageName: String, senderName: String): Flow<List<MessageLog>>

    @Query("DELETE FROM message_logs WHERE packageName = :packageName AND senderName = :senderName")
    suspend fun clearMessagesForThread(packageName: String, senderName: String)

    @Query("DELETE FROM message_logs WHERE packageName = :packageName AND senderName = :senderName")
    suspend fun deleteThread(packageName: String, senderName: String)

    @Delete
    suspend fun deleteMessages(messages: List<MessageLog>)

    @Query("DELETE FROM message_logs")
    suspend fun nukeTable()
}