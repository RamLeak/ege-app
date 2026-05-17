package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Тонкая обёртка над corpus.db для Stage 1: один запрос — количество задач.
 *
 * В Stage 2 этот файл будет переписан под Room-Dao и сущности.
 */
object CorpusStats {

    suspend fun countProblems(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val db = CorpusDb.open(context)
            db.rawQuery("SELECT COUNT(*) FROM problems", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
    }
}
