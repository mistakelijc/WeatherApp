package com.leon.weather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.weather.data.repository.AppError
import com.leon.weather.data.repository.AppResult
import com.leon.weather.data.repository.WeatherRepository
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Error(val error: AppError) : WeatherUiState
    data class Content(
        val data: WeatherSnapshot,
        val isRefreshing: Boolean = false,
        val refreshError: AppError? = null,
        val referenceTime: Instant = data.fetchedAt,
    ) : WeatherUiState
}

class WeatherViewModel(
    val location: Location,
    private val repository: WeatherRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var job: Job? = null

    init { refresh() }

    fun onForeground() {
        val content = _uiState.value as? WeatherUiState.Content ?: return
        val now = clock.instant()
        _uiState.value = content.copy(referenceTime = now)
        val data = content.data
        val crossedLocalDate = now.atZone(data.timeZone).toLocalDate() != data.fetchedAt.atZone(data.timeZone).toLocalDate()
        if (now < data.fetchedAt || Duration.between(data.fetchedAt, now) >= REFRESH_INTERVAL || crossedLocalDate) {
            refresh()
        }
    }

    fun refresh() {
        if (job?.isActive == true) return
        val previous = (_uiState.value as? WeatherUiState.Content)?.data
        _uiState.value = previous?.let { WeatherUiState.Content(it, isRefreshing = true, referenceTime = clock.instant()) } ?: WeatherUiState.Loading
        job = viewModelScope.launch {
            _uiState.value = when (val result = repository.getWeather(location)) {
                is AppResult.Success -> WeatherUiState.Content(result.value, referenceTime = clock.instant())
                is AppResult.Failure -> previous?.let { WeatherUiState.Content(it, refreshError = result.error, referenceTime = clock.instant()) }
                    ?: WeatherUiState.Error(result.error)
            }
        }
    }

    private companion object { val REFRESH_INTERVAL: Duration = Duration.ofMinutes(15) }
}
