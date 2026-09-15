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
import com.novara.app.model.ScenarioResponse
import com.novara.app.network.NovaraRepository
import com.novara.app.ui.*
import com.novara.app.ui.theme.BgPrimary
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileStore = ProfileStore(applicationContext)
        val repository = NovaraRepository(profileStore)

        setContent {
            NovaraTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgPrimary)
                ) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    // Task 3: If profile is saved on disk, resume into scenario; else intake
                    val startDestination = remember {
                        if (profileStore.hasValidProfile()) "scenario" else "intake"
                    }

                    var activeScenario by remember { mutableStateOf<ScenarioResponse?>(null) }
                    var currentProfile by remember { mutableStateOf(profileStore.loadProfile()) }

                    NavHost(navController = navController, startDestination = startDestination) {
                        // 1. Intake Screen (Task 2)
                        composable("intake") {
                            IntakeScreen(
                                onProfileCreated = { newProfile ->
                                    scope.launch {
                                        // Save locally (Task 3)
                                        profileStore.saveProfile(newProfile)
                                        currentProfile = newProfile

                                        // Call backend (Task 7)
                                        repository.createProfile(newProfile)

                                        navController.navigate("scenario") {
                                            popUpTo("intake") { inclusive = true }
                                        }
                                    }
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
                                }
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
