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

    /**
     * Phase 4 Stage P4-D2 part А (Convention #64) — статусы последних попыток
     * для подсветки карточек в ProblemListScreen.
     *
     * Для каждого `problem_id` из списка возвращаем `is_correct` самой свежей
     * (по timestamp) попытки. JOIN c подзапросом MAX(timestamp) GROUP BY
     * problem_id. Тренажёрные attempts (problem_id IS NULL) отфильтрованы
     * через `problem_id IS NOT NULL`.
     *
     * Используется в ProblemListViewModel.load → строит Map<Long, Boolean>
     * → UI отображает зелёный/красный/серый фон карточки.
     */
    @Query(
        """
        SELECT al.problem_id AS problemId, al.is_correct AS isCorrect
        FROM attempt_log al
        INNER JOIN (
            SELECT problem_id, MAX(timestamp) AS max_ts
            FROM attempt_log
            WHERE problem_id IS NOT NULL AND problem_id IN (:problemIds)
            GROUP BY problem_id
        ) latest
        ON al.problem_id = latest.problem_id AND al.timestamp = latest.max_ts
        """
    )
    suspend fun getLastAttempts(problemIds: List<Long>): List<LastAttemptInfo>

    /**
     * Phase 4 Stage P4-D2 part Б (Convention #65) — для расчёта прогресса
     * типа/подвида. Возвращает problem_id'ы, у которых ПОСЛЕДНЯЯ попытка
     * правильная. Отсев по списку id'ов делает caller (ProgressRepository),
     * передавая problem_id'ы из corpus.db.
     */
    @Query(
        """
        SELECT al.problem_id AS problemId
        FROM attempt_log al
        INNER JOIN (
            SELECT problem_id, MAX(timestamp) AS max_ts
            FROM attempt_log
            WHERE problem_id IS NOT NULL AND problem_id IN (:problemIds)
            GROUP BY problem_id
        ) latest
        ON al.problem_id = latest.problem_id AND al.timestamp = latest.max_ts
        WHERE al.is_correct = 1
        """
    )
    suspend fun getLastCorrectIds(problemIds: List<Long>): List<ProblemIdRow>
}

data class DailyStat(
    val day: String,    // "2026-05-18"
    val total: Int,
    val correct: Int,
)

/**
 * Phase 4 Stage P4-D2 part А (Convention #64) — пара problem_id + последний
 * результат, для подсветки карточек.
 */
data class LastAttemptInfo(
    val problemId: Long,
    val isCorrect: Boolean,
)

data class ProblemIdRow(
    val problemId: Long,
)
