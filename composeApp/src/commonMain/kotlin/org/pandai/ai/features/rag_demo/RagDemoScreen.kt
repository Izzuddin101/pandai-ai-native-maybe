package org.pandai.ai.features.rag_demo

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable
data object RagDemoScreen

@Composable
expect fun RagDemoScreen()