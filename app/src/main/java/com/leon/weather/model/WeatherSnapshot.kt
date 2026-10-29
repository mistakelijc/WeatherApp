package com.leon.weather.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CurrentWeather(
    val time: Instant,
    val temperature: Double?,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val windSpeed: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val uvIndex: Double? = null,
)

data class HourlyForecast(
    val time: Instant,
    val temperature: Double?,
    val precipitationProbability: Int?,
    val condition: WeatherCondition,
    val isDay: Boolean,
)

data class WeatherSnapshot(
    val location: Location,
    val timeZone: ZoneId,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val fetchedAt: Instant,
    val daily: List<DailyForecast> = emptyList(),
) {
    val today: DailyForecast? get() = daily.firstOrNull { it.date == fetchedAt.atZone(timeZone).toLocalDate() }
}

data class DailyForecast(
    val date: LocalDate,
    val maximumTemperature: Double?,
    val minimumTemperature: Double?,
    val condition: WeatherCondition,
)

enum class WeatherCondition {
    Clear, MostlyClear, PartlyCloudy, Overcast, Fog, Drizzle, Rain, Snow, Showers, Thunderstorm, Unknown;

    companion object {
        fun fromWmo(code: Int?): WeatherCondition = when (code) {
            0 -> Clear
            1 -> MostlyClear
            2 -> PartlyCloudy
            3 -> Overcast
            45, 48 -> Fog
            51, 53, 55, 56, 57 -> Drizzle
            61, 63, 65, 66, 67 -> Rain
            71, 73, 75, 77, 85, 86 -> Snow
            80, 81, 82 -> Showers
            95, 96, 99 -> Thunderstorm
            else -> Unknown
        }
    }
}
