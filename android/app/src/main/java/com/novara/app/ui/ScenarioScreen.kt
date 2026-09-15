package com.novara.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.data.MockDataProvider
import com.novara.app.model.ScenarioResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Task 4: Situational Scenario Screen
 * Renders scenario for all 4 purposes (Trip, Casual, Exam, Relocation).
 * Supports Mock <-> Real swap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioScreen(
    learnerId: String,
    initialPurpose: String,
    repository: NovaraRepository,
    onBeginConversation: (ScenarioResponse) -> Unit,
    onEditProfile: () -> Unit
) {
    var selectedPurpose by remember { mutableStateOf(initialPurpose) }
    var scenario by remember { mutableStateOf<ScenarioResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isMockMode by remember { mutableStateOf(repository.isMockMode) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun loadScenario(purpose: String) {
        isLoading = true
        scope.launch {
            val result = repository.getScenario(learnerId, purpose)
            scenario = result.getOrNull() ?: MockDataProvider.getScenarioForPurpose(purpose)
            isLoading = false
        }
    }

    LaunchedEffect(selectedPurpose, isMockMode) {
        loadScenario(selectedPurpose)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onEditProfile) {
                Text("← Edit Profile", color = TextSecondary, fontSize = 13.sp)
            }

            // Mock vs Real Toggle (Task 7)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isMockMode) AccentEmerald.copy(alpha = 0.2f) else BgSurface)
                    .border(1.dp, if (isMockMode) AccentEmerald else BorderColor, CircleShape)
                    .clickable {
                        isMockMode = !isMockMode
                        repository.isMockMode = isMockMode
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isMockMode) "⚡ MOCK MODE" else "🟢 LIVE BACKEND",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMockMode) AccentEmerald else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 Purpose Selector (Task 4: Check 4 purposes)
        Text("TASK 4: PURPOSE SELECTOR", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("trip" to "✈️ Trip", "casual" to "💬 Casual", "exam" to "🎓 Exam", "relocation" to "🏡 Move").forEach { (purp, label) ->
                val isSelected = selectedPurpose == purp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentEmerald.copy(alpha = 0.2f) else BgSurface)
                        .border(1.dp, if (isSelected) AccentEmerald else BorderColor, RoundedCornerShape(8.dp))
                        .clickable { selectedPurpose = purp }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AccentEmerald else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentEmerald)
            }
        } else if (scenario != null) {
            val sc = scenario!!

            // Scenario Title & Setting Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = sc.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "📍 ${sc.setting}",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Situation Tags
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sc.situationTags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BgElevated)
                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("#$tag", fontSize = 11.sp, color = AccentCyan)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Opening Line Card with Audio Indicator
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgElevated)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI PARTNER OPENING LINE",
                            fontSize = 11.sp,
                            color = AccentGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text("🔊 Audio Ready", fontSize = 11.sp, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "\"${sc.openingLine}\"",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Action: Begin Conversation
            Button(
                onClick = { onBeginConversation(sc) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
            ) {
                Text("Begin Conversation →", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
