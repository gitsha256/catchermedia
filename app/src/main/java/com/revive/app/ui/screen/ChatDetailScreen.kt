package com.revive.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.revive.app.data.MessageLog
import com.revive.app.ui.theme.RoseBg
import com.revive.app.ui.theme.RoseError
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    senderName: String,
    packageName: String,
    messages: List<MessageLog>,
    onBackClick: () -> Unit,
    onDeleteMessages: (List<MessageLog>) -> Unit,
    onDeleteAllMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTelegram = packageName.contains("telegram") || packageName.contains("challegram")
    val appThemeColor = if (isTelegram) Color(0xFF229ED9) else Color(0xFF25D366)

    // Fixing B7: Optimized Selection State with O(1) Lookups
    var selectedMessageIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionModeActive = selectedMessageIds.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionModeActive) {
                        Text(
                            text = "${selectedMessageIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Column {
                            Text(
                                text = senderName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isTelegram) "Telegram" else "WhatsApp",
                                style = MaterialTheme.typography.labelMedium,
                                color = appThemeColor
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionModeActive) {
                        IconButton(onClick = { selectedMessageIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSelectionModeActive) {
                        IconButton(
                            onClick = {
                                selectedMessageIds = messages.map { it.id }.toSet()
                            }
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        
                        IconButton(
                            onClick = {
                                val selectedMessages = messages.filter { it.id in selectedMessageIds }
                                onDeleteMessages(selectedMessages)
                                selectedMessageIds = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = onDeleteAllMessages) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages caught in this chat yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Fixing B9: Cache Date Formatter to prevent allocation churn
                val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()) }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    reverseLayout = true
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        val isSelected = selectedMessageIds.contains(message.id)
                        MessageBubble(
                            message = message,
                            isSelected = isSelected,
                            isSelectionModeActive = isSelectionModeActive,
                            timeFormatter = timeFormatter,
                            onToggleSelection = {
                                if (isSelected) {
                                    selectedMessageIds = selectedMessageIds - message.id
                                } else {
                                    selectedMessageIds = selectedMessageIds + message.id
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
fun MessageBubble(
    message: MessageLog,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    timeFormatter: DateTimeFormatter,
    onToggleSelection: () -> Unit
) {
    val isDeleted = message.isDeleted

    // Visual background color mapping
    val bubbleBg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isDeleted -> RoseBg
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    // Fixing B10: Use modern java.time and remember the formatted string
    val formattedTime = remember(message.timestamp) { 
        val localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp), ZoneId.systemDefault())
        localTime.format(timeFormatter)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 48.dp)
            .combinedClickable(
                onLongClick = {
                    onToggleSelection()
                },
                onClick = {
                    if (isSelectionModeActive) {
                        onToggleSelection()
                    }
                }
            ),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 16.dp
                    )
                )
                .background(bubbleBg)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomEnd = 16.dp,
                                bottomStart = 16.dp
                            )
                        )
                    } else if (isDeleted) {
                        Modifier.border(
                            BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomEnd = 16.dp,
                                bottomStart = 16.dp
                            )
                        )
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                if (isDeleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Deleted Warning",
                            tint = RoseError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Deleted & Recovered",
                            color = RoseError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (message.mediaPath != null) {
                    AsyncImage(
                        model = message.mediaPath,
                        contentDescription = "Rescued Media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                    )
                }

                Text(
                    text = message.messageText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        isDeleted -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
