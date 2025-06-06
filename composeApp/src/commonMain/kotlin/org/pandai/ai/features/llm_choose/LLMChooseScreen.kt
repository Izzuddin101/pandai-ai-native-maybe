package org.pandai.ai.features.llm_choose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.android.annotation.KoinViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.pandai.ai.data.PandaiDataStore
import org.pandai.ai.services.ModelFormat
import org.pandai.ai.services.PandaiLLMService

@KoinViewModel
class LLMChooseViewModel(
    private val llmService: PandaiLLMService,
    private val dataStore: PandaiDataStore
) : ViewModel() {
    var uiState by mutableStateOf<LLMChooseUiState>(LLMChooseUiState.Loading)
        private set

    var downloadProgress by mutableStateOf<Map<String, Float>>(emptyMap())
        private set

    var downloadedFiles = llmService.downloadedLLMs

    var accessToken by mutableStateOf("")
        private set

    init {
        loadAvailableModels()

        viewModelScope.launch {
            accessToken = dataStore.getToken().orEmpty()
            downloadedFiles.collect {
                val state = uiState
                if (state is LLMChooseUiState.FilesLoaded) {
                    uiState = state.copy(downlaodedFiles = it)
                }
            }
        }
    }

    private fun loadAvailableModels() {
        try {
            val models = llmService.getAvailableModels()
            uiState = LLMChooseUiState.Success(models)
        } catch (e: Exception) {
            uiState = LLMChooseUiState.Error(e.message ?: "Failed to load models")
        }
    }

    fun loadModelFiles(repoId: String?) {
        if (repoId == null) {
            loadAvailableModels()
            return
        }
        viewModelScope.launch {
            val result = llmService.getAvailableModelFiles(repoId, dataStore.getToken())
            when {
                result.isOk -> {
                    uiState =
                        LLMChooseUiState.FilesLoaded(result.value, repoId, downloadedFiles.value)
                }

                result.isErr -> {
                    uiState = LLMChooseUiState.Error(result.error.message ?: "Failed to load files")
                }
            }
        }
    }

    fun downloadModel(file: String, repoId: String) {
        downloadProgress = downloadProgress.toMutableMap().apply {
            put(file, 0f)
        }

        viewModelScope.launch {
            llmService.downloadModelWithProgress(file, repoId, dataStore.getToken())
                .collect { result ->
                    when {
                        result.isOk -> {
                            val (file, progress) = result.value
                            downloadProgress = downloadProgress.toMutableMap().apply {
                                put(file, progress * 100)
                            }
                        }

                        result.isErr -> {
                            uiState = LLMChooseUiState.Error(result.error)
                        }
                    }
                }
        }

    }

    fun deleteModel(file: String) {
        llmService.deleteModel(file)
    }

    fun updateAccessToken(token: String) {
        accessToken = token
        viewModelScope.launch {
            dataStore.setToken(token)
        }
        loadAvailableModels()
    }
}

sealed class LLMChooseUiState {
    data object Loading : LLMChooseUiState()
    data class Success(val models: Map<String, ModelFormat>) : LLMChooseUiState()
    data class FilesLoaded(
        val files: List<String>,
        val modelGroup: String,
        val downlaodedFiles: List<String> = emptyList()
    ) : LLMChooseUiState()

    data class Error(val message: String) : LLMChooseUiState()
}

@Serializable
data object LLMChooseScreen

@Composable
fun LLMChooseScreen(
    viewModel: LLMChooseViewModel = koinViewModel(),
    onNavigateToChat: (String) -> Unit
) {
    LLMChooseContent(
        uiState = viewModel.uiState,
        downloadProgress = viewModel.downloadProgress,
        onModelSelected = viewModel::loadModelFiles,
        onDownload = viewModel::downloadModel,
        onDelete = viewModel::deleteModel,
        onNavigateToChat = onNavigateToChat,
        accessToken = viewModel.accessToken,
        onAccessTokenChange = viewModel::updateAccessToken
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LLMChooseContent(
    uiState: LLMChooseUiState,
    downloadProgress: Map<String, Float>,
    onModelSelected: (String?) -> Unit,
    onDownload: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
    accessToken: String,
    onAccessTokenChange: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose LLM Model") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Hugging Face Access Token",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    androidx.compose.material3.TextField(
                        value = accessToken,
                        onValueChange = onAccessTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your Hugging Face access token") },
                        singleLine = true
                    )
                }
            }

            when (uiState) {
                is LLMChooseUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is LLMChooseUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.models.toList()) { (repoId, model) ->
                            ModelCard(
                                model = model,
                                isSelected = false,
                                onSelect = {
                                    onModelSelected(repoId)
                                }
                            )
                        }
                    }
                }

                is LLMChooseUiState.FilesLoaded -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.modelGroup,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    IconButton(onClick = { onModelSelected(null) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }
                            }
                        }

                        items(uiState.files) { file ->
                            ModelFileCard(
                                fileName = file,
                                downloadProgress = downloadProgress[file] ?: 0f,
                                onDownload = { onDownload(file, uiState.modelGroup) },
                                onDelete = { onDelete(file) },
                                onPlay = { onNavigateToChat(file) },
                                isDownloaded = remember(
                                    uiState.downlaodedFiles,
                                    file
                                ) { uiState.downlaodedFiles.contains(file) }
                            )
                        }
                    }
                }

                is LLMChooseUiState.Error -> {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelFormat,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = model.label,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (isSelected) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelFileCard(
    fileName: String,
    downloadProgress: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    isDownloaded: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (isDownloaded) onPlay()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = fileName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Row {
                    if (isDownloaded) {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    if (downloadProgress < 1f && !isDownloaded) {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                }
            }
            if (downloadProgress > 0f && downloadProgress < 1f) {
                LinearProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun LLMChooseScreenPreview() {
    MaterialTheme {
        Surface {
            LLMChooseContent(
                uiState = LLMChooseUiState.Success(
                    mapOf(
                        "litert-community/Gemma3-1B-IT" to ModelFormat(
                            label = "litert-community/Gemma3-1B-IT"
                        )
                    )
                ),
                downloadProgress = emptyMap(),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
                accessToken = "",
                onAccessTokenChange = {}
            )
        }
    }
}

@Preview
@Composable
private fun LLMChooseScreenLoadingPreview() {
    MaterialTheme {
        Surface {
            LLMChooseContent(
                uiState = LLMChooseUiState.Loading,
                downloadProgress = emptyMap(),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
                accessToken = "",
                onAccessTokenChange = {}
            )
        }
    }
}

@Preview
@Composable
private fun LLMChooseScreenErrorPreview() {
    MaterialTheme {
        Surface {
            LLMChooseContent(
                uiState = LLMChooseUiState.Error("Failed to load models"),
                downloadProgress = emptyMap(),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
                accessToken = "",
                onAccessTokenChange = {}
            )
        }
    }
}

@Preview
@Composable
private fun LLMChooseScreenFilesLoadedPreview() {
    MaterialTheme {
        Surface {
            LLMChooseContent(
                uiState = LLMChooseUiState.FilesLoaded(
                    listOf(
                        "model-1.gguf",
                        "model-2.gguf",
                        "model-3.gguf"
                    ),
                    "litert-community/Gemma3-1B-IT",
                    downlaodedFiles = listOf()
                ),
                downloadProgress = mapOf(
                    "model-1.gguf" to 0.5f,
                    "model-2.gguf" to 1.0f
                ),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
                accessToken = "",
                onAccessTokenChange = {}
            )
        }
    }
}