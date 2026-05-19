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
 * Конвенция #28: не часть corpus.db. corpus.db read-only, поставляется с APK
 * (192 MB, неизменный). Журнал ошибок и попыток должен расти и меняться —
 * требует writable RoomDatabase, отдельный файл `user_data.db` в
 * `context.dataDir/databases/`.
 *
 * **Foreign key к corpus.db нельзя** — это разные БД. Хранится только
 * `problem_id` как Long, целостность проверяется на уровне приложения.
 *
 * Версии:
 *   v1 (P3-C): error_log + attempt_log.
 *   v2 (P3-FINAL): + mock_exam_results (старая структура math+rus в одной строке).
 *   v3 (P4-A1): mock_exam_results перестроена — отдельная строка per subject.
 *                Старые данные конвертируются в Migration 2→3.
 *   v4 (P4-A5): + ai_response_cache.
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
    /** "problem" | "errors_retry" | "accent_trainer" | "wordblank_trainer" | "quick_trainer" | "mock_exam" */
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
        AiResponseCacheEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun attemptLogDao(): AttemptLogDao
    abstract fun mockExamResultDao(): MockExamResultDao
    abstract fun aiResponseCacheDao(): AiResponseCacheDao

    companion object {
        @Volatile
        private var INSTANCE: UserDataDatabase? = null

        /**
         * Phase 3 Stage FINAL: миграция 1→2 добавляет таблицу mock_exam_results
         * со старой структурой (math и rus в одной строке).
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

        /**
         * Phase 4 Stage A1: миграция 2→3 перестраивает mock_exam_results —
         * отдельная строка per subject + source. Каждая существующая строка
         * v2 (math + rus в одной) → одна или две строки v3 (только не-нулевые).
         *
         * Алгоритм:
         *   1. Создаём new_table с новой схемой.
         *   2. INSERT в new_table математических данных из mock_exam_results
         *      где math_total > 0.
         *   3. INSERT русских аналогично.
         *   4. DROP старой таблицы.
         *   5. RENAME new_table → mock_exam_results.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mock_exam_results_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `plan_index` INTEGER NOT NULL,
                        `subject` TEXT NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'internal',
                        `scheduled_date` TEXT NOT NULL,
                        `completed_date` INTEGER NOT NULL,
                        `correct` INTEGER NOT NULL,
                        `total` INTEGER NOT NULL,
                        `score` INTEGER NOT NULL,
                        `duration_ms` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                // Перенос математических данных.
                db.execSQL(
                    """
                    INSERT INTO mock_exam_results_new
                        (plan_index, subject, source, scheduled_date, completed_date,
                         correct, total, score, duration_ms)
                    SELECT plan_index, 'math', 'internal', scheduled_date, completed_date,
                           math_correct, math_total, math_score, duration_ms
                    FROM mock_exam_results
                    WHERE math_total > 0
                    """.trimIndent(),
                )
                // Перенос русских данных.
                db.execSQL(
                    """
                    INSERT INTO mock_exam_results_new
                        (plan_index, subject, source, scheduled_date, completed_date,
                         correct, total, score, duration_ms)
                    SELECT plan_index, 'rus', 'internal', scheduled_date, completed_date,
                           rus_correct, rus_total, rus_score, duration_ms
                    FROM mock_exam_results
                    WHERE rus_total > 0
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE mock_exam_results")
                db.execSQL("ALTER TABLE mock_exam_results_new RENAME TO mock_exam_results")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mock_exam_results_plan_index` ON `mock_exam_results` (`plan_index`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mock_exam_results_completed_date` ON `mock_exam_results` (`completed_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mock_exam_results_subject` ON `mock_exam_results` (`subject`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mock_exam_results_source` ON `mock_exam_results` (`source`)")
            }
        }

        /**
         * Phase 4 Stage A5: миграция 3→4 добавляет ai_response_cache.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_response_cache` (
                        `cache_key` TEXT NOT NULL,
                        `response` TEXT NOT NULL,
                        `cached_at` INTEGER NOT NULL,
                        PRIMARY KEY(`cache_key`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_response_cache_cached_at` ON `ai_response_cache` (`cached_at`)")
            }
        }

        fun get(context: Context): UserDataDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserDataDatabase::class.java,
                    "user_data.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
