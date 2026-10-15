package com.novara.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.model.AuthResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Account login/signup - Phase 1 security work. Calls the existing
 * backend /auth/signup and /auth/login endpoints (unchanged this
 * session); on success hands the caller the AuthResponse (token,
 * email, learner_id == email, has_profile) so it can store the token
 * and route to intake (new account) or scenario (returning account).
 */
@Composable
fun AuthScreen(
    repository: NovaraRepository,
    anonymousProfileLearnerId: String?,
    onAuthenticated: (AuthResponse) -> Unit
) {
    var isSignupMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "NOVARA",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isSignupMode) "Create an account to start learning" else "Welcome back",
            fontSize = 14.sp,
            color = TextSecondary
        )

        if (anonymousProfileLearnerId != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BgElevated)
            ) {
                Text(
                    text = "You have previous guest activity (id: $anonymousProfileLearnerId). " +
                        "Signing in starts a separate, account-linked profile - your guest data " +
                        "isn't deleted, but it also isn't linked to this account.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = errorMessage ?: "", color = ErrorColor, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Enter both email and password."
                    return@Button
                }
                errorMessage = null
                isLoading = true
                scope.launch {
                    val result = if (isSignupMode) {
                        repository.signup(email.trim(), password)
                    } else {
                        repository.login(email.trim(), password)
                    }
                    isLoading = false
                    result.onSuccess { onAuthenticated(it) }
                        .onFailure { errorMessage = it.message ?: "Couldn't reach the backend." }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = TextPrimary)
            } else {
                Text(if (isSignupMode) "Sign up" else "Log in", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isSignupMode = !isSignupMode; errorMessage = null }) {
            Text(
                if (isSignupMode) "Already have an account? Log in" else "New here? Sign up",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}
