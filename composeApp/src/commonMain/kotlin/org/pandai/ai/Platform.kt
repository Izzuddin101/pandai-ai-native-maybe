package org.pandai.ai

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform