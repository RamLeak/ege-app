package com.daniel.ege100.data

/**
 * Phase 4 Stage P4-C2 part Б (Convention #57) — варианты букв для
 * тренажёров №9-12 русского.
 *
 * В `WordBlank` нет slug подвида (только masked/answer/full/rule_hint),
 * поэтому используем **fallback на основе самого правильного ответа**:
 * для типичных орфографических пар возвращаем оба варианта (а/о, е/и,
 * з/с, н/нн и т.д.). Для редких ответов вроде «иги», «ёвы», «ице»,
 * «вз» возвращаем одну кнопку с правильным ответом — тренажёр всё
 * равно полезнее чем текстовое поле (тап вместо набора).
 *
 * Если когда-нибудь добавим `WordBlank.subtypeSlug` (например через
 * парсер `parser/scrapers/extract_word_blanks.py` v2) — расширить
 * `choicesFor` пословным маппингом kasn-kosn/pre-pri/iz-is и т.д.
 */
object WordBlankChoices {

    /**
     * Возвращает 1-3 кнопки-варианта. Правильный ответ всегда
     * присутствует. Стабильный порядок (alphabetical) — чтобы
     * кнопки не прыгали при повторных показах слова.
     */
    fun choicesFor(correctAnswer: String): List<String> {
        val correct = correctAnswer.trim().lowercase()
        if (correct.isEmpty()) return emptyList()
        val pair = PAIRS[correct]
        if (pair != null) {
            return if (correct in pair) pair else pair + correct
        }
        return listOf(correct)
    }

    /**
     * Типичные пары орфографического выбора. Ключ — правильный
     * ответ, значение — упорядоченный список кнопок (включая
     * правильный). Сортировка — алфавитная для стабильности UI.
     */
    private val PAIRS: Map<String, List<String>> = mapOf(
        // Безударные гласные / чередующиеся корни.
        "а" to listOf("а", "о"),
        "о" to listOf("а", "о"),
        "е" to listOf("е", "и"),
        "и" to listOf("е", "и"),
        "у" to listOf("у", "ю"),
        "ю" to listOf("у", "ю"),
        "я" to listOf("а", "я"),
        "ы" to listOf("и", "ы"),
        "ё" to listOf("е", "ё"),
        // Приставки на з/с.
        "з" to listOf("з", "с"),
        "с" to listOf("з", "с"),
        // Суффиксы н/нн.
        "н" to listOf("н", "нн"),
        "нн" to listOf("н", "нн"),
        // Согласные / разделительные ъь.
        "ъ" to listOf("ъ", "ь"),
        "ь" to listOf("ъ", "ь"),
    )
}
