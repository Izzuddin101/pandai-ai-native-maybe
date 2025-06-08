package org.pandai.ai.services

import android.content.Context
import android.util.Log
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.koin.core.annotation.Single
import org.pandai.ai.services.sentence_embeding.Model
import org.pandai.ai.services.sentence_embeding.getConfig
import org.pandai.ai.services.vector.PandaiVector
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * RagManager - Handles the Retrieval Augmented Generation process
 * Combines sentence embedding generation with vector search for knowledge retrieval
 */
@Single
class RagService(
    private val vectorManager: PandaiVector,
) {
    private val sentenceEmbedding = SentenceEmbedding()
    private var isInitialized = false

    /**
     * Initialize the RAG system. Must be called before using other methods.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        runCatching {
            val fileSystem = FileSystem.SYSTEM
            // Get model configuration from ModelConfig.kt
            val modelConfig = Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2.getConfig()
            val downloadPath = LLMDownloadPath.toPath().resolve(modelConfig.repo)

            // Check what's in the assets directory to debug
            val assetsList = fileSystem.list(downloadPath)

            // Log the asset paths for debugging
            Log.d("RagManager", "Assets: $assetsList")

            // Load tokenizer from assets with the correct path
            val tokenizerPath = modelConfig.tokenizer
            val tokenizerBytes = runCatching {
                fileSystem.openReadOnly(downloadPath.resolve(tokenizerPath))
            }.onFailure {
                Log.e("RagManager", "Error in initialize: ${it.message}", it)
            }.getOrThrow()

            Log.d("RagManager", "Tokenizer loaded successfully: ${tokenizerBytes.size()} bytes")

            // Initialize sentence embeddings model with configuration and correct path
            val modelPath = modelConfig.modelFile
            Log.d("RagManager", "Attempting to load model from: $modelPath")

            val modelFilepath = downloadPath.resolve(modelPath).toString()

            Log.d(
                "RagManager",
                "Initializing SentenceEmbedding with model: $modelFilepath"
            )

            runCatching {
                sentenceEmbedding.init(
                    modelFilepath = modelFilepath,
                    tokenizerBytes = tokenizerBytes.source().buffer().readByteArray(),
                    useTokenTypeIds = modelConfig.useTokenTypeIds,
                    outputTensorName = modelConfig.outputTensorName,
                    useFP16 = true,
                    useXNNPack = false,
                    normalizeEmbeddings = false
                )
                Log.d("RagManager", "SentenceEmbedding initialized successfully")
            }.mapError { e ->
                Log.e(
                    "RagManager",
                    "Error initializing SentenceEmbedding: ${e.message}",
                    e
                )
                Exception("Failed to initialize Sentence Embeding: ${e.message}", e)
            }.getOrThrow()

            runCatching {
                // Ensure database has embeddings data
                Log.d("RagManager", "Populating dummy data")
                vectorManager.populateDummyData()
                Log.d("RagManager", "Dummy data populated successfully")
            }.mapError { e ->
                Log.e(
                    "RagManager",
                    "Error populating dummy data: ${e.message}",
                    e
                )
                Exception(
                    "Failed to populate dummy data: ${e.message}",
                    e
                )
            }.getOrThrow()
        }.onFailure { e ->
            Log.e("RagManager", "Error in initialize: ${e.message}", e)
        }.getOrThrow()

        isInitialized = true
    }

    /**
     * Calculate cosine similarity between two vectors
     * @return similarity score between 0 and 1, where 1 is identical
     */
    private fun cosineDistance(x1: FloatArray, x2: FloatArray): Float {
        var mag1 = 0.0f
        var mag2 = 0.0f
        var product = 0.0f
        for (i in x1.indices) {
            mag1 += x1[i].pow(2)
            mag2 += x2[i].pow(2)
            product += x1[i] * x2[i]
        }
        mag1 = sqrt(mag1)
        mag2 = sqrt(mag2)
        return product / (mag1 * mag2)
    }

    /**
     * Process a user query and retrieve relevant context from the vector database
     * @param query User's question
     * @return RetrievalResult containing top matches and their content
     */
    suspend fun retrieveRelevantContext(query: String): RetrievalResult =
        withContext(Dispatchers.IO) {
            // Generate embeddings for the query
            val queryEmbedding = sentenceEmbedding.encode(query)

            // Get top 3 matches from vector database with similarity scores
            val matches = vectorManager.search(queryEmbedding)

            return@withContext RetrievalResult(
                query = query,
                matches = matches.map { MatchResult(it.text ?: "", it.score) }
            )
        }

    /**
     * Generate a response using the retrieved context and the query
     */
    suspend fun generateAnswer(retrievalResult: RetrievalResult): String =
        withContext(Dispatchers.Default) {
            // Format the results to include scores
            val formattedResults = retrievalResult.matches.mapIndexed { index, match ->
                val scoreFormatted = "%.4f".format(match.similarityScore)
                "${index + 1}. $scoreFormatted\n${match.content}"
            }.joinToString("\n\n")

            return@withContext if (retrievalResult.matches.isNotEmpty()) {
                formattedResults
            } else {
                "I don't have enough information to answer your question about \"${retrievalResult.query}\""
            }
        }

    /**
     * Generate embeddings for a text query
     * @param query The text to encode
     * @return FloatArray containing the embedding vector
     */
    suspend fun generateEmbedding(query: String): FloatArray = withContext(Dispatchers.IO) {
        sentenceEmbedding.encode(query)
    }

    /**
     * Combined method to process a query and generate an answer in one step
     * @return Pair of (answer text, embedding vector)
     */
    suspend fun processQuery(query: String): Pair<String, FloatArray> {
        val embedding = generateEmbedding(query)
        val retrievalResult = retrieveRelevantContext(query)
        val answer = generateAnswer(retrievalResult)
        return Pair(answer, embedding)
    }

    /**
     * Calculate similarity score between two text inputs
     * @param text1 First text input
     * @param text2 Second text input
     * @return Similarity score between 0 and 1, where 1 means identical
     */
    suspend fun calculateSimilarity(text1: String, text2: String): Float =
        withContext(Dispatchers.IO) {
            val embedding1 = sentenceEmbedding.encode(text1)
            val embedding2 = sentenceEmbedding.encode(text2)

            // Calculate cosine similarity between the two embeddings
            var dotProduct = 0f
            var norm1 = 0f
            var norm2 = 0f

            for (i in embedding1.indices) {
                dotProduct += embedding1[i] * embedding2[i]
                norm1 += embedding1[i] * embedding1[i]
                norm2 += embedding2[i] * embedding2[i]
            }

            return@withContext if (norm1 > 0 && norm2 > 0) {
                dotProduct / (sqrt(norm1) * sqrt(norm2))
            } else {
                0f
            }
        }
}

/**
 * Data class representing a single matching document
 */
data class MatchResult(
    val content: String,
    val similarityScore: Float
)

/**
 * Data class representing the result of a retrieval operation
 */
data class RetrievalResult(
    val query: String,
    val matches: List<MatchResult>
)
