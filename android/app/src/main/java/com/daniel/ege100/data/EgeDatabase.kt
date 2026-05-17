package com.daniel.ege100.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Stage 1: ручное подключение corpus.db.
 *
 * corpus.db лежит в assets/. При первом запуске копируем её в
 * `getDatabasePath("corpus.db")` (внутренняя память приложения, ~200 MB)
 * и открываем readonly через `SQLiteDatabase.openDatabase`.
 *
 * Почему не Room на этом этапе:
 *   corpus.db собрана `parser/build_db.py` со своим набором колонок и
 *   индексов (см. CLAUDE.md §«Схема БД»). Room строго валидирует, что
 *   все @Entity точно совпадают с таблицами, иначе бросает
 *   IllegalStateException при открытии. Объявлять все 10+ Entity ради
 *   одного `SELECT COUNT(*)` в Stage 1 — преждевременно. Полноценные
 *   Entity/Dao с матчингом существующей схемы появятся в Stage 2.
 *
 * Зависимость `androidx.room:room-runtime` уже подключена в build.gradle —
 * она ждёт Stage 2.
 */
object CorpusDb {
    private const val DB_NAME = "corpus.db"
    private const val COPY_BUFFER_BYTES = 8 * 1024

    @Volatile private var cached: SQLiteDatabase? = null

    fun open(context: Context): SQLiteDatabase {
        cached?.let { if (it.isOpen) return it }
        synchronized(this) {
            cached?.let { if (it.isOpen) return it }
            val dst = context.getDatabasePath(DB_NAME)
            if (!dst.exists() || dst.length() == 0L) {
                copyFromAssets(context, dst)
            }
            val db = SQLiteDatabase.openDatabase(
                dst.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            cached = db
            return db
        }
    }

    private fun copyFromAssets(context: Context, dst: File) {
        dst.parentFile?.mkdirs()
        context.assets.open(DB_NAME).use { input ->
            dst.outputStream().use { output ->
                val buf = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                }
            }
        }
    }
}
