package com.leon.weather.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.DateTimeException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

enum class AppError { Network, Timeout, RateLimited, ServiceUnavailable, InvalidData }

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

internal class InvalidWeatherData : Exception()

internal suspend fun <T> apiResult(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: SocketTimeoutException) {
    AppResult.Failure(AppError.Timeout)
} catch (http: HttpException) {
    AppResult.Failure(if (http.code() == 429) AppError.RateLimited else AppError.ServiceUnavailable)
} catch (_: IOException) {
    AppResult.Failure(AppError.Network)
} catch (_: SerializationException) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: InvalidWeatherData) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: DateTimeException) {
    AppResult.Failure(AppError.InvalidData)
}
