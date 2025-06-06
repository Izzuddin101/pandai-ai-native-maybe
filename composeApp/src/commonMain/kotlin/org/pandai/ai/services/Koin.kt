package org.pandai.ai.services

import org.koin.core.annotation.ComponentScan
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.ksp.generated.module

expect fun platformModule(): List<Module>

fun appModule() = listOf<Module>() + platformModule() + CommonModule().module

fun initKoin() {
    startKoin {
        modules(appModule())
    }
}

@org.koin.core.annotation.Module
@ComponentScan("org.pandai.ai")
class CommonModule

@org.koin.core.annotation.Module
expect class NativeModule()