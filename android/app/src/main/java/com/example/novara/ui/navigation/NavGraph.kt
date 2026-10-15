package com.example.novara.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.novara.NovaraApplication
import com.example.novara.data.repository.NovaraRepository
import com.example.novara.data.repository.UserPreferencesRepository
import com.example.novara.ui.components.ProfileSwitcherDialog
import com.example.novara.ui.screens.chat.ChatScreen
import com.example.novara.ui.screens.chat.ChatViewModel
import com.example.novara.ui.screens.profile.ProfileScreen
import com.example.novara.ui.screens.profile.ProfileViewModel
import com.example.novara.ui.screens.readiness.ReadinessScreen
import com.example.novara.ui.screens.readiness.ReadinessViewModel
import com.example.novara.ui.screens.repair.RepairPracticeScreen
import com.example.novara.ui.screens.repair.RepairViewModel
import com.example.novara.ui.screens.scenario.ScenarioScreen
import com.example.novara.ui.screens.scenario.ScenarioViewModel
import com.example.novara.ui.screens.splash.SplashScreen
import com.example.novara.ui.screens.splash.SplashViewModel
import com.example.novara.ui.screens.topics.TopicsScreen
import com.example.novara.ui.screens.topics.TopicsViewModel
import com.example.novara.ui.theme.OchreAccent
import com.example.novara.ui.theme.TerracottaPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    application: NovaraApplication,
    navController: NavHostController = rememberNavController()
) {
    val repository: NovaraRepository = application.repository
    val userPreferencesRepository: UserPreferencesRepository = application.userPreferencesRepository

    val activeLearnerId by userPreferencesRepository.activeLearnerIdFlow.collectAsState(initial = null)
    val savedLearnerIds by userPreferencesRepository.savedLearnerIdsFlow.collectAsState(initial = emptyList())

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showProfileSwitcher by remember { mutableStateOf(false) }

    val isFullscreenRoute = currentRoute == Screen.Splash.route || currentRoute?.startsWith("chat") == true

    Scaffold(
        topBar = {
            if (!isFullscreenRoute) {
                TopAppBar(
                    title = {
                        Text(
                            text = "NOVARA",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 2.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { showProfileSwitcher = true }) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Manage Profiles",
                                tint = OchreAccent
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TerracottaPrimary)
                )
            }
        },
        bottomBar = {
            if (!isFullscreenRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Scenarios.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                item.icon?.let { icon ->
                                    Icon(imageVector = icon, contentDescription = item.title)
                                }
                            },
                            label = { Text(item.title, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TerracottaPrimary,
                                selectedTextColor = TerracottaPrimary,
                                indicatorColor = TerracottaPrimary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        if (showProfileSwitcher) {
            ProfileSwitcherDialog(
                activeLearnerId = activeLearnerId,
                savedLearnerIds = savedLearnerIds,
                onSelectProfile = { selectedId ->
                    showProfileSwitcher = false
                    val profileViewModel = ProfileViewModel(repository, userPreferencesRepository)
                    profileViewModel.switchProfile(selectedId)
                },
                onCreateNewProfile = {
                    showProfileSwitcher = false
                    navController.navigate(Screen.ProfileSetup.route)
                },
                onRemoveProfile = { idToRemove ->
                    val profileViewModel = ProfileViewModel(repository, userPreferencesRepository)
                    profileViewModel.removeProfile(idToRemove)
                },
                onSignOut = {
                    showProfileSwitcher = false
                    val profileViewModel = ProfileViewModel(repository, userPreferencesRepository)
                    profileViewModel.signOut()
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onDismissRequest = { showProfileSwitcher = false }
            )
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Splash Screen
            composable(Screen.Splash.route) {
                val splashViewModel = remember { SplashViewModel(userPreferencesRepository) }
                SplashScreen(
                    viewModel = splashViewModel,
                    onNavigateToHome = {
                        navController.navigate(Screen.Scenarios.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToProfileSetup = {
                        navController.navigate(Screen.ProfileSetup.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // Profile Setup / Management
            composable(Screen.ProfileSetup.route) {
                val profileViewModel = remember { ProfileViewModel(repository, userPreferencesRepository) }
                ProfileScreen(
                    viewModel = profileViewModel,
                    onProfileSaved = {
                        navController.navigate(Screen.Scenarios.route) {
                            popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                        }
                    }
                )
            }

            // Adaptive Scenarios (Home)
            composable(Screen.Scenarios.route) {
                val scenarioViewModel = remember { ScenarioViewModel(repository, userPreferencesRepository) }
                ScenarioScreen(
                    learnerId = activeLearnerId ?: "",
                    viewModel = scenarioViewModel,
                    onStartConversation = { scenarioId, openingLine ->
                        navController.navigate(Screen.Chat.createRoute(scenarioId, openingLine))
                    },
                    onManageProfile = {
                        navController.navigate(Screen.ProfileSetup.route)
                    }
                )
            }

            // Spanish Learning Hub (Topics & Vocabulary)
            composable(Screen.Topics.route) {
                val topicsViewModel = remember { TopicsViewModel(repository) }
                TopicsScreen(
                    learnerId = activeLearnerId ?: "",
                    viewModel = topicsViewModel
                )
            }

            // AI Conversation Partner Chat Screen
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("scenarioId") { type = NavType.StringType },
                    navArgument("openingLine") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val scenarioId = backStackEntry.arguments?.getString("scenarioId") ?: "scenario_default"
                val rawOpening = backStackEntry.arguments?.getString("openingLine") ?: ""
                val openingLine = java.net.URLDecoder.decode(rawOpening, "UTF-8")

                val chatViewModel = remember { ChatViewModel(repository, userPreferencesRepository) }

                ChatScreen(
                    learnerId = activeLearnerId ?: "",
                    scenarioId = scenarioId,
                    openingLine = openingLine,
                    viewModel = chatViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Readiness Competency Dashboard
            composable(Screen.Readiness.route) {
                val readinessViewModel = remember { ReadinessViewModel(repository, userPreferencesRepository) }
                ReadinessScreen(
                    learnerId = activeLearnerId ?: "",
                    viewModel = readinessViewModel
                )
            }

            // Standalone Repair Sandbox
            composable(Screen.RepairPractice.route) {
                val repairViewModel = remember { RepairViewModel(repository) }
                RepairPracticeScreen(viewModel = repairViewModel)
            }
        }
    }
}
