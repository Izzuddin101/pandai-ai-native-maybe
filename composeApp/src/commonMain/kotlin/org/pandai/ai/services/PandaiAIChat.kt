package org.pandai.ai.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class MessageResult(
    val message: String? = null,
    val context: String? = null,
)

interface PandaiAIChat {
    suspend fun init() {}
    fun sendMessage(message: String): Flow<MessageResult> {
        return flowOf(
            MessageResult(
                message = "Answer to $message",
                context = "Noop context"
            )
        )
    }
}


class PandaiAIChatNoOp : PandaiAIChat