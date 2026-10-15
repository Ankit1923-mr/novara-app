package com.example.novara.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    object Splash : Screen("splash", "Splash")
    object ProfileSetup : Screen("profile_setup", "Profile", Icons.Default.Person)
    object Scenarios : Screen("scenarios", "Scenarios", Icons.Default.CompassCalibration)
    object Topics : Screen("topics", "Learning Hub", Icons.Default.School)
    object Chat : Screen("chat/{scenarioId}/{openingLine}", "Chat", Icons.Default.Chat) {
        fun createRoute(scenarioId: String, openingLine: String): String {
            val encodedOpening = java.net.URLEncoder.encode(openingLine, "UTF-8")
            return "chat/$scenarioId/$encodedOpening"
        }
    }
    object Readiness : Screen("readiness", "Readiness", Icons.Default.Assessment)
    object RepairPractice : Screen("repair_practice", "Repair Tool", Icons.Default.Build)
}

val bottomNavItems = listOf(
    Screen.Scenarios,
    Screen.Topics,
    Screen.Readiness,
    Screen.RepairPractice,
    Screen.ProfileSetup
)
