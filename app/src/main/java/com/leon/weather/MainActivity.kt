package com.leon.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leon.weather.ui.navigation.WeatherApp
import com.leon.weather.ui.theme.WeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WeatherApplication).container
        setContent {
            WeatherTheme { WeatherApp(container.locationRepository, container.weatherRepository, container.historyRepository, onExit = { finish() }, clock = container.clock) }
        }
    }
}
