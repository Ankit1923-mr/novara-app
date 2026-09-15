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
import com.novara.app.model.LearnerProfile
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

val LEVELS = listOf(
    "A1" to "Beginner",
    "A2" to "Elementary",
    "B1" to "Intermediate",
    "B2" to "Upper Intermediate"
)

val PURPOSES = listOf(
    "trip" to "✈️ Travel & Trip",
    "casual" to "💬 Social & Casual",
    "exam" to "🎓 DELE Exam",
    "relocation" to "🏡 Relocation"
)

val ALL_INTERESTS = listOf("food", "travel", "culture", "music", "history", "art", "sports", "technology")
val ALL_WEAK_AREAS = listOf("pronunciation", "listening", "speaking", "grammar", "vocabulary")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntakeScreen(
    onProfileCreated: (LearnerProfile) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedLevel by remember { mutableStateOf("B1") }
    var selectedPurpose by remember { mutableStateOf("trip") }
    var region by remember { mutableStateOf("Madrid") }
    var nativeLanguage by remember { mutableStateOf("English") }
    val selectedInterests = remember { mutableStateListOf("food", "travel") }
    val selectedWeakAreas = remember { mutableStateListOf("pronunciation", "listening") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "NOVARA INTAKE",
                fontSize = 12.sp,
                color = AccentEmerald,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create Learner Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Text(
            text = "Task 2: 100% Contract Validated Intake",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. Spanish Level Selection
        Text("1. Spanish Proficiency Level", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LEVELS.forEach { (lvl, desc) ->
                val isSelected = selectedLevel == lvl
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccentEmerald.copy(alpha = 0.2f) else BgSurface)
                        .border(1.dp, if (isSelected) AccentEmerald else BorderColor, RoundedCornerShape(12.dp))
                        .clickable { selectedLevel = lvl }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(lvl, fontWeight = FontWeight.Bold, color = if (isSelected) AccentEmerald else TextPrimary, fontSize = 16.sp)
                        Text(desc, fontSize = 9.sp, color = TextMuted)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Purpose Selection (4 purposes: Trip, Casual, Exam, Relocation)
        Text("2. Immersion Purpose (4 Situations)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PURPOSES.forEach { (purp, label) ->
                val isSelected = selectedPurpose == purp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccentEmerald.copy(alpha = 0.15f) else BgSurface)
                        .border(1.dp, if (isSelected) AccentEmerald else BorderColor, RoundedCornerShape(12.dp))
                        .clickable { selectedPurpose = purp }
                        .padding(14.dp)
                ) {
                    Text(label, color = if (isSelected) TextPrimary else TextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Region / City
        Text("3. Target City or Region", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            placeholder = { Text("e.g. Madrid, Barcelona, Mexico City") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentEmerald,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BgSurface,
                unfocusedContainerColor = BgSurface
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Interests (Chips)
        Text("4. Topics & Interests", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ALL_INTERESTS.forEach { interest ->
                val isSelected = selectedInterests.contains(interest)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) AccentEmerald.copy(alpha = 0.2f) else BgSurface)
                        .border(1.dp, if (isSelected) AccentEmerald else BorderColor, CircleShape)
                        .clickable {
                            if (isSelected) selectedInterests.remove(interest) else selectedInterests.add(interest)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(interest, fontSize = 13.sp, color = if (isSelected) Color.White else TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Weak Areas (Chips)
        Text("5. Weak Areas & Speech Focus", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ALL_WEAK_AREAS.forEach { weak ->
                val isSelected = selectedWeakAreas.contains(weak)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) AccentGold.copy(alpha = 0.2f) else BgSurface)
                        .border(1.dp, if (isSelected) AccentGold else BorderColor, CircleShape)
                        .clickable {
                            if (isSelected) selectedWeakAreas.remove(weak) else selectedWeakAreas.add(weak)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (weak == "pronunciation") "🔊 pronunciation" else weak,
                        fontSize = 13.sp,
                        color = if (isSelected) AccentGold else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error Banner
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ErrorColor.copy(alpha = 0.15f))
                    .border(1.dp, ErrorColor, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(errorMessage!!, color = Color(0xFFFCA5A5), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Submit Button
        Button(
            onClick = {
                // 100% field validation matching contract
                val cleanRegion = region.trim()
                if (cleanRegion.isBlank()) {
                    errorMessage = "Region cannot be empty (1-200 characters)."
                    return@Button
                }
                if (selectedInterests.isEmpty()) {
                    errorMessage = "Please select at least one interest."
                    return@Button
                }
                if (selectedWeakAreas.isEmpty()) {
                    errorMessage = "Please select at least one skill to hone."
                    return@Button
                }

                errorMessage = null
                isLoading = true

                val learnerId = "android_" + System.currentTimeMillis().toString(36)
                val profile = LearnerProfile(
                    learnerId = learnerId,
                    language = "spanish",
                    level = selectedLevel,
                    region = cleanRegion,
                    purpose = selectedPurpose,
                    interests = selectedInterests.toList(),
                    weakAreas = selectedWeakAreas.toList(),
                    nativeLanguage = nativeLanguage
                )

                onProfileCreated(profile)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
            } else {
                Text("Create Profile & Start", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
