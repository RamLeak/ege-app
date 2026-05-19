package com.daniel.ege100.data

import androidx.room.Dao
import androidx.room.Query

/**
 * Phase 4 Stage P4-D (Convention #71) — read-only доступ к pre-gen объяснениям.
 *
 * `kind` обычно: "accent" | "word_blank" | "paronym" | "pleonasm" | "grammar" | "math".
 * `word` — точное слово (например "торты") или композитный ключ (например "ОДЕЛ->надел").
 * `subtype` — узкая категория (nouns, t9, trig, ...).
 *
 * Если по `(word, kind)` нашлась запись с непустым explanation — pre-gen.
 * Если null — ExplanationViewModel идёт в fallback на онлайн AI.
 */
@Dao
interface TrainerExplanationDao {

    /**
     * Точное совпадение по слову + типу тренажёра. Возвращает первую запись,
     * если для одного word существуют разные subtype (например omoформы).
     */
    @Query("SELECT * FROM trainer_explanations WHERE word = :word AND kind = :kind LIMIT 1")
    suspend fun get(word: String, kind: String): TrainerExplanationEntity?

    /** Расширенный вариант когда нужна точная подкатегория. */
    @Query("SELECT * FROM trainer_explanations WHERE word = :word AND kind = :kind AND subtype = :subtype LIMIT 1")
    suspend fun getExact(word: String, kind: String, subtype: String): TrainerExplanationEntity?

    @Query("SELECT COUNT(*) FROM trainer_explanations WHERE kind = :kind")
    suspend fun countByKind(kind: String): Int

    @Query("SELECT COUNT(*) FROM trainer_explanations")
    suspend fun countAll(): Int
}
