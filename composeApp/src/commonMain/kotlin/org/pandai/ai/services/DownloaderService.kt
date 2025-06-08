package org.pandai.ai.services

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use
import org.koin.core.annotation.Single

@Single
class DownloaderService(
    private val httpClient: HttpClient
) {
    fun download(
        downloadUrl: String,
        destPath: Path,
        builder: HttpRequestBuilder.() -> Unit = {}
    ) = flow {
        val fileSystem = FileSystem.SYSTEM
        httpClient.prepareGet(downloadUrl) {
            builder()
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
    }
}