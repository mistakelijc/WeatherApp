package com.leon.weather.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.weather.data.repository.AppError
import com.leon.weather.data.repository.AppResult
import com.leon.weather.data.repository.LocationRepository
import com.leon.weather.data.repository.SearchHistoryRepository
import com.leon.weather.model.Location
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface SearchStatus {
    data object Idle : SearchStatus
    data object TooShort : SearchStatus
    data object Loading : SearchStatus
    data object Empty : SearchStatus
    data class Results(val locations: List<Location>) : SearchStatus
    data class Error(val error: AppError) : SearchStatus
}

data class SearchUiState(
    val query: String = "",
    val status: SearchStatus = SearchStatus.Idle,
    val history: List<Location> = emptyList(),
    val historyUpdateFailed: Boolean = false,
)

class SearchViewModel(
    private val repository: LocationRepository,
    private val savedStateHandle: SavedStateHandle,
    private val historyRepository: SearchHistoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState(query = savedStateHandle[QUERY] ?: ""))
    val uiState = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var revision = 0L

    init {
        viewModelScope.launch {
            historyRepository.locations.collect { history -> _uiState.update { it.copy(history = history) } }
        }
        if (_uiState.value.query.isNotBlank()) search(immediate = true)
    }

    fun onQueryChanged(query: String) {
        if (query == _uiState.value.query) return
        savedStateHandle[QUERY] = query
        _uiState.update { it.copy(query = query, status = SearchStatus.Idle) }
        search(immediate = false)
    }

    fun retry() = search(immediate = true)

    fun onLocationSelected(location: Location) {
        onQueryChanged("")
        updateHistory { historyRepository.record(location) }
    }

    fun removeHistory(locationId: Long) {
        updateHistory { historyRepository.remove(locationId) }
    }

    fun clearHistory() {
        updateHistory { historyRepository.clear() }
    }

    private fun updateHistory(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
                _uiState.update { it.copy(historyUpdateFailed = false) }
            } catch (_: IOException) {
                // Keep the persisted list on failure; city navigation remains available.
                _uiState.update { it.copy(historyUpdateFailed = true) }
            }
        }
    }

    private fun search(immediate: Boolean) {
        searchJob?.cancel()
        val requestRevision = ++revision
        val query = _uiState.value.query.trim()
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(
                status = if (query.isEmpty()) SearchStatus.Idle else SearchStatus.TooShort,
            )
            return
        }
        _uiState.value = _uiState.value.copy(status = SearchStatus.Loading)
        searchJob = viewModelScope.launch {
            if (!immediate) delay(300)
            val result = repository.search(query)
            // A cancelled/non-cooperative data source may still finish. Only the latest input wins.
            if (requestRevision != revision) return@launch
            val status = when (result) {
                is AppResult.Success -> if (result.value.isEmpty()) SearchStatus.Empty else SearchStatus.Results(result.value)
                is AppResult.Failure -> SearchStatus.Error(result.error)
            }
            _uiState.value = _uiState.value.copy(status = status)
        }
    }

    companion object { private const val QUERY = "search_query" }
}
