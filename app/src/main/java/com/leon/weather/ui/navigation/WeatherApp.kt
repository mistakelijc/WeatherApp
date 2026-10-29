package com.leon.weather.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.leon.weather.data.repository.LocationRepository
import com.leon.weather.data.repository.WeatherRepository
import com.leon.weather.data.repository.SearchHistoryRepository
import com.leon.weather.model.Location
import com.leon.weather.ui.search.SearchScreen
import com.leon.weather.ui.search.SearchViewModel
import com.leon.weather.ui.weather.WeatherScreen
import com.leon.weather.ui.weather.WeatherViewModel
import kotlinx.serialization.Serializable
import java.time.Clock

@Serializable
data object SearchRoute

// Small, serializable location parameters survive process recreation without an in-memory selection.
@Serializable
data class WeatherRoute(
    val id: Long,
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
) {
    fun location() = Location(id, name, region, country, latitude, longitude, timeZone)
    companion object {
        fun from(location: Location) = with(location) { WeatherRoute(id, name, region, country, latitude, longitude, timeZone) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherApp(locationRepository: LocationRepository, weatherRepository: WeatherRepository, historyRepository: SearchHistoryRepository, onExit: () -> Unit, clock: Clock = Clock.systemUTC()) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    NavHost(navController = navController, startDestination = SearchRoute) {
        composable<SearchRoute> {
            val viewModel: SearchViewModel = viewModel(factory = viewModelFactory {
                initializer { SearchViewModel(locationRepository, createSavedStateHandle(), historyRepository) }
            })
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SearchScreen(
                state = state,
                onQueryChange = viewModel::onQueryChanged,
                onRetry = viewModel::retry,
                onRemoveHistory = viewModel::removeHistory,
                onClearHistory = viewModel::clearHistory,
            ) {
                navController.navigate(WeatherRoute.from(it)) { launchSingleTop = true }
                viewModel.onLocationSelected(it)
            }
        }
        composable<WeatherRoute> { entry ->
            val location = entry.toRoute<WeatherRoute>().location()
            val viewModel: WeatherViewModel = viewModel(factory = viewModelFactory {
                initializer { WeatherViewModel(location, weatherRepository, clock) }
            })
            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WeatherScreen(viewModel.location, state, onBack = { navController.popBackStack() }, onRefresh = viewModel::refresh)
        }
    }
    DoubleBackToExitHandler(
        enabled = currentEntry?.destination?.hasRoute<SearchRoute>() == true && !WindowInsets.isImeVisible,
        onExit = onExit,
    )
}
