package com.daniel.ege100.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CatalogDao {

    @Query("SELECT COUNT(*) FROM problems")
    suspend fun countProblems(): Int

    @Query(
        """
        SELECT s.id, s.slug, s.title,
               (SELECT COUNT(*) FROM problem_types t WHERE t.subject_id = s.id AND t.is_supplementary = 0) AS type_count,
               (SELECT COUNT(*) FROM problems p WHERE p.subject_id = s.id) AS problem_count
        FROM subjects s
        ORDER BY s.id
        """
    )
    suspend fun getSubjectsWithCount(): List<SubjectWithCount>

    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    suspend fun getSubject(subjectId: Long): SubjectEntity?

    @Query(
        """
        SELECT t.id, t.subject_id, t.number, t.title, t.is_supplementary,
               (SELECT COUNT(*) FROM problems p WHERE p.type_id = t.id) AS problem_count
        FROM problem_types t
        WHERE t.subject_id = :subjectId AND t.is_supplementary = 0
        ORDER BY t.number
        """
    )
    suspend fun getTypesBySubject(subjectId: Long): List<TypeWithCount>

    @Query("SELECT * FROM problem_types WHERE id = :typeId")
    suspend fun getType(typeId: Long): ProblemTypeEntity?

    @Query(
        """
        SELECT st.id, st.type_id, st.kes_code, st.title,
               (SELECT COUNT(*) FROM problems p WHERE p.subtype_id = st.id) AS problem_count
        FROM problem_subtypes st
        WHERE st.type_id = :typeId
        ORDER BY st.title
        """
    )
    suspend fun getSubtypesByType(typeId: Long): List<SubtypeWithCount>

    @Query("SELECT * FROM problem_subtypes WHERE id = :subtypeId")
    suspend fun getSubtype(subtypeId: Long): ProblemSubtypeEntity?

    @Query("SELECT COUNT(*) FROM problems WHERE type_id = :typeId")
    suspend fun countProblemsByType(typeId: Long): Int

    @Query("SELECT COUNT(*) FROM problems WHERE subtype_id = :subtypeId")
    suspend fun countProblemsBySubtype(subtypeId: Long): Int

    /**
     * Список задач подвида с пагинацией.
     * statement_html обрезаем превью на стороне Kotlin (а не SQL) — sdamgia HTML
     * содержит теги, которые надо снять; делать это в SQL громоздко.
     */
    @Query(
        """
        SELECT * FROM problems
        WHERE subtype_id = :subtypeId
        ORDER BY id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getProblemsBySubtype(subtypeId: Long, limit: Int, offset: Int): List<ProblemEntity>

    /**
     * Список ВСЕХ задач типа (используется когда пользователь тапает «🎯 Все задачи типа»).
     */
    @Query(
        """
        SELECT * FROM problems
        WHERE type_id = :typeId
        ORDER BY id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getProblemsByType(typeId: Long, limit: Int, offset: Int): List<ProblemEntity>

    @Query("SELECT * FROM problems WHERE id = :problemId")
    suspend fun getProblem(problemId: Long): ProblemEntity?

    // --- Stage 3: решения + навигация в пределах подвида/типа ---

    @Query("SELECT * FROM solutions WHERE problem_id = :problemId")
    suspend fun getSolution(problemId: Long): SolutionEntity?

    /**
     * Позиция текущей задачи (1-based) в выборке по подвиду — для шапки «3/150».
     * Сортировка по id (та же, что в getProblemsBySubtype), так что соответствует
     * порядку в каталоге.
     */
    @Query(
        """
        SELECT COUNT(*) FROM problems
        WHERE subtype_id = :subtypeId AND id <= :currentId
        """
    )
    suspend fun positionInSubtype(currentId: Long, subtypeId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM problems
        WHERE type_id = :typeId AND id <= :currentId
        """
    )
    suspend fun positionInType(currentId: Long, typeId: Long): Int

    @Query(
        """
        SELECT id FROM problems
        WHERE subtype_id = :subtypeId AND id > :currentId
        ORDER BY id ASC LIMIT 1
        """
    )
    suspend fun nextProblemIdInSubtype(currentId: Long, subtypeId: Long): Long?

    @Query(
        """
        SELECT id FROM problems
        WHERE subtype_id = :subtypeId AND id < :currentId
        ORDER BY id DESC LIMIT 1
        """
    )
    suspend fun prevProblemIdInSubtype(currentId: Long, subtypeId: Long): Long?

    @Query(
        """
        SELECT id FROM problems
        WHERE type_id = :typeId AND id > :currentId
        ORDER BY id ASC LIMIT 1
        """
    )
    suspend fun nextProblemIdInType(currentId: Long, typeId: Long): Long?

    @Query(
        """
        SELECT id FROM problems
        WHERE type_id = :typeId AND id < :currentId
        ORDER BY id DESC LIMIT 1
        """
    )
    suspend fun prevProblemIdInType(currentId: Long, typeId: Long): Long?

    /**
     * Stage 5 part Д: подгрузка избранных задач по списку id (для FavoritesScreen).
     */
    @Query(
        """
        SELECT * FROM problems
        WHERE id IN (:ids)
        ORDER BY id
        """
    )
    suspend fun getProblemsByIds(ids: List<Long>): List<ProblemEntity>

    /** Phase 3 Stage B — поиск subject по slug ('mathb' / 'rus'). */
    @Query("SELECT * FROM subjects WHERE slug = :slug LIMIT 1")
    suspend fun getSubjectBySlug(slug: String): SubjectEntity?

    /** Phase 3 Stage B — все подвиды subject'а с информацией о типе (для радара). */
    @Query(
        """
        SELECT st.id, st.type_id, st.kes_code, st.title,
               t.subject_id, t.number AS type_number
        FROM problem_subtypes st
        JOIN problem_types t ON st.type_id = t.id
        WHERE t.subject_id = :subjectId AND t.is_supplementary = 0
        ORDER BY t.number, st.title
        """
    )
    suspend fun getSubtypesBySubject(subjectId: Long): List<SubtypeWithType>

    /** Phase 3 Stage B — N случайных задач из подвида (для быстрого тренажёра). */
    @Query(
        """
        SELECT * FROM problems
        WHERE subtype_id = :subtypeId
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomProblemsInSubtype(subtypeId: Long, limit: Int): List<ProblemEntity>

    /**
     * Phase 4 Stage A1 — все problem_id данного типа предмета (для MockExamComposer).
     * Возвращаем ID, не Entity — на стороне composer выбирается random.
     */
    @Query(
        """
        SELECT p.id FROM problems p
        JOIN problem_types pt ON p.type_id = pt.id
        JOIN subjects s ON pt.subject_id = s.id
        WHERE s.slug = :subjectSlug AND pt.number = :typeNumber AND pt.is_supplementary = 0
        """
    )
    suspend fun getProblemIdsByTypeNumber(subjectSlug: String, typeNumber: Int): List<Long>

    /**
     * Phase 4 Stage P4-D2 part Б (Convention #65) — problem_id'ы конкретного
     * типа. Используется ProgressRepository для расчёта прогресса —
     * перекидывает их в attempt_log.getLastCorrectIds().
     */
    @Query("SELECT id FROM problems WHERE type_id = :typeId")
    suspend fun getProblemIdsByType(typeId: Long): List<Long>

    /** Аналог для подвида (для SubtypesScreen прогресс-бары). */
    @Query("SELECT id FROM problems WHERE subtype_id = :subtypeId")
    suspend fun getProblemIdsBySubtype(subtypeId: Long): List<Long>
}
