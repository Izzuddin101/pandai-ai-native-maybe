package org.pandai.ai

import android.app.Application
import org.pandai.ai.services.vector.ObjectBox

private lateinit var _app: CoreApp
val app get() = _app

class CoreApp : Application() {
    override fun onCreate() {
        super.onCreate()

        _app = this
    }
}