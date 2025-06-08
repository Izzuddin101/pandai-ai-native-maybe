package org.pandai.ai.services.sentence_embeding

data class ModelConfig(
    val repo: String,
    val modelFile: String,
    val tokenizer: String,
    val useTokenTypeIds: Boolean,
    val outputTensorName: String,
    val normalizeEmbeddings: Boolean,
)


enum class Model {
    PARAPHRASE_MULTILINGUAL_MINILM_L12_V2
}

fun Model.getConfig(): ModelConfig =
    when (this) {
        Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2 ->
            ModelConfig(
                repo = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                modelFile = "onnx/model.onnx",
                tokenizer = "tokenizer.json",
                useTokenTypeIds = true,
                outputTensorName = "last_hidden_state",
                normalizeEmbeddings = false,
            )
    }
