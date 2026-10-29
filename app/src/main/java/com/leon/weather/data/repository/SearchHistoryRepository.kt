package com.leon.weather.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.leon.weather.model.Location
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface SearchHistoryRepository {
    val locations: Flow<List<Location>>
    suspend fun record(location: Location)
    suspend fun remove(locationId: Long)
    suspend fun clear()
}

class DataStoreSearchHistoryRepository(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SearchHistoryRepository {
    override val locations = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { decode(it[RECENT_LOCATIONS]) }.distinctUntilChanged()

    override suspend fun record(location: Location) {
        store.edit { preferences ->
            val updated = (listOf(location) + decode(preferences[RECENT_LOCATIONS]))
                .distinctBy(Location::id).take(5)
            preferences[RECENT_LOCATIONS] = json.encodeToString(updated)
        }
    }

    override suspend fun remove(locationId: Long) {
        store.edit { preferences ->
            val remaining = decode(preferences[RECENT_LOCATIONS]).filterNot { it.id == locationId }
            preferences[RECENT_LOCATIONS] = json.encodeToString(remaining)
        }
    }

    override suspend fun clear() {
        store.edit { it.remove(RECENT_LOCATIONS) }
    }

    private fun decode(value: String?): List<Location> {
        if (value == null) return emptyList()
        return try {
            json.decodeFromString<List<Location>>(value).filter {
                it.name.isNotBlank() && it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
                    it.longitude.isFinite() && it.longitude in -180.0..180.0
            }.distinctBy(Location::id).take(5)
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private companion object { val RECENT_LOCATIONS = stringPreferencesKey("recent_locations") }
}
