package com.leon.weather.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") query: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "zh",
    ): LocationsDto
}

interface ForecastApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day",
        @Query("hourly") hourly: String = "temperature_2m,weather_code,precipitation_probability,is_day,uv_index",
        @Query("forecast_hours") hours: Int = 25,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("forecast_days") days: Int = 7,
        @Query("timezone") timeZone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windUnit: String = "ms",
    ): ForecastDto
}

@Serializable
data class LocationsDto(val results: List<LocationDto> = emptyList())

@Serializable
data class LocationDto(
    val id: Long? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val admin1: String = "",
    val country: String = "",
    val timezone: String = "UTC",
)

@Serializable
data class ForecastDto(
    val timezone: String,
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
)

@Serializable
data class CurrentDto(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("is_day") val isDay: Int = 1,
)

@Serializable
data class HourlyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
)

@Serializable
data class DailyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m_max") val maximumTemperature: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val minimumTemperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)
