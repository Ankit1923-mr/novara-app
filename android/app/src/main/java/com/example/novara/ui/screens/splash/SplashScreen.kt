package com.example.novara.ui.screens.splash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.ui.components.NovaraLogo
import com.example.novara.ui.theme.TerracottaPrimary

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToHome: (String) -> Unit,
    onNavigateToProfileSetup: () -> Unit
) {
    val navState by viewModel.navigationState.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "SplashPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    LaunchedEffect(navState) {
        when (val state = navState) {
            is SplashNavigationState.Authenticated -> onNavigateToHome(state.learnerId)
            is SplashNavigationState.Unauthenticated -> onNavigateToProfileSetup()
            is SplashNavigationState.Loading -> { /* Keep loading */ }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.scale(scale)) {
                NovaraLogo(
                    size = 130.dp,
                    showSubtitle = true
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            CircularProgressIndicator(
                color = TerracottaPrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading Adaptive Session...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
