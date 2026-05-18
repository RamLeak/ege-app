package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Журнал слов из тренажёра №4, в которых пользователь хоть раз ошибся.
 *
 * Stage 3 polish: только пишем; UI-режим «только ошибки» придёт в Phase 3.
 * Тип хранения — Set<String> в DataStore Preferences (~1 KB на устройстве,
 * никаких сетевых вызовов).
 */
private val Context.accentDataStore by preferencesDataStore("accent_errors")

object AccentErrorsStore {
    private val KEY = stringSetPreferencesKey("wrong_words")

    fun errorsFlow(context: Context): Flow<Set<String>> =
        context.accentDataStore.data.map { it[KEY] ?: emptySet() }

    suspend fun recordError(context: Context, word: String) {
        context.accentDataStore.edit { prefs ->
            val cur = prefs[KEY] ?: emptySet()
            prefs[KEY] = cur + word
        }
    }

    suspend fun snapshot(context: Context): Set<String> =
        errorsFlow(context).first()

    /** Phase 3 Stage A part Д: восстановление из бэкапа. */
    suspend fun restore(context: Context, words: Set<String>) {
        context.accentDataStore.edit { it[KEY] = words }
    }

    /** Phase 3 Stage A part Д: сброс. */
    suspend fun clearAll(context: Context) {
        context.accentDataStore.edit { it.remove(KEY) }
    }
}
