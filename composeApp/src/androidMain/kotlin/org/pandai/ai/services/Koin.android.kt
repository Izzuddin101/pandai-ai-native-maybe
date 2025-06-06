package org.pandai.ai.services

import android.content.Context
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.pandai.ai.app

@Module
@ComponentScan("org.pandai.ai")
actual class NativeModule actual constructor() {
    @Single
    fun provideContext(): Context = app
}