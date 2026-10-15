package com.novara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novara.app.data.ProfileStore
import com.novara.app.data.TokenStore
import com.novara.app.model.ScenarioResponse
import com.novara.app.network.NovaraNetworkClient
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.*
import com.novara.app.ui.theme.BgPrimary
import com.novara.app.ui.theme.NovaraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileStore = ProfileStore(applicationContext)
        val tokenStore = TokenStore(applicationContext)
        val repository = NovaraRepository(profileStore)

        // Attach whatever session token is already stored (app relaunch
        // with an existing login) before any request fires.
        NovaraNetworkClient.bearerToken = tokenStore.token

        setContent {
            NovaraTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgPrimary)
                ) {
                    val navController = rememberNavController()

                    // Phase 1: every learner now needs an authenticated session
                    // (learner_id == the account's email) before intake/scenario -
                    // the backend requires a matching bearer token on every
                    // learner-specific endpoint. A profile saved locally under
                    // an old anonymous "android_<timestamp>" id (from before
                    // this change) is left exactly as-is on disk and on the
                    // backend - it is never auto-migrated, merged, or deleted -
                    // but it's no longer reachable through this start-up path
                    // unless that same anonymous id is also an authenticated
                    // account's email, which it structurally cannot be.
                    val startDestination = remember {
                        if (!tokenStore.isLoggedIn) "auth"
                        else if (profileStore.loadProfile()?.learnerId == tokenStore.email) "scenario"
                        else "intake"
                    }

                    var activeScenario by remember { mutableStateOf<ScenarioResponse?>(null) }
                    var currentProfile by remember { mutableStateOf(profileStore.loadProfile()) }

                    fun logout() {
                        tokenStore.clear()
                        NovaraNetworkClient.bearerToken = null
                        profileStore.clear()
                        currentProfile = null
                        activeScenario = null
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    repository.onUnauthorized = { logout() }

                    NavHost(navController = navController, startDestination = startDestination) {
                        // 0. Auth Screen (Phase 1: accounts)
                        composable("auth") {
                            val anonymousId = profileStore.loadProfile()?.learnerId
                                ?.takeIf { it.startsWith("android_") }
                            AuthScreen(
                                repository = repository,
                                anonymousProfileLearnerId = anonymousId,
                                onAuthenticated = { auth ->
                                    tokenStore.token = auth.token
                                    tokenStore.email = auth.email
                                    NovaraNetworkClient.bearerToken = auth.token
                                    val destination = if (auth.hasProfile) "scenario" else "intake"
                                    navController.navigate(destination) {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 1. Intake Screen (Task 2)
                        composable("intake") {
                            IntakeScreen(
                                learnerId = tokenStore.email,
                                onProfileCreated = { newProfile ->
                                    // Save locally (Task 3)
                                    profileStore.saveProfile(newProfile)
                                    currentProfile = newProfile

                                    // Call backend (Task 7) - only navigate
                                    // onward if it actually succeeded;
                                    // IntakeScreen shows the failure and
                                    // keeps the learner on this screen
                                    // otherwise.
                                    val result = repository.createProfile(newProfile)
                                    if (result.isSuccess) {
                                        navController.navigate("scenario") {
                                            popUpTo("intake") { inclusive = true }
                                        }
                                    }
                                    result.isSuccess
                                },
                                onNavigateBack = {
                                    if (profileStore.hasValidProfile()) {
                                        navController.navigate("scenario")
                                    }
                                }
                            )
                        }

                        // 2. Scenario Screen (Task 4)
                        composable("scenario") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = profile?.learnerId ?: "learner_demo"
                            val purpose = profile?.purpose ?: "trip"

                            ScenarioScreen(
                                learnerId = learnerId,
                                initialPurpose = purpose,
                                repository = repository,
                                onBeginConversation = { sc ->
                                    activeScenario = sc
                                    navController.navigate("conversation")
                                },
                                onEditProfile = {
                                    navController.navigate("intake")
                                },
                                onLogout = { logout() }
                            )
                        }

                        // 3. Conversation Screen (Task 5)
                        composable("conversation") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = profile?.learnerId ?: "learner_demo"
                            val sc = activeScenario ?: com.novara.app.data.MockDataProvider.getScenarioForPurpose(profile?.purpose ?: "trip")

                            ConversationScreen(
                                learnerId = learnerId,
                                scenario = sc,
                                repository = repository,
                                onNavigateToReadiness = {
                                    navController.navigate("readiness")
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 4. Readiness Screen (Task 6)
                        composable("readiness") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = profile?.learnerId ?: "learner_demo"

                            ReadinessScreen(
                                learnerId = learnerId,
                                repository = repository,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onStartNewScenario = {
                                    navController.navigate("scenario") {
                                        popUpTo("scenario") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
