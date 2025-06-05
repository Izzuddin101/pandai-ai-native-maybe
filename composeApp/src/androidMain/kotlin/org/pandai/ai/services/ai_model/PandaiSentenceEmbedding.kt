package org.pandai.ai.services.ai_model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import com.ml.shubham0204.sentence_embeddings.HFTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.EnumSet
import kotlin.math.sqrt

/**
 * Based on https://github.com/ml-shubham0204/sentence_embeddings
 *
 * Modified mean pollilng logic to match Python's
 */
class PandaiSentenceEmbedding {
    private lateinit var hfTokenizer: HFTokenizer
    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var ortSession: OrtSession
    private var useTokenTypeIds: Boolean = false
    private var outputTensorName: String = ""

    suspend fun init(
        modelFilepath: String,
        tokenizerBytes: ByteArray,
        useTokenTypeIds: Boolean,
        outputTensorName: String,
        useFP16: Boolean = false,
        useXNNPack: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        hfTokenizer = HFTokenizer(tokenizerBytes)
        ortEnvironment = OrtEnvironment.getEnvironment()
        val options =
            OrtSession.SessionOptions().apply {
                if (useFP16) {
                    addNnapi(EnumSet.of(NNAPIFlags.USE_FP16, NNAPIFlags.CPU_DISABLED))
                }
                if (useXNNPack) {
                    addXnnpack(
                        mapOf(
                            "intra_op_num_threads" to "2",
                        ),
                    )
                }
            }
        ortSession = ortEnvironment.createSession(modelFilepath, options)
        this@PandaiSentenceEmbedding.useTokenTypeIds = useTokenTypeIds
        this@PandaiSentenceEmbedding.outputTensorName = outputTensorName
        Log.d(PandaiSentenceEmbedding::class.simpleName, "Input Names: " + ortSession.inputNames.toList())
        Log.d(
            PandaiSentenceEmbedding::class.simpleName,
            "Output Names: " + ortSession.outputNames.toList(),
        )
    }

    suspend fun encode(sentence: String): FloatArray =
        withContext(Dispatchers.IO) {
            val result = hfTokenizer.tokenize(sentence)
            val inputTensorMap = mutableMapOf<String, OnnxTensor>()
            val idsTensor =
                OnnxTensor.createTensor(
                    ortEnvironment,
                    LongBuffer.wrap(result.ids),
                    longArrayOf(1, result.ids.size.toLong()),
                )
            inputTensorMap["input_ids"] = idsTensor
            val attentionMaskTensor =
                OnnxTensor.createTensor(
                    ortEnvironment,
                    LongBuffer.wrap(result.attentionMask),
                    longArrayOf(1, result.attentionMask.size.toLong()),
                )
            inputTensorMap["attention_mask"] = attentionMaskTensor
            if (useTokenTypeIds) {
                val tokenTypeIdsTensor =
                    OnnxTensor.createTensor(
                        ortEnvironment,
                        LongBuffer.wrap(result.tokenTypeIds),
                        longArrayOf(1, result.tokenTypeIds.size.toLong()),
                    )
                inputTensorMap["token_type_ids"] = tokenTypeIdsTensor
            }

            val outputs = ortSession.run(inputTensorMap)
            val tokenEmbeddings3D = outputs.get(0).value as Array<Array<FloatArray>>
            val tokenEmbeddings = tokenEmbeddings3D[0]
            val pooledEmbedding = meanPooling(tokenEmbeddings, result.attentionMask)
            return@withContext pooledEmbedding
        }

    private fun meanPooling(
        tokenEmbeddings: Array<FloatArray>, // [seq, emb]
        attentionMask: LongArray            // [seq]
    ): FloatArray {
        Log.d("TEST", "Attention mask: ${attentionMask.contentToString()}")

        val seqLen = tokenEmbeddings.size
        val embSize = tokenEmbeddings[0].size

        // First operation: multiply token embeddings by expanded attention mask
        val maskedEmbeddings = Array(seqLen) { i ->
            FloatArray(embSize) { j ->
                tokenEmbeddings[i][j] * attentionMask[i].toFloat()
            }
        }
        Log.d("TEST", "First operation completed: ${maskedEmbeddings.contentDeepToString()}")

        // Sum along sequence dimension (axis 0)
        val sumEmbeddings = FloatArray(embSize) { j ->
            var sum = 0f
            for (i in 0 until seqLen) {
                sum += maskedEmbeddings[i][j]
            }
            sum
        }

        // Second operation: sum of attention mask (clamped to min 1e-9)
        val maskSum = maxOf(attentionMask.sum().toFloat(), 1e-9f)
        Log.d("TEST", "Second operation (mask sum): $maskSum")
        Log.d("Sum opperations", "sumEmbeddings: ${sumEmbeddings.contentToString()}")


        // Final mean pooling: divide by mask sum
        val result = FloatArray(embSize) { i ->
            sumEmbeddings[i] / maskSum
        }

        Log.d("TEST", result.contentToString())

        return result
    }

    // Function to normalize embeddings
    private fun normalize(embeddings: FloatArray): FloatArray {
        // Calculate the L2 norm (Euclidean norm)
        val norm = sqrt(embeddings.sumOf { it * it.toDouble() }).toFloat()
        // Normalize each embedding by dividing by the norm
        return embeddings.map { it / norm }.toFloatArray()
    }

    fun close() {
        ortSession.close()
        ortEnvironment.close()
        hfTokenizer.close()
    }
}