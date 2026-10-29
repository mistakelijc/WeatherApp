package com.leon.weather.data.mapper

import com.leon.weather.data.remote.CurrentDto
import com.leon.weather.data.remote.DailyDto
import com.leon.weather.data.remote.ForecastDto
import com.leon.weather.data.remote.HourlyDto
import com.leon.weather.data.remote.LocationDto
import com.leon.weather.model.PopularLocations
import com.leon.weather.model.WeatherCondition
import com.leon.weather.ui.weather.localHour
import com.leon.weather.ui.weather.relativeDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WeatherMapperTest {
    private val location = PopularLocations.all.first()

    @Test fun `select next 24 hours across midnight in target city time`() {
        val now = Instant.parse("2026-10-28T15:30:00Z") // 23:30 in Beijing
        val start = Instant.parse("2026-10-28T15:00:00Z").epochSecond
        val dto = ForecastDto("Asia/Shanghai", CurrentDto(now.epochSecond), HourlyDto(time = (0L..24L).map { start + it * 3600 }))
        val result = dto.toSnapshot(location, now)
        assertEquals(24, result.hourly.size)
        assertEquals("00:00", localHour(result.hourly.first().time, result.timeZone))
        assertEquals(1L, relativeDay(result.hourly.first().time, now, result.timeZone))
        assertTrue(result.hourly.all { it.time > now })
    }

    @Test fun `DST repeated local hour preserves two distinct instants`() {
        val now = Instant.parse("2026-11-01T04:30:00Z")
        val times = listOf(Instant.parse("2026-11-01T05:00:00Z"), Instant.parse("2026-11-01T06:00:00Z"))
        val result = ForecastDto("America/New_York", CurrentDto(now.epochSecond), HourlyDto(time = times.map { it.epochSecond })).toSnapshot(location, now)
        assertEquals(2, result.hourly.size)
        assertEquals(listOf("01:00", "01:00"), result.hourly.map { localHour(it.time, result.timeZone) })
        assertNotEquals(result.hourly[0].time, result.hourly[1].time)
    }

    @Test fun `missing values do not become zero or shift hourly series`() {
        val now = Instant.parse("2026-10-28T08:15:00Z")
        val times = (1L..3L).map { now.epochSecond + it * 3600 }
        val result = ForecastDto("Asia/Shanghai", CurrentDto(now.epochSecond, weatherCode = 999),
            HourlyDto(time = times, temperature = listOf(18.0, null, 20.0), precipitationProbability = listOf(null)))
            .toSnapshot(location, now)
        assertNull(result.current.temperature)
        assertEquals(WeatherCondition.Unknown, result.current.condition)
        assertNull(result.hourly[1].temperature)
        assertEquals(20.0, result.hourly[2].temperature)
        assertNull(result.hourly[2].precipitationProbability)
    }

    @Test fun `unknown condition has fallback and missing forecast retains current`() {
        val now = Instant.now()
        val result = ForecastDto("Asia/Shanghai", CurrentDto(now.epochSecond, temperature = 10.0)).toSnapshot(location, now)
        assertEquals(WeatherCondition.Unknown, result.current.condition)
        assertEquals(10.0, result.current.temperature)
        assertTrue(result.hourly.isEmpty())
    }

    @Test fun `invalid coordinates are excluded from selectable locations`() {
        assertNull(LocationDto(1, "Invalid", latitude = 999.0, longitude = 1.0).toLocation())
        assertNull(LocationDto(1, "Missing").toLocation())
    }

    @Test fun `local day is independent of system timezone`() {
        val now = Instant.parse("2026-10-28T23:30:00Z")
        val next = now.plusSeconds(3600)
        assertEquals(0L, relativeDay(next, now, ZoneId.of("Asia/Shanghai")))
        assertEquals(1L, relativeDay(next, now, ZoneId.of("Europe/London")))
    }

    @Test fun `daily forecast selects seven local calendar days across DST and month boundary`() {
        val zone = ZoneId.of("America/Los_Angeles")
        val today = LocalDate.of(2026, 10, 31)
        val now = Instant.parse("2026-11-01T06:30:00Z") // Still October 31 at the destination.
        val offset = zone.rules.getOffset(now)
        val dates = (-1L..7L).map { today.plusDays(it) }
        val dto = ForecastDto(zone.id, CurrentDto(now.epochSecond), daily = DailyDto(
            time = dates.map { it.atStartOfDay().toEpochSecond(offset) },
            maximumTemperature = dates.indices.map { 20.0 + it },
            minimumTemperature = dates.indices.map { 10.0 + it },
            weatherCode = dates.map { 3 },
        ), utcOffsetSeconds = offset.totalSeconds)
        val result = dto.toSnapshot(location, now)
        assertEquals((0L..6L).map { today.plusDays(it) }, result.daily.map { it.date })
        assertEquals(21.0, result.today!!.maximumTemperature)
        assertEquals(11.0, result.today!!.minimumTemperature)
    }

    @Test fun `daily dates use response offset when a request crosses the spring DST change`() {
        val now = Instant.parse("2026-03-08T06:59:59Z") // New York is still UTC-5 as the request starts.
        val responseOffset = java.time.ZoneOffset.ofHours(-4)
        val dates = (0L..6L).map { LocalDate.of(2026, 3, 8).plusDays(it) }
        val result = ForecastDto("America/New_York", CurrentDto(now.epochSecond), daily = DailyDto(
            time = dates.map { it.atStartOfDay().toEpochSecond(responseOffset) },
            maximumTemperature = dates.indices.map { 20.0 + it },
        ), utcOffsetSeconds = responseOffset.totalSeconds).toSnapshot(location, now)
        assertEquals(dates, result.daily.map { it.date })
        assertEquals(20.0, result.today!!.maximumTemperature)
    }

    @Test fun `daily missing arrays preserve alignment and missing today stays missing`() {
        val now = Instant.parse("2026-10-29T08:15:00Z")
        val zone = ZoneId.of("Asia/Shanghai")
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        val result = ForecastDto(zone.id, CurrentDto(now.epochSecond), daily = DailyDto(
            time = (0L..2L).map { tomorrow.plusDays(it).atStartOfDay(zone).toEpochSecond() },
            maximumTemperature = listOf(24.0, null, 26.0), minimumTemperature = listOf(14.0),
        )).toSnapshot(location, now)
        assertNull(result.today)
        assertNull(result.daily[1].maximumTemperature)
        assertEquals(26.0, result.daily[2].maximumTemperature)
        assertNull(result.daily[2].minimumTemperature)
        assertEquals(WeatherCondition.Unknown, result.daily[2].condition)
    }

    @Test fun `UV comes from the current hour rather than a past or future peak`() {
        val now = Instant.parse("2026-10-29T08:30:00Z")
        val time = Instant.parse("2026-10-29T08:00:00Z").epochSecond
        val result = ForecastDto("Asia/Shanghai", CurrentDto(now.epochSecond), HourlyDto(
            time = listOf(time - 3600, time, time + 3600), uvIndex = listOf(9.0, 3.7, 10.0),
        )).toSnapshot(location, now)
        assertEquals(3.7, result.current.uvIndex)
        assertEquals(1, result.hourly.size)
    }

    @Test fun `nighttime zero UV is valid while missing stale and invalid UV stay unavailable`() {
        val now = Instant.parse("2026-10-29T08:30:00Z")
        listOf(null, -1.0, Double.NaN, 0.0).forEach { uv ->
            val result = ForecastDto("Asia/Shanghai", CurrentDto(now.epochSecond), HourlyDto(
                time = listOf(now.epochSecond - 1800), uvIndex = listOf(uv),
            )).toSnapshot(location, now)
            assertEquals(if (uv == 0.0) 0.0 else null, result.current.uvIndex)
        }
        val stale = ForecastDto("Asia/Shanghai", CurrentDto(now.epochSecond), HourlyDto(
            time = listOf(now.epochSecond - 3600), uvIndex = listOf(3.0),
        )).toSnapshot(location, now)
        assertNull(stale.current.uvIndex)
    }
}
