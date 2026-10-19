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
import com.novara.app.ui.theme.NovaraTheme
import com.novara.app.ui.theme.WarmIvory

/**
 * NOVARA Main Activity
 * NavHost connecting all 11 screens of the NOVARA Editorial UI Redesign.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileStore = ProfileStore(applicationContext)
        val tokenStore = TokenStore(applicationContext)
        val repository = NovaraRepository(profileStore)

        // Attach stored session token at startup
        NovaraNetworkClient.bearerToken = tokenStore.token

        setContent {
            NovaraTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WarmIvory)
                ) {
                    val navController = rememberNavController()

                    val startDestination = remember {
                        if (!tokenStore.isLoggedIn) "splash"
                        else if (profileStore.loadProfile()?.learnerId == tokenStore.email) "home"
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

                        // Screen 1 — Splash Screen
                        composable("splash") {
                            SplashScreen(
                                onContinue = {
                                    val destination = if (tokenStore.isLoggedIn) "home" else "auth"
                                    navController.navigate(destination) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Screen 2 — Authentication
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
                                    val destination = if (auth.hasProfile) "home" else "intake"
                                    navController.navigate(destination) {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Screen 3 — Home Dashboard
                        composable("home") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = tokenStore.email ?: profile?.learnerId ?: "sakshi@example.com"

                            HomeScreen(
                                learnerId = learnerId,
                                repository = repository,
                                profileStore = profileStore,
                                onNavigateTab = { route ->
                                    if (route != "home") {
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onStartLesson = {
                                    navController.navigate("scenario")
                                },
                                onOpenProfile = {
                                    navController.navigate("readiness")
                                }
                            )
                        }

                        // Screen 4 — Immersion Hub
                        composable("immersion_hub") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = tokenStore.email ?: profile?.learnerId ?: "sakshi@example.com"

                            ImmersionHubScreen(
                                learnerId = learnerId,
                                repository = repository,
                                profileStore = profileStore,
                                onNavigateTab = { route ->
                                    if (route != "immersion_hub") {
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onProfileDispatched = {
                                    navController.navigate("scenario")
                                }
                            )
                        }

                        // Screen 5 — Learning Intake
                        composable("intake") {
                            IntakeScreen(
                                learnerId = tokenStore.email,
                                onProfileCreated = { newProfile ->
                                    profileStore.saveProfile(newProfile)
                                    currentProfile = newProfile
                                    val result = repository.createProfile(newProfile)
                                    if (result.isSuccess) {
                                        navController.navigate("home") {
                                            popUpTo("intake") { inclusive = true }
                                        }
                                    }
                                    result.isSuccess
                                },
                                onNavigateBack = {
                                    if (profileStore.hasValidProfile()) {
                                        navController.navigate("home")
                                    }
                                }
                            )
                        }

                        // Screen 6 — Topic Quiz Screen
                        composable("topic_quiz") {
                            TopicQuizScreen(
                                onNavigateNext = {
                                    navController.navigate("curriculum_quiz")
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // Screen 7 — Scenario Lesson
                        composable("scenario") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = tokenStore.email ?: profile?.learnerId ?: "sakshi@example.com"
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
                                    navController.navigate("immersion_hub")
                                },
                                onLogout = { logout() }
                            )
                        }

                        // Screen 8 — Conversation Screen
                        composable("conversation") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = tokenStore.email ?: profile?.learnerId ?: "sakshi@example.com"
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

                        // Screen 9 — Readiness Dashboard
                        composable("readiness") {
                            val profile = currentProfile ?: profileStore.loadProfile()
                            val learnerId = tokenStore.email ?: profile?.learnerId ?: "sakshi@example.com"

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

                        // Screen 10 — Topic List Screen
                        composable("topic_list") {
                            TopicListScreen(
                                onNavigateTab = { route ->
                                    if (route != "topic_list") {
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onSelectTopic = {
                                    navController.navigate("topic_quiz")
                                }
                            )
                        }

                        // Screen 11 — Curriculum & Quizzes
                        composable("curriculum_quiz") {
                            CurriculumQuizScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onReviewAnswers = {
                                    navController.navigate("readiness")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
