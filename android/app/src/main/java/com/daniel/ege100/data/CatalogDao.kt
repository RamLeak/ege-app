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
}
