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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    threads: List<MessageLog>,
    onThreadClick: (packageName: String, senderName: String) -> Unit,
    onDeleteThreads: (List<Pair<String, String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedKeys = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedKeys.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedKeys.clear()
    }

    val filteredThreads = remember(threads, searchQuery) {
        threads.filter {
            it.senderName.contains(searchQuery, ignoreCase = true) ||
            it.messageText.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isSelectionMode) {
            TopAppBar(
                title = { Text("${selectedKeys.size} Selected") },
                navigationIcon = {
                    IconButton(onClick = { selectedKeys.clear() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val allKeys = filteredThreads.map { "${it.packageName}|${it.senderName}" }
                            if (selectedKeys.size == allKeys.size) {
                                selectedKeys.clear()
                            } else {
                                selectedKeys.clear()
                                selectedKeys.addAll(allKeys)
                            }
                        }
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                    }
                    IconButton(
                        onClick = {
                            val toDelete = selectedKeys.map { key ->
                                val parts = key.split("|")
                                parts[0] to parts[1]
                            }
                            onDeleteThreads(toDelete)
                            selectedKeys.clear()
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredThreads,
                    key = { "${it.packageName}|${it.senderName}" }
                ) { thread ->
                    val threadKey = "${thread.packageName}|${thread.senderName}"
                    val isSelected = selectedKeys.contains(threadKey)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                onDeleteThreads(listOf(thread.packageName to thread.senderName))
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
                            onToggleSelection = {
                                if (isSelected) selectedKeys.remove(threadKey)
                                else selectedKeys.add(threadKey)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedKeys.remove(threadKey)
                                    else selectedKeys.add(threadKey)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadItem(
    thread: MessageLog,
    isSelected: Boolean,
    isSelectionMode: Boolean,
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
                    Text(
                        text = formatTimestamp(thread.timestamp),
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

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { time = date }
    
    return if (now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)
    ) {
        sdf.format(date)
    } else {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
    }
}
