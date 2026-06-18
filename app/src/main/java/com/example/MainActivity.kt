package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.ManageCategoriesScreen
import com.example.ui.screens.MyShortcutsScreen
import com.example.ui.screens.PracticeModeScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StatsAndHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ExerciseViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val navController = rememberNavController()
        val exerciseViewModel: ExerciseViewModel = viewModel()

        // Let NavHost fill the screen allowing true Edge-to-Edge.
        // Each individual screen manages its own Scaffold and custom insets.
        NavHost(
          navController = navController,
          startDestination = "browse",
          modifier = Modifier.fillMaxSize()
        ) {
            // Browse Library Screen
            composable("browse") {
              BrowseScreen(
                viewModel = exerciseViewModel,
                onExerciseClick = { id ->
                  navController.navigate("detail/$id")
                },
                onManageCategoriesClick = {
                  navController.navigate("manage_categories")
                },
                onMyShortcutsClick = {
                  navController.navigate("shortcuts")
                },
                onStreakClick = {
                  navController.navigate("calendar")
                },
                onStatsAndHistoryClick = {
                  navController.navigate("stats_history")
                },
                onProfileClick = {
                  navController.navigate("profile")
                }
              )
            }

            // Stats & History Screen
            composable("stats_history") {
              StatsAndHistoryScreen(
                viewModel = exerciseViewModel,
                onBackClick = {
                  navController.popBackStack()
                }
              )
            }

            // Exercise Detail Screen
            composable(
              route = "detail/{exerciseId}",
              arguments = listOf(
                navArgument("exerciseId") { type = NavType.StringType }
              )
            ) { backStackEntry ->
              val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: ""
              DetailScreen(
                exerciseId = exerciseId,
                viewModel = exerciseViewModel,
                onBackClick = {
                  navController.popBackStack()
                }
              )
            }

            // Manage Categories Screen
            composable("manage_categories") {
              ManageCategoriesScreen(
                viewModel = exerciseViewModel,
                onBackClick = {
                  navController.popBackStack()
                }
              )
            }

            // My Shortcuts Screen
            composable("shortcuts") {
              MyShortcutsScreen(
                viewModel = exerciseViewModel,
                onBackClick = {
                  navController.popBackStack()
                },
                onStartShortcutClick = { shortcutId ->
                  navController.navigate("practice/$shortcutId")
                }
              )
            }

            // Practice Mode Screen
            composable(
              route = "practice/{shortcutId}",
              arguments = listOf(
                navArgument("shortcutId") { type = NavType.StringType }
              )
            ) { backStackEntry ->
              val shortcutId = backStackEntry.arguments?.getString("shortcutId") ?: ""
              PracticeModeScreen(
                shortcutId = shortcutId,
                viewModel = exerciseViewModel,
                onCloseClick = {
                  navController.popBackStack()
                }
              )
            }

            // Calendar Screen
            composable("calendar") {
              CalendarScreen(
                viewModel = exerciseViewModel,
                onBackClick = {
                  navController.popBackStack()
                }
              )
            }

            // User Profile Screen
            composable("profile") {
              ProfileScreen(
                viewModel = exerciseViewModel,
                onBackClick = {
                  navController.popBackStack()
                },
                onStatsAndHistoryClick = {
                  navController.navigate("stats_history")
                }
              )
            }
          }
        }
      }
    }
  }