package com.pandaiNative.pandaiVector.vector
import android.content.Context

/**
 * Helper class for vector operations that replaces the previous React Native bridge module.
 * Use this class to interact with ObjectBox vector database from native Kotlin code.
 */
class PandaiVectorModule(private val context: Context) {

    init {
        // Initialize ObjectBox if not already initialized
        ObjectBox.init(context)
    }

    /**
     * Populate the database with dummy data from resources
     */
    fun populateDummyData() {
        ObjectBox.populateDummyData(context)
    }

    /**
     * Search result data class containing both content and similarity score
     */
    data class SearchResult(val text: String?, val score: Float)

    /**
     * Search for nearest neighbors to the provided embedding vector
     * @param embeddings The embedding vector to search for
     * @return List of search results containing text and similarity scores
     */
    fun search(embeddings: FloatArray): List<SearchResult> {
        val results = ObjectBox.search(embeddings)
//        return results.map { result ->
//            // Calculate similarity score between the query and result embeddings
//            val similarityScore = calculateSimilarity(embeddings, result.embed ?: FloatArray(0))
//            SearchResult(result.text, similarityScore)
//        }
        return results.map { result ->
            val similarityScore = calculateSimilarity(embeddings, result.embed ?: FloatArray(0))
            SearchResult(result.text, similarityScore)
        }.sortedByDescending { it.score }
    }

    /**
     * Calculate cosine similarity between two embedding vectors
     */
    private fun calculateSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.isEmpty() || vec2.isEmpty()) return 0f

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        val minLength = minOf(vec1.size, vec2.size)
        for (i in 0 until minLength) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble())).toFloat()
        } else {
            0f
        }
    }

    /**
     * Get all text data from the vector database
     * @return List of text from all documents in the database
     */
    fun getAllData(): List<String?> {
        return ObjectBox.getAllData().map { it.text }
    }
}
