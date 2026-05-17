package com.daniel.ege100.ui.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe routes для NavHost (Navigation Compose 2.8 + kotlinx.serialization).
 *
 * Bottom-tabs: HomeStub | Catalog | JournalStub. Stage 2 содержательно
 * наполняет только Catalog → Types → Subtypes → Problems → ProblemDetail.
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

/**
 * Список задач. Если `subtypeId == null` — все задачи типа (режим
 * «🎯 Все задачи типа», DESIGN_SPEC §6.5).
 */
@Serializable
data class ProblemListRoute(
    val typeId: Long,
    val subtypeId: Long? = null,
)

@Serializable
data class ProblemDetailRoute(val problemId: Long)
