package com.leon.weather.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leon.weather.R
import com.leon.weather.data.repository.AppError
import com.leon.weather.model.WeatherCondition

@Composable
fun errorMessage(error: AppError): String = stringResource(when (error) {
    AppError.Network -> R.string.network_error
    AppError.Timeout -> R.string.timeout_error
    AppError.RateLimited -> R.string.rate_limit_error
    AppError.ServiceUnavailable -> R.string.service_error
    AppError.InvalidData -> R.string.data_error
})

@Composable
fun conditionLabel(condition: WeatherCondition, isDay: Boolean = true): String = stringResource(when (condition) {
    WeatherCondition.Clear -> if (isDay) R.string.clear_day else R.string.clear_night
    WeatherCondition.MostlyClear -> R.string.mostly_clear
    WeatherCondition.PartlyCloudy -> R.string.partly_cloudy
    WeatherCondition.Overcast -> R.string.overcast
    WeatherCondition.Fog -> R.string.fog
    WeatherCondition.Drizzle -> R.string.drizzle
    WeatherCondition.Rain -> R.string.rain
    WeatherCondition.Snow -> R.string.snow
    WeatherCondition.Showers -> R.string.showers
    WeatherCondition.Thunderstorm -> R.string.thunderstorm
    WeatherCondition.Unknown -> R.string.unknown_weather
})

@Composable
fun WeatherIcon(condition: WeatherCondition, isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val vector = when (condition) {
        WeatherCondition.Clear, WeatherCondition.MostlyClear -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
        WeatherCondition.PartlyCloudy -> Icons.Outlined.WbCloudy
        WeatherCondition.Overcast -> Icons.Outlined.Cloud
        WeatherCondition.Fog -> Icons.Outlined.Waves
        WeatherCondition.Drizzle, WeatherCondition.Rain -> Icons.Outlined.WaterDrop
        WeatherCondition.Showers -> Icons.Outlined.Grain
        WeatherCondition.Snow -> Icons.Outlined.AcUnit
        WeatherCondition.Thunderstorm -> Icons.Outlined.Thunderstorm
        WeatherCondition.Unknown -> Icons.Outlined.CloudOff
    }
    val color = when (condition) {
        WeatherCondition.Clear, WeatherCondition.MostlyClear -> if (isDay) Color(0xFFE8AD42) else Color(0xFFAAC8E8)
        WeatherCondition.Thunderstorm -> Color(0xFF9D93C7)
        else -> Color(0xFF75B5D0)
    }
    Icon(vector, contentDescription = conditionLabel(condition, isDay), tint = color, modifier = modifier.size(size))
}

@Composable
fun LoadingContent(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MessageContent(title: String, message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun AttributionFooter(modifier: Modifier = Modifier, linkEnabled: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    val linkModifier = if (linkEnabled) {
        Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.attribution_action)) {
            uriHandler.openUri("https://open-meteo.com/")
        }
    } else Modifier
    Text(
        stringResource(R.string.attribution),
        modifier = modifier.fillMaxWidth().then(linkModifier).padding(vertical = 18.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
