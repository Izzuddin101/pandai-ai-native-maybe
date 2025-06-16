package org.pandai.ai.model

/**
 * Represents an entry in the semantic cache.
 *
 * @property query The original user query
 * @property queryEmbedding The vector representation of the query
 * @property response The stored response for this query
 */

data class CacheEntry(
    val query: String,
    val queryEmbedding: FloatArray,
    val response: String
) {
    // Overriding equals and hashCode is crucial for data classes containing arrays.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CacheEntry) return false // Changed this line

        // Now we compare all fields for equality
        if (query != other.query) return false
        if (!queryEmbedding.contentEquals(other.queryEmbedding)) return false
        if (response != other.response) return false

        return true
    }

    override fun hashCode(): Int {
        var result = query.hashCode()
        result = 31 * result + queryEmbedding.contentHashCode()
        result = 31 * result + response.hashCode()
        return result
    }
}