package com.pandaiNative.pandaiVector.vector

import android.content.Context
import com.PandaiNative.R
import io.objectbox.BoxStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import com.pandaiNative.pandaiVector.vector.model.EmbedingData
import com.pandaiNative.pandaiVector.vector.model.MyObjectBox
import com.pandaiNative.pandaiVector.vector.model.EmbedingData_


internal object ObjectBox {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        if (ObjectBox::store.isInitialized) return
        store = MyObjectBox.builder()
            .androidContext(context)
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun populateDummyData(context: Context) {
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