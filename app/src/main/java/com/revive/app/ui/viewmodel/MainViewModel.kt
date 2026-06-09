package com.revive.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.revive.app.data.MessageRepository
import com.revive.app.data.MessageLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn


class MainViewModel(private val repository: MessageRepository) : ViewModel() {

    private val _hiddenThreads = MutableStateFlow<Set<Pair<String, String>>>(emptySet())

    // Fixing B8 & B17: Filter out threads currently in the "Undo" pending state
    @OptIn(ExperimentalCoroutinesApi::class)
    val threads: StateFlow<List<MessageLog>> = combine(
        repository.getAllThreads(),
        _hiddenThreads
    ) { allThreads, hidden ->
        allThreads.filter { (it.packageName to it.senderName) !in hidden }
    }
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
                repository.getMessagesForThread(selected.first, selected.second)
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

    fun hideThreads(keys: List<Pair<String, String>>) {
        _hiddenThreads.value = _hiddenThreads.value + keys
    }

    fun unhideThreads(keys: List<Pair<String, String>>) {
        _hiddenThreads.value = _hiddenThreads.value - keys.toSet()
    }

    fun deleteMessages(messages: List<MessageLog>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessages(messages)
        }
    }

    fun deleteThread(packageName: String, senderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteThread(packageName, senderName)
        }
    }

    fun clearMessages(packageName: String, senderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteThread(packageName, senderName)
        }
    }

    class Factory(private val repository: MessageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
