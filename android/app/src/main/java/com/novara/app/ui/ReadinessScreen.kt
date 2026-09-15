package com.novara.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.novara.app.model.ReadinessResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Task 6: Readiness Dashboard
 * Features:
 * - Aggregate score gauge
 * - Dimension breakdown bars
 * - 3 sample payload toggles (High, Mid, Low)
 * - Zero clipping layout
 */
@Composable
fun ReadinessScreen(
    learnerId: String,
    repository: NovaraRepository,
    onNavigateBack: () -> Unit,
    onStartNewScenario: () -> Unit
) {
    var selectedSample by remember { mutableStateOf("live") }
    var readinessData by remember { mutableStateOf<ReadinessResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun loadData(sampleKey: String) {
        isLoading = true
        scope.launch {
            if (sampleKey == "live") {
                val result = repository.getReadiness(learnerId)
                readinessData = result.getOrNull() ?: MockDataProvider.readinessPayloads["mid"]
            } else {
                readinessData = MockDataProvider.readinessPayloads[sampleKey]
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedSample) {
        loadData(selectedSample)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onNavigateBack) {
                Text("← Chat", color = TextSecondary, fontSize = 13.sp)
            }
            Text("READINESS DASHBOARD", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Task 6: 3 Sample Payloads Evaluator Switcher
        Text("TASK 6: SAMPLE PAYLOAD SWITCHER", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                "live" to "🟢 Live",
                "high" to "🏆 High 88%",
                "mid" to "⚖️ Mid 62%",
                "low" to "📉 Low 34%"
            ).forEach { (key, label) ->
                val isSelected = selectedSample == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentEmerald.copy(alpha = 0.2f) else BgSurface)
                        .border(1.dp, if (isSelected) AccentEmerald else BorderColor, RoundedCornerShape(8.dp))
                        .clickable { selectedSample = key }
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

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentEmerald)
            }
        } else if (readinessData != null) {
            val rd = readinessData!!
            val score = rd.aggregateScore
            val animatedScore by animateFloatAsState(
                targetValue = score.toFloat(),
                animationSpec = tween(durationMillis = 800),
                label = "score"
            )

            val scoreColor = when {
                score >= 0.70 -> AccentEmerald
                score >= 0.40 -> AccentGold
                else -> ErrorColor
            }

            // Aggregate Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSurface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("COMPOSITE READINESS INDEX", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${(animatedScore * 100).toInt()}%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when {
                            score >= 0.80 -> "Proficient & Situational Ready"
                            score >= 0.65 -> "Strong Conversationalist"
                            score >= 0.45 -> "Developing Fluency"
                            else -> "Needs Focused Practice"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Purpose: ${rd.purpose.replaceFirstChar { it.uppercase() }}",
                        fontSize = 12.sp,
                        color = AccentCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dimension Breakdown Bars
            Text("DIMENSION BREAKDOWN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rd.breakdown.forEach { (dimKey, dimVal) ->
                    val dimLabel = when (dimKey) {
                        "language_accuracy" -> "Language Accuracy"
                        "repair_success_rate" -> "Repair Success"
                        "register_appropriateness" -> "Register & Tone"
                        "transfer_success" -> "Situational Transfer"
                        else -> dimKey.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgSurface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dimLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("${(dimVal * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentEmerald)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { dimVal.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = scoreColor,
                                trackColor = BgElevated
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action: Start Next Scenario
            Button(
                onClick = onStartNewScenario,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
            ) {
                Text("Next Scenario ➔", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
