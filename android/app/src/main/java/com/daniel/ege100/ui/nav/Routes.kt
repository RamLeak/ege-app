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
