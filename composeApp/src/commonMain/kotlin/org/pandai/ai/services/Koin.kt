package org.pandai.ai.services

import org.koin.core.context.startKoin
import org.koin.core.module.Module

expect fun platformModule(): List<Module>

fun appModule() = listOf<Module>() + platformModule()

fun initKoin() {
    startKoin {
        modules(appModule())
    }
}