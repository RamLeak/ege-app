package com.daniel.ege100.data

/**
 * Phase 4 Stage P4-C part А (Convention #48) — корректность ответов.
 *
 * Реальные форматы в corpus.db (по результатам SELECT 200):
 *  - `number`     — `"14"`, `"0,5"`, `"-1,5"`, `"59321"`.
 *  - `string`     — `"красящими"`, `"если"`.
 *  - `alternatives` — `"красивая|красива"`, `"235|253|325|352|523|532"`.
 *    Разделитель `|` (точкой с запятой в БД не встречается — но
 *    добавляю `;` defensively на случай если sdamgia добавит).
 *  - `multipart`  — пробельные группы (`"16 27"`, `"12345678."`).
 *  - `null`        — обращаемся как со `string`.
 *
 * До Convention #48 `matchesAnswer` / `matches` в ProblemDetailScreen
 * и MockExamRunnerScreen раскалывали `alternatives` по **пробелу** —
 * `красивая|красива` не дробился на варианты, и пользователь не мог
 * ответить ни «красивая», ни «красива». Это критический баг.
 *
 * Дополнительная плюшка для типов вроде «выпишите номера через запятую»:
 * если **и ответ, и пользовательский ввод — чисто цифры** и прямого
 * матча нет — допускаем любую перестановку цифр. Этот fallback покрывает
 * случай где sdamgia не догадался перечислить все 6 перестановок (хотя
 * обычно догадался — см. `235|253|325|352|523|532`).
 */
object AnswerChecker {

    /**
     * Возвращает true если ответ пользователя считается верным.
     *
     * @param userAnswer Что ввёл пользователь (любой регистр, пробелы).
     * @param correctAnswer Ответ из БД (`problems.answer`).
     * @param answerFormat `problems.answer_format` — number / string /
     *                    alternatives / multipart / null.
     */
    fun isCorrect(userAnswer: String, correctAnswer: String, answerFormat: String?): Boolean {
        val nt = normalize(userAnswer)
        if (nt.isEmpty()) return false
        val variants = splitVariants(correctAnswer).map { normalize(it) }.filter { it.isNotEmpty() }
        if (variants.isEmpty()) return false

        // Прямое совпадение с любым вариантом — самый частый кейс.
        if (variants.any { it == nt }) return true

        // multipart: «12345678.» / «16 27» — токены без учёта порядка.
        if (answerFormat == "multipart") {
            val userTokens = nt.split(' ').filter { it.isNotEmpty() }.toSet()
            if (variants.any { it.split(' ').filter { t -> t.isNotEmpty() }.toSet() == userTokens }) {
                return true
            }
        }

        // Fallback: чисто цифровой ответ — допускаем перестановку.
        // Покрывает случай если БД не перечислила все варианты.
        if (nt.all { it.isDigit() }) {
            val ntSorted = nt.toCharArray().sortedArray().concatToString()
            if (variants.any { v -> v.all { it.isDigit() } && v.toCharArray().sortedArray().concatToString() == ntSorted }) {
                return true
            }
        }

        return false
    }

    /**
     * Подсказка под полем ввода: есть ли у задачи несколько правильных
     * вариантов или это multipart-ответ (несколько чисел/слов).
     */
    fun hasMultipleVariants(correctAnswer: String?, answerFormat: String?): Boolean {
        if (correctAnswer.isNullOrBlank()) return false
        if (correctAnswer.contains('|') || correctAnswer.contains(';')) return true
        if (answerFormat == "alternatives" || answerFormat == "multipart") return true
        return false
    }

    private fun normalize(s: String): String =
        s.trim()
            .lowercase()
            .replace(',', '.')
            .replace(Regex("\\s+"), " ")
            // Точка в конце ответа sdamgia — иногда есть, иногда нет (#18 «12345678.»).
            // Снимаем чтобы «12345678» и «12345678.» считались одинаковыми.
            .trimEnd('.')

    private fun splitVariants(answer: String): List<String> =
        answer.split('|', ';').map { it.trim() }
}
