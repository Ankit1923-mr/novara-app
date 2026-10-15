package com.example.novara.ui.screens.scenario

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.ui.theme.OchreAccent
import com.example.novara.ui.theme.OliveSecondary
import com.example.novara.ui.theme.SurfaceVariantWarm
import com.example.novara.ui.theme.TerracottaPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioScreen(
    learnerId: String,
    viewModel: ScenarioViewModel,
    onStartConversation: (String, String) -> Unit, // scenarioId, openingLine
    onManageProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(learnerId) {
        viewModel.loadScenario(learnerId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Active Learner ID Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TerracottaPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Learner: ${uiState.learnerId.ifEmpty { learnerId }}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Adaptive Situational Immersion",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
                IconButton(onClick = { viewModel.loadScenario(learnerId) }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Scenario",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TerracottaPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Selecting next novelty-aware scenario...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (uiState.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to Load Scenario",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadScenario(learnerId) }) {
                        Text("Retry")
                    }
                }
            }
        } else if (uiState.scenario != null) {
            val scenario = uiState.scenario!!

            // Main Scenario Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Purpose Tag & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (scenario.purpose.lowercase() == "trip") OliveSecondary else OchreAccent
                        ) {
                            Text(
                                text = scenario.purpose.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (scenario.purpose.lowercase() == "trip") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = scenario.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Setting
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Setting",
                            tint = TerracottaPrimary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = scenario.setting,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Situation Tags
                    if (scenario.situationTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            scenario.situationTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Opening Line Box
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = SurfaceVariantWarm
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = TerracottaPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI PARTNER OPENING LINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TerracottaPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "“${scenario.openingLine}”",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Selection Scores Breakdown (Adaptive Engine Details)
                    scenario.selectionScores?.firstOrNull()?.let { score ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CompassCalibration,
                                        contentDescription = null,
                                        tint = OliveSecondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Adaptive Selection Score: ${String.format("%.2f", score.score)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OliveSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Interest Overlap: ${if (score.interestOverlap.isEmpty()) "None" else score.interestOverlap.joinToString()} • Weak Area Overlap: ${if (score.weakAreaOverlap.isEmpty()) "None" else score.weakAreaOverlap.joinToString()} • Novelty Bonus: +${String.format("%.2f", score.noveltyBonus)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CTA Button to Start AI Chat
            Button(
                onClick = { onStartConversation(scenario.scenarioId, scenario.openingLine) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
            ) {
                Icon(Icons.Default.Chat, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start AI Conversation Partner", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
