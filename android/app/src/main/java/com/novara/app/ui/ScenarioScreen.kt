package com.novara.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.audio.SpanishSpeechRecognizerManager
import com.novara.app.audio.SpanishTextToSpeechManager
import com.novara.app.model.ScenarioResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.components.*
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 7 — Scenario Lesson Screen
 * Features native Spanish TextToSpeech for opening line & vocabulary, and runtime
 * RECORD_AUDIO permissions with SpanishSpeechRecognizerManager for voice practice.
 */
@Composable
fun ScenarioScreen(
    learnerId: String,
    initialPurpose: String,
    repository: NovaraRepository,
    onBeginConversation: (ScenarioResponse) -> Unit,
    onEditProfile: () -> Unit,
    onLogout: (() -> Unit)? = null
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

    var selectedPurpose by remember { mutableStateOf(initialPurpose) }
    var scenario by remember { mutableStateOf<ScenarioResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var recognizedSpeech by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizer.startListening(
                onResult = { text ->
                    recognizedSpeech = text
                    isListening = false
                },
                onError = { err ->
                    errorMessage = err
                    isListening = false
                },
                onListeningStateChanged = { listening ->
                    isListening = listening
                }
            )
        } else {
            showPermissionDialog = true
        }
    }

    fun loadScenario(purpose: String) {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = repository.getScenario(learnerId, purpose)
            result.onSuccess {
                scenario = it
            }.onFailure {
                errorMessage = it.message ?: "Couldn't reach the backend."
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedPurpose) {
        loadScenario(selectedPurpose)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmIvory)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Nav Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onEditProfile) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Edit Profile",
                    tint = PrimaryText
                )
            }

            Text(
                text = "1/3",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = SecondaryText
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category & Scenario Title
        val sc = scenario
        val titleText = sc?.title ?: "Ordering Morning Coffee & Pastries"
        val settingText = sc?.setting ?: "TRIP / MADRID CAFÉ"
        val openingLineText = sc?.openingLine ?: "¡Buenos días! ¿Qué le pongo?"

        Text(
            text = settingText.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = Terracotta,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = titleText,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = PrimaryText,
            lineHeight = 32.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Partner Opening Speech Card with Speaker Playback
        NovaraCard(backgroundColor = CardBackground) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LearnerAvatarBadge(
                    initials = "B",
                    backgroundColor = DeepTealContainer,
                    textColor = DarkTeal,
                    sizeDp = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = openingLineText,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap speaker to hear Spanish pronunciation",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DeepTealContainer)
                        .clickable { ttsManager.speakSpanish(openingLineText) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Audio Playback",
                        tint = DeepTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Target Vocabulary
        Text(
            text = "Target Vocabulary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VocabItemCard("un café", "coffee", Modifier.weight(1f)) { ttsManager.speakSpanish("un café") }
            VocabItemCard("un croissant", "croissant", Modifier.weight(1f)) { ttsManager.speakSpanish("un croissant") }
            VocabItemCard("por favor", "please", Modifier.weight(1f)) { ttsManager.speakSpanish("por favor") }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Roleplay Section with Speech Recognition
        Text(
            text = "Try it yourself (Roleplay)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            SpanishStreetscapeIllustration(height = 130.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isListening) TerracottaContainer else CardBackground)
                    .clickable {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to speak",
                    tint = if (isListening) TerracottaDark else DeepTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isListening) "Listening in Spanish..." else (recognizedSpeech ?: "Tap to speak"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = errorMessage!!, color = ErrorRed, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        NovaraButton(
            text = "Start AI Conversation →",
            onClick = {
                if (sc != null) {
                    onBeginConversation(sc)
                }
            },
            containerColor = DeepTeal,
            isLoading = isLoading
        )
    }

    // Microphone Permission Rationale Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Microphone Permission Required") },
            text = { Text("NOVARA needs microphone access to practice Spanish pronunciation and conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text("Grant Permission", color = DeepTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        )
    }
}

@Composable
fun VocabItemCard(
    spanish: String,
    english: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = spanish,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DeepTeal,
                fontSize = 12.sp
            )
            Text(
                text = english,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                fontSize = 10.sp
            )
        }
    }
}
