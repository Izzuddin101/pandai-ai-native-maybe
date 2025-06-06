package org.pandai.ai.data

import org.pandai.ai.app

internal const val dataStoreFileName = "pandai.preferences_pb"

actual fun producePath(): String {
    return app.filesDir.resolve(dataStoreFileName).absolutePath
}