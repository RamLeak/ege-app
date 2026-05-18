package com.daniel.ege100.ui.common

import kotlin.math.abs

/**
 * Phase 3 Stage B — корректное склонение русских числительных.
 *
 * Использование:
 *   pluralize(7, "день", "дня", "дней") → "дней"
 *   pluralize(1, "день", "дня", "дней") → "день"
 *   pluralize(22, "день", "дня", "дней") → "дня"
 *   pluralize(11, "день", "дня", "дней") → "дней" (особая зона 11..14)
 */
fun pluralize(n: Int, one: String, few: String, many: String): String {
    val abs100 = abs(n) % 100
    val abs10 = abs100 % 10
    return when {
        abs100 in 11..14 -> many
        abs10 == 1 -> one
        abs10 in 2..4 -> few
        else -> many
    }
}

fun daysWord(n: Int): String = pluralize(n, "день", "дня", "дней")

fun problemsWord(n: Int): String = pluralize(n, "задача", "задачи", "задач")

fun attemptsWord(n: Int): String = pluralize(n, "попытка", "попытки", "попыток")
