package com.pandaiNative.pandaiVector.vector.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("ArrayInDataClass")
@Entity
@Serializable
data class EmbedingData(
    @Id
    var id: Long = 0,
    @SerialName("text")
    var text: String? = null,
    @SerialName("index")
    var index: Int? = null,
    @SerialName("embed")
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var embed: FloatArray? = null
)