package com.daniel.ege100.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.serialization.Serializable

/**
 * Phase 3 Stage C part А — отдельная пользовательская БД.
 *
 * **Важно**: НЕ часть corpus.db. corpus.db read-only, поставляется с APK
 * (192 MB, неизменный). Журнал ошибок и попыток должен расти и меняться —
 * требует writable RoomDatabase, отдельный файл `user_data.db` в
 * `context.dataDir/databases/`.
 *
 * **Foreign key к corpus.db нельзя** — это разные БД. Хранится только
 * `problem_id` как Long, целостность проверяется на уровне приложения.
 *
 * Версия 1: error_log + attempt_log. При добавлении полей в Phase 3 D+
 * (например MockExamScheduleEntity для уведомлений) — версия 2 + Migration.
 */

@Entity(
    tableName = "error_log",
    indices = [
        Index("problem_id"),
        Index("timestamp"),
    ],
)
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long,
    @ColumnInfo(name = "user_answer") val userAnswer: String,
    @ColumnInfo(name = "correct_answer") val correctAnswer: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "is_resolved") val isResolved: Boolean = false,
)

@Entity(
    tableName = "attempt_log",
    indices = [
        Index("timestamp"),
        Index("problem_id"),
        Index("subject"),
    ],
)
data class AttemptLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long?,  // null для тренажёров
    @ColumnInfo(name = "subject") val subject: String,      // "math" | "rus"
    @ColumnInfo(name = "type_number") val typeNumber: Int,
    @ColumnInfo(name = "subtype_id") val subtypeId: Long?,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** "problem" | "accent_trainer" | "wordblank_trainer" | "quick_trainer" */
    @ColumnInfo(name = "source") val source: String,
)

/** Сериализационная версия ErrorLogEntity для BackupSnapshot. */
@Serializable
data class ErrorLogRecord(
    val problemId: Long,
    val userAnswer: String,
    val correctAnswer: String,
    val timestamp: Long,
    val isResolved: Boolean,
) {
    fun toEntity(): ErrorLogEntity = ErrorLogEntity(
        problemId = problemId,
        userAnswer = userAnswer,
        correctAnswer = correctAnswer,
        timestamp = timestamp,
        isResolved = isResolved,
    )
    companion object {
        fun fromEntity(e: ErrorLogEntity) = ErrorLogRecord(
            problemId = e.problemId,
            userAnswer = e.userAnswer,
            correctAnswer = e.correctAnswer,
            timestamp = e.timestamp,
            isResolved = e.isResolved,
        )
    }
}

@Serializable
data class AttemptLogRecord(
    val problemId: Long?,
    val subject: String,
    val typeNumber: Int,
    val subtypeId: Long?,
    val isCorrect: Boolean,
    val durationMs: Long,
    val timestamp: Long,
    val source: String,
) {
    fun toEntity(): AttemptLogEntity = AttemptLogEntity(
        problemId = problemId,
        subject = subject,
        typeNumber = typeNumber,
        subtypeId = subtypeId,
        isCorrect = isCorrect,
        durationMs = durationMs,
        timestamp = timestamp,
        source = source,
    )
    companion object {
        fun fromEntity(e: AttemptLogEntity) = AttemptLogRecord(
            problemId = e.problemId,
            subject = e.subject,
            typeNumber = e.typeNumber,
            subtypeId = e.subtypeId,
            isCorrect = e.isCorrect,
            durationMs = e.durationMs,
            timestamp = e.timestamp,
            source = e.source,
        )
    }
}

@Database(
    entities = [ErrorLogEntity::class, AttemptLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun attemptLogDao(): AttemptLogDao

    companion object {
        @Volatile
        private var INSTANCE: UserDataDatabase? = null

        fun get(context: Context): UserDataDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserDataDatabase::class.java,
                    "user_data.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
