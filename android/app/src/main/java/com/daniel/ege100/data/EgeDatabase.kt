package com.daniel.ege100.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Stage 2: полноценное подключение `corpus.db` через Room.createFromAsset.
 *
 * Asset лежит в `app/src/main/assets/corpus.db` (192 MB, gitignored). Room
 * при первом запуске копирует его в `databases/corpus.db` и инициализирует
 * `room_master_table` с identity-хешем нашей @Database. На последующих
 * запусках открывается мгновенно.
 *
 * Все @Entity объявлены так, чтобы точно соответствовать DDL из build_db.py
 * (см. Entities.kt и CLAUDE.md §«Схема БД»). При расхождении Room выбросит
 * IllegalStateException при open — это ожидаемое поведение.
 *
 * `exportSchema = false` сознательно — мы не управляем версионированием
 * через Room-миграции, БД пересобирается build_db.py отдельно.
 */
@Database(
    entities = [
        SubjectEntity::class,
        ProblemTypeEntity::class,
        ProblemSubtypeEntity::class,
        ProblemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EgeDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao

    companion object {
        private const val DB_NAME = "corpus.db"

        @Volatile
        private var INSTANCE: EgeDatabase? = null

        fun get(context: Context): EgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): EgeDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EgeDatabase::class.java,
                DB_NAME,
            )
                .createFromAsset(DB_NAME)
                .build()
        }
    }
}
