package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Stage 5 part Д — избранное.
 *
 * DataStore Preferences хранит Set<String> с problem_id (как строки).
 * При тапе на звезду в шапке задачи — toggle. В Журнале — отдельный экран
 * со списком отмеченных.
 */
private val Context.favoritesStore by preferencesDataStore("favorites")

object FavoritesStore {
    private val KEY = stringSetPreferencesKey("favorite_problem_ids")

    fun favoritesFlow(context: Context): Flow<Set<Long>> =
        context.favoritesStore.data.map { prefs ->
            (prefs[KEY] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        }

    fun isFavorite(context: Context, problemId: Long): Flow<Boolean> =
        context.favoritesStore.data.map { (it[KEY] ?: emptySet()).contains(problemId.toString()) }

    suspend fun toggle(context: Context, problemId: Long) {
        context.favoritesStore.edit { prefs ->
            val key = problemId.toString()
            val cur = prefs[KEY] ?: emptySet()
            prefs[KEY] = if (key in cur) cur - key else cur + key
        }
    }

    suspend fun snapshot(context: Context): Set<Long> =
        favoritesFlow(context).first()
}
