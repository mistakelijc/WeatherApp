package com.leon.weather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leon.weather.R
import com.leon.weather.model.HourlyForecast
import com.leon.weather.model.DailyForecast
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherSnapshot
import com.leon.weather.ui.components.AttributionFooter
import com.leon.weather.ui.components.LoadingContent
import com.leon.weather.ui.components.MessageContent
import com.leon.weather.ui.components.WeatherIcon
import com.leon.weather.ui.components.conditionLabel
import com.leon.weather.ui.components.errorMessage
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(location: Location, state: WeatherUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(location.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(location.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = onRefresh, enabled = state != WeatherUiState.Loading && (state as? WeatherUiState.Content)?.isRefreshing != true) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag("weather_screen")) {
            when (state) {
                WeatherUiState.Loading -> LoadingContent(stringResource(R.string.loading_weather), Modifier.align(Alignment.Center))
                is WeatherUiState.Error -> MessageContent(stringResource(R.string.weather_error_title), errorMessage(state.error), Modifier.align(Alignment.Center), onRefresh)
                is WeatherUiState.Content -> WeatherContent(state, onRefresh)
            }
        }
    }
}

@Composable
private fun WeatherContent(state: WeatherUiState.Content, onRefresh: () -> Unit) {
    val now = state.referenceTime
    val today = now.atZone(state.data.timeZone).toLocalDate()
    // Keep the original snapshot and its timestamps, but don't present elapsed forecasts as future.
    val data = state.data.copy(
        hourly = state.data.hourly.filter { it.time > now },
        daily = state.data.daily.filter { !it.date.isBefore(today) },
    )
    LazyColumn(modifier = Modifier.testTag("weather_content"), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.isRefreshing) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.refreshing), style = MaterialTheme.typography.labelMedium)
            }
        }
        state.refreshError?.let { error -> item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.refresh_failed), fontWeight = FontWeight.Medium)
                    Text(errorMessage(error), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Text(stringResource(R.string.weather_time, localDateTime(data.current.time, data.timeZone)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp).testTag("refresh_error_time"))
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            }
        } }
        item { CurrentWeatherCard(data, now) }
        item {
            Text(stringResource(R.string.hourly_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            if (data.hourly.isEmpty()) {
                Text(stringResource(R.string.hourly_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("hourly_forecast")) {
                    items(data.hourly, key = { it.time.epochSecond }) { hour -> HourCard(hour, data, now) }
                }
            }
        }
        if (data.hourly.size in 1..23) item {
            Text(stringResource(R.string.hourly_partial, data.hourly.size), style = MaterialTheme.typography.bodySmall)
        }
        item { Text(stringResource(R.string.daily_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (data.daily.isEmpty()) item {
            Text(stringResource(R.string.daily_empty), style = MaterialTheme.typography.bodyMedium)
        } else item { DailyForecastCard(data, now) }
        if (data.daily.size in 1..6) item {
            Text(stringResource(R.string.daily_partial, data.daily.size), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text(stringResource(R.string.time_zone, data.timeZone.id), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AttributionFooter()
        }
    }
}

@Composable
private fun CurrentWeatherCard(data: WeatherSnapshot, referenceTime: Instant) {
    val weather = data.current
    val today = data.daily.firstOrNull { it.date == referenceTime.atZone(data.timeZone).toLocalDate() }
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF163E56), contentColor = Color.White), modifier = Modifier.fillMaxWidth().testTag("current_weather")) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.current_weather), style = MaterialTheme.typography.labelLarge, color = Color(0xFFB5D4E4))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(temperatureLabel(weather.temperature), fontSize = 64.sp, lineHeight = 76.sp, fontWeight = FontWeight.Light)
                    Text(conditionLabel(weather.condition, weather.isDay), style = MaterialTheme.typography.titleLarge)
                }
                WeatherIcon(weather.condition, weather.isDay, size = 64.dp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.high_temperature, temperatureLabel(today?.maximumTemperature)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("today_high"))
                Text(stringResource(R.string.low_temperature, temperatureLabel(today?.minimumTemperature)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("today_low"))
            }
            Text(stringResource(R.string.weather_time, localDateTime(weather.time, data.timeZone)), modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB5D4E4))
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Metric(stringResource(R.string.feels_like), temperatureLabel(weather.apparentTemperature), Modifier.weight(1f))
                    Metric(stringResource(R.string.humidity), weather.humidity?.let { stringResource(R.string.percent_value, it) } ?: stringResource(R.string.missing_value), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Metric(stringResource(R.string.wind), weather.windSpeed?.let { stringResource(R.string.wind_value, decimalNumber(it)) } ?: stringResource(R.string.missing_value), Modifier.weight(1f))
                    Metric(stringResource(R.string.uv_index), weather.uvIndex?.let(::decimalNumber) ?: stringResource(R.string.missing_value), Modifier.weight(1f).testTag("current_uv"))
                }
            }
        }
    }
}

@Composable
private fun temperatureLabel(value: Double?): String = value?.let { stringResource(R.string.temperature_celsius, temperatureNumber(it)) } ?: stringResource(R.string.missing_value)

@Composable
private fun DailyForecastCard(data: WeatherSnapshot, referenceTime: Instant) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.testTag("daily_forecast")) {
        data.daily.forEachIndexed { index, day ->
            DailyForecastRow(day, data, referenceTime)
            if (index < data.daily.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun DailyForecastRow(day: DailyForecast, data: WeatherSnapshot, referenceTime: Instant) {
    val today = referenceTime.atZone(data.timeZone).toLocalDate()
    val label = when (day.date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> weekday(day.date)
    }
    Row(Modifier.fillMaxWidth().testTag("day_${day.date}").padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(52.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(calendarDay(day.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WeatherIcon(day.condition, true, size = 26.dp)
        Text(conditionLabel(day.condition), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.daily_high, temperatureLabel(day.maximumTemperature)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.daily_low, temperatureLabel(day.minimumTemperature)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB5D4E4), textAlign = TextAlign.Center)
        Text(value, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HourCard(hour: HourlyForecast, data: WeatherSnapshot, referenceTime: Instant) {
    val day = when (relativeDay(hour.time, referenceTime, data.timeZone)) {
        0L -> stringResource(R.string.today)
        1L -> stringResource(R.string.tomorrow)
        else -> hour.time.atZone(data.timeZone).toLocalDate().toString()
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.width(88.dp).padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(localHour(hour.time, data.timeZone), style = MaterialTheme.typography.labelLarge)
            WeatherIcon(hour.condition, hour.isDay)
            Text(hour.temperature?.let { stringResource(R.string.temperature_celsius, temperatureNumber(it)) } ?: stringResource(R.string.missing_value), fontWeight = FontWeight.SemiBold)
            val chance = hour.precipitationProbability?.let { stringResource(R.string.percent_value, it) } ?: stringResource(R.string.missing_value)
            val description = stringResource(R.string.precipitation_accessibility, chance)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.precipitation_probability), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text(chance, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { contentDescription = description })
            }
        }
    }
}
