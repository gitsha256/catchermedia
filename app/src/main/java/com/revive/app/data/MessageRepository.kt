package com.revive.app.data

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {

    fun getAllThreads(): Flow<List<MessageLog>> = messageDao.getAllThreads()

    fun getMessagesForThread(packageName: String, senderName: String): Flow<List<MessageLog>> =
        messageDao.getMessagesForThread(packageName, senderName)

    suspend fun insertMessage(message: MessageLog) = messageDao.insertMessage(message)

    suspend fun findLastMessageForDeletion(packageName: String, senderName: String, timeLimit: Long) =
        messageDao.findLastMessageForDeletion(packageName, senderName, timeLimit)

    suspend fun markMessageAsDeleted(id: Long) = messageDao.markMessageAsDeleted(id)

    suspend fun findLatestMessageByPackage(packageName: String) =
        messageDao.findLatestMessageByPackage(packageName)

    suspend fun findLatestMessage(packageName: String, senderName: String) =
        messageDao.findLatestMessage(packageName, senderName)

    suspend fun deleteThread(packageName: String, senderName: String) = messageDao.deleteThread(packageName, senderName)

    suspend fun deleteMessages(messages: List<MessageLog>) = messageDao.deleteMessages(messages)
}