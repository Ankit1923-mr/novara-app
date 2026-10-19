package com.novara.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.data.ProfileStore
import com.novara.app.model.LearnerProfile
import com.novara.app.model.ReadinessResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.components.*
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 3 — Home Dashboard
 * Displays personalized learner greeting, 14-day streak, current lesson card,
 * live readiness score gauge, and bottom navigation.
 */
@Composable
fun HomeScreen(
    learnerId: String,
    repository: NovaraRepository,
    profileStore: ProfileStore,
    onNavigateTab: (String) -> Unit,
    onStartLesson: () -> Unit,
    onOpenProfile: () -> Unit
) {
    var profile by remember { mutableStateOf(profileStore.loadProfile()) }
    var readinessResponse by remember { mutableStateOf<ReadinessResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(learnerId) {
        val readinessResult = repository.getReadiness(learnerId)
        readinessResponse = readinessResult.getOrNull()
        isLoading = false
    }

    val displayLevel = profile?.level ?: "B2"
    val displayPurpose = profile?.purpose?.uppercase() ?: "TRIP"
    val readinessPercent = ((readinessResponse?.aggregateScore ?: 0.84) * 100).toInt()

    Scaffold(
        bottomBar = {
            NovaraBottomNavigation(
                currentRoute = "home",
                onNavigate = onNavigateTab
            )
        },
        containerColor = WarmIvory
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Greeting & Avatar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LearnerAvatarBadge(
                        initials = learnerId.take(1).uppercase().ifEmpty { "S" },
                        sizeDp = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "¡Hola, ${learnerId.substringBefore("@")}!",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = PrimaryText
                        )
                        Text(
                            text = "$displayLevel Learner • $displayPurpose",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                }

                IconButton(
                    onClick = onOpenProfile,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CardBackground)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = DeepTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 14-Day Streak Card
            NovaraCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔥 14-Day Streak",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Terracotta
                            )
                        }
                        Text(
                            text = "Keep going! 2 lessons left today",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Days 1..14 Circle Indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (day in 1..14) {
                        val isCompleted = day <= 10
                        val isToday = day == 11
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCompleted -> DeepTeal
                                        isToday -> Terracotta
                                        else -> SurfaceElevated
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$day",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted || isToday) Color.White else MutedText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Current Lesson Card
            NovaraCard(
                backgroundColor = CardBackground
            ) {
                Text(
                    text = "CURRENT LESSON",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    color = Terracotta,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Café Madrileño\n(Desayuno)",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = PrimaryText
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NovaraChip(text = displayLevel, selected = true, onClick = { }, activeColor = DeepTeal)
                    NovaraChip(text = "Daily Life", selected = false, onClick = { })
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Illustration + Start Action
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SpanishStreetscapeIllustration(height = 110.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Terracotta)
                            .clickable { onStartLesson() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Start Lesson",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Readiness Gauge Card
            NovaraCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live Readiness",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nice progress! Keep completing lessons to boost Spanish fluency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    ReadinessCircleGauge(
                        scorePercent = readinessPercent,
                        sizeDp = 95.dp,
                        strokeWidthDp = 9.dp,
                        label = "Accuracy"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
