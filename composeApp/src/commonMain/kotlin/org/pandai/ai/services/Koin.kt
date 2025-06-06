package org.pandai.ai.services

import io.ktor.client.HttpClient
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ksp.generated.module

fun appModule() = listOf(
    module {
        single { buildKtor() } bind HttpClient::class
    }
) + NativeModule().module

fun initKoin() {
    startKoin {
        modules(appModule())
    }
}

@org.koin.core.annotation.Module
expect class NativeModule()