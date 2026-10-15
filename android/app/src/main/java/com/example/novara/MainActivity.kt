package com.example.novara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.novara.ui.navigation.NavGraph
import com.example.novara.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NovaraApplication
        setContent {
            MyApplicationTheme {
                NavGraph(application = app)
            }
        }
    }
}
