package com.leon.weather.ui.weather

import com.leon.weather.MainDispatcherRule
import com.leon.weather.MutableClock
import com.leon.weather.data.repository.AppError
import com.leon.weather.data.repository.AppResult
import com.leon.weather.data.repository.WeatherRepository
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherSnapshot
import com.leon.weather.sampleWeather
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test fun `initial error can recover by retry`() = runTest {
        var calls = 0
        val vm = WeatherViewModel(sampleWeather().location, repository {
            if (++calls == 1) AppResult.Failure(AppError.Timeout) else AppResult.Success(sampleWeather())
        })
        assertEquals(WeatherUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(WeatherUiState.Error(AppError.Timeout), vm.uiState.value)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(sampleWeather(), (vm.uiState.value as WeatherUiState.Content).data)
    }

    @Test fun `failed refresh retains weather and exposes recoverable error`() = runTest {
        var calls = 0
        val pending = CompletableDeferred<AppResult<WeatherSnapshot>>()
        val vm = WeatherViewModel(sampleWeather().location, repository {
            if (++calls == 1) AppResult.Success(sampleWeather()) else pending.await()
        })
        advanceUntilIdle()
        vm.refresh(); runCurrent()
        assertTrue((vm.uiState.value as WeatherUiState.Content).isRefreshing)
        assertEquals(sampleWeather(), (vm.uiState.value as WeatherUiState.Content).data)
        pending.complete(AppResult.Failure(AppError.Network))
        advanceUntilIdle()
        val state = vm.uiState.value as WeatherUiState.Content
        assertFalse(state.isRefreshing)
        assertEquals(AppError.Network, state.refreshError)
        assertEquals(sampleWeather(), state.data)
    }

    @Test fun `multiple refresh taps share one in-flight request`() = runTest {
        var calls = 0
        val pending = CompletableDeferred<AppResult<WeatherSnapshot>>()
        val vm = WeatherViewModel(sampleWeather().location, repository { calls++; pending.await() })
        runCurrent()
        repeat(4) { vm.refresh() }
        runCurrent()
        assertEquals(1, calls)
        pending.complete(AppResult.Success(sampleWeather()))
        advanceUntilIdle()
    }

    @Test fun `foreground reuses fresh data and refreshes once at fifteen minutes`() = runTest {
        val initial = sampleWeather()
        val clock = MutableClock(initial.fetchedAt)
        var calls = 0
        val vm = WeatherViewModel(initial.location, repository {
            calls++
            AppResult.Success(initial.copy(fetchedAt = clock.instant()))
        }, clock)
        advanceUntilIdle()
        vm.onForeground()
        clock.now = initial.fetchedAt.plusSeconds(899)
        vm.onForeground()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(clock.instant(), (vm.uiState.value as WeatherUiState.Content).referenceTime)
        clock.now = initial.fetchedAt.plusSeconds(900)
        vm.onForeground(); vm.onForeground()
        advanceUntilIdle()
        assertEquals(2, calls)
        vm.onForeground()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun `city local midnight refreshes even when data is only two minutes old`() = runTest {
        for ((zone, fetchedAt) in listOf(
            "Asia/Shanghai" to "2026-10-29T15:59:00Z",
            "America/New_York" to "2026-10-30T03:59:00Z",
        )) {
            val clock = MutableClock(Instant.parse(fetchedAt))
            val data = sampleWeather().copy(timeZone = ZoneId.of(zone), fetchedAt = clock.now)
            var calls = 0
            val vm = WeatherViewModel(data.location, repository { calls++; AppResult.Success(data.copy(fetchedAt = clock.now)) }, clock)
            advanceUntilIdle()
            clock.now = clock.now.plusSeconds(120)
            vm.onForeground()
            advanceUntilIdle()
            assertEquals(zone, 2, calls)
        }
    }

    @Test fun `UTC midnight alone does not refresh a city that has not changed date`() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-29T23:59:00Z"))
        val data = sampleWeather().copy(timeZone = ZoneId.of("America/New_York"), fetchedAt = clock.now)
        var calls = 0
        val vm = WeatherViewModel(data.location, repository { calls++; AppResult.Success(data) }, clock)
        advanceUntilIdle()
        clock.now = clock.now.plusSeconds(120)
        vm.onForeground()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun `failed automatic refresh keeps snapshot timestamps and advances display reference until retry succeeds`() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-29T15:59:00Z"))
        val data = sampleWeather().copy(fetchedAt = clock.now)
        val pending = CompletableDeferred<AppResult<WeatherSnapshot>>()
        var calls = 0
        val vm = WeatherViewModel(data.location, repository {
            when (++calls) {
                1 -> AppResult.Success(data)
                2 -> pending.await()
                else -> AppResult.Success(data.copy(fetchedAt = clock.now, current = data.current.copy(time = clock.now)))
            }
        }, clock)
        advanceUntilIdle()
        clock.now = clock.now.plusSeconds(120)
        vm.onForeground(); runCurrent()
        repeat(3) { vm.onForeground(); vm.refresh() }
        runCurrent()
        assertEquals(2, calls)
        assertTrue((vm.uiState.value as WeatherUiState.Content).isRefreshing)
        pending.complete(AppResult.Failure(AppError.Network))
        advanceUntilIdle()
        val failed = vm.uiState.value as WeatherUiState.Content
        assertEquals(data, failed.data)
        assertEquals(clock.now, failed.referenceTime)
        assertEquals(AppError.Network, failed.refreshError)
        assertFalse(failed.isRefreshing)
        vm.refresh()
        advanceUntilIdle()
        val recovered = vm.uiState.value as WeatherUiState.Content
        assertNull(recovered.refreshError)
        assertEquals(clock.now, recovered.data.fetchedAt)
        assertEquals(clock.now, recovered.data.current.time)
    }

    @Test fun `moving device clock backwards refreshes instead of keeping a future snapshot indefinitely`() = runTest {
        val data = sampleWeather()
        val clock = MutableClock(data.fetchedAt)
        var calls = 0
        val vm = WeatherViewModel(data.location, repository { calls++; AppResult.Success(data.copy(fetchedAt = clock.now)) }, clock)
        advanceUntilIdle()
        clock.now = clock.now.minusSeconds(60)
        vm.onForeground()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    private fun repository(block: suspend () -> AppResult<WeatherSnapshot>) = object : WeatherRepository {
        override suspend fun getWeather(location: Location) = block()
    }
}
