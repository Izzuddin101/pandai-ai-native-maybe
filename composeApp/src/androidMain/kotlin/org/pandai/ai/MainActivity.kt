package org.pandai.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.koin.android.ext.android.inject
import org.pandai.ai.services.initKoin
import org.pandai.ai.services.vector.ObjectBox

class MainActivity : ComponentActivity() {

    private val objectBox: ObjectBox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initKoin()
        objectBox.populateDummyData()

        setContent {
            App()
        }
    }
}

