package com.revive.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revive.app.data.MessageLog
import java.time.Instant
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    threads: List<MessageLog>,
    onThreadClick: (packageName: String, senderName: String) -> Unit,
    onDeleteThreads: (List<Pair<String, String>>) -> Unit, // This now happens after Snackbar timeout
    onHideThreads: (List<Pair<String, String>>) -> Unit,
    onUnhideThreads: (List<Pair<String, String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Fixing B7: Optimized Selection State with O(1) Lookups
    var selectedKeys by remember { mutableStateOf(setOf<Pair<String, String>>()) }
    val isSelectionMode = selectedKeys.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedKeys = emptySet()
    }

    val filteredThreads = remember(threads, searchQuery) {
        threads.filter {
            it.senderName.contains(searchQuery, ignoreCase = true) ||
            it.messageText.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
        if (isSelectionMode) {
            TopAppBar(
                title = { Text("${selectedKeys.size} Selected") },
                navigationIcon = {
                    IconButton(onClick = { selectedKeys = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val allKeys = filteredThreads.map { it.packageName to it.senderName }
                            if (selectedKeys.size == allKeys.size) {
                                selectedKeys = emptySet()
                            } else {
                                selectedKeys = allKeys.toSet()
                            }
                        }
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                    }
                    IconButton(
                        onClick = {
                            val toDelete = selectedKeys.toList()
                            onHideThreads(toDelete)
                            selectedKeys = emptySet()
                            
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "${toDelete.size} threads deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onUnhideThreads(toDelete)
                                } else {
                                    onDeleteThreads(toDelete)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "revive",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Rescued Messages & Notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search sender or content...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredThreads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isEmpty()) "No caught messages yet" else "No match found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Fixing B9/B10: Cache thread-safe formatters outside the list loop
            val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()) }
            val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault()) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredThreads,
                    key = { it.packageName to it.senderName }
                ) { thread ->
                    val threadKey = thread.packageName to thread.senderName
                    val isSelected = selectedKeys.contains(threadKey)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                onHideThreads(listOf(threadKey))
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Thread deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onUnhideThreads(listOf(threadKey))
                                    } else {
                                        onDeleteThreads(listOf(threadKey))
                                    }
                                }
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                MaterialTheme.colorScheme.errorContainer
                            } else Color.Transparent
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    ) {
                        ThreadItem(
                            thread = thread,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            timeFormatter = timeFormatter,
                            dateFormatter = dateFormatter,
                            onToggleSelection = {
                                selectedKeys = if (isSelected) selectedKeys - threadKey 
                                else selectedKeys + threadKey
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedKeys = if (isSelected) selectedKeys - threadKey 
                                    else selectedKeys + threadKey
                                } else {
                                    onThreadClick(thread.packageName, thread.senderName)
                                }
                            }
                        )
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadItem(
    thread: MessageLog,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit
) {
    val isTelegram = thread.packageName.contains("telegram") || thread.packageName.contains("challegram")
    val badgeColor = if (isTelegram) Color(0xFF229ED9) else Color(0xFF25D366)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!isSelectionMode) onToggleSelection()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = thread.senderName.take(1).uppercase(),
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Fixing B10: Logic wrapped in remember to avoid per-frame calculation
                    val timestampText = remember(thread.timestamp) {
                        val msgTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(thread.timestamp), ZoneId.systemDefault())
                        val now = LocalDateTime.now()
                        if (msgTime.toLocalDate() == now.toLocalDate()) msgTime.format(timeFormatter)
                        else msgTime.format(dateFormatter)
                    }

                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = thread.messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (thread.isDeleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (thread.isDeleted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Deleted",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
