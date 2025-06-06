package org.pandai.ai.services

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class MessageResult(
    val message: String? = null,
    val context: String? = null,
    val isCompleted: Boolean = false
)

interface PandaiAIChat {
    suspend fun init() {}
    fun sendMessage(message: String): Flow<MessageResult> {
        return callbackFlow {
            delay(100)
            send(MessageResult(
                message = "Answer",
                context = "Noop context"
            ))
            delay(100)
            send(MessageResult(
                message = "Answer to",
                context = "Noop context"
            ))
            delay(100)
            send(MessageResult(
                message = "Answer to rickroll",
                context = "Noop context",
                isCompleted = true
            ))
            awaitClose()
        }
    }
}


class PandaiAIChatNoOp : PandaiAIChat