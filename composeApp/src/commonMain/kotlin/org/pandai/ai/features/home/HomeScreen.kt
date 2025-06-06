package org.pandai.ai.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

@Serializable
data object HomeScreen

@Composable
fun HomeScreen(onNavigateToRagDemo: () -> Unit, onNavigateToChat: () -> Unit) {
    Scaffold {
        Column(
            Modifier.fillMaxWidth().padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = {
                onNavigateToRagDemo()
            }) {
                Text("Open RAG Demo")
            }

            Button(onClick = {
                onNavigateToChat()
            }) {
                Text("Open Chato")
            }
        }
    }
}