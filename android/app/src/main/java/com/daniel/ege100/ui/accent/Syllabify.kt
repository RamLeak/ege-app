package com.daniel.ege100.ui.accent

/**
 * Разбивка русского слова на слоги для тренажёра ударений.
 *
 * Правила деления (упрощённый школьный подход для UX, не строгая фонетика):
 *   1. В каждом слоге ровно одна гласная.
 *   2. После гласной — переход к следующему слогу.
 *   3. Если после гласной только один согласный в конце слова — он
 *      присоединяется к слогу (предотвращает «пустой» хвостовой слог из
 *      одной согласной).
 *
 * Примеры:
 *   каталог       → [ка][та][лог]
 *   аэропорты     → [а][э][ро][пор][ты]
 *   вероисповедание → [ве][ро][и][спо][ве][да][ни][е]
 *   банты         → [бан][ты]   (тут «н» уходит к первому слогу так как
 *                                 не последний согласный)
 *   водопровод    → [во][до][про][вод]
 */

val ACCENT_VOWELS: Set<Char> = setOf('а','е','ё','и','о','у','ы','э','ю','я')

data class Syllable(
    val text: String,
    /** Индекс первой буквы слога в исходном слове (inclusive). */
    val startInWord: Int,
    /** Индекс последней буквы слога в исходном слове (inclusive). */
    val endInWord: Int,
) {
    /** Индексы гласных букв внутри слога (offset относительно слова). */
    fun vowelIndicesInWord(word: String): List<Int> {
        val result = mutableListOf<Int>()
        for (i in startInWord..endInWord) {
            if (word[i] in ACCENT_VOWELS) result += i
        }
        return result
    }
}

fun syllabify(word: String): List<Syllable> {
    val result = mutableListOf<Syllable>()
    var start = 0
    val n = word.length
    var i = 0
    while (i < n) {
        if (word[i] in ACCENT_VOWELS) {
            // Решение: где закончится слог?
            // Если это последняя буква слова — слог захватывает её.
            // Если после гласной идёт согласный, который ЕДИНСТВЕННЫЙ оставшийся
            // (т.е. дальше до конца только этот согласный) — берём и его.
            // Иначе слог заканчивается на самой гласной.
            val end = when {
                i == n - 1 -> i
                i + 1 < n && word[i + 1] !in ACCENT_VOWELS && i + 1 == n - 1 -> i + 1
                else -> i
            }
            result += Syllable(
                text = word.substring(start, end + 1),
                startInWord = start,
                endInWord = end,
            )
            start = end + 1
            i = end + 1
        } else {
            i++
        }
    }
    // Если в конце остались только согласные без гласной — приклеить к последнему слогу.
    if (start < n) {
        if (result.isEmpty()) {
            // слово без гласных — экзотика, но не падать
            result += Syllable(word, 0, n - 1)
        } else {
            val last = result.removeAt(result.size - 1)
            result += Syllable(
                text = word.substring(last.startInWord, n),
                startInWord = last.startInWord,
                endInWord = n - 1,
            )
        }
    }
    return result
}
