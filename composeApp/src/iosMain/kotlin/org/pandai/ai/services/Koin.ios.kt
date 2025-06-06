package org.pandai.ai.services

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual fun platformModule(): List<Module> = listOf(
    module {
        singleOf<PandaiAIChat>(::PandaiAIChatNoOp)
    }
)

@org.koin.core.annotation.Module
actual class NativeModule actual constructor()