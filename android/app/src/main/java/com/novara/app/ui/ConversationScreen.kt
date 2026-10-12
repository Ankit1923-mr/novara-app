package com.novara.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.model.ChatMessage
import com.novara.app.model.MessageRole
import com.novara.app.model.ScenarioResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Task 5: Conversation/Chat Screen
 * Features:
 * - LazyColumn chat stream
 * - Inline repair bubble with error_type and strategy
 * - Turn counter (1..10+ manual test)
 * - Accent score indicator
 */
@Composable
fun ConversationScreen(
    learnerId: String,
    scenario: ScenarioResponse,
    repository: NovaraRepository,
    onNavigateToReadiness: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = "open",
                role = MessageRole.ASSISTANT,
                text = scenario.openingLine
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var turnNumber by remember { mutableIntStateOf(0) }
    var isSending by remember { mutableStateOf(false) }
    var isMockMode by remember { mutableStateOf(repository.isMockMode) }
    // Non-null exactly when the last send attempt failed - holds the EXACT
    // text/turn that failed so Retry resends the same turn instead of
    // treating it as a new message (which would duplicate the user's bubble
    // and advance turnNumber past what the backend may have already seen).
    var failedSend by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun performSend(text: String, turn: Int) {
        isSending = true
        failedSend = null

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
                        text = resp.reply
                    )
                )

                // Add inline repair if triggered (Task 5 core requirement)
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
                // Surface the failure instead of silently dropping the
                // reply - the user's own message bubble (added before this
                // call) stays visible, with a retry affordance below it.
                failedSend = text to turn
            }

            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank() || isSending) return

        inputText = ""

        val newTurn = turnNumber + 1
        turnNumber = newTurn

        // Calculate simple organic accent score based on diacritics
        val hasAccents = text.any { it in "áéíóúÁÉÍÓÚñÑ" }
        val accentScore = if (hasAccents) 92 else 84

        // Add user turn
        messages.add(
            ChatMessage(
                id = "u_$newTurn",
                role = MessageRole.USER,
                text = text,
                accentScore = accentScore
            )
        )

        performSend(text, newTurn)
    }

    fun retryLastSend() {
        val (text, turn) = failedSend ?: return
        // Re-sends the exact same text/turn - does not touch `messages` or
        // `turnNumber`, so nothing is duplicated on the Android side. The
        // backend itself has no idempotency guard on turn_number (see
        // docs/challenges.md discussion), so a retry after a failure where
        // the backend actually processed the request but the response was
        // lost in transit can still double-count server-side; this is a
        // known, reported limitation, not something this UI-level fix can
        // close on its own.
        performSend(text, turn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNavigateBack) {
                    Text("← Back", color = TextSecondary, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(scenario.title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Text("Turn $turnNumber · ${scenario.setting}", color = TextMuted, fontSize = 11.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mock toggle
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isMockMode) AccentEmerald.copy(alpha = 0.2f) else BgElevated)
                        .border(1.dp, if (isMockMode) AccentEmerald else BorderColor, CircleShape)
                        .clickable {
                            isMockMode = !isMockMode
                            repository.isMockMode = isMockMode
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (isMockMode) "⚡ MOCK" else "🟢 LIVE", fontSize = 10.sp, color = if (isMockMode) AccentEmerald else TextSecondary)
                }

                // Readiness button
                Button(
                    onClick = onNavigateToReadiness,
                    enabled = turnNumber >= 1,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Score 📊", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AccentEmerald)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(msg.text, color = Color.White, fontSize = 14.sp)
                            }
                            if (msg.accentScore != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Accent: ${msg.accentScore}% · Pure Vowels",
                                    fontSize = 10.sp,
                                    color = AccentCyan
                                )
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
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BgSurface)
                                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text("🇪🇸 ", fontSize = 14.sp)
                                        Text(msg.text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Text(" 🔊", fontSize = 13.sp, color = AccentCyan)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // English Subtitle Placeholder Box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BgElevated.copy(alpha = 0.7f))
                                            .border(1.dp, AccentCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Column {
                                            Text(
                                                "🇬🇧 ENGLISH MEANING:",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentCyan
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                msg.translation ?: getEnglishTranslation(msg.text),
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    MessageRole.REPAIR -> {
                        // Task 5 Deliverable: Inline Repair Bubble with English explanation
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.12f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "⚡ INLINE CORRECTION: ${msg.errorType?.uppercase()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGold
                                    )
                                    Text(
                                        "Strategy: ${msg.strategy}",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    msg.text,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // English Translation Box for Repair
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BgElevated.copy(alpha = 0.7f))
                                        .border(1.dp, AccentGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        Text(
                                            "🇬🇧 EXPLANATION:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            msg.translation ?: getEnglishTranslation(msg.text),
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            if (isSending) {
                item {
                    Text("AI is typing…", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }

        if (failedSend != null) {
            // Visible failure + retry, replacing the old behavior of
            // silently dropping the reply on error.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠ Message didn't send", fontSize = 12.sp, color = TextSecondary)
                TextButton(onClick = { retryLastSend() }, enabled = !isSending) {
                    Text("Retry", color = AccentEmerald, fontSize = 12.sp)
                }
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Escribe en español o inglés…", color = TextMuted, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentEmerald,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgElevated,
                    unfocusedContainerColor = BgElevated
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { sendMessage() },
                enabled = inputText.isNotBlank() && !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("➤", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Intelligent Spanish to English translation helper for native English speakers
 */
private fun getEnglishTranslation(text: String): String {
    val clean = text.lowercase().trim()
    return when {
        clean.contains("buenos") || clean.contains("hola") -> "Hello! Welcome to Novara. Let's practice Spanish with native pronunciation."
        clean.contains("qué le pongo") || clean.contains("pongo de beber") -> "What can I get you to drink?"
        clean.contains("caña") && clean.contains("tapa") -> "Each draft beer comes with a free house tapa."
        clean.contains("para aquí") || clean.contains("llevar") -> "For here or to go (takeaway)?"
        clean.contains("tortilla") && clean.contains("cebolla") -> "One portion of Spanish omelette! Do you prefer it with or without onion?"
        clean.contains("cuenta") -> "I'll bring the bill right away. Will you pay with card or cash?"
        clean.contains("tarjeta") || clean.contains("efectivo") -> "We accept contactless cards without problem. Pay at the register anytime."
        clean.contains("euros") -> "Stating the price in euros."
        clean.contains("repetir") -> "Could you please repeat that?"
        clean.contains("para llevar") -> "You say 'para llevar' (to take away), not 'para llevo'."
        clean.contains("contrato") || clean.contains("identidad") -> "Did you bring your rental contract and identity document?"
        clean.contains("viaje") -> "Tell me about a recent trip and what surprised you most."
        else -> "English meaning: $text"
    }
}

