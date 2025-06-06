package org.pandai.ai.services

import io.ktor.client.HttpClient

fun buildKtor(): HttpClient {
    return HttpClient()
}