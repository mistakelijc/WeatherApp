package com.leon.weather.data.repository

import com.leon.weather.data.mapper.toLocation
import com.leon.weather.data.mapper.toSnapshot
import com.leon.weather.data.remote.ForecastApi
import com.leon.weather.data.remote.GeocodingApi
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherSnapshot
import java.time.Clock

interface LocationRepository {
    suspend fun search(query: String): AppResult<List<Location>>
}

interface WeatherRepository {
    suspend fun getWeather(location: Location): AppResult<WeatherSnapshot>
}

class OpenMeteoLocationRepository(private val api: GeocodingApi) : LocationRepository {
    override suspend fun search(query: String): AppResult<List<Location>> = apiResult {
        val name = query.trim()
        val matches = findLocations(name)
        if (matches.isNotEmpty()) return@apiResult matches

        // GeoNames indexes some Chinese cities with 市 (厦门市), and others without it (杭州).
        // Retry a single name variant only for a successful, empty Chinese-city search.
        val alternative = chineseCityAlternative(name) ?: return@apiResult emptyList()
        findLocations(alternative)
    }

    private suspend fun findLocations(name: String) =
        api.search(name).results.mapNotNull { it.toLocation() }.distinctBy(Location::id)

    private fun chineseCityAlternative(name: String): String? {
        if (name.length < 2 || !name.all { it in '\u4E00'..'\u9FFF' }) return null
        return if (name.endsWith("市")) name.dropLast(1).takeIf { it.length >= 2 } else "${name}市"
    }
}

class OpenMeteoWeatherRepository(
    private val api: ForecastApi,
    private val clock: Clock,
) : WeatherRepository {
    override suspend fun getWeather(location: Location): AppResult<WeatherSnapshot> = apiResult {
        val asOf = clock.instant()
        api.forecast(location.latitude, location.longitude).toSnapshot(location, asOf)
    }
}
