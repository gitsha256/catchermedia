package com.revive.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.revive.app.data.MessageRepository
import com.revive.app.data.MessageLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(private val repository: MessageRepository) : ViewModel() {

    // Maps thread identifiers to the timestamp of the latest message when it was hidden
    private val _hiddenThreads = MutableStateFlow<Map<Pair<String, String>, Long>>(emptyMap())
    
    // Tracks active background countdown timers to prevent lifecycle cancellation leaks
    private val deletionJobs = ConcurrentHashMap<Pair<String, String>, Job>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val threads: StateFlow<List<MessageLog>> = repository.getAllThreads()
        .onEach { allThreads ->
            val currentHidden = _hiddenThreads.value
            if (currentHidden.isNotEmpty()) {
                // AUTO-EVICTION LOGIC: If a thread in the DB has a newer timestamp than 
                // when it was hidden, it means a new message arrived.
                val resurrected = allThreads.filter { thread ->
                    val key = thread.packageName to thread.senderName
                    val hiddenAtTimestamp = currentHidden[key]
                    hiddenAtTimestamp != null && thread.timestamp > hiddenAtTimestamp
                }.map { it.packageName to it.senderName }

                if (resurrected.isNotEmpty()) {
                    undoDeleteThreads(resurrected)
                }
            }
        }
        .combine(_hiddenThreads) { allThreads, hidden ->
            allThreads.filter { (it.packageName to it.senderName) !in hidden.keys }
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
                repository.getMessagesForThread(selected.first.trim(), selected.second.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectThread(packageName: String, senderName: String) {
        _selectedThread.value = Pair(packageName.trim(), senderName.trim())
    }

    fun clearSelectedThread() {
        _selectedThread.value = null
    }

    /**
     * Hides threads instantly from the UI list and schedules a robust database purge
     * that survives application closure or minimization events.
     */
    fun deleteThreadsDeferred(keys: List<Pair<String, String>>) {
        // Capture current timestamps so we can detect if a newer message arrives later
        val currentThreads = threads.value
        val keysWithTimestamps = keys.associateWith { key ->
            currentThreads.find { it.packageName == key.first && it.senderName == key.second }?.timestamp ?: 0L
        }

        _hiddenThreads.value = _hiddenThreads.value + keysWithTimestamps
        
        keys.forEach { key ->
            deletionJobs[key]?.cancel() // Reset any previous countdown overlaps
            deletionJobs[key] = viewModelScope.launch(Dispatchers.IO) {
                delay(4000) // Matches SnackbarDuration.Short duration window
                repository.deleteThread(key.first, key.second)
                deletionJobs.remove(key)
            }
        }
    }

    /**
     * Intercepts and stops scheduled database purges instantly if the user selects 'Undo'.
     */
    fun undoDeleteThreads(keys: List<Pair<String, String>>) {
        keys.forEach { key ->
            deletionJobs[key]?.cancel()
            deletionJobs.remove(key)
        }
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
            // FIX: Clears message table rows under this thread partition instead of destroying the thread skeleton entirely
            repository.clearMessagesForThread(packageName, senderName)
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