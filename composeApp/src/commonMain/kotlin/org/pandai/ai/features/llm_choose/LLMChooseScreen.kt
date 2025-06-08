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
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Ok
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.android.annotation.KoinViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.pandai.ai.data.PandaiDataStore
import org.pandai.ai.services.ModelFormat
import org.pandai.ai.services.PandaiModelService
import org.pandai.ai.services.sentence_embeding.Model
import org.pandai.ai.services.sentence_embeding.SentenceEmbeddingService
import org.pandai.ai.services.sentence_embeding.getConfig

@Stable
data class LLMChooseUIState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val models: Map<String, ModelFormat> = emptyMap(),
    val downloadedFiles: Set<String> = emptySet(),
    val selectedModel: SelectedModel? = null,
    val downloadProgress: Map<String, Float> = emptyMap(),
    val accessToken: String? = null
) {
    @Stable
    data class SelectedModel(
        val modelGroup: String,
        val files: List<String>
    )
}

@KoinViewModel
class LLMChooseViewModel(
    private val llmService: PandaiModelService,
    private val dataStore: PandaiDataStore,
    private val sentenceEmbeddingService: SentenceEmbeddingService
) : ViewModel() {
    var uiState by mutableStateOf(LLMChooseUIState(isLoading = true))
        private set

    init {
        loadAvailableModels()

        viewModelScope.launch {
            uiState = uiState.copy(
                accessToken = dataStore.getToken().orEmpty()
            )
            if (sentenceEmbeddingService.checkDownloaded(Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2)) {
                uiState = uiState.copy(
                    downloadedFiles = uiState.downloadedFiles.toMutableSet().apply {
                        add(Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2.getConfig().repo)
                    }
                )
            }
            llmService.downloadedLLMs.collect { files ->
                uiState = uiState.copy(
                    downloadedFiles = uiState.downloadedFiles.toMutableSet().apply {
                        addAll(files)
                    },
                )
            }
        }
    }

    private fun loadAvailableModels() {
        try {
            val models = llmService.getAvailableModels()
            uiState = uiState.copy(
                isLoading = false,
                models = models,
                error = null
            )
        } catch (e: Exception) {
            uiState = uiState.copy(
                isLoading = false,
                error = e.message ?: "Failed to load models"
            )
        }
    }

    fun loadModelFiles(repoId: String?) {
        if (repoId == null) {
            uiState = uiState.copy(selectedModel = null)
            return
        }
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            val result = llmService.getAvailableModelFiles(repoId, dataStore.getToken())
            when {
                result.isOk -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        selectedModel = LLMChooseUIState.SelectedModel(
                            modelGroup = repoId,
                            files = result.value
                        ),
                        error = ""
                    )
                }

                result.isErr -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = result.error.message ?: "Failed to load files"
                    )
                }
            }
        }
    }

    fun downloadModel(file: String, repoId: String) {
        uiState = uiState.copy(
            downloadProgress = uiState.downloadProgress.toMutableMap().apply {
                put(repoId, 0f)
            }
        )

        viewModelScope.launch {
            if (repoId == Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2.getConfig().repo) {
                sentenceEmbeddingService.downloadSentenceEmbedding(Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2)
                    .map {
                        if (it.isOk) {
                            Ok(Pair(repoId, it.value.second))
                        } else it
                    }
            } else {
                llmService.download(file, repoId, uiState.accessToken)
            }.collect { result ->
                if (result.isErr) {
                    uiState = uiState.copy(error = result.error)
                } else {
                    val (file, progress) = result.value
                    uiState = uiState.copy(
                        downloadProgress = uiState.downloadProgress.toMutableMap().apply {
                            put(file, progress)
                        }
                    )
                }
            }
        }
    }

    fun deleteModel(file: String) {
        if (file == Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2.getConfig().repo) {
            sentenceEmbeddingService.deleteModel(Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2)
        } else {
            llmService.deleteModel(file)
        }
    }

    fun updateAccessToken(token: String) {
        uiState = uiState.copy(
            accessToken = token
        )
        viewModelScope.launch {
            dataStore.setToken(token)
        }
        loadAvailableModels()
    }
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
        onModelSelected = viewModel::loadModelFiles,
        onDownload = viewModel::downloadModel,
        onDelete = viewModel::deleteModel,
        onNavigateToChat = onNavigateToChat,
        onAccessTokenChange = viewModel::updateAccessToken
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LLMChooseContent(
    uiState: LLMChooseUIState,
    onModelSelected: (String?) -> Unit,
    onDownload: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
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
                    TextField(
                        value = uiState.accessToken.orEmpty(),
                        onValueChange = onAccessTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your Hugging Face access token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }

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
                    val model = remember { Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2.getConfig() }
                    Text(
                        text = "Sentence Embedding Model",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SentenceEmbeddingCard(
                        model = Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2,
                        downloadProgress = uiState.downloadProgress,
                        downloadedFiles = uiState.downloadedFiles,
                        onDownload = {
                            onDownload(
                                "",
                                model.repo
                            )
                        },
                        onDelete = { onDelete(model.repo) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (!uiState.error.isNullOrBlank()) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (uiState.selectedModel != null) {
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
                                    text = uiState.selectedModel.modelGroup,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onModelSelected(null) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }
                    }

                    items(uiState.selectedModel.files) { file ->
                        val relativePath = uiState.selectedModel.modelGroup + "/" + file
                        ModelFileCard(
                            fileName = file,
                            downloadProgress = uiState.downloadProgress[file] ?: 0f,
                            onDownload = { onDownload(file, uiState.selectedModel.modelGroup) },
                            onDelete = { onDelete(relativePath) },
                            onPlay = { onNavigateToChat(relativePath) },
                            isDownloaded = uiState.downloadedFiles.contains(relativePath)
                        )
                    }
                }
            } else {
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
                    if (isDownloaded || downloadProgress == 1f) {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        if (downloadProgress != 0f) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = onDownload) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
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

@Composable
private fun SentenceEmbeddingCard(
    model: Model,
    downloadProgress: Map<String, Float>,
    downloadedFiles: Set<String>,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val modelConfig = model.getConfig()
    val isDownloaded =
        downloadProgress[modelConfig.repo] == 1f || downloadedFiles.contains(modelConfig.repo)
    val isDownloading =
        downloadProgress[modelConfig.repo] != null && downloadProgress[modelConfig.repo] != 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (isDownloaded) onDelete() }
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = modelConfig.repo,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row {
                    if (isDownloaded) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        if (isDownloading) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = onDownload) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                }
            }
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = downloadProgress[modelConfig.repo] ?: 0f,
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
                uiState = LLMChooseUIState(
                    models = mapOf(
                        "litert-community/Gemma3-1B-IT" to ModelFormat(
                            label = "litert-community/Gemma3-1B-IT"
                        )
                    )
                ),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
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
                uiState = LLMChooseUIState(isLoading = true),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
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
                uiState = LLMChooseUIState(error = "Failed to load models"),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
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
                uiState = LLMChooseUIState(
                    selectedModel = LLMChooseUIState.SelectedModel(
                        modelGroup = "litert-community/Gemma3-1B-IT",
                        files = listOf(
                            "model-1.gguf",
                            "model-2.gguf",
                            "model-3.gguf"
                        )
                    ),
                    downloadedFiles = setOf("model-2.gguf"),
                    downloadProgress = mapOf(
                        "model-1.gguf" to 0.5f,
                        "model-2.gguf" to 1.0f
                    )
                ),
                onModelSelected = {},
                onDownload = { _, _ -> },
                onDelete = {},
                onNavigateToChat = {},
                onAccessTokenChange = {}
            )
        }
    }
}