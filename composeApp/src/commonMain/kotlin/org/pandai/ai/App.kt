package org.pandai.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.pandai.ai.features.chat.ChatScreen
import org.pandai.ai.features.home.HomeScreen
import org.pandai.ai.features.llm_choose.LLMChooseScreen
import org.pandai.ai.features.rag_demo.RagDemoScreen

@Composable
@Preview
fun App() {
    val navController = rememberNavController()
    MaterialTheme {
        NavHost(navController = navController, startDestination = HomeScreen) {
            composable<HomeScreen> {
                HomeScreen(onNavigateToRagDemo = {
                    navController.navigate(
                        RagDemoScreen
                    )
                }, onNavigateToChat = { navController.navigate(LLMChooseScreen) })
            }
            composable<RagDemoScreen> { RagDemoScreen() }
            composable<ChatScreen> { ChatScreen() }
            composable<LLMChooseScreen> {
                LLMChooseScreen {
                    navController.navigate(ChatScreen(it))
                }
            }
        }
    }
}

expect fun onSubmit(text: String)