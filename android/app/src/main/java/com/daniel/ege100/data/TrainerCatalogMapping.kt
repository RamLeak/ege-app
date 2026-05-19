package com.daniel.ege100.data

import androidx.compose.ui.graphics.Color
import com.daniel.ege100.ui.nav.AccentCategoriesRoute
import com.daniel.ege100.ui.nav.DerivativesTrainerRoute
import com.daniel.ege100.ui.nav.GeometryTrainerRoute
import com.daniel.ege100.ui.nav.GrammarTrainerRoute
import com.daniel.ege100.ui.nav.LogPowerTrainerRoute
import com.daniel.ege100.ui.nav.ParonymTrainerRoute
import com.daniel.ege100.ui.nav.PleonasmTrainerRoute
import com.daniel.ege100.ui.nav.ShortMultTrainerRoute
import com.daniel.ege100.ui.nav.TrigTrainerRoute
import com.daniel.ege100.ui.nav.WordBlankTrainerRoute

/**
 * Phase 4 Stage P4-D4 (Conventions #80, #82) — реестр привязок тренажёров к
 * парам (subjectSlug, typeNumber). Каждый ЕГЭ-тип может иметь 0..N тренажёров;
 * математические тренажёры (тригонометрия / производные / геометрия / логарифмы /
 * сокращённое умножение) часто привязаны к нескольким типам — это один и тот же
 * экран с общим прогрессом, доступный из нескольких мест каталога.
 *
 * Раньше (P4-D) тренажёры можно было открыть только через AllTrainersScreen хаб;
 * теперь хаб удалён (Convention #81), тренажёры открываются исключительно через
 * каталог: `Решать → Предмет → Тип → 🎯 Тренажёры`.
 */
object TrainerCatalogMapping {

    /**
     * Один tile в списке тренажёров типа.
     *   - `id` — стабильный ключ для `UserStatsStore.trainersCompletedFlow`.
     *   - `route` — type-safe Compose route (Any для navigate compatibility).
     *   - `totalItems` — для счётчика «N элементов» в карточке.
     *   - `emoji` + `tint` — визуальный стиль.
     */
    data class TrainerEntry(
        val id: String,
        val title: String,
        val subtitle: String,
        val emoji: String,
        val tint: Color,
        val route: Any,
        val totalItems: Int,
    )

    private val MathTint = Color(0x1F0A84FF)       // SystemBlue alpha 0.12
    private val RusTint = Color(0x1F32D74B)        // SystemGreen alpha 0.12
    private val OrangeTint = Color(0x1FFF9F0A)     // SystemOrange alpha 0.12

    private val attachments: Map<Pair<String, Int>, List<TrainerEntry>> = mapOf(
        // ---------- Русский ----------
        Pair("rus", 4) to listOf(
            TrainerEntry("accent_all_alphabetical", "Тренажёр ударений", "Орфоэпический словник · 230 слов",
                "🔤", OrangeTint, AccentCategoriesRoute, 230),
        ),
        Pair("rus", 5) to listOf(
            TrainerEntry("paronym", "Тренажёр паронимов", "Замена слова · 211 пар",
                "📝", RusTint, ParonymTrainerRoute, 211),
        ),
        Pair("rus", 6) to listOf(
            TrainerEntry("pleonasm", "Тренажёр плеоназмов", "Тап на лишнее слово · 96 примеров",
                "✂", RusTint, PleonasmTrainerRoute, 96),
        ),
        Pair("rus", 7) to listOf(
            TrainerEntry("grammar", "Тренажёр грамошибок", "4 варианта исправления · 34 задачи",
                "⚠", Color(0x1FFF453A), GrammarTrainerRoute, 34),
        ),
        Pair("rus", 9) to listOf(
            TrainerEntry("wordblank_t9", "Тренажёр корней", "Пропущенная буква · корни",
                "🌱", RusTint, WordBlankTrainerRoute(9), 847),
        ),
        Pair("rus", 10) to listOf(
            TrainerEntry("wordblank_t10", "Тренажёр приставок", "Пропущенная буква · приставки",
                "🧱", RusTint, WordBlankTrainerRoute(10), 715),
        ),
        Pair("rus", 11) to listOf(
            TrainerEntry("wordblank_t11", "Тренажёр суффиксов", "Пропущенная буква · суффиксы",
                "🎀", RusTint, WordBlankTrainerRoute(11), 517),
        ),
        Pair("rus", 12) to listOf(
            TrainerEntry("wordblank_t12", "Тренажёр окончаний", "Пропущенная буква · окончания/причастия",
                "🌀", RusTint, WordBlankTrainerRoute(12), 595),
        ),

        // ---------- Математика ----------
        Pair("mathb", 1) to listOf(
            TrainerEntry("math_geometry", "Тренажёр геометрии", "Формулы · площадь, объём",
                "🔷", MathTint, GeometryTrainerRoute, 25),
        ),
        Pair("mathb", 2) to listOf(
            TrainerEntry("math_geometry", "Тренажёр геометрии", "Формулы · площадь, объём",
                "🔷", MathTint, GeometryTrainerRoute, 25),
        ),
        Pair("mathb", 3) to listOf(
            TrainerEntry("math_geometry", "Тренажёр геометрии", "Формулы · площадь, объём",
                "🔷", MathTint, GeometryTrainerRoute, 25),
        ),
        Pair("mathb", 6) to listOf(
            TrainerEntry("math_shortmult", "Сокращённое умножение", "7 формул",
                "✖", MathTint, ShortMultTrainerRoute, 7),
            TrainerEntry("math_logpower", "Логарифмы и степени", "13 свойств",
                "📈", MathTint, LogPowerTrainerRoute, 13),
        ),
        Pair("mathb", 7) to listOf(
            TrainerEntry("math_trig", "Тригонометрия", "Значения · 11 углов",
                "📐", MathTint, TrigTrainerRoute, 30),
            TrainerEntry("math_shortmult", "Сокращённое умножение", "7 формул",
                "✖", MathTint, ShortMultTrainerRoute, 7),
            TrainerEntry("math_logpower", "Логарифмы и степени", "13 свойств",
                "📈", MathTint, LogPowerTrainerRoute, 13),
            TrainerEntry("math_derivatives", "Производные", "13 стандартных функций",
                "∂", MathTint, DerivativesTrainerRoute, 13),
        ),
        Pair("mathb", 9) to listOf(
            TrainerEntry("math_logpower", "Логарифмы и степени", "13 свойств",
                "📈", MathTint, LogPowerTrainerRoute, 13),
        ),
        Pair("mathb", 11) to listOf(
            TrainerEntry("math_derivatives", "Производные", "13 стандартных функций",
                "∂", MathTint, DerivativesTrainerRoute, 13),
        ),
        Pair("mathb", 12) to listOf(
            TrainerEntry("math_trig", "Тригонометрия", "Значения · 11 углов",
                "📐", MathTint, TrigTrainerRoute, 30),
        ),
        Pair("mathb", 13) to listOf(
            TrainerEntry("math_derivatives", "Производные", "13 стандартных функций",
                "∂", MathTint, DerivativesTrainerRoute, 13),
        ),
        Pair("mathb", 14) to listOf(
            TrainerEntry("math_geometry", "Тренажёр геометрии", "Формулы · площадь, объём",
                "🔷", MathTint, GeometryTrainerRoute, 25),
        ),
    )

    fun getForType(subjectSlug: String, typeNumber: Int): List<TrainerEntry> =
        attachments[Pair(subjectSlug, typeNumber)] ?: emptyList()
}
