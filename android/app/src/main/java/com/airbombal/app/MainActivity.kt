package com.airbombal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.airbombal.app.ui.AirbombApp

class MainActivity : ComponentActivity() {

    private val viewModel: AirbombViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirbombApp(viewModel)
        }
    }
}
