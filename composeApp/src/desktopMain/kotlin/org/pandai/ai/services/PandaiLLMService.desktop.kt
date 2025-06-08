package org.pandai.ai.services

actual val LLMDownloadPath: String
    get() = System.getProperty("user.home") + "/pandai/llms"