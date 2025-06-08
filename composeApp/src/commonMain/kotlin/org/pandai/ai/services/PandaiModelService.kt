package org.pandai.ai.services

import co.touchlab.kermit.Logger
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.koin.core.annotation.Single

@Serializable
data class HuggingFaceFile(
    val rfilename: String
)

@Serializable
data class HuggingFaceResponse(
    val siblings: List<HuggingFaceFile>
)

data class ModelFormat(
    val label: String,
)

expect val LLMDownloadPath: String

@Single
class PandaiModelService(
    private val httpClient: HttpClient,
    private val downloaderService: DownloaderService
) {
    private val modelFormatsData = mapOf(
        "litert-community/Gemma3-1B-IT" to ModelFormat(
            label = "litert-community/Gemma3-1B-IT"
        ),
        "pandaiedu/Gemma3-1b-it-merged-lora-edge-q4" to ModelFormat(
            label = "pandaiedu/Gemma3-1b-it-merged-lora-edge-q4"
        )
    )

    private val _downloadedLLMs = MutableStateFlow<List<String>>(emptyList())
    val downloadedLLMs = _downloadedLLMs.asStateFlow()

    private val downloadDestination = LLMDownloadPath.toPath()

    init {
        checkDownloadedModels()
    }

    private fun checkDownloadedModels() {
        try {
            val files = FileSystem.SYSTEM.listRecursively(downloadDestination).toList()
            val ggufFiles = files
                .filter { it.name.endsWith(".gguf") || it.name.endsWith(".task") }
                .map { it.relativeTo(downloadDestination).toString() }
            _downloadedLLMs.value = ggufFiles
        } catch (e: Exception) {
            println("Error checking downloaded models: ${e.message}")
        }
    }

    fun getAvailableModels(): Map<String, ModelFormat> {
        return modelFormatsData
    }

    suspend fun getAvailableModelFiles(
        repoId: String,
        accessToken: String?
    ): Result<List<String>, Throwable> {
        val response = runSuspendCatching {
            httpClient.get("https://huggingface.co/api/models/${repoId}") {
                headers {
                    accessToken?.let { append("Authorization", "Bearer $it") }
                }
            }
        }
        if (response.isErr) return Err(response.error)
        val huggingFaceResponse = runSuspendCatching {
            sharedJson.decodeFromString<HuggingFaceResponse>(response.value.bodyAsText())
        }.mapError {
            Logger.e(
                "Error fetching model files: ${response.value.status}\n${response.value.bodyAsText()}",
                it
            )
            Error("Error fetching model files: ${response.value.status}\n${response.value.bodyAsText()}")
        }
        if (huggingFaceResponse.isErr) return Err(huggingFaceResponse.error)


        val files = huggingFaceResponse.value.siblings.filter { file ->
            file.rfilename.endsWith(".task")
        }

        val availableGGUFs = files.map { it.rfilename }
        return Ok(availableGGUFs)
    }

    fun download(
        file: String,
        repoId: String,
        accessToken: String? = null
    ): Flow<Result<Pair<String, Float>, String>> = flow {
        val downloadUrl = "https://huggingface.co/${repoId}/resolve/main/$file"
        val destPath = downloadDestination.resolve(repoId).resolve(file)

        downloaderService.download(downloadUrl, destPath) {
            headers {
                accessToken?.let { append("Authorization", "Bearer $it") }
            }
        }.collect {
            if (it.isOk) {
                emit(Ok(Pair(file, it.value.second)))
            } else {
                emit(it)
            }
        }
    }

    fun deleteModel(file: String) {
        val destPath = downloadDestination.resolve(file)
        FileSystem.SYSTEM.delete(destPath)
    }

    fun resolveModelPath(filePath: String): Path? {
        val fileSystem = FileSystem.SYSTEM
        val file = filePath.toPath()
        if (fileSystem.exists(file)) {
            return file
        }

        val fileWithParentPath = LLMDownloadPath.toPath().resolve(file)
        if (fileSystem.exists(fileWithParentPath)) {
            return fileWithParentPath
        }

        return null
    }
}