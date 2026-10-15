package com.example.novara.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.data.model.ChatMessage
import com.example.novara.data.model.Sender
import com.example.novara.ui.components.PaceChangeBanner
import com.example.novara.ui.components.RepairFeedbackCard
import com.example.novara.ui.theme.OchreAccent
import com.example.novara.ui.theme.TerracottaPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    learnerId: String,
    scenarioId: String,
    openingLine: String,
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(learnerId, scenarioId, openingLine) {
        viewModel.initializeChat(learnerId, scenarioId, openingLine)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "AI Conversation Partner",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Turn: ${uiState.turnNumber} • Learner: ${uiState.learnerId}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            actions = {
                uiState.currentPacePreference?.let { pace ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OchreAccent,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.height(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pace: ${(pace * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TerracottaPrimary)
        )

        // Error message if any
        uiState.errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                ChatMessageItem(message = message)
            }

            if (uiState.isSending) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = TerracottaPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Partner is thinking...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Bottom Input Row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Responder en español...", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerracottaPrimary
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !uiState.isSending,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (inputText.isNotBlank() && !uiState.isSending) TerracottaPrimary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !uiState.isSending) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isLearner = message.sender == Sender.LEARNER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isLearner) Alignment.End else Alignment.Start
    ) {
        // Message Bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isLearner) 16.dp else 4.dp,
                bottomEnd = if (isLearner) 4.dp else 16.dp
            ),
            color = if (isLearner) TerracottaPrimary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isLearner) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = TerracottaPrimary,
                            modifier = Modifier.height(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI CONVERSATION PARTNER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerracottaPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    color = if (isLearner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }
        }

        // Inline Pace Banner if applicable
        message.paceChangeReason?.let { reason ->
            Spacer(modifier = Modifier.height(6.dp))
            PaceChangeBanner(
                reason = reason,
                pacePreference = message.pacePreference,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }

        // Inline Repair Feedback Card if applicable
        message.repairDetails?.let { repair ->
            Spacer(modifier = Modifier.height(6.dp))
            RepairFeedbackCard(
                repairDetails = repair,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }
    }
}
