package com.example.novara.ui.screens.readiness

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.ui.theme.ConfidenceGreen
import com.example.novara.ui.theme.OchreAccent
import com.example.novara.ui.theme.OliveSecondary
import com.example.novara.ui.theme.TerracottaPrimary

@Composable
fun ReadinessScreen(
    learnerId: String,
    viewModel: ReadinessViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(learnerId) {
        viewModel.loadReadiness(learnerId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header
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
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Readiness Scoring Dashboard",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Learner: ${uiState.learnerId.ifEmpty { learnerId }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
                IconButton(onClick = { viewModel.loadReadiness(learnerId) }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Readiness",
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
                        text = "Calculating 4-dimension readiness score...",
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
                        text = "Readiness Calculation Error",
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
                    Button(onClick = { viewModel.loadReadiness(learnerId) }) {
                        Text("Retry")
                    }
                }
            }
        } else if (uiState.readiness != null) {
            val readiness = uiState.readiness!!
            val aggregatePct = (readiness.aggregateScore * 100).toInt()

            // Main Score Circle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (readiness.purpose.lowercase() == "trip") OliveSecondary else OchreAccent
                    ) {
                        Text(
                            text = "PURPOSE: ${readiness.purpose.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (readiness.purpose.lowercase() == "trip") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Aggregate Gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { readiness.aggregateScore },
                            modifier = Modifier.size(140.dp),
                            color = ConfidenceGreen,
                            trackColor = ConfidenceGreen.copy(alpha = 0.2f),
                            strokeWidth = 12.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$aggregatePct%",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (aggregatePct >= 70) "IMMERSION READY" else "IN PROGRESS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ConfidenceGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Calculated from interaction history & repair resolutions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4 Competency Dimensions Breakdown
            Text(
                text = "Competency Dimensions",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val breakdown = readiness.breakdown
            val weights = readiness.weightsUsed

            DimensionScoreCard(
                title = "Language Accuracy",
                score = breakdown.languageAccuracy,
                weight = weights.languageAccuracy,
                icon = Icons.Default.CheckCircle,
                accentColor = TerracottaPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            DimensionScoreCard(
                title = "Repair Success Rate",
                score = breakdown.repairSuccessRate,
                weight = weights.repairSuccessRate,
                icon = Icons.Default.Build,
                accentColor = OchreAccent
            )

            Spacer(modifier = Modifier.height(8.dp))

            DimensionScoreCard(
                title = "Register Appropriateness",
                score = breakdown.registerAppropriateness,
                weight = weights.registerAppropriateness,
                icon = Icons.Default.Translate,
                accentColor = OliveSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            DimensionScoreCard(
                title = "Transfer Success",
                score = breakdown.transferSuccess,
                weight = weights.transferSuccess,
                icon = Icons.Default.Psychology,
                accentColor = ConfidenceGreen
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DimensionScoreCard(
    title: String,
    score: Float,
    weight: Float,
    icon: ImageVector,
    accentColor: Color
) {
    val pct = (score * 100).toInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$pct%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { score },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Purpose Weight: ${(weight * 100).toInt()}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
