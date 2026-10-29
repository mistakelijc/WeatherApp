package com.leon.weather.di

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.leon.weather.data.remote.ForecastApi
import com.leon.weather.data.remote.GeocodingApi
import com.leon.weather.data.repository.LocationRepository
import com.leon.weather.data.repository.OpenMeteoLocationRepository
import com.leon.weather.data.repository.OpenMeteoWeatherRepository
import com.leon.weather.data.repository.WeatherRepository
import com.leon.weather.data.repository.DataStoreSearchHistoryRepository
import com.leon.weather.data.repository.SearchHistoryRepository
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val Context.historyDataStore by preferencesDataStore(
    name = "search_history",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    val historyRepository: SearchHistoryRepository = DataStoreSearchHistoryRepository(context.applicationContext.historyDataStore, json)

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val locationRepository: LocationRepository = OpenMeteoLocationRepository(
        retrofit("https://geocoding-api.open-meteo.com/").create(GeocodingApi::class.java),
    )
    val weatherRepository: WeatherRepository = OpenMeteoWeatherRepository(
        retrofit("https://api.open-meteo.com/").create(ForecastApi::class.java),
        clock,
    )
}
