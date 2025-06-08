package org.pandai.ai.services

import android.content.Context
import co.touchlab.kermit.Logger
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single(binds = [PandaiAIChat::class])
class PandaiAIChatImpl(
    private val context: Context,
    private val ragService: RagService,
    private val modelService: PandaiModelService
) : PandaiAIChat {
    private var llmInference: LlmInference? = null

    override suspend fun init(modelPath: String): Result<Unit, String> {
        val fullModelPath =
            modelService.resolveModelPath(modelPath) ?: return Err("Model not found")
        ragService.initialize()
        llmInference?.close()
        llmInference = null

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(fullModelPath.toString())
            .setMaxTokens(512)
            .build()

        llmInference =
            withContext(Dispatchers.Default) { LlmInference.createFromOptions(context, options) }

        return Ok(Unit)
    }

    override fun sendMessage(message: String, contextEnabled: Boolean): Flow<MessageResult> = flow {
        var data = MessageResult(isCompleted = false)

        val (answer, _) = if (contextEnabled) {
            withContext(Dispatchers.Default) { ragService.processQuery(message) }
        } else {
            Pair(null, null)
        }
        data = data.copy(context = answer)
        emit(data)

        val llmInference = llmInference
        if (llmInference == null) {
            emit(data.copy(isCompleted = true))
            return@flow
        }

        val flow = callbackFlow {
            val input = if (contextEnabled && answer != null) {
                "Context from knowledge base: $answer\n=========\n$message"
            } else {
                message
            }
            Logger.d("Input: $input")
            val future = withContext(Dispatchers.Default) {
                llmInference.generateResponseAsync(
                    input
                ) { partialResult, done ->
                    Logger.d("Result, $partialResult")
                    trySend(partialResult)

                    if (done) close()
                }
            }
            awaitClose {
                future.cancel(true)
            }
        }

        flow.collect {
            data = data.copy(message = data.message.orEmpty() + it)
            emit(data)
        }

        emit(data)
    }
}