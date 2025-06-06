package org.pandai.ai.services

actual val LLMDOwnloadPath: String
    get() = System.getProperty("user.home") + "/pandai/llms"