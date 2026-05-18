package com.daniel.ege100.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3 Stage C part А — DAO для журнала ошибок.
 *
 * `getErrorsWithProblems()` использует JOIN с corpus.db через ATTACH? Нет —
 * Room не умеет cross-database join. Поэтому возвращаем сырые ErrorLogEntity,
 * а данные задачи (statement, type) подтягиваем отдельно через CatalogDao
 * в ViewModel'е (см. ErrorsListViewModel.refresh).
 */
@Dao
interface ErrorLogDao {

    @Insert
    suspend fun insert(error: ErrorLogEntity): Long

    @Query("SELECT * FROM error_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ErrorLogEntity>>

    @Query("SELECT * FROM error_log ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int, offset: Int = 0): List<ErrorLogEntity>

    @Query("SELECT * FROM error_log WHERE is_resolved = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getUnresolved(limit: Int, offset: Int = 0): List<ErrorLogEntity>

    @Query("SELECT * FROM error_log WHERE timestamp >= :sinceMs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSince(sinceMs: Long, limit: Int): List<ErrorLogEntity>

    @Query("SELECT COUNT(*) FROM error_log")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM error_log WHERE is_resolved = 0")
    fun observeUnresolvedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM error_log")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM error_log WHERE is_resolved = 0")
    suspend fun getUnresolvedCount(): Int

    @Query("UPDATE error_log SET is_resolved = 1 WHERE id = :errorId")
    suspend fun markResolved(errorId: Long)

    /** Phase 3 Stage C: при «Перерешать» — отметить **все** записи по этой задаче как resolved. */
    @Query("UPDATE error_log SET is_resolved = 1 WHERE problem_id = :problemId AND is_resolved = 0")
    suspend fun markAllResolvedFor(problemId: Long): Int

    @Query("DELETE FROM error_log WHERE id = :errorId")
    suspend fun delete(errorId: Long)

    @Query("DELETE FROM error_log")
    suspend fun deleteAll()

    @Query("SELECT * FROM error_log")
    suspend fun getAllForExport(): List<ErrorLogEntity>
}
