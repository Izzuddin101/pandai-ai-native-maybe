package org.pandai.ai.services.ai_model

data class ModelConfig(
    val modelName: String,
    val modelFile: String,
    val tokenizer: String,
    val useTokenTypeIds: Boolean,
    val outputTensorName: String,
    val normalizeEmbeddings: Boolean,
)

enum class Model {
    PARAPHRASE_MULTILINGUAL_MINILM_L12_V2
}

fun getModelConfig(model: Model): ModelConfig =
    when (model) {
        Model.PARAPHRASE_MULTILINGUAL_MINILM_L12_V2 ->
            ModelConfig(
                modelName = "paraphrase-multilingual-MiniLM-L12-v2",
                modelFile = "paraphrase-miniLM/model.onnx",
                tokenizer = "paraphrase-miniLM/tokenizer.json",
                useTokenTypeIds = true,
                outputTensorName = "last_hidden_state",
                normalizeEmbeddings = true,
            )
    }
