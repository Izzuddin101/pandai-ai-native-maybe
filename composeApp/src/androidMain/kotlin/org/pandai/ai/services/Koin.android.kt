package org.pandai.ai.services

import android.content.Context
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ksp.generated.module
import org.pandai.ai.app
import org.pandai.ai.features.rag_demo.RagViewModel
import org.pandai.ai.services.vector.ObjectBox
import org.pandai.ai.services.vector.PandaiVector

actual fun platformModule() = listOf(
    module {
        factory<Context> { app }
    },
    manualScanModule()
) + NativeModule().module

@Module
@ComponentScan("org.pandai.ai")
actual class NativeModule actual constructor()

// Work around as currently Koin unable to scan outside commonMain module for component
fun manualScanModule() = module {
    singleOf(::PandaiAIChatImpl) bind PandaiAIChat::class
    singleOf(::PandaiVector)
    singleOf(::ObjectBox)
    singleOf(::RagService)
    viewModelOf(::RagViewModel)
}