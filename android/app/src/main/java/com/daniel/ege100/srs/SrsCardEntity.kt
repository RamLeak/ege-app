package com.daniel.ege100.srs

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.serialization.Serializable

/**
 * Phase 5 Stage E1 — SRS-карточка для интервальных повторений (SM-2).
 *
 * Живёт в `user_data.db` (не `corpus.db`): это пользовательские данные, должны
 * расти и меняться. corpus.db — read-only словарь, поставляется с APK.
 *
 * **Ключ** карточки: (word, kind, subtype). UNIQUE INDEX гарантирует
 * что одна и та же ошибка не создаст две карточки — `addCardOnMistake`
 * использует INSERT с OnConflictStrategy.IGNORE.
 *
 * `word` — точное слово (например "торты") или композитный ключ
 * для паронимов ("ОДЕЛ->надел"). Совпадает с ключом в `trainer_explanations`
 * (corpus.db) — оттуда тянутся тексты для front/back.
 */
@Entity(
    tableName = "srs_cards",
    indices = [
        Index(value = ["word", "kind", "subtype"], unique = true),
        Index("next_review_at"),
        Index(value = ["kind", "subtype"]),
    ],
)
data class SrsCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "subtype") val subtype: String,

    // SM-2 state
    @ColumnInfo(name = "ease_factor", defaultValue = "2.5") val easeFactor: Double = SrsAlgorithm.INITIAL_EF,
    @ColumnInfo(name = "interval_days", defaultValue = "1") val intervalDays: Int = 1,
    @ColumnInfo(name = "repetitions", defaultValue = "0") val repetitions: Int = 0,

    // timestamps (epoch millis)
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_review_at") val lastReviewAt: Long? = null,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long,

    // статистика
    @ColumnInfo(name = "total_reviews", defaultValue = "0") val totalReviews: Int = 0,
    @ColumnInfo(name = "total_lapses", defaultValue = "0") val totalLapses: Int = 0,
)

/**
 * Сериализационная версия для будущего BackupSnapshot (когда добавим SRS
 * в бэкап — Phase 5 carry-over). Сейчас в backup не входит, но структура
 * готова, чтобы не переписывать позже.
 */
@Serializable
data class SrsCardRecord(
    val word: String,
    val kind: String,
    val subtype: String,
    val easeFactor: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val createdAt: Long,
    val lastReviewAt: Long?,
    val nextReviewAt: Long,
    val totalReviews: Int,
    val totalLapses: Int,
)

@Dao
interface SrsCardDao {

    /**
     * Добавить карточку. При конфликте (карточка уже есть для этого
     * (word, kind, subtype)) — IGNORE: существующая запись не трогается,
     * её SM-2 state сохраняется. Возвращает rowId новой записи или -1
     * если был конфликт.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(card: SrsCardEntity): Long

    @Query("SELECT * FROM srs_cards WHERE id = :id")
    suspend fun getById(id: Long): SrsCardEntity?

    @Query("SELECT * FROM srs_cards WHERE word = :word AND kind = :kind AND subtype = :subtype LIMIT 1")
    suspend fun findByKey(word: String, kind: String, subtype: String): SrsCardEntity?

    /**
     * Карточки, у которых пришло время повторить.
     *
     * Сортировка: те, что просрочены давнее всех, идут первыми.
     * Лимит читается из настроек (SrsRepository.getDueCards).
     */
    @Query("SELECT * FROM srs_cards WHERE next_review_at <= :now ORDER BY next_review_at ASC LIMIT :limit")
    suspend fun getDueCards(now: Long, limit: Int): List<SrsCardEntity>

    @Query("SELECT COUNT(*) FROM srs_cards WHERE next_review_at <= :now")
    suspend fun countDue(now: Long): Int

    @Query("SELECT COUNT(*) FROM srs_cards")
    suspend fun countAll(): Int

    @Query(
        """
        UPDATE srs_cards SET
            ease_factor = :easeFactor,
            interval_days = :intervalDays,
            repetitions = :repetitions,
            last_review_at = :lastReviewAt,
            next_review_at = :nextReviewAt,
            total_reviews = total_reviews + 1,
            total_lapses = total_lapses + :lapseIncrement
        WHERE id = :id
        """,
    )
    suspend fun applyReview(
        id: Long,
        easeFactor: Double,
        intervalDays: Int,
        repetitions: Int,
        lastReviewAt: Long,
        nextReviewAt: Long,
        lapseIncrement: Int,
    )

    @Query("SELECT * FROM srs_cards")
    suspend fun getAll(): List<SrsCardEntity>

    @Query("DELETE FROM srs_cards")
    suspend fun deleteAll()

    @Query("DELETE FROM srs_cards WHERE id = :id")
    suspend fun deleteById(id: Long)
}
