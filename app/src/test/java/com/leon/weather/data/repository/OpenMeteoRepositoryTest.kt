package com.leon.weather.data.repository

import com.leon.weather.data.remote.ForecastApi
import com.leon.weather.data.remote.GeocodingApi
import com.leon.weather.data.remote.LocationsDto
import com.leon.weather.model.PopularLocations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OpenMeteoRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit
    @Before fun setup() {
        server = MockWebServer()
        server.start()
        retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    }
    @After fun teardown() { server.shutdown() }

    @Test fun `no results field is a valid empty search response`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"generationtime_ms":0.1}"""))
        val result = OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java)).search("UnknownCity")
        assertEquals(AppResult.Success(emptyList<Nothing>()), result)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("UnknownCity", request.requestUrl!!.queryParameter("name"))
        assertEquals("zh", request.requestUrl!!.queryParameter("language"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `empty Xiamen search retries with Chinese city suffix`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"results":[{"id":1790645,"name":"厦门市","latitude":24.47979,"longitude":118.08187,"admin1":"福建省","country":"中国","timezone":"Asia/Shanghai"}]}"""))
        val result = OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java)).search(" 厦门 ")
        val city = (result as AppResult.Success).value.single()
        assertEquals(1790645L, city.id)
        assertEquals("福建省", city.region)
        assertEquals("厦门", server.takeRequest().requestUrl!!.queryParameter("name"))
        assertEquals("厦门市", server.takeRequest().requestUrl!!.queryParameter("name"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `empty full city name retries without suffix`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"results":[{"id":1808926,"name":"杭州","latitude":30.29365,"longitude":120.16142}]}"""))
        val result = OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java)).search("杭州市")
        assertEquals("杭州", (result as AppResult.Success).value.single().name)
        assertEquals("杭州市", server.takeRequest().requestUrl!!.queryParameter("name"))
        assertEquals("杭州", server.takeRequest().requestUrl!!.queryParameter("name"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `successful Chinese search does not issue a fallback request`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[{"id":1816670,"name":"北京","latitude":39.9,"longitude":116.4}]}"""))
        val result = OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java)).search("北京")
        assertEquals("北京", (result as AppResult.Success).value.single().name)
        assertEquals(1, server.requestCount)
    }

    @Test fun `unmatched Chinese city stops after one alternative`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody("{}")) }
        val result = OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java)).search("不存在的城市")
        assertEquals(AppResult.Success(emptyList<Nothing>()), result)
        assertEquals(2, server.requestCount)
    }

    @Test fun `fallback server failure remains an error instead of an empty result`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(503))
        val result = OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java)).search("厦门")
        assertEquals(AppResult.Failure(AppError.ServiceUnavailable), result)
        assertEquals(2, server.requestCount)
    }

    @Test fun `rate limit and server errors are classified separately`() = runBlocking {
        val repository = OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java))
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(AppResult.Failure(AppError.RateLimited), repository.search("北京"))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(AppResult.Failure(AppError.ServiceUnavailable), repository.search("北京"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `malformed payload returns data error rather than crashing`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":"invalid"}"""))
        assertEquals(AppResult.Failure(AppError.InvalidData), OpenMeteoLocationRepository(retrofit.create(GeocodingApi::class.java)).search("北京"))
    }

    @Test fun `forecast request fixes units range and timestamp format`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"timezone":"Asia/Shanghai","current":{"time":1793174400,"temperature_2m":18.0},"hourly":{"time":[1793178000],"temperature_2m":[19.0]},"new_server_field":true}"""))
        val repository = OpenMeteoWeatherRepository(retrofit.create(ForecastApi::class.java), Clock.fixed(Instant.ofEpochSecond(1793174400), ZoneOffset.UTC))
        val result = repository.getWeather(PopularLocations.all.first())
        assertTrue(result is AppResult.Success)
        assertEquals(19.0, (result as AppResult.Success).value.hourly.single().temperature)
        val url = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals("25", url.queryParameter("forecast_hours"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertEquals("ms", url.queryParameter("wind_speed_unit"))
        assertEquals("celsius", url.queryParameter("temperature_unit"))
        assertEquals("auto", url.queryParameter("timezone"))
        assertTrue(url.queryParameter("hourly")!!.split(',').contains("precipitation_probability"))
        assertTrue(url.queryParameter("hourly")!!.split(',').contains("uv_index"))
        assertEquals("7", url.queryParameter("forecast_days"))
        assertEquals("weather_code,temperature_2m_max,temperature_2m_min", url.queryParameter("daily"))
    }

    @Test fun `daily temperatures and hourly UV deserialize into current city weather`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"timezone":"Asia/Shanghai","current":{"time":1793174400},"hourly":{"time":[1793174400,1793178000],"uv_index":[2.5,3.8]},"daily":{"time":[1793116800],"temperature_2m_max":[22.0],"temperature_2m_min":[10.0],"weather_code":[2]}}"""))
        val repository = OpenMeteoWeatherRepository(retrofit.create(ForecastApi::class.java), Clock.fixed(Instant.ofEpochSecond(1793174400), ZoneOffset.UTC))
        val result = repository.getWeather(PopularLocations.all.first()) as AppResult.Success
        assertEquals(2.5, result.value.current.uvIndex)
        assertEquals(22.0, result.value.today!!.maximumTemperature)
        assertEquals(10.0, result.value.today!!.minimumTemperature)
    }

    @Test fun `hourly precipitation keeps zero nonzero and missing values from JSON`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"timezone":"Asia/Shanghai","current":{"time":1793174400},"hourly":{"time":[1793178000,1793181600,1793185200,1793188800],"precipitation_probability":[0,37,100,null]}}"""))
        val repository = OpenMeteoWeatherRepository(retrofit.create(ForecastApi::class.java), Clock.fixed(Instant.ofEpochSecond(1793174400), ZoneOffset.UTC))
        val result = repository.getWeather(PopularLocations.all.first()) as AppResult.Success
        assertEquals(listOf(0, 37, 100, null), result.value.hourly.map { it.precipitationProbability })
    }

    @Test fun `Miami API response retains all seven dates and aligned values across fall DST`() = runBlocking {
        // Real response captured on 2026-10-29; all daily boundaries retain UTC-4.
        server.enqueue(MockResponse().setBody(javaClass.getResource("/forecast-miami-dst.json")!!.readText()))
        val repository = OpenMeteoWeatherRepository(retrofit.create(ForecastApi::class.java),
            Clock.fixed(Instant.parse("2026-10-29T04:33:00Z"), ZoneOffset.UTC))
        val miami = PopularLocations.all.first().copy(id = 4164138, name = "迈阿密", latitude = 25.7743,
            longitude = -80.1937, region = "佛罗里达州", country = "美国", timeZone = "America/New_York")
        val weather = (repository.getWeather(miami) as AppResult.Success).value
        assertEquals((0L..6L).map { java.time.LocalDate.of(2026, 10, 29).plusDays(it) }, weather.daily.map { it.date })
        assertEquals(listOf(28.4, 28.2, 28.1, 28.2, 28.6, 28.7, 28.5), weather.daily.map { it.maximumTemperature })
        assertEquals(listOf(22.8, 22.8, 26.5, 26.1, 26.1, 26.3, 26.2), weather.daily.map { it.minimumTemperature })
        assertEquals(28.4, weather.today!!.maximumTemperature)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation during city fallback is propagated`() {
        runBlocking {
            var calls = 0
            val api = object : GeocodingApi {
                override suspend fun search(query: String, count: Int, language: String): LocationsDto {
                    if (++calls == 1) return LocationsDto()
                    throw CancellationException()
                }
            }
            OpenMeteoLocationRepository(api).search("厦门")
        }
    }

    @Test fun `missing current weather returns data error`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"timezone":"Asia/Shanghai"}"""))
        val repository = OpenMeteoWeatherRepository(retrofit.create(ForecastApi::class.java), Clock.systemUTC())
        assertEquals(AppResult.Failure(AppError.InvalidData), repository.getWeather(PopularLocations.all.first()))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not turned into a UI error`() {
        runBlocking {
            val api = object : GeocodingApi {
                override suspend fun search(query: String, count: Int, language: String): LocationsDto = throw CancellationException()
            }
            OpenMeteoLocationRepository(api).search("北京")
        }
    }
}
