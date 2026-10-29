package com.leon.weather.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.leon.weather.model.PopularLocations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchHistoryRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `latest five unique cities retain order and survive reopening storage`() = runBlocking {
        val file = folder.newFolder().resolve("history.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repository = DataStoreSearchHistoryRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        val cities = (1L..6L).map { PopularLocations.all.first().copy(id = it, name = "城市$it") }
        try {
            cities.forEach { repository.record(it) }
            assertEquals(listOf(6L, 5L, 4L, 3L, 2L), repository.locations.first().map { it.id })
            repository.record(cities[2].copy(name = "更新的城市"))
            assertEquals(listOf(3L, 6L, 5L, 4L, 2L), repository.locations.first().map { it.id })
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
        val reopenedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val reopened = DataStoreSearchHistoryRepository(PreferenceDataStoreFactory.create(scope = reopenedScope) { file })
            val saved = reopened.locations.first()
            assertEquals(listOf(3L, 6L, 5L, 4L, 2L), saved.map { it.id })
            assertEquals(cities[2].copy(name = "更新的城市"), saved.first())
        } finally { reopenedScope.cancel(); reopenedScope.coroutineContext.job.join() }
    }

    @Test fun `corrupt JSON becomes empty history and a subsequent selection repairs it`() = runBlocking {
        val file = folder.newFolder().resolve("history.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit { it[stringPreferencesKey("recent_locations")] = "invalid json" }
            val repository = DataStoreSearchHistoryRepository(store)
            assertTrue(repository.locations.first().isEmpty())
            repository.record(PopularLocations.all.first())
            assertEquals(listOf(PopularLocations.all.first()), repository.locations.first())
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
    }

    @Test fun `concurrent selections do not overwrite each other`() = runBlocking {
        val file = folder.newFolder().resolve("history.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val repository = DataStoreSearchHistoryRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            val first = async { repository.record(PopularLocations.all[0]) }
            val second = async { repository.record(PopularLocations.all[1]) }
            first.await(); second.await()
            assertEquals(PopularLocations.all.take(2).map { it.id }.toSet(), repository.locations.first().map { it.id }.toSet())
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
    }

    @Test fun `remove targets city ID preserves order and persists across reopening`() = runBlocking {
        val file = folder.newFolder().resolve("history.preferences_pb")
        val cities = (1L..3L).map { PopularLocations.all.first().copy(id = it, name = "同名城市") }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val repository = DataStoreSearchHistoryRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            cities.forEach { repository.record(it) }
            repository.remove(2L)
            repository.remove(2L) // Repeated taps must be harmless.
            assertEquals(listOf(cities[2], cities[0]), repository.locations.first())
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
        val reopenedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val reopened = DataStoreSearchHistoryRepository(PreferenceDataStoreFactory.create(scope = reopenedScope) { file })
            assertEquals(listOf(cities[2], cities[0]), reopened.locations.first())
        } finally { reopenedScope.cancel(); reopenedScope.coroutineContext.job.join() }
    }

    @Test fun `clear persists without deleting other preferences and new selections can be recorded`() = runBlocking {
        val file = folder.newFolder().resolve("history.preferences_pb")
        val otherKey = stringPreferencesKey("unrelated_preference")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit { it[otherKey] = "keep" }
            val repository = DataStoreSearchHistoryRepository(store)
            PopularLocations.all.forEach { repository.record(it) }
            repository.clear()
            assertTrue(repository.locations.first().isEmpty())
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
        val reopenedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = reopenedScope) { file }
            val reopened = DataStoreSearchHistoryRepository(store)
            assertTrue(reopened.locations.first().isEmpty())
            assertEquals("keep", store.data.first()[otherKey])
            reopened.record(PopularLocations.all.first())
            assertEquals(listOf(PopularLocations.all.first()), reopened.locations.first())
        } finally { reopenedScope.cancel(); reopenedScope.coroutineContext.job.join() }
    }
}
