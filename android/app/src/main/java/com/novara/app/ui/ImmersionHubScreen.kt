package com.novara.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.data.ProfileStore
import com.novara.app.model.LearnerProfile
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.components.*
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 4 — Immersion Hub
 * Choose immersion modes (Trip vs Casual), CEFR proficiency (A1-B2),
 * region (Madrid, Barcelona, Seville), focus areas, and dispatch profile.
 */
@Composable
fun ImmersionHubScreen(
    learnerId: String,
    repository: NovaraRepository,
    profileStore: ProfileStore,
    onNavigateTab: (String) -> Unit,
    onProfileDispatched: () -> Unit
) {
    var selectedPurpose by remember { mutableStateOf(profileStore.loadProfile()?.purpose ?: "trip") }
    var selectedLevel by remember { mutableStateOf(profileStore.loadProfile()?.level ?: "A2") }
    var selectedRegion by remember { mutableStateOf(profileStore.loadProfile()?.region ?: "Madrid") }
    var selectedFocusAreas by remember {
        mutableStateOf(
            profileStore.loadProfile()?.weakAreas?.toSet() ?: setOf("listening", "grammar")
        )
    }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NovaraBottomNavigation(
                currentRoute = "immersion_hub",
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

            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Immersion Hub",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = PrimaryText
                    )
                    Text(
                        text = "Choose how you want to learn today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                BotanicalLeafEmblem(modifier = Modifier.size(36.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Two Immersion Mode Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Trip Immersion Card
                ImmersionModeCard(
                    title = "Trip\nImmersion",
                    subtitle = "Hotels, dining, transport",
                    isSelected = selectedPurpose == "trip",
                    onClick = { selectedPurpose = "trip" },
                    modifier = Modifier.weight(1f),
                    activeColor = DeepTeal
                )

                // Casual Conversation Card
                ImmersionModeCard(
                    title = "Casual\nConversation",
                    subtitle = "Smalltalk, culture, hobbies",
                    isSelected = selectedPurpose == "casual",
                    onClick = { selectedPurpose = "casual" },
                    modifier = Modifier.weight(1f),
                    activeColor = Terracotta
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Proficiency Level Selector
            Text(
                text = "Your Level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("A1", "A2", "B1", "B2").forEach { level ->
                    NovaraChip(
                        text = level,
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = level },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Region Selector
            Text(
                text = "Region",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("Madrid", "Barcelona", "Seville").forEach { region ->
                    NovaraChip(
                        text = region,
                        selected = selectedRegion == region,
                        onClick = { selectedRegion = region },
                        activeColor = Terracotta,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Focus Areas
            Text(
                text = "Focus Areas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FocusAreaIcon(
                    label = "Listening",
                    icon = Icons.Default.Headphones,
                    selected = selectedFocusAreas.contains("listening"),
                    onToggle = {
                        selectedFocusAreas = if (selectedFocusAreas.contains("listening"))
                            selectedFocusAreas - "listening" else selectedFocusAreas + "listening"
                    }
                )
                FocusAreaIcon(
                    label = "Grammar",
                    icon = Icons.Default.MenuBook,
                    selected = selectedFocusAreas.contains("grammar"),
                    onToggle = {
                        selectedFocusAreas = if (selectedFocusAreas.contains("grammar"))
                            selectedFocusAreas - "grammar" else selectedFocusAreas + "grammar"
                    }
                )
                FocusAreaIcon(
                    label = "Vocabulary",
                    icon = Icons.Default.AutoAwesome,
                    selected = selectedFocusAreas.contains("vocabulary"),
                    onToggle = {
                        selectedFocusAreas = if (selectedFocusAreas.contains("vocabulary"))
                            selectedFocusAreas - "vocabulary" else selectedFocusAreas + "vocabulary"
                    }
                )
                FocusAreaIcon(
                    label = "Pronunciation",
                    icon = Icons.Default.Mic,
                    selected = selectedFocusAreas.contains("pronunciation"),
                    onToggle = {
                        selectedFocusAreas = if (selectedFocusAreas.contains("pronunciation"))
                            selectedFocusAreas - "pronunciation" else selectedFocusAreas + "pronunciation"
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Dispatch Profile Action
            NovaraButton(
                text = "Dispatch Profile →",
                onClick = {
                    isSaving = true
                    scope.launch {
                        val newProfile = LearnerProfile(
                            learnerId = learnerId,
                            language = "spanish",
                            level = selectedLevel,
                            region = selectedRegion,
                            purpose = selectedPurpose,
                            interests = listOf("travel", "food"),
                            weakAreas = selectedFocusAreas.toList().ifEmpty { listOf("listening") }
                        )
                        profileStore.saveProfile(newProfile)
                        repository.createProfile(newProfile)
                        isSaving = false
                        onProfileDispatched()
                    }
                },
                containerColor = DeepTeal,
                isLoading = isSaving
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ImmersionModeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = DeepTeal
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) activeColor else CardBackground)
            .clickable { onClick() }
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) activeColor else CardBackground),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else activeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = if (isSelected) Color.White else PrimaryText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else SecondaryText
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = if (isSelected) activeColor else SecondaryText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun FocusAreaIcon(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onToggle() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (selected) DeepTealContainer else SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) DeepTeal else SecondaryText,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) DeepTeal else SecondaryText
        )
    }
}
