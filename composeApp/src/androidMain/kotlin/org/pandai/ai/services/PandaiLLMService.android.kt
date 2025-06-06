package org.pandai.ai.services

import android.os.Environment

actual val LLMDOwnloadPath: String
    get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath + "/pandai/llms"