package io.hammerhead.blibsram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.blibsram.screens.MainScreen
import io.hammerhead.blibsram.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
