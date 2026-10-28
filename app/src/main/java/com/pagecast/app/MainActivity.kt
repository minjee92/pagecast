package com.pagecast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pagecast.app.ui.navigation.PagecastNavHost
import com.pagecast.app.ui.theme.PagecastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PagecastTheme {
                PagecastNavHost()
            }
        }
    }
}
