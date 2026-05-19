package com.daniel.ege100.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    entities = [
        ErrorLogEntity::class,
        AttemptLogEntity::class,
        MockExamResultEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun attemptLogDao(): AttemptLogDao
    abstract fun mockExamResultDao(): MockExamResultDao

    companion object {
        @Volatile
        private var INSTANCE: UserDataDatabase? = null

        /**
         * Phase 3 Stage FINAL: миграция 1→2 добавляет таблицу mock_exam_results.
         * DDL должен **точно** совпадать с тем, что Room сгенерирует для
         * `@Entity(MockExamResultEntity)` — иначе room схема-валидация
         * выкинет IllegalStateException на старте.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mock_exam_results` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `plan_index` INTEGER NOT NULL,
                        `scheduled_date` TEXT NOT NULL,
                        `completed_date` INTEGER NOT NULL,
                        `math_correct` INTEGER NOT NULL,
                        `math_total` INTEGER NOT NULL,
                        `rus_correct` INTEGER NOT NULL,
                        `rus_total` INTEGER NOT NULL,
                        `math_score` INTEGER NOT NULL,
                        `rus_score` INTEGER NOT NULL,
                        `duration_ms` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mock_exam_results_plan_index` ON `mock_exam_results` (`plan_index`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mock_exam_results_completed_date` ON `mock_exam_results` (`completed_date`)")
            }
        }

        fun get(context: Context): UserDataDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserDataDatabase::class.java,
                    "user_data.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
