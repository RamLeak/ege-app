package com.daniel.ege100.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Stage 3: Room с 5 @Entity (Subject, ProblemType, ProblemSubtype, Problem, Solution).
 *
 * Asset лежит в `app/src/main/assets/corpus.db` (192 MB, gitignored). Room
 * при первом запуске копирует его в `databases/corpus.db` и инициализирует
 * `room_master_table` с identity-хешем нашей @Database.
 *
 * Версия бампается с 1 → 2 ради добавления SolutionEntity. На свежей установке
 * Room берёт asset как есть (таблица solutions уже там, integrity_check ok).
 * На устройстве, где уже стоит Stage 2 (v=1), запускается MIGRATION_1_2 —
 * она пустая, потому что schema добавлена только на стороне Room (Entity),
 * физическая таблица solutions уже существует в pre-packaged DB. После
 * migration Room валидирует схему и обновляет identity_hash в room_master_table.
 *
 * Все @Entity объявлены так, чтобы точно соответствовать DDL из build_db.py
 * (см. Entities.kt и CLAUDE.md §«Схема БД» + Convention #12).
 */
@Database(
    entities = [
        SubjectEntity::class,
        ProblemTypeEntity::class,
        ProblemSubtypeEntity::class,
        ProblemEntity::class,
        SolutionEntity::class,
        TrainerExplanationEntity::class,
    ],
    // Phase 4 Stage P4-D5 fix (Convention #86) — бамп с 3 до 4 БЕЗ migration,
    // чтобы fallbackToDestructiveMigration перетащил свежий corpus.db из asset'а.
    // Phase 4 Stage P4-D6 (Convention #91) — бамп с 4 до 5 БЕЗ migration: в asset
    // добавлено ещё +60 pre-gen объяснений (304 → 364).
    // Phase 4 Stage P4-D6 ночная сессия (Convention #91) — бамп с 5 до 6 БЕЗ
    // migration: pre-gen вырос до 573 (+209 за ночь): math полностью закрыт
    // (37), paronyms 28, pleonasms 27, t12 30, plus добавления в t10/t11.
    // Тот же destructive-recreate паттерн.
    version = 6,
    exportSchema = true,
)
abstract class EgeDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao
    abstract fun trainerExplanationDao(): TrainerExplanationDao

    companion object {
        private const val DB_NAME = "corpus.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Stage 2 → Stage 3: добавили SolutionEntity. Физическая таблица
                // `solutions` уже присутствует в pre-packaged corpus.db (build_db.py
                // создаёт её с самого начала), поэтому никаких ALTER не нужно.
                // Room после migration сам обновит identity_hash в room_master_table.
            }
        }

        /**
         * Phase 4 Stage P4-D — добавили TrainerExplanationEntity. Таблица
         * trainer_explanations создана скриптом `save_explanations_batch.py` в
         * pre-packaged corpus.db, поэтому DDL/ALTER не нужны — Room просто
         * валидирует схему и обновит identity_hash.
         *
         * Если устройство стоит на v=2 без таблицы (старый asset) — создаём её
         * на месте. Тогда pre-gen объяснений не будет, но fallback на онлайн AI
         * сохраняет функциональность.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trainer_explanations (
                        id INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        subtype TEXT NOT NULL,
                        explanation TEXT,
                        rule TEXT,
                        examples TEXT,
                        mnemonic TEXT,
                        generated_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id),
                        UNIQUE(word, kind, subtype)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_explanations_lookup ON trainer_explanations(word, kind)")
            }
        }

        @Volatile
        private var INSTANCE: EgeDatabase? = null

        fun get(context: Context): EgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): EgeDatabase {
            // Phase 4 Stage P4-D2 hotfix (init-краш): fallbackToDestructiveMigration
            // как страховка. Если схема не сходится по какой-то новой причине —
            // Room сотрёт локальную копию и пересоздаст её. На createFromAsset это
            // безопасно: после destroy Room снова копирует asset (read-only, в APK).
            // Пользовательские данные хранятся в `user_data.db` (UserDataDatabase),
            // не здесь, поэтому destructive reset не теряет прогресс.
            return Room.databaseBuilder(
                context.applicationContext,
                EgeDatabase::class.java,
                DB_NAME,
            )
                .createFromAsset(DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
