package com.leon.weather

import android.app.Application
import com.leon.weather.di.AppContainer

class WeatherApplication : Application() {
    val container by lazy { AppContainer(this) }
}
