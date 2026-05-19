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
 * Phase 3 Stage FINAL part А — результаты прошедших пробников.
 *
 * Не использует attempt_log напрямую — пробник идёт **отдельной сущностью**
 * с фиксированным составом 8 math + 8 rus. Сохраняется агрегированный
 * результат (correct/total/score per subject + duration).
 *
 * Уникальность по plan_index: пользователь может «перепройти» пробник —
 * это будет НОВАЯ запись (не upsert). История хранит все попытки.
 */
@Entity(
    tableName = "mock_exam_results",
    indices = [Index("plan_index"), Index("completed_date")],
)
data class MockExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_index") val planIndex: Int,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String,
    @ColumnInfo(name = "completed_date") val completedDate: Long,
    @ColumnInfo(name = "math_correct") val mathCorrect: Int,
    @ColumnInfo(name = "math_total") val mathTotal: Int,
    @ColumnInfo(name = "rus_correct") val rusCorrect: Int,
    @ColumnInfo(name = "rus_total") val rusTotal: Int,
    @ColumnInfo(name = "math_score") val mathScore: Int,
    @ColumnInfo(name = "rus_score") val rusScore: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

@Serializable
data class MockExamResultRecord(
    val planIndex: Int,
    val scheduledDate: String,
    val completedDate: Long,
    val mathCorrect: Int,
    val mathTotal: Int,
    val rusCorrect: Int,
    val rusTotal: Int,
    val mathScore: Int,
    val rusScore: Int,
    val durationMs: Long,
) {
    fun toEntity() = MockExamResultEntity(
        planIndex = planIndex,
        scheduledDate = scheduledDate,
        completedDate = completedDate,
        mathCorrect = mathCorrect,
        mathTotal = mathTotal,
        rusCorrect = rusCorrect,
        rusTotal = rusTotal,
        mathScore = mathScore,
        rusScore = rusScore,
        durationMs = durationMs,
    )
    companion object {
        fun fromEntity(e: MockExamResultEntity) = MockExamResultRecord(
            planIndex = e.planIndex,
            scheduledDate = e.scheduledDate,
            completedDate = e.completedDate,
            mathCorrect = e.mathCorrect,
            mathTotal = e.mathTotal,
            rusCorrect = e.rusCorrect,
            rusTotal = e.rusTotal,
            mathScore = e.mathScore,
            rusScore = e.rusScore,
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

    /** Самый свежий результат по plan_index (если пользователь перепроходил — берём последний). */
    @Query("SELECT * FROM mock_exam_results WHERE plan_index = :idx ORDER BY completed_date DESC LIMIT 1")
    suspend fun getLatestByPlanIndex(idx: Int): MockExamResultEntity?

    @Query("SELECT COUNT(DISTINCT plan_index) FROM mock_exam_results")
    suspend fun getCompletedCount(): Int

    @Query("SELECT COUNT(DISTINCT plan_index) FROM mock_exam_results")
    fun observeCompletedCount(): Flow<Int>

    @Query("DELETE FROM mock_exam_results")
    suspend fun deleteAll()
}
