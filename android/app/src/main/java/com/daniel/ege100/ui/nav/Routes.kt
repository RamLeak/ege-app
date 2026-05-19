package com.daniel.ege100.ui.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe routes для NavHost (Navigation Compose 2.8 + kotlinx.serialization).
 *
 * Bottom-tabs: HomeStub | Catalog | JournalStub. Stage 3 polish добавляет
 * 2 экрана тренажёра ударений (Categories + Trainer).
 */
@Serializable
object HomeStubRoute

@Serializable
object CatalogRoute

@Serializable
object JournalStubRoute

@Serializable
data class TypesRoute(val subjectId: Long)

@Serializable
data class SubtypesRoute(val typeId: Long)

@Serializable
data class ProblemListRoute(
    val typeId: Long,
    val subtypeId: Long? = null,
)

@Serializable
data class ProblemDetailRoute(
    val problemId: Long,
    val typeId: Long,
    val subtypeId: Long? = null,
    /** Phase 3 Stage C — открыто из «Ошибки → Перерешать». */
    val fromErrors: Boolean = false,
)

@Serializable
object AccentCategoriesRoute

/**
 * Stage 3 polish 2: 3 режима входа.
 *   - categoryId="nouns"/.../"adverbs" + defaultOrder="alphabetical" — категория А-Я.
 *   - categoryId=null + defaultOrder="random" — все слова перемешать (🎲).
 *   - categoryId=null + defaultOrder="alphabetical" — все слова по алфавиту (🔤).
 *
 * Toggle ⇆ внутри тренажёра позволяет переключаться между Alphabetical и Random.
 */
@Serializable
data class AccentTrainerRoute(
    val categoryId: String? = null,
    val defaultOrder: String = "alphabetical",
)

/**
 * Stage 4: тренажёр слов с пропусками (типы 9-12 русского).
 */
@Serializable
data class WordBlankTrainerRoute(
    val typeNumber: Int,
    val defaultOrder: String = "alphabetical",
)

/**
 * Stage 5: экран избранных задач (в табе Журнал).
 */
@Serializable
object FavoritesRoute

/**
 * Phase 3 Stage A: 4-й таб «Профиль».
 */
@Serializable
object ProfileRoute

/**
 * Phase 3 Stage A: экран настроек (заходим из Профиля).
 */
@Serializable
object SettingsRoute

/**
 * Phase 3 Stage FINAL: календарь пробников (заходим с главного по тапу
 * на MockExamPreviewCard).
 */
@Serializable
object MockExamCalendarRoute

/**
 * Phase 3 Stage FINAL: детали конкретного пробника (предстоящий или прошлый).
 */
@Serializable
data class MockExamDetailRoute(val planIndex: Int)

/**
 * Phase 3 Stage FINAL + Phase 4 A1 + B1: прохождение пробника.
 *
 * Варианты входа:
 *   planIndex >= 0 + subject = "math"/"rus" → internal пробник по subject.
 *   planIndex == -1 + fipiVariantId != null → ФИПИ-вариант.
 */
@Serializable
data class MockExamRunnerRoute(
    val planIndex: Int,
    val subject: String = "math",
    val fipiVariantId: String? = null,
)

/**
 * Phase 4 Stage B1: экран списка ФИПИ-вариантов.
 */
@Serializable
object FipiVariantsRoute

/**
 * Phase 4 Stage B2: история пробников + тренд.
 */
@Serializable
object MockExamHistoryRoute

/**
 * Phase 3 Stage C: журнал ошибок (заходим из Журнала → «📝 Ошибки»).
 */
@Serializable
object ErrorsListRoute

/**
 * Phase 3 Stage C: детальная статистика (заходим из Журнала → «📊 Статистика»).
 */
@Serializable
object StatsRoute

/**
 * Phase 3 Stage B: быстрый тренажёр из радара. Список problemIds кодируется
 * как строка с запятой-разделителем — Navigation Compose 2.8 не поддерживает
 * @Serializable List<Long> в URL напрямую.
 */
@Serializable
data class QuickTrainerRoute(
    val problemIdsCsv: String,
) {
    val problemIds: List<Long>
        get() = problemIdsCsv.split(',').mapNotNull { it.toLongOrNull() }

    companion object {
        fun of(ids: List<Long>) = QuickTrainerRoute(ids.joinToString(","))
    }
}

/**
 * Phase 4 Stage P4-D (Convention #71) — 3 новых русских тренажёра.
 */
@Serializable
object ParonymTrainerRoute

@Serializable
object PleonasmTrainerRoute

@Serializable
object GrammarTrainerRoute

/**
 * Phase 4 Stage P4-D5 (Convention #83) — правильный №7: словосочетания
 * с двухшаговой логикой (тап на ошибочную фразу + ввод исправления).
 * GrammarTrainerRoute теперь привязан к №8 (TrainerCatalogMapping #84).
 */
@Serializable
object CollocationTrainerRoute

/**
 * Phase 4 Stage P4-D (Convention #74) — 5 новых математических тренажёров.
 */
@Serializable
object TrigTrainerRoute

@Serializable
object ShortMultTrainerRoute

@Serializable
object LogPowerTrainerRoute

@Serializable
object DerivativesTrainerRoute

@Serializable
object GeometryTrainerRoute

// Phase 4 Stage P4-D4 (Convention #81) — AllTrainersRoute удалён. Тренажёры теперь
// открываются исключительно через каталог (TrainerCatalogMapping → SubtypesScreen).
