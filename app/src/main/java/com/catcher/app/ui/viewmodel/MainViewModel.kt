package com.catcher.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catcher.app.data.MessageDao
import com.catcher.app.data.MessageLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn


class MainViewModel(private val messageDao: MessageDao) : ViewModel() {

    val threads: StateFlow<List<MessageLog>> = messageDao.getUniqueChatThreads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedThread = MutableStateFlow<Pair<String, String>?>(null)
    val selectedThread = _selectedThread.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageLog>> = _selectedThread
        .flatMapLatest { selected ->
            if (selected == null) {
                flowOf(emptyList())
            } else {
                messageDao.getMessagesForThread(selected.first, selected.second)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectThread(packageName: String, senderName: String) {
        _selectedThread.value = Pair(packageName, senderName)
    }

    fun clearSelectedThread() {
        _selectedThread.value = null
    }

    fun deleteMessages(messages: List<MessageLog>) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.deleteMessages(messages)
        }
    }

    fun deleteThread(packageName: String, senderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.deleteMessagesBySender(packageName, senderName)
        }
    }

    fun clearMessages(packageName: String, senderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.deleteMessagesBySender(packageName, senderName)
        }
    }

    class Factory(private val messageDao: MessageDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(messageDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
