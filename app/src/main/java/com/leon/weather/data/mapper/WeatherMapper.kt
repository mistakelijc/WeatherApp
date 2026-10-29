package com.leon.weather.data.mapper

import com.leon.weather.data.remote.ForecastDto
import com.leon.weather.data.remote.LocationDto
import com.leon.weather.data.repository.InvalidWeatherData
import com.leon.weather.model.CurrentWeather
import com.leon.weather.model.DailyForecast
import com.leon.weather.model.HourlyForecast
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherCondition
import com.leon.weather.model.WeatherSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal fun LocationDto.toLocation(): Location? {
    val locationId = id ?: return null
    val label = name?.takeIf(String::isNotBlank) ?: return null
    val lat = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
    val lon = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
    return Location(locationId, label, admin1, country, lat, lon, timezone)
}

internal fun ForecastDto.toSnapshot(location: Location, asOf: Instant): WeatherSnapshot {
    val now = current ?: throw InvalidWeatherData()
    val zone = ZoneId.of(timezone)
    val today = asOf.atZone(zone).toLocalDate()
    // Daily aggregates use the API's fixed request-time offset, even across DST.
    // Applying zone rules to future daily boundaries can repeat a date and lose a day.
    val dailyOffset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds ?: zone.rules.getOffset(asOf).totalSeconds)
    // UV is an hourly estimate. Never substitute a future hour or the day's peak value.
    val uv = hourly?.let { values ->
        val index = values.time.indices.filter { asOf.epochSecond - values.time[it] in 0L until 3600L }
            .maxByOrNull { values.time[it] }
        index?.let { values.uvIndex.getOrNull(it) }?.takeIf { it.isFinite() && it >= 0 }
    }
    val days = daily?.let { values ->
        values.time.mapIndexed { index, timestamp ->
            DailyForecast(
                date = Instant.ofEpochSecond(timestamp).atOffset(dailyOffset).toLocalDate(),
                maximumTemperature = values.maximumTemperature.getOrNull(index)?.takeIf(Double::isFinite),
                minimumTemperature = values.minimumTemperature.getOrNull(index)?.takeIf(Double::isFinite),
                condition = WeatherCondition.fromWmo(values.weatherCode.getOrNull(index)),
            )
        }.filter { !it.date.isBefore(today) }.distinctBy { it.date }.sortedBy { it.date }.take(7)
    }.orEmpty()
    val hours = hourly?.let { values ->
        // Keep all arrays aligned by their time index; missing values never shift later hours.
        values.time.mapIndexed { index, timestamp ->
            HourlyForecast(
                time = Instant.ofEpochSecond(timestamp),
                temperature = values.temperature.getOrNull(index)?.takeIf(Double::isFinite),
                precipitationProbability = values.precipitationProbability.getOrNull(index)?.takeIf { it in 0..100 },
                condition = WeatherCondition.fromWmo(values.weatherCode.getOrNull(index)),
                isDay = values.isDay.getOrNull(index) != 0,
            )
        }.filter { it.time > asOf }.distinctBy { it.time }.sortedBy { it.time }.take(24)
    }.orEmpty()
    return WeatherSnapshot(
        location = location,
        timeZone = zone,
        current = CurrentWeather(
            time = Instant.ofEpochSecond(now.time),
            temperature = now.temperature?.takeIf(Double::isFinite),
            apparentTemperature = now.apparentTemperature?.takeIf(Double::isFinite),
            humidity = now.humidity?.takeIf { it in 0..100 },
            windSpeed = now.windSpeed?.takeIf { it.isFinite() && it >= 0 },
            condition = WeatherCondition.fromWmo(now.weatherCode),
            isDay = now.isDay != 0,
            uvIndex = uv,
        ),
        hourly = hours,
        fetchedAt = asOf,
        daily = days,
    )
}
