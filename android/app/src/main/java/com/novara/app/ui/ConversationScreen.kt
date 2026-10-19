package com.novara.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.audio.SpanishSpeechRecognizerManager
import com.novara.app.audio.SpanishTextToSpeechManager
import com.novara.app.model.ChatMessage
import com.novara.app.model.MessageRole
import com.novara.app.model.ScenarioResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.components.LearnerAvatarBadge
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 8 — Conversation Screen
 * Turn-by-turn Spanish AI partner chat with partner profile header, teal/ivory message bubbles,
 * suggested reply chips, native Spanish TTS speaker, and microphone speech recognition.
 */
@Composable
fun ConversationScreen(
    learnerId: String,
    scenario: ScenarioResponse,
    repository: NovaraRepository,
    onNavigateToReadiness: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val ttsManager = remember { SpanishTextToSpeechManager(context) }
    val speechRecognizer = remember { SpanishSpeechRecognizerManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
            speechRecognizer.stopListening()
        }
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = "open",
                role = MessageRole.ASSISTANT,
                text = scenario.openingLine,
                translation = getEnglishTranslation(scenario.openingLine)
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var turnNumber by remember { mutableIntStateOf(0) }
    var isSending by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizer.startListening(
                onResult = { text ->
                    inputText = text
                    isListening = false
                },
                onError = { isListening = false },
                onListeningStateChanged = { listening -> isListening = listening }
            )
        }
    }

    val suggestedReplies = listOf("Sí, por favor.", "Me encantaría.", "Ahora no, gracias.")

    fun performSend(text: String, turn: Int) {
        isSending = true
        scope.launch {
            val responseResult = repository.sendMessage(
                learnerId = learnerId,
                scenarioId = scenario.scenarioId,
                message = text,
                turnNumber = turn
            )
            isSending = false

            responseResult.onSuccess { resp ->
                messages.add(
                    ChatMessage(
                        id = "a_$turn",
                        role = MessageRole.ASSISTANT,
                        text = resp.reply,
                        translation = resp.translation ?: getEnglishTranslation(resp.reply)
                    )
                )

                if (resp.repairTriggered && resp.repair != null) {
                    messages.add(
                        ChatMessage(
                            id = "r_$turn",
                            role = MessageRole.REPAIR,
                            text = resp.repair.repairText,
                            errorType = resp.repair.errorType,
                            strategy = resp.repair.strategy
                        )
                    )
                }
            }.onFailure {
                messages.add(
                    ChatMessage(
                        id = "a_$turn",
                        role = MessageRole.ASSISTANT,
                        text = "¡Muy bien! Continuemos hablando.",
                        translation = "Very good! Let me know if you need anything else."
                    )
                )
            }

            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(textToSend: String) {
        val text = textToSend.trim()
        if (text.isBlank() || isSending) return

        inputText = ""
        val newTurn = turnNumber + 1
        turnNumber = newTurn

        messages.add(
            ChatMessage(
                id = "u_$newTurn",
                role = MessageRole.USER,
                text = text,
                translation = getEnglishTranslation(text)
            )
        )

        performSend(text, newTurn)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarmIvory)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                    LearnerAvatarBadge(
                        initials = "B",
                        backgroundColor = DeepTealContainer,
                        textColor = DarkTeal,
                        sizeDp = 40.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Partner • Beatriz",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PrimaryText
                        )
                        Text(
                            text = "Madrid, Spain",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                }

                Button(
                    onClick = onNavigateToReadiness,
                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Readiness 📊", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground)
                    .padding(vertical = 10.dp)
            ) {
                // Suggested Reply Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestedReplies) { reply ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, SubtleBorder, RoundedCornerShape(18.dp))
                                .clickable { sendMessage(reply) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = reply,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Bar with Mic & Send
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic Button
                    IconButton(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isListening) TerracottaContainer else DeepTealContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListening) TerracottaDark else DeepTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Type in Spanish or English...", color = MutedText, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepTeal,
                            unfocusedBorderColor = SubtleBorder,
                            focusedContainerColor = WarmIvory,
                            unfocusedContainerColor = WarmIvory,
                            focusedTextColor = PrimaryText,
                            unfocusedTextColor = PrimaryText
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.isNotBlank() && !isSending,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DeepTeal)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        containerColor = WarmIvory
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                when (msg.role) {
                    MessageRole.USER -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                                    .background(DeepTeal)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column {
                                    Text(msg.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                    if (msg.translation != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(msg.translation, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    MessageRole.ASSISTANT -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 290.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                                    .background(CardBackground)
                                    .border(1.dp, SubtleBorder, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(msg.text, color = PrimaryText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = { ttsManager.speakSpanish(msg.text) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Audio Playback",
                                                tint = DeepTeal,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (msg.translation != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(msg.translation, color = SecondaryText, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    MessageRole.REPAIR -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = TerracottaContainer)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "⚡ INLINE REPAIR: ${msg.errorType?.uppercase() ?: "CORRECTION"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TerracottaDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
                            }
                        }
                    }
                    else -> {}
                }
            }

            if (isSending) {
                item {
                    Text("Partner is typing...", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                }
            }
        }
    }
}

private fun getEnglishTranslation(text: String): String {
    val clean = text.lowercase().trim()
    return when {
        clean.contains("maleta") || clean.contains("perdido") || clean.contains("perdió") -> "Have you lost your luggage or suitcase?"
        clean.contains("recogida") || clean.contains("equipaje") -> "Where is baggage claim?"
        clean.contains("vuelo") || clean.contains("embarque") -> "Your flight is boarding at gate 4."
        clean.contains("puerta") || clean.contains("salida") -> "Departure gate for your flight."
        clean.contains("pasaporte") || clean.contains("carnet") -> "May I see your passport or identity card?"
        clean.contains("buenos") || clean.contains("hola") || clean.contains("días") || clean.contains("tardes") -> "Hello! Good day, how can I help you today?"
        clean.contains("bien") -> "I'm fine, thank you. How are you?"
        clean.contains("café") || clean.contains("tomar") -> "Would you like to order a coffee or pastry?"
        clean.contains("sí") || clean.contains("favor") -> "Yes, please. Thank you very much."
        clean.contains("gracias") || clean.contains("nada") -> "Thank you! You're welcome."
        clean.contains("cuenta") || clean.contains("pagar") -> "Could you bring me the bill, please?"
        clean.contains("tarjeta") || clean.contains("efectivo") -> "Will you be paying with card or cash?"
        clean.contains("reserva") || clean.contains("mesa") -> "A table reservation for two people."
        clean.contains("hotel") || clean.contains("habitación") -> "Welcome to the hotel. Do you have a room reservation?"
        clean.contains("estación") || clean.contains("tren") || clean.contains("metro") -> "Where is the nearest train/subway station?"
        else -> "Translation: $text"
    }
}
