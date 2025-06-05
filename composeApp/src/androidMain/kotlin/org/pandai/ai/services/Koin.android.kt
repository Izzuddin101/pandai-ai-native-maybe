package org.pandai.ai.services

import android.content.Context
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import org.pandai.ai.app

actual fun platformModule() = listOf(
    module { factory<Context> { app } },
) + defaultModule
