package com.daniel.ege100.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Phase 4 Stage A5 — кеш AI-ответов.
 *
 * cacheKey = sha256("<provider>|<modelId>|<question>|<problemContext>")
 * (helper в AskAiViewModel). При повторном вопросе с тем же контекстом
 * берём из кеша — экономит API-токены и работает офлайн.
 *
 * TTL 30 дней. Cleanup в EgeApplication.onCreate (старые ключи удаляются).
 */
@Entity(
    tableName = "ai_response_cache",
    indices = [Index("cached_at")],
)
data class AiResponseCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "response") val response: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)

@Dao
interface AiResponseCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: AiResponseCacheEntity)

    @Query("SELECT response FROM ai_response_cache WHERE cache_key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("DELETE FROM ai_response_cache WHERE cached_at < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long)

    @Query("DELETE FROM ai_response_cache")
    suspend fun deleteAll()
}
