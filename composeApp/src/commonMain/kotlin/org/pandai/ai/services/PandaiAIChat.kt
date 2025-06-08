package org.pandai.ai.services

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class MessageResult(
    val message: String? = null,
    val context: String? = null,
    val isCompleted: Boolean = false
)

interface PandaiAIChat {
    suspend fun init(modelPath: String): Result<Unit, String> {
        return Ok(Unit)
    }

    fun sendMessage(message: String, contextEnabled: Boolean = true): Flow<MessageResult> {
        return flow {
            delay(100)
            emit(
                MessageResult(
                    message = "Answer",
                    context = if (contextEnabled) "Noop context" else null
                )
            )
            delay(100)
            emit(
                MessageResult(
                    message = "Answer to",
                    context = if (contextEnabled) "Noop context" else null
                )
            )
            delay(100)
            emit(
                MessageResult(
                    message = "Answer to rickroll",
                    context = if (contextEnabled) "Noop context" else null,
                    isCompleted = true
                )
            )
        }
    }
}


class PandaiAIChatNoOp : PandaiAIChat