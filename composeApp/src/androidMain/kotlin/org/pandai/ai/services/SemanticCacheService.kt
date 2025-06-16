package org.pandai.ai.services

import android.content.Context
import android.util.Log
import org.koin.core.annotation.Single
import org.pandai.ai.model.CacheEntry

/**
 * Service to manage semantic caching of conversations.
 * Maintains the last 5 conversations and provides semantically similar conversations when needed.
 */
@Single
class SemanticCacheService(
    private val context: Context,
    private val embed: RagService
) {
    companion object {
        private const val MAX_CACHE_SIZE = 5
        private const val HIGH_SIMILARITY_THRESHOLD = 0.95f
        private const val MEDIUM_SIMILARITY_THRESHOLD = 0.60f
        private const val TAG = "SemanticCache"
    }

    private val cache = mutableListOf<CacheEntry>()
    private val logMessages = mutableListOf<String>()

    // Define a result type to handle different outcomes using traditional sealed class pattern
    sealed class CacheResult {
        object NoMatch : CacheResult()
        class CacheHit(val response: String) : CacheResult()
        class ContextAssist(val context: String) : CacheResult()
    }

    suspend fun search(newQuery: String): CacheResult {
        val logMessage = "cacheQuery: {\"${newQuery.take(20)}${if(newQuery.length > 20) "..." else ""}\"}"
        Log.d(TAG, logMessage)
        logMessages.add(logMessage)

        if (cache.isEmpty()) {
            return CacheResult.NoMatch
        }

        val newQueryEmbedding = embed.generateEmbedding(newQuery)

        // Find the entry with the highest similarity
        var bestMatch: CacheEntry? = null
        var highestScore = -1.0f

        for (entry in cache) {
            val score = calculateSimilarity(newQueryEmbedding, entry.queryEmbedding)
            if (score > highestScore) {
                highestScore = score
                bestMatch = entry
            }
        }

        // Apply the hybrid logic
        return when {
            highestScore > HIGH_SIMILARITY_THRESHOLD -> {
                val hitMessage = "cacheHit: {\"${bestMatch!!.query.take(20)}${if(bestMatch.query.length > 20) "..." else ""}\"} (score: $highestScore)"
                Log.d(TAG, hitMessage)
                logMessages.add(hitMessage)
                CacheResult.CacheHit(bestMatch.response)
            }
            highestScore > MEDIUM_SIMILARITY_THRESHOLD -> {
                val assistMessage = "cacheAssist: {\"${bestMatch!!.query.take(20)}${if(bestMatch.query.length > 20) "..." else ""}\"} (score: $highestScore)"
                Log.d(TAG, assistMessage)
                logMessages.add(assistMessage)
                // Create a formatted context string
                val contextString = "Previously, a similar question was asked: \"${bestMatch.query}\". The answer was: \"${bestMatch.response}\""
                CacheResult.ContextAssist(contextString)
            }
            else -> {
                val missMessage = "cacheMiss: No similar queries found (best score: $highestScore)"
                Log.d(TAG, missMessage)
                logMessages.add(missMessage)
                CacheResult.NoMatch
            }
        }
    }

    private fun calculateSimilarity(v1: FloatArray, v2: FloatArray): Float {
        // Cosine Similarity implementation
        var dotProduct = 0.0f
        var normV1 = 0.0f
        var normV2 = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normV1 += v1[i] * v1[i]
            normV2 += v2[i] * v2[i]
        }
        return dotProduct / (kotlin.math.sqrt(normV1) * kotlin.math.sqrt(normV2))
    }

    fun addToCache(query: String, queryEmbedding: FloatArray, response: String) {
        // Enhanced logging for better visibility of what's being cached
        val shortResponse = response.take(50) + if(response.length > 50) "..." else ""

        // Log part 1: Basic store notification with full query (not shortened)
        val storeMessage = "storedMem: {\"$query\"}"
        Log.d(TAG, storeMessage)

        // Log the cache size for better visibility
        Log.d(TAG, "Current cache size: ${cache.size + 1}/${MAX_CACHE_SIZE}")

        // Log part 2: More details about what was stored
        Log.d(TAG, "Cache details - QUERY: $query")
        Log.d(TAG, "Cache details - RESPONSE: $shortResponse")

        // Store the main message in our log collection
        logMessages.add(storeMessage)
        logMessages.add("Full query: $query")
        logMessages.add("Response preview: $shortResponse")

        // If cache is full, remove the oldest entry (at the beginning of the list)
        if (cache.size >= MAX_CACHE_SIZE) {
            val removedEntry = cache.removeAt(0)
            Log.d(TAG, "Cache full - Removed oldest entry: \"${removedEntry.query}\"")
            logMessages.add("Removed from cache: \"${removedEntry.query}\"")
        }

        // Add the new entry to the end of the list
        cache.add(CacheEntry(query, queryEmbedding, response))

        // Log summary of all current cache entries for debugging
        Log.d(TAG, "=== FULL CACHE CONTENTS (${cache.size} entries) ===")
        cache.forEachIndexed { index, entry ->
            Log.d(TAG, "$index: \"${entry.query}\"")
        }
        Log.d(TAG, "=====================================")
    }

    /**
     * Returns a copy of the current cache entries for debugging purposes
     * This method should only be used for debugging/visualization
     */
    fun getDebugCache(): List<CacheEntry> {
        return cache.toList()
    }

    /**
     * Returns a list of recent log messages related to cache operations
     */
    fun getCacheLogMessages(): List<String> {
        return logMessages.toList()
    }

}