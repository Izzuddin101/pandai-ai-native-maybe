package org.pandai.ai.services

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("org.pandai.ai")
actual class NativeModule actual constructor() {
    @Single
    fun provideAIChat(): PandaiAIChat = PandaiAIChatNoOp()
}