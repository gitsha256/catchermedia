package com.revive.app.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "Revive"

class MessageRepository(private val messageDao: MessageDao) {

    fun getAllThreads(): Flow<List<MessageLog>> = messageDao.getAllThreads()
        .onStart { Log.d(TAG, "[${Thread.currentThread().name}] Repository: getAllThreads Flow started") }
        .onEach { Log.d(TAG, "[${Thread.currentThread().name}] Repository: getAllThreads emitted ${it.size} threads") }
        .onCompletion { Log.d(TAG, "[${Thread.currentThread().name}] Repository: getAllThreads Flow completed") }
        .flowOn(Dispatchers.IO)

    fun getMessagesForThread(packageName: String, senderName: String): Flow<List<MessageLog>> =
        messageDao.getMessagesForThread(packageName, senderName)
            .onStart { Log.d(TAG, "[${Thread.currentThread().name}] Repository: getMessagesForThread ($senderName) Flow started") }
            .onEach { Log.d(TAG, "[${Thread.currentThread().name}] Repository: getMessagesForThread emitted ${it.size} messages") }
            .flowOn(Dispatchers.IO)

    suspend fun insertMessage(message: MessageLog) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[${Thread.currentThread().name}] Repository insertMessage: pkg=${message.packageName}, sender=${message.senderName}")
            messageDao.insertMessage(message)
            Log.d(TAG, "[${Thread.currentThread().name}] Repository: Message inserted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[${Thread.currentThread().name}] Repository: Failed to insert message", e)
        }
    }

    suspend fun findLastMessageForDeletion(packageName: String, senderName: String, timeLimit: Long): MessageLog? = withContext(Dispatchers.IO) {
        Log.d(TAG, "[${Thread.currentThread().name}] Repository: Searching for deletion target: $senderName in $packageName since $timeLimit")
        messageDao.findLastMessageForDeletion(packageName, senderName, timeLimit).also {
            Log.d(TAG, "[${Thread.currentThread().name}] Repository: findLastMessageForDeletion result: ${if (it != null) "Found ID ${it.id}" else "Not Found"}")
        }
    }

    suspend fun markMessageAsDeleted(id: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[${Thread.currentThread().name}] Repository: Marking message $id as deleted")
            messageDao.markMessageAsDeleted(id)
        } catch (e: Exception) {
            Log.e(TAG, "[${Thread.currentThread().name}] Repository: Error marking message $id deleted", e)
        }
    }

    suspend fun findLatestMessageByPackage(packageName: String): MessageLog? = withContext(Dispatchers.IO) {
        Log.d(TAG, "[${Thread.currentThread().name}] Repository: findLatestByPackage for $packageName")
        messageDao.findLatestMessageByPackage(packageName)
    }

    suspend fun getMostRecentSenderForPackage(packageName: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "[${Thread.currentThread().name}] Repository: getMostRecentSenderForPackage for $packageName")
        messageDao.getMostRecentSenderForPackage(packageName)
    }

    suspend fun findLatestMessage(packageName: String, senderName: String): MessageLog? = withContext(Dispatchers.IO) {
        Log.d(TAG, "[${Thread.currentThread().name}] Repository: findLatest (Duplicate check) for $senderName")
        messageDao.findLatestMessage(packageName, senderName)
    }

    suspend fun deleteThread(packageName: String, senderName: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[${Thread.currentThread().name}] Repository: Deleting thread $senderName")
            messageDao.deleteThread(packageName, senderName)
        } catch (e: Exception) {
            Log.e(TAG, "[${Thread.currentThread().name}] Repository: Error deleting thread $senderName", e)
        }
    }

    suspend fun deleteMessages(messages: List<MessageLog>) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[${Thread.currentThread().name}] Repository: Deleting batch of ${messages.size} messages")
            messageDao.deleteMessages(messages)
        } catch (e: Exception) {
            Log.e(TAG, "[${Thread.currentThread().name}] Repository: Error deleting messages batch", e)
        }
    }

    suspend fun clearMessagesForThread(packageName: String, senderName: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[${Thread.currentThread().name}] Repository: Clearing messages for thread $senderName")
            messageDao.clearMessagesForThread(packageName, senderName)
        } catch (e: Exception) {
            Log.e(TAG, "[${Thread.currentThread().name}] Repository: Error clearing messages for thread $senderName", e)
        }
    }

    suspend fun clearMessages(packageName: String, senderName: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[${Thread.currentThread().name}] Repository: Clearing all messages for $senderName")
            messageDao.deleteThread(packageName, senderName)
        } catch (e: Exception) {
            Log.e(TAG, "[${Thread.currentThread().name}] Repository: Error clearing messages for $senderName", e)
        }
    }
}