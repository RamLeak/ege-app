package com.daniel.ege100.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3 Stage C part А — DAO для журнала всех попыток.
 *
 * Используется в:
 *   - StatsScreen (OverviewCard + ActivityChart за 30 дней + AchievementsRow).
 *   - JournalSummaryCard (сегодня / точность сегодня / всего).
 *   - CsvExporter — для экспорта `ege100_attempts_DATE.csv`.
 */
@Dao
interface AttemptLogDao {

    @Insert
    suspend fun insert(attempt: AttemptLogEntity): Long

    @Query("SELECT COUNT(*) FROM attempt_log")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attempt_log")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM attempt_log WHERE timestamp >= :sinceMs")
    suspend fun getCountSince(sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM attempt_log WHERE is_correct = 1 AND timestamp >= :sinceMs")
    suspend fun getCorrectCountSince(sinceMs: Long): Int

    /** Phase 3 Stage FINAL — для SafetyGuards (#5 50/week, #6 300/8weeks). */
    @Query("SELECT COUNT(*) FROM attempt_log WHERE timestamp >= :startMs AND timestamp < :endMs")
    suspend fun getCountBetween(startMs: Long, endMs: Long): Int

    @Query("SELECT AVG(duration_ms) FROM attempt_log WHERE duration_ms > 0")
    suspend fun getAverageDurationMs(): Double?

    /**
     * Группировка по дням за период (для ActivityChart за 30 дней).
     * `DATE(ts/1000, 'unixepoch', 'localtime')` — приводит UTC-millis к локальной дате.
     */
    @Query(
        """
        SELECT DATE(timestamp/1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS total,
               SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correct
        FROM attempt_log
        WHERE timestamp >= :sinceMs
        GROUP BY day
        ORDER BY day ASC
        """
    )
    suspend fun getDailyStats(sinceMs: Long): List<DailyStat>

    @Query("SELECT * FROM attempt_log ORDER BY timestamp DESC")
    suspend fun getAllForExport(): List<AttemptLogEntity>

    @Query("DELETE FROM attempt_log")
    suspend fun deleteAll()
}

data class DailyStat(
    val day: String,    // "2026-05-18"
    val total: Int,
    val correct: Int,
)
