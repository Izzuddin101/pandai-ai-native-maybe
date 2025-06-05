package com.pandaiNative

data class ModelConfig(
    val modelName: String,
    val modelAssetsFilepath: String,
    val tokenizerAssetsFilepath: String,
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
                modelAssetsFilepath = "paraphrase-miniLM/model.onnx",
                tokenizerAssetsFilepath = "paraphrase-miniLM/tokenizer.json",
                useTokenTypeIds = true,
                outputTensorName = "last_hidden_state",
                normalizeEmbeddings = true,
            )
    }
