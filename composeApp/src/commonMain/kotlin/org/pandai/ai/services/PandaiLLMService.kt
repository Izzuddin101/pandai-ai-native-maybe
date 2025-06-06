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
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.utils.EmptyContent.contentLength
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
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

expect val LLMDOwnloadPath: String

@Single
class PandaiLLMService(
    private val httpClient: HttpClient,
) {
    private val modelFormatsData = mapOf(
        "litert-community/Gemma3-1B-IT" to ModelFormat(
            label = "litert-community/Gemma3-1B-IT"
        )
    )

    private val _downloadedLLMs = MutableStateFlow<List<String>>(emptyList())
    val downloadedLLMs = _downloadedLLMs.asStateFlow()

    private val downloadDestination = LLMDOwnloadPath.toPath()

    init {
        checkDownloadedModels()
    }

    private fun checkDownloadedModels() {
        try {
            val files = FileSystem.SYSTEM.list(downloadDestination)
            val ggufFiles = files
                .filter { it.name.endsWith(".gguf") }
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

    fun downloadModelWithProgress(
        file: String,
        repoId: String,
        accessToken: String?
    ): Flow<Result<Pair<String, Float>, String>> = flow {
        val modelFormat = modelFormatsData[repoId] ?: run {
            emit(Err("Invalid format"))
            return@flow
        }

        val downloadUrl = "https://huggingface.co/${modelFormat.label}/resolve/main/$file"
        val destPath = downloadDestination.resolve(file)
        val fileSystem = FileSystem.SYSTEM

        try {
            httpClient.prepareGet(downloadUrl) {
                headers {
                    accessToken?.let { append("Authorization", "Bearer $it") }
                }
            }.execute { response ->
                if (response.status.isSuccess()) {
                    // Ensure the destination directory exists
                    val parentDir = destPath.parent
                    if (parentDir != null && !fileSystem.exists(parentDir)) {
                        fileSystem.createDirectories(parentDir)
                    }

                    // Open a sink for writing the file
                    fileSystem.sink(destPath).buffer().use { sink ->
                        val byteReadChannel = response.bodyAsChannel()
                        val buffer = ByteArray(8 * 1024) // 8 KB buffer
                        val contentLength = response.contentLength() ?: -1L
                        var bytesReadTotal = 0L

                        while (!byteReadChannel.isClosedForRead) {
                            val bytesRead = byteReadChannel.readAvailable(buffer)
                            if (bytesRead > 0) {
                                sink.write(buffer, 0, bytesRead)
                                bytesReadTotal += bytesRead

                                // Emit progress as a percentage
                                if (contentLength > 0) {
                                    val progress = (bytesReadTotal / contentLength.toFloat())
                                    emit(Ok(destPath.toString() to progress))
                                } else {
                                    emit(Ok(destPath.toString() to -1f))
                                }
                            }
                        }
                    }

                    // Emit 100% progress when done
                    emit(Ok(destPath.toString() to 1f))
                } else {
                    emit(Err("Failed to download file. HTTP Status: ${response.status.value}"))
                }
            }
        } catch (error: Exception) {
            emit(Err(error.message ?: "Unknown error"))
        }
    }

    fun deleteModel(file: String) {
        val destPath = downloadDestination.resolve(file)
        FileSystem.SYSTEM.delete(destPath)
    }
}