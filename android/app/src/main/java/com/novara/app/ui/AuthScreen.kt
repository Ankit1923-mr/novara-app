package com.novara.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.model.AuthResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.components.BotanicalLeafEmblem
import com.novara.app.ui.components.NovaraButton
import com.novara.app.ui.components.NovaraTextField
import com.novara.app.ui.components.SpanishStreetscapeIllustration
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 2 — Authentication
 * Redesigned Sign In / Create Account with warm editorial aesthetics, tab bar,
 * password toggle, and social icons, connected to live backend signup/login.
 */
@Composable
fun AuthScreen(
    repository: NovaraRepository,
    anonymousProfileLearnerId: String?,
    onAuthenticated: (AuthResponse) -> Unit
) {
    var isSignInMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("sakshi@example.com") }
    var password by remember { mutableStateOf("password123") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmIvory)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Logo & Slogan
        BotanicalLeafEmblem(modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "NOVARA",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = 3.sp,
            color = DeepTeal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Learn languages.\nLive the culture.",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sign In / Create Account Tab Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { isSignInMode = true; errorMessage = null }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Sign In",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSignInMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSignInMode) PrimaryText else SecondaryText
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (isSignInMode) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(3.dp)
                            .background(Terracotta, shape = RoundedCornerShape(2.dp))
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { isSignInMode = false; errorMessage = null }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Create Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (!isSignInMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isSignInMode) PrimaryText else SecondaryText
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (!isSignInMode) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(3.dp)
                            .background(Terracotta, shape = RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Input Fields
        NovaraTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            placeholder = "your.email@domain.com",
            leadingIcon = Icons.Default.Email
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Password",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("••••••••", style = MaterialTheme.typography.bodyMedium, color = MutedText) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SecondaryText) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Password Visibility",
                            tint = SecondaryText
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = DeepTeal,
                    unfocusedBorderColor = SubtleBorder,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isSignInMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Forgot password?",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SecondaryText,
                    modifier = Modifier.clickable { }
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = errorMessage ?: "", color = ErrorRed, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Terracotta Sign In Button
        NovaraButton(
            text = if (isSignInMode) "Sign In" else "Create Account",
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter both email and password."
                    return@NovaraButton
                }
                errorMessage = null
                isLoading = true
                scope.launch {
                    val result = if (!isSignInMode) {
                        repository.signup(email.trim(), password)
                    } else {
                        repository.login(email.trim(), password)
                    }
                    isLoading = false
                    result.onSuccess { onAuthenticated(it) }
                        .onFailure { errorMessage = it.message ?: "Authentication failed. Check your connection." }
                }
            },
            containerColor = Terracotta,
            isLoading = isLoading
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Architectural Illustration
        SpanishStreetscapeIllustration(height = 120.dp)
    }
}
