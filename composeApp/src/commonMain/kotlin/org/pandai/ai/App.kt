package org.pandai.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.pandai.ai.features.home.HomeScreen
import org.pandai.ai.features.rag_demo.RagDemoScreen

@Composable
@Preview
fun App() {
    val navController = rememberNavController()
    MaterialTheme {
        NavHost(navController = navController, startDestination = HomeScreen) {
            composable<HomeScreen> { HomeScreen { navController.navigate(RagDemoScreen) } }
            composable<RagDemoScreen> { RagDemoScreen() }
        }
    }
}

expect fun onSubmit(text: String)