package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.theme.MyApplicationTheme
import com.example.voltcam.simulator.BoxSimulatorEngine
import com.example.voltcam.ui.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var simulatorEngine: BoxSimulatorEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        simulatorEngine = BoxSimulatorEngine(applicationContext)

        setContent {
            MyApplicationTheme {
                MainScreen(engine = simulatorEngine)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::simulatorEngine.isInitialized) {
            simulatorEngine.stopAllServices()
        }
    }
}
