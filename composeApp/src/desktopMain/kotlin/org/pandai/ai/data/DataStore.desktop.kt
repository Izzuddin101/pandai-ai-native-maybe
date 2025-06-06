package org.pandai.ai.data

actual fun producePath(): String {
    return System.getProperty("user.home") + "/pandai/llms"
}