package org.pandai.ai.services

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.ksp.generated.module

fun appModule() = listOf<Module>() + NativeModule().module

fun initKoin() {
    startKoin {
        modules(appModule())
    }
}

@org.koin.core.annotation.Module
expect class NativeModule()