package org.pandai.ai.services.vector

import android.content.Context
import io.objectbox.BoxStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.koin.core.annotation.Single
import org.pandai.ai.R
import org.pandai.ai.model.EmbedingData
import org.pandai.ai.model.EmbedingData_
import org.pandai.ai.model.MyObjectBox
import org.pandai.ai.services.sharedJson

@Single
class ObjectBox(private val context: Context) {
    val store: BoxStore = MyObjectBox.builder()
        .androidContext(context)
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    fun populateDummyData() {
        if (!store.boxFor(EmbedingData::class.java).isEmpty) return
        val data = sharedJson.decodeFromStream<List<EmbedingData>>(
            context.resources.openRawResource(
                R.raw.parquet_embeds_rag
            )
        )
        val box = store.boxFor(EmbedingData::class.java)
        box.put(data)
    }

    fun search(array: FloatArray): List<EmbedingData> {
        val box = store.boxFor(EmbedingData::class.java)
        return box.query(EmbedingData_.embed.nearestNeighbors(array, 3))
            .build()
            .find()
    }

    // Experimental function to search with scores
//    fun searchWithScores(array: FloatArray): List<Pair<EmbedingData, Float>> {
//        val box = store.boxFor(EmbedingData::class.java)
//        return box.query(EmbedingData_.embed.nearestNeighbors(array, 10))
//            .build()
//            .find()
//            .filter { it.embed != null && it.embed!!.size == array.size }
//            .map { it to cosineSimilarity(array, it.embed!!) }
//            .sortedByDescending { it.second }
//    }


    fun getAllData(): List<EmbedingData> {
        val box = store.boxFor(EmbedingData::class.java)
        return box.query().build().find()
    }
}