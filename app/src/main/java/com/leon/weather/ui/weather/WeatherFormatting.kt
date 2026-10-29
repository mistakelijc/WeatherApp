package com.leon.weather.ui.weather

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal fun temperatureNumber(value: Double?): String = value?.roundToInt()?.toString() ?: "—"
internal fun decimalNumber(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
internal fun localHour(time: Instant, zone: ZoneId): String = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(time.atZone(zone))
internal fun localDateTime(time: Instant, zone: ZoneId): String = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).format(time.atZone(zone))
internal fun weekday(date: LocalDate): String = DateTimeFormatter.ofPattern("EEE", Locale.SIMPLIFIED_CHINESE).format(date)
internal fun calendarDay(date: LocalDate): String = DateTimeFormatter.ofPattern("MM/dd", Locale.ROOT).format(date)

internal fun relativeDay(time: Instant, reference: Instant, zone: ZoneId): Long =
    time.atZone(zone).toLocalDate().toEpochDay() - reference.atZone(zone).toLocalDate().toEpochDay()
