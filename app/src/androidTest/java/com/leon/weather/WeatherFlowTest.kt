package com.leon.weather

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import com.leon.weather.data.repository.AppError
import com.leon.weather.data.repository.AppResult
import com.leon.weather.data.repository.LocationRepository
import com.leon.weather.data.repository.WeatherRepository
import com.leon.weather.data.repository.SearchHistoryRepository
import com.leon.weather.model.CurrentWeather
import com.leon.weather.model.DailyForecast
import com.leon.weather.model.HourlyForecast
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherCondition
import com.leon.weather.model.WeatherSnapshot
import com.leon.weather.ui.navigation.WeatherApp
import com.leon.weather.ui.theme.WeatherTheme
import java.time.Instant
import java.time.ZoneId
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val clock = MutableWeatherClock(Instant.parse("2026-10-28T08:30:00Z"))
    private val london = Location(2643743, "伦敦", "英格兰", "英国", 51.5085, -0.12574, "Europe/London")
    private val history = object : SearchHistoryRepository {
        override val locations = MutableStateFlow<List<Location>>(emptyList())
        override suspend fun record(location: Location) {
            locations.value = (listOf(location) + locations.value).distinctBy(Location::id).take(5)
        }
        override suspend fun remove(locationId: Long) {
            locations.value = locations.value.filterNot { it.id == locationId }
        }
        override suspend fun clear() { locations.value = emptyList() }
    }
    private val locations = object : LocationRepository {
        override suspend fun search(query: String) = AppResult.Success(if (query == "London") listOf(london) else emptyList())
    }

    @Test fun popularCityOpensWeatherAndBackReturnsToSearch() {
        render(weatherRepository())
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.onNodeWithText("18°C").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回搜索").performClick()
        rule.onNodeWithTag("city_search").assertIsDisplayed()
    }

    @Test fun englishSearchSelectsCorrectCityAndClearsQueryOnBack() {
        render(weatherRepository())
        rule.onNodeWithTag("city_search").performTextInput("London")
        rule.waitUntil(5000) { rule.onAllNodesWithText("伦敦").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("伦敦").performClick()
        rule.onNodeWithText("伦敦").assertIsDisplayed()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回搜索").performClick()
        assertSearchIsCleared()
    }

    @Test fun initialFailureCanBeRetried() {
        var requests = 0
        render(object : WeatherRepository {
            override suspend fun getWeather(location: Location) = if (++requests == 1) AppResult.Failure(AppError.Network) else AppResult.Success(snapshot(location))
        })
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithText("重试").performClick()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.runOnIdle { assertEquals("北京", history.locations.value.single().name) }
    }

    @Test fun refreshFailureKeepsExistingWeather() {
        var requests = 0
        render(object : WeatherRepository {
            override suspend fun getWeather(location: Location) = if (++requests == 1) AppResult.Success(snapshot(location)) else AppResult.Failure(AppError.Timeout)
        })
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithContentDescription("刷新天气").performClick()
        rule.onNodeWithText("更新失败，仍显示上次获取的数据").assertIsDisplayed()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
    }

    @Test fun savedNavigationRestoresSelectedCity() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { WeatherTheme { WeatherApp(locations, weatherRepository(), history, onExit = {}, clock = clock) } }
        rule.onNodeWithTag("city_search").performTextInput("London")
        rule.waitUntil(5000) { rule.onAllNodesWithText("伦敦").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("伦敦").performClick()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("伦敦").assertIsDisplayed()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回搜索").performClick()
        assertSearchIsCleared()
    }

    @Test fun hourlyPrecipitationDisplaysZeroNonzeroAndMissingSeparately() {
        render(weatherRepository())
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithTag("weather_content").performScrollToNode(hasTestTag("hourly_forecast"))
        rule.onNodeWithContentDescription("降水概率 0%").assertIsDisplayed()
        rule.onNodeWithContentDescription("降水概率 37%").assertIsDisplayed()
        rule.onNodeWithTag("hourly_forecast").performScrollToIndex(2)
        rule.onNodeWithContentDescription("降水概率 100%").assertIsDisplayed()
        rule.onNodeWithContentDescription("降水概率 —").assertIsDisplayed()
    }

    @Test fun homeBackRequiresTwoPresses() {
        var exits = 0
        render(weatherRepository(), onExit = { exits++ })
        pressSystemBack()
        rule.runOnIdle { assertEquals(0, exits) }
        rule.onNodeWithTag("search_screen").assertIsDisplayed()
        pressSystemBack()
        rule.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun leavingHomeResetsExitConfirmationAndDetailBackOnlyNavigates() {
        var exits = 0
        render(weatherRepository(), onExit = { exits++ })
        pressSystemBack()
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        pressSystemBack()
        rule.onNodeWithTag("search_screen").assertIsDisplayed()
        pressSystemBack()
        rule.runOnIdle { assertEquals(0, exits) }
        pressSystemBack()
        rule.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun backgroundingResetsExitConfirmation() {
        var exits = 0
        render(weatherRepository(), onExit = { exits++ })
        pressSystemBack()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithTag("search_screen").assertIsDisplayed()
        pressSystemBack()
        rule.runOnIdle { assertEquals(0, exits) }
        pressSystemBack()
        rule.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun selectingSearchResultCreatesAReusableHistoryEntry() {
        render(weatherRepository())
        rule.onNodeWithTag("city_search").performTextInput("London")
        rule.waitUntil(5000) { rule.onAllNodesWithText("伦敦").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("伦敦").performClick()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回搜索").performClick()
        rule.onNodeWithTag("history_${london.id}").assertIsDisplayed().performClick()
        rule.onNodeWithText("伦敦").assertIsDisplayed()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf(london), history.locations.value) }
    }

    @Test fun currentExtremesUvAndSevenDailyForecastsAreDisplayed() {
        render(weatherRepository())
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithTag("today_high").assertTextEquals("最高 25°C")
        rule.onNodeWithTag("today_low").assertTextEquals("最低 12°C")
        rule.onNodeWithText("2.8").assertIsDisplayed()
        rule.onNodeWithText("按当地时间显示 · 降水概率").assertDoesNotExist()
        rule.onNodeWithTag("weather_content").performScrollToNode(hasTestTag("daily_forecast"))
        (0L..6L).forEach {
            val date = java.time.LocalDate.of(2026, 10, 28).plusDays(it)
            rule.onNodeWithTag("day_$date").assertIsDisplayed()
        }
    }

    @Test fun historyDeletionStaysOnHomeAndClearAllKeepsPopularCities() {
        val cities = com.leon.weather.model.PopularLocations.all
        history.locations.value = listOf(london) + cities
        render(weatherRepository())
        rule.onNodeWithContentDescription("删除伦敦的历史记录").assertIsDisplayed().performClick()
        rule.onNodeWithTag("history_${london.id}").assertDoesNotExist()
        rule.onNodeWithTag("search_screen").assertIsDisplayed()
        rule.onNodeWithTag("current_weather").assertDoesNotExist()
        rule.runOnIdle { assertEquals(cities, history.locations.value) }
        rule.onNodeWithText("清空全部").performClick()
        rule.onNodeWithTag("search_history").assertDoesNotExist()
        rule.onNodeWithTag("clear_history").assertDoesNotExist()
        rule.onNodeWithText("最近搜索").assertDoesNotExist()
        rule.onNodeWithText("常用城市").assertIsDisplayed()
        rule.runOnIdle { assertEquals(emptyList<Location>(), history.locations.value) }
        rule.onNodeWithTag("location_${cities.first().id}").performClick()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回搜索").performClick()
        rule.onNodeWithTag("history_${cities.first().id}").assertIsDisplayed()
    }

    @Test fun returningToForegroundRefreshesOnlyWhenFifteenMinutesHaveElapsed() {
        var requests = 0
        render(object : WeatherRepository {
            override suspend fun getWeather(location: Location): AppResult<WeatherSnapshot> {
                requests++
                return AppResult.Success(snapshot(location))
            }
        })
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, requests) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        clock.now = clock.now.plusSeconds(899)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithTag("current_weather").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, requests) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        clock.now = clock.now.plusSeconds(1)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText("天气数据时间 10-28 16:45").assertIsDisplayed()
        rule.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun failedMidnightRefreshKeepsTimestampAndRelabelsForecastForTheNewLocalDay() {
        clock.now = Instant.parse("2026-10-28T15:59:00Z") // 23:59 in Beijing.
        var requests = 0
        render(object : WeatherRepository {
            override suspend fun getWeather(location: Location): AppResult<WeatherSnapshot> = if (++requests == 1) {
                val data = snapshot(location)
                AppResult.Success(data.copy(hourly = data.hourly.mapIndexed { index, hour ->
                    if (index == 0) hour.copy(time = clock.now.plusSeconds(30)) else hour
                }))
            } else AppResult.Failure(AppError.Network)
        })
        rule.onNodeWithText("北京").performClick()
        rule.onNodeWithTag("today_high").assertTextEquals("最高 25°C")
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        clock.now = clock.now.plusSeconds(120)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText("更新失败，仍显示上次获取的数据").assertIsDisplayed()
        rule.onNodeWithTag("refresh_error_time").assertTextEquals("天气数据时间 10-28 23:59")
        rule.onNodeWithTag("today_high").assertTextEquals("最高 26°C")
        rule.onNodeWithTag("weather_content").performScrollToNode(hasTestTag("hourly_forecast"))
        rule.onNodeWithText("23:59").assertDoesNotExist()
        rule.onNodeWithTag("weather_content").performScrollToNode(hasTestTag("daily_forecast"))
        rule.onNodeWithTag("day_2026-10-28").assertDoesNotExist()
        rule.onNodeWithTag("day_2026-10-29").assert(hasAnyDescendant(hasText("今天")))
        rule.onNodeWithTag("day_2026-10-30").assert(hasAnyDescendant(hasText("明天")))
        rule.runOnIdle { assertEquals(2, requests) }
    }

    private fun pressSystemBack() = rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }

    private fun assertSearchIsCleared() {
        rule.onNodeWithTag("city_search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithText("常用城市").assertIsDisplayed()
    }

    private fun render(weather: WeatherRepository, onExit: () -> Unit = {}) {
        rule.setContent { WeatherTheme { WeatherApp(locations, weather, history, onExit, clock) } }
    }
    private fun weatherRepository() = object : WeatherRepository {
        override suspend fun getWeather(location: Location) = AppResult.Success(snapshot(location))
    }
    private fun snapshot(location: Location): WeatherSnapshot {
        val now = clock.instant()
        return WeatherSnapshot(location, ZoneId.of(location.timeZone),
            CurrentWeather(now, 18.0, 17.0, 65, 2.0, WeatherCondition.Clear, true, uvIndex = 2.8),
            (1L..24L).map {
                val chance = when (it) { 1L -> 0; 2L -> 37; 3L -> 100; 4L -> null; else -> 20 }
                HourlyForecast(now.plusSeconds(it * 3600 - 1800), 19.0, chance, WeatherCondition.PartlyCloudy, true)
            }, now,
            daily = (0L..6L).map { DailyForecast(now.atZone(ZoneId.of(location.timeZone)).toLocalDate().plusDays(it), 25.0 + it, 12.0 + it, WeatherCondition.PartlyCloudy) })
    }
}

private class MutableWeatherClock(@Volatile var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = object : Clock() {
        override fun instant(): Instant = this@MutableWeatherClock.instant()
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = this@MutableWeatherClock.withZone(zone)
    }
}
