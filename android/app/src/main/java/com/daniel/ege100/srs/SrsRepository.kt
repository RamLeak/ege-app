package com.daniel.ege100.srs

import android.content.Context
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.UserDataDatabase

/**
 * Phase 5 Stage E1 — фасад над SrsCardDao + TrainerExplanationDao.
 *
 * `SrsRepository` НЕ создаёт собственные БД-инстансы — берёт singleton'ы
 * из `UserDataDatabase.get()` (карточки) и `EgeDatabase.get()` (тексты
 * объяснений). Cross-database JOIN недоступен в Room, поэтому fetch
 * объяснений идёт отдельным запросом в `getTextsForCard`.
 *
 * Все методы suspend — вызываются из viewModelScope/Dispatchers.IO в
 * репозитории вызывающего тренажёра.
 */
object SrsRepository {

    /**
     * Тексты для front/back карточки. Source-of-truth — таблица
     * `trainer_explanations` в corpus.db (3186 строк после P5-D2).
     *
     * Если по (word, kind) в БД нет записи (например, ошибка пришла на
     * слово без pre-gen) — все 4 поля будут null. UI должен корректно
     * показать «объяснение не найдено» / fallback на онлайн AI.
     */
    data class CardTexts(
        val explanation: String?,
        val rule: String?,
        val examples: String?,
        val mnemonic: String?,
    ) {
        val isEmpty: Boolean get() =
            explanation.isNullOrBlank() &&
                rule.isNullOrBlank() &&
                examples.isNullOrBlank() &&
                mnemonic.isNullOrBlank()
    }

    /**
     * Создать карточку при ошибке в тренажёре. Идемпотентно: если карточка
     * для (word, kind, subtype) уже есть — ничего не меняем (её SM-2 state
     * сохраняется, чтобы повторная ошибка не сбрасывала прогресс).
     *
     * Возвращает id новой карточки, либо id существующей если был конфликт,
     * либо null если что-то пошло не так.
     */
    suspend fun addCardOnMistake(
        context: Context,
        word: String,
        kind: String,
        subtype: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        if (word.isBlank() || kind.isBlank() || subtype.isBlank()) return null
        val dao = UserDataDatabase.get(context).srsCardDao()

        val card = SrsCardEntity(
            word = word,
            kind = kind,
            subtype = subtype,
            createdAt = nowMillis,
            nextReviewAt = nowMillis,
        )
        val inserted = dao.insertIgnore(card)
        return if (inserted != -1L) {
            inserted
        } else {
            dao.findByKey(word, kind, subtype)?.id
        }
    }

    /**
     * Карточки, которые надо повторить сегодня (next_review_at <= now).
     *
     * @param limit берётся из настроек (default 50, см. AppSettings.srsDailyLimit).
     */
    suspend fun getDueCards(
        context: Context,
        limit: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<SrsCardEntity> {
        return UserDataDatabase.get(context).srsCardDao().getDueCards(nowMillis, limit)
    }

    /** Сколько карточек сегодня на повторение — для бейджа на главном экране. */
    suspend fun countDueToday(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        return UserDataDatabase.get(context).srsCardDao().countDue(nowMillis)
    }

    /**
     * Применить оценку пользователя к карточке. Внутри:
     *   1. Тянем актуальное состояние карточки (на случай если её обновили
     *      параллельно).
     *   2. Считаем новый SM-2 state.
     *   3. UPDATE строки.
     *   4. Возвращаем обновлённую карточку.
     *
     * @param grade одна из 0 / 3 / 4 / 5 (валидируется внутри SrsAlgorithm).
     */
    suspend fun submitReview(
        context: Context,
        cardId: Long,
        grade: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): SrsCardEntity? {
        val dao = UserDataDatabase.get(context).srsCardDao()
        val current = dao.getById(cardId) ?: return null
        val result = SrsAlgorithm.computeNextReview(
            grade = grade,
            currentEf = current.easeFactor,
            currentInterval = current.intervalDays,
            currentReps = current.repetitions,
            nowMillis = nowMillis,
        )
        dao.applyReview(
            id = cardId,
            easeFactor = result.easeFactor,
            intervalDays = result.intervalDays,
            repetitions = result.repetitions,
            lastReviewAt = nowMillis,
            nextReviewAt = result.nextReviewAt,
            lapseIncrement = if (grade < 3) 1 else 0,
        )
        return dao.getById(cardId)
    }

    /**
     * Достать объяснение для карточки из pre-gen словаря.
     *
     * Сначала ищем точное совпадение по (word, kind, subtype) — это правильный
     * случай, когда `subtype` действительно различает разные таблицы (t9 vs t10).
     * Если такой записи нет (например, словарь был пересобран и subtype поменялся),
     * fallback на `get(word, kind)` который вернёт первую запись по слову.
     */
    suspend fun getTextsForCard(
        context: Context,
        card: SrsCardEntity,
    ): CardTexts {
        val dao = EgeDatabase.get(context).trainerExplanationDao()
        val exact = dao.getExact(card.word, card.kind, card.subtype)
            ?: dao.get(card.word, card.kind)
        return CardTexts(
            explanation = exact?.explanation,
            rule = exact?.rule,
            examples = exact?.examples,
            mnemonic = exact?.mnemonic,
        )
    }

    /** Для unit / smoke-тестов. Чистит все карточки. НЕ вызывать из UI. */
    suspend fun clearAll(context: Context) {
        UserDataDatabase.get(context).srsCardDao().deleteAll()
    }
}
