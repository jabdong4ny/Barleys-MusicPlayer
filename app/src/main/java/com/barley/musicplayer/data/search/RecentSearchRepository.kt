package com.barley.musicplayer.data.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentSearchRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val recentQueries: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[QUERIES_KEY]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[QUERIES_KEY]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }
            prefs[QUERIES_KEY] = updated.take(MAX_RECENT_QUERIES).joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(QUERIES_KEY) }
    }

    companion object {
        private val QUERIES_KEY = stringPreferencesKey("recent_search_queries")
        private const val SEPARATOR = "␟"
        private const val MAX_RECENT_QUERIES = 10
    }
}
