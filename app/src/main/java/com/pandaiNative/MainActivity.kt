package com.pandaiNative

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.pandaiNative.rag.RagManager
import com.pandaiNative.ui.theme.PandaiainativeTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box

class MainActivity : ComponentActivity() {

    private lateinit var ragManager: RagManager
    private val isInitializedState = mutableStateOf(false)
    private val isLoadingState = mutableStateOf(false)
    private val queryState = mutableStateOf("")
    private val answerState = mutableStateOf("")
    private val embeddingState = mutableStateOf<FloatArray?>(null) // Added embedding state

    // New states for similarity check feature
    private val sentence1State = mutableStateOf("")
    private val sentence2State = mutableStateOf("")
    private val similarityScoreState = mutableStateOf<Float?>(null)
    private val isSimilarityCheckingState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MainActivity", "Uncaught exception: ${throwable.message}", throwable)
            answerState.value = "App crashed: ${throwable.message}\n${throwable.stackTraceToString()}"
        }

        // Initialize RAG Manager
        try {
            ragManager = RagManager(applicationContext)

            // Initialize the RAG system asynchronously
            lifecycleScope.launch {
                isLoadingState.value = true
                try {
                    ragManager.initialize()
                    isInitializedState.value = true
                } catch (e: Exception) {
                    // Log the error and display it to help with debugging
                    Log.e("MainActivity", "Error initializing RAG system: ${e.message}", e)
                    answerState.value = "Error initializing: ${e.message}\n${e.stackTraceToString()}"
                } finally {
                    isLoadingState.value = false
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating RagManager: ${e.message}", e)
            answerState.value = "Failed to create RagManager: ${e.message}\n${e.stackTraceToString()}"
        }

        setContent {
            PandaiainativeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Simple header instead of TopAppBar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "PandAI RAG Demo",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // Main app tabs
                        var selectedAppTab by remember { mutableStateOf(0) }
                        val appTabs = listOf("RAG Query", "Similarity Check")

                        // Top level tab navigation
                        TabRow(selectedTabIndex = selectedAppTab) {
                            appTabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedAppTab == index,
                                    onClick = { selectedAppTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }

                        // Main content based on selected tab
                        when (selectedAppTab) {
                            0 -> {
                                // RAG Query tab
                                RagScreen(
                                    isInitialized = isInitializedState.value,
                                    isLoading = isLoadingState.value,
                                    query = queryState.value,
                                    answer = answerState.value,
                                    embedding = embeddingState.value,
                                    onQueryChange = { queryState.value = it },
                                    onAskQuestion = {
                                        lifecycleScope.launch {
                                            if (isInitializedState.value && !isLoadingState.value && queryState.value.isNotBlank()) {
                                                isLoadingState.value = true
                                                try {
                                                    val (answer, embedding) = ragManager.processQuery(queryState.value)
                                                    answerState.value = answer
                                                    embeddingState.value = embedding
                                                } catch (e: Exception) {
                                                    answerState.value = "Error processing query: ${e.message}"
                                                } finally {
                                                    isLoadingState.value = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                            1 -> {
                                // Similarity Check tab
                                SimilarityCheckTab(
                                    isInitialized = isInitializedState.value,
                                    isLoading = isLoadingState.value,
                                    sentence1 = sentence1State.value,
                                    sentence2 = sentence2State.value,
                                    similarityScore = similarityScoreState.value,
                                    onSentence1Change = { sentence1State.value = it },
                                    onSentence2Change = { sentence2State.value = it },
                                    onCheckSimilarity = {
                                        lifecycleScope.launch {
                                            if (isInitializedState.value && !isLoadingState.value &&
                                                sentence1State.value.isNotBlank() && sentence2State.value.isNotBlank()
                                            ) {
                                                isSimilarityCheckingState.value = true
                                                isLoadingState.value = true
                                                try {
                                                    val score = ragManager.calculateSimilarity(
                                                        sentence1State.value,
                                                        sentence2State.value
                                                    )
                                                    similarityScoreState.value = score
                                                } catch (e: Exception) {
                                                    Log.e("MainActivity", "Error calculating similarity: ${e.message}", e)
                                                } finally {
                                                    isSimilarityCheckingState.value = false
                                                    isLoadingState.value = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RagScreen(
    isInitialized: Boolean,
    isLoading: Boolean,
    query: String,
    answer: String,
    embedding: FloatArray?,
    onQueryChange: (String) -> Unit,
    onAskQuestion: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Tab state
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Answer", "Embedding")

    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Status indicator
        if (!isInitialized || isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = if (!isInitialized) "Initializing models..." else "Processing...",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Query input
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Ask a question") },
            placeholder = { Text("Enter your question here") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isInitialized && !isLoading
        )

        // Submit button
        Button(
            onClick = onAskQuestion,
            enabled = isInitialized && !isLoading && query.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Ask")
        }

        // Display tabs if we have content to show
        if (answer.isNotBlank() || embedding != null) {
            // Tab row
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Tab content
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> AnswerTab(answer)
                    1 -> EmbeddingTab(embedding)
                }
            }
        }
    }
}

@Composable
fun AnswerTab(answer: String) {
    if (answer.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No answer available yet. Ask a question first.")
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Answer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(answer)
            }
        }
    }
}

@Composable
fun EmbeddingTab(embedding: FloatArray?) {
    if (embedding == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No embedding available yet. Ask a question first.")
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Embedding Vector",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Format the embedding vector for display
                val formattedValues = embedding.mapIndexed { index, value ->
                    "[$index]: ${"%.6f".format(value)}"
                }
                
                // Display the embedding vector in multiple columns if possible
                Text(
                    text = "Dimensions: ${embedding.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(formattedValues.joinToString("\n"))
            }
        }
    }
}

@Composable
fun SimilarityCheckTab(
    isInitialized: Boolean,
    isLoading: Boolean,
    sentence1: String,
    sentence2: String,
    similarityScore: Float?,
    onSentence1Change: (String) -> Unit,
    onSentence2Change: (String) -> Unit,
    onCheckSimilarity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // First sentence input
        OutlinedTextField(
            value = sentence1,
            onValueChange = onSentence1Change,
            label = { Text("First Sentence") },
            placeholder = { Text("Enter first sentence here") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isInitialized && !isLoading
        )

        // Second sentence input
        OutlinedTextField(
            value = sentence2,
            onValueChange = onSentence2Change,
            label = { Text("Second Sentence") },
            placeholder = { Text("Enter second sentence here") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isInitialized && !isLoading
        )

        // Check button
        Button(
            onClick = onCheckSimilarity,
            enabled = isInitialized && !isLoading && sentence1.isNotBlank() && sentence2.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Check Similarity")
        }

        // Display result
        if (similarityScore != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Similarity Score",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Format the similarity score as a percentage
                    val scorePercentage = (similarityScore * 100).toInt()
                    Text(
                        text = "$scorePercentage% (${String.format("%.4f", similarityScore)})",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Display interpretation of the score
                    val interpretation = when {
                        similarityScore > 0.9 -> "Very similar meaning (near identical)"
                        similarityScore > 0.75 -> "Similar meaning"
                        similarityScore > 0.5 -> "Somewhat related"
                        similarityScore > 0.25 -> "Slightly related"
                        else -> "Different meaning"
                    }
                    Text(
                        text = "Interpretation: $interpretation",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RagScreenPreview() {
    PandaiainativeTheme {
        RagScreen(
            isInitialized = true,
            isLoading = false,
            query = "How does photosynthesis work?",
            answer = "Photosynthesis is the process used by plants to convert light energy into chemical energy. This chemical energy is stored in the bonds of glucose molecules the plant builds.",
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f), // Sample embedding
            onQueryChange = {},
            onAskQuestion = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}