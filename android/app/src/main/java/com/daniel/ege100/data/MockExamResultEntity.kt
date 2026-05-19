package com.daniel.ege100.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Phase 3 Stage FINAL part А + Phase 4 Stage A1 — результаты пробников.
 *
 * v2 (P3-FINAL) — math + rus в одной строке (mathCorrect/Total/Score +
 * rusCorrect/Total/Score). Стало неудобно после A1 (отдельный пробник
 * только math или только rus).
 *
 * v3 (P4-A1) — отдельная строка per subject. `subject` ∈ {"math", "rus"}.
 * Старые строки v2 в Migration конвертируются: каждая существующая
 * строка с math+rus → две строки (отдельно math и отдельно rus). Если
 * total = 0 для одного из subjects (например, пользователь пропустил
 * предмет в P3-FINAL UI — не должно было быть, но защитимся) — такая
 * строка не создаётся.
 *
 * `source` ∈ {"internal", "fipi"} (для B1) — пробник через MockExamSchedule
 * vs ФИПИ-вариант. По умолчанию "internal".
 */
@Entity(
    tableName = "mock_exam_results",
    indices = [Index("plan_index"), Index("completed_date"), Index("subject"), Index("source")],
)
data class MockExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_index") val planIndex: Int,
    @ColumnInfo(name = "subject") val subject: String,           // "math" | "rus"
    @ColumnInfo(name = "source") val source: String = "internal",
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String,
    @ColumnInfo(name = "completed_date") val completedDate: Long,
    @ColumnInfo(name = "correct") val correct: Int,
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "score") val score: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

/**
 * Все поля с default-значениями — для backward-compat при импорте бэкапов
 * v1.3 (старая структура mathCorrect+mathTotal+rusCorrect+rusTotal в одной
 * записи). При парсинге через ignoreUnknownKeys=true старые поля будут
 * проигнорированы, а недостающие новые получат default. Такие записи
 * фильтруются на applyBackup (total=0 пропускаем).
 */
@Serializable
data class MockExamResultRecord(
    val planIndex: Int = -1,
    val subject: String = "math",
    val source: String = "internal",
    val scheduledDate: String = "",
    val completedDate: Long = 0L,
    val correct: Int = 0,
    val total: Int = 0,
    val score: Int = 0,
    val durationMs: Long = 0L,
) {
    fun toEntity() = MockExamResultEntity(
        planIndex = planIndex,
        subject = subject,
        source = source,
        scheduledDate = scheduledDate,
        completedDate = completedDate,
        correct = correct,
        total = total,
        score = score,
        durationMs = durationMs,
    )
    companion object {
        fun fromEntity(e: MockExamResultEntity) = MockExamResultRecord(
            planIndex = e.planIndex,
            subject = e.subject,
            source = e.source,
            scheduledDate = e.scheduledDate,
            completedDate = e.completedDate,
            correct = e.correct,
            total = e.total,
            score = e.score,
            durationMs = e.durationMs,
        )
    }
}

@Dao
interface MockExamResultDao {
    @Insert
    suspend fun insert(result: MockExamResultEntity): Long

    @Query("SELECT * FROM mock_exam_results ORDER BY completed_date DESC")
    fun observeAll(): Flow<List<MockExamResultEntity>>

    @Query("SELECT * FROM mock_exam_results ORDER BY completed_date DESC")
    suspend fun getAll(): List<MockExamResultEntity>

    /** Phase 4 A1 — самый свежий по plan + subject (если пользователь перепроходит — последний). */
    @Query(
        """
        SELECT * FROM mock_exam_results
        WHERE plan_index = :idx AND subject = :subject
        ORDER BY completed_date DESC LIMIT 1
        """
    )
    suspend fun getLatestByPlanAndSubject(idx: Int, subject: String): MockExamResultEntity?

    /** Phase 4 A1 — все результаты для конкретного plan_index (для MockExamCard в календаре). */
    @Query("SELECT * FROM mock_exam_results WHERE plan_index = :idx ORDER BY completed_date DESC")
    suspend fun getAllByPlanIndex(idx: Int): List<MockExamResultEntity>

    /** Phase 4 B2 — результаты конкретного subject для тренда. */
    @Query("SELECT * FROM mock_exam_results WHERE subject = :subject ORDER BY completed_date ASC")
    suspend fun getAllBySubject(subject: String): List<MockExamResultEntity>

    @Query("SELECT COUNT(DISTINCT plan_index) FROM mock_exam_results WHERE plan_index >= 0")
    suspend fun getCompletedCount(): Int

    @Query("SELECT COUNT(DISTINCT plan_index) FROM mock_exam_results WHERE plan_index >= 0")
    fun observeCompletedCount(): Flow<Int>

    @Query("DELETE FROM mock_exam_results")
    suspend fun deleteAll()
}
