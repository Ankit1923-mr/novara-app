package com.novara.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
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
import com.novara.app.audio.SpanishTextToSpeechManager
import com.novara.app.ui.components.NovaraButton
import com.novara.app.ui.components.SpanishStreetscapeIllustration
import com.novara.app.ui.theme.*

data class QuizOptionItem(
    val id: String,
    val text: String,
    val isCorrect: Boolean = false
)

/**
 * Screen 6 — Topic Quiz Screen
 * Features native Spanish TTS playback for sentence practice, question progress bar (2/5),
 * and instant answer option feedback.
 */
@Composable
fun TopicQuizScreen(
    onNavigateNext: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val ttsManager = remember { SpanishTextToSpeechManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    var selectedOptionId by remember { mutableStateOf("libreria") }

    val options = listOf(
        QuizOptionItem("transporte", "transporte"),
        QuizOptionItem("libreria", "librería", isCorrect = true),
        QuizOptionItem("restaurante", "restaurante"),
        QuizOptionItem("hotel", "hotel")
    )

    val currentQuestionSpanish = "¿Dónde está la librería?"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmIvory)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Progress Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryText
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .clip(CircleShape)
                            .background(DeepTeal)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "2/5",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Title & Speaker Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select the\ncorrect option",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = PrimaryText,
                lineHeight = 30.sp
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DeepTealContainer)
                    .clickable { ttsManager.speakSpanish(currentQuestionSpanish) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Audio Playback",
                    tint = DeepTeal
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SpanishStreetscapeIllustration(height = 130.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "¿Dónde está la _____?",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = PrimaryText
        )
        Text(
            text = "(Where is the library?)",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 4 Answer Option Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { opt ->
                val isSelected = selectedOptionId == opt.id
                val isCorrectAnswer = opt.isCorrect

                val cardBg by animateColorAsState(
                    targetValue = when {
                        isSelected && isCorrectAnswer -> SageDark.copy(alpha = 0.15f)
                        isSelected -> TerracottaContainer
                        else -> CardBackground
                    },
                    label = "optionBg"
                )

                val borderColor by animateColorAsState(
                    targetValue = when {
                        isSelected && isCorrectAnswer -> SageDark
                        isSelected -> Terracotta
                        else -> SubtleBorder
                    },
                    label = "optionBorder"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .clickable {
                            selectedOptionId = opt.id
                            ttsManager.speakSpanish(opt.text)
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        if (isSelected) borderColor else SecondaryText,
                                        CircleShape
                                    )
                                    .background(if (isSelected) borderColor else Color.Transparent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = opt.text,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = PrimaryText
                            )
                        }

                        if (isSelected && isCorrectAnswer) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Correct",
                                tint = SageDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        NovaraButton(
            text = "Next →",
            onClick = onNavigateNext,
            containerColor = DeepTeal
        )
    }
}
