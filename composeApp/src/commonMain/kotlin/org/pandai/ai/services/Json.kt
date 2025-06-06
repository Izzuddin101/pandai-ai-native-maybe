package org.pandai.ai.services

import kotlinx.serialization.json.Json

val sharedJson = Json {
    ignoreUnknownKeys = true
}