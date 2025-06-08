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
class PandaiLLMService(
    private val httpClient: HttpClient,
    private val downloaderService: DownloaderService
) {
    private val modelFormatsData = mapOf(
        "litert-community/Gemma3-1B-IT" to ModelFormat(
            label = "litert-community/Gemma3-1B-IT"
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
            val files = FileSystem.SYSTEM.list(downloadDestination)
            val ggufFiles = files
                .filter { it.name.endsWith(".gguf") || it.name.endsWith(".task") || it.name.endsWith(".onnx") }
                .map { it.name }
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
        val modelFormat =
            modelFormatsData[repoId] ?: return Err(IllegalArgumentException("Invalid format"))

        val response = httpClient.get("https://huggingface.co/api/models/${modelFormat.label}") {
            headers {
                accessToken?.let { append("Authorization", "Bearer $it") }
            }
        }
        val huggingFaceResponse = runSuspendCatching {
            sharedJson.decodeFromString<HuggingFaceResponse>(response.bodyAsText())
        }.mapError {
            Logger.e("Error fetching model files: ${response.status}\n${response.bodyAsText()}", it)
            Error("Error fetching model files: ${response.status}\n${response.bodyAsText()}")
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
        val modelFormat = modelFormatsData[repoId] ?: run {
            emit(Err("Invalid format"))
            return@flow
        }

        val downloadUrl = "https://huggingface.co/${modelFormat.label}/resolve/main/$file"
        val destPath = downloadDestination.resolve(file)

        downloaderService.download(downloadUrl, destPath) {
            headers {
                accessToken?.let { append("Authorization", "Bearer $it") }
            }
        }.collect {
            Logger.d("Downloading AI Model: $it")
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
}