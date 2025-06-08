package org.pandai.ai.services.sentence_embeding

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.koin.core.annotation.Single
import org.pandai.ai.services.LLMDownloadPath
import org.pandai.ai.services.PandaiModelService

@Single
class SentenceEmbeddingService(
    private val modelService: PandaiModelService
) {
    fun downloadSentenceEmbedding(model: Model): Flow<Result<Pair<String, Float>, String>> {
        val modelConfig = model.getConfig()
        return flow {
            modelService.download(modelConfig.tokenizer, modelConfig.repo).collect {
                emit(it)
            }
            modelService.download(modelConfig.modelFile, modelConfig.repo).collect {
                emit(it)
            }
        }
    }

    fun checkDownloaded(model: Model): Boolean {
        val modelConfig = model.getConfig()
        val parentPath = modelConfig.parentPath()
        runCatching {
            val fileSystem = FileSystem.SYSTEM
            return fileSystem.exists(parentPath.resolve(modelConfig.tokenizer)) && fileSystem.exists(
                parentPath.resolve(modelConfig.modelFile)
            )
        }.onFailure {
            println("Error checking downloaded models: ${it.message}")
        }
        return false
    }

    fun deleteModel(model: Model) {
        val fileSystem = FileSystem.SYSTEM
        val modelConfig = model.getConfig()
        val parentPath = modelConfig.parentPath()
        fileSystem.delete(parentPath.resolve(modelConfig.modelFile))
        fileSystem.delete(parentPath.resolve(modelConfig.tokenizer))
    }

    private fun ModelConfig.parentPath(): Path {
        return LLMDownloadPath.toPath().resolve(repo)
    }
}