package org.pandai.ai.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single

@Single(binds = [PandaiAIChat::class])
class PandaiAIChatImpl(
    private val _ragService: RagService
) : PandaiAIChat {
    override suspend fun init() {
        _ragService.initialize()
    }

    override fun sendMessage(message: String): Flow<MessageResult> = flow {
        val (answer, _) = _ragService.processQuery(message)
        var data = MessageResult()

        data = data.copy(context = answer)
        emit(data)

    }

}