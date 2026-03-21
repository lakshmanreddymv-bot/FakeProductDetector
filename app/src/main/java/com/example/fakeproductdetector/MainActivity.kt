package com.example.fakeproductdetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fakeproductdetector.ui.history.HistoryScreen
import com.example.fakeproductdetector.ui.result.ResultScreen
import com.example.fakeproductdetector.ui.result.ResultViewModel
import com.example.fakeproductdetector.ui.scan.ScanScreen
import com.example.fakeproductdetector.ui.theme.FakeProductDetectorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FakeProductDetectorTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavRoutes = setOf("scan", "history")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                AppBottomNavBar(
                    currentRoute = currentRoute,
                    navController = navController
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "scan",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("scan") {
                ScanScreen(
                    onNavigateToResult = { result ->
                        navController.navigate("result/${result.id}")
                    },
                    onNavigateToHistory = {
                        navController.navigate("history") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "result/{scanId}",
                arguments = listOf(navArgument("scanId") { type = NavType.StringType })
            ) {
                val viewModel: ResultViewModel = hiltViewModel()
                val result by viewModel.result.collectAsState()

                result?.let { scanResult ->
                    ResultScreen(
                        scanResult = scanResult,
                        onBack = { navController.popBackStack() }
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            composable("history") {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onItemClick = { scanId ->
                        navController.navigate("result/$scanId")
                    }
                )
            }
        }
    }
}

@Composable
private fun AppBottomNavBar(
    currentRoute: String?,
    navController: NavController
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "scan",
            onClick = {
                navController.navigate("scan") {
                    popUpTo("scan") { inclusive = false }
                    launchSingleTop = true
                }
            },
            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = "Scan") },
            label = { Text("Scan") }
        )
        NavigationBarItem(
            selected = currentRoute == "history",
            onClick = {
                navController.navigate("history") {
                    popUpTo("scan") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.History, contentDescription = "History") },
            label = { Text("History") }
        )
    }
}