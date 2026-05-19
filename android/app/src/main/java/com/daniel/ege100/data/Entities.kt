package com.daniel.ege100.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-сущности под существующую схему `parser/corpus.db` (см. CLAUDE.md §«Схема БД»).
 *
 * Декларации (типы, NOT NULL, default, FK, индексы) должны ТОЧНО совпадать с DDL,
 * иначе Room.createFromAsset бросает IllegalStateException при первом open.
 * Колоночные UNIQUE и UNIQUE-табличные ограничения создают sqlite_autoindex_*,
 * которые Room игнорирует при сверке схемы — поэтому здесь декларируются только
 * явные `CREATE INDEX idx_*`.
 *
 * Stage 2: каталог нужен только для 4 таблиц (subjects/types/subtypes/problems).
 * Остальные (rules, problem_rules, user_progress, solutions, …) подключим в Stage 3+.
 */

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: Long,
    val slug: String,
    val title: String,
    @ColumnInfo(name = "sdamgia_subdomain") val sdamgiaSubdomain: String,
)

@Entity(
    tableName = "problem_types",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
        ),
    ],
)
data class ProblemTypeEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "subject_id") val subjectId: Long,
    val number: Int,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "is_supplementary", defaultValue = "0") val isSupplementary: Int,
)

@Entity(
    tableName = "problem_subtypes",
    foreignKeys = [
        ForeignKey(
            entity = ProblemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
        ),
    ],
    indices = [
        Index(value = ["sdamgia_category_id"], name = "idx_subtypes_sdamgia"),
    ],
)
data class ProblemSubtypeEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "type_id") val typeId: Long,
    @ColumnInfo(name = "kes_code") val kesCode: String?,
    val title: String,
    @ColumnInfo(name = "sdamgia_category_id") val sdamgiaCategoryId: Long?,
)

@Entity(
    tableName = "problems",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
        ),
        ForeignKey(
            entity = ProblemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
        ),
        ForeignKey(
            entity = ProblemSubtypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["subtype_id"],
        ),
    ],
    indices = [
        Index(value = ["subject_id"], name = "idx_problems_subject"),
        Index(value = ["type_id"], name = "idx_problems_type"),
        Index(value = ["subtype_id"], name = "idx_problems_subtype"),
    ],
)
data class ProblemEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "subject_id") val subjectId: Long,
    @ColumnInfo(name = "sdamgia_id") val sdamgiaId: String,
    @ColumnInfo(name = "prototype_id") val prototypeId: String?,
    @ColumnInfo(name = "type_id") val typeId: Long,
    @ColumnInfo(name = "subtype_id") val subtypeId: Long?,
    @ColumnInfo(name = "statement_html") val statementHtml: String,
    val answer: String?,
    @ColumnInfo(name = "answer_format") val answerFormat: String?,
    @ColumnInfo(name = "images_json") val imagesJson: String?,
    val source: String?,
    val difficulty: String?,
    @ColumnInfo(name = "scraped_at") val scrapedAt: String,
    @ColumnInfo(name = "raw_hash") val rawHash: String,
)

/**
 * Stage 3: авторское решение задачи.
 * DDL: problem_id PK FK→problems(id), solution_html NOT NULL, explanation_text nullable.
 * `explanation_text` пока пуст в БД (зарезервировано под plain-text для AI).
 */
@Entity(
    tableName = "solutions",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
        ),
    ],
)
data class SolutionEntity(
    @PrimaryKey @ColumnInfo(name = "problem_id") val problemId: Long,
    @ColumnInfo(name = "solution_html") val solutionHtml: String,
    @ColumnInfo(name = "explanation_text") val explanationText: String?,
)

/**
 * Composite-результат: тип + количество задач в нём (для экрана списка типов).
 * Не маппится в таблицу, используется только в Dao.
 */
data class TypeWithCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "subject_id") val subjectId: Long,
    @ColumnInfo(name = "number") val number: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "is_supplementary") val isSupplementary: Int,
    @ColumnInfo(name = "problem_count") val problemCount: Int,
)

data class SubtypeWithCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "type_id") val typeId: Long,
    @ColumnInfo(name = "kes_code") val kesCode: String?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "problem_count") val problemCount: Int,
)

data class SubjectWithCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "slug") val slug: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "type_count") val typeCount: Int,
    @ColumnInfo(name = "problem_count") val problemCount: Int,
)

/**
 * Phase 3 Stage B — подвид + его тип (для радара слабых мест).
 * Используется в SubtypeStatsRepository.
 */
data class SubtypeWithType(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "type_id") val typeId: Long,
    @ColumnInfo(name = "kes_code") val kesCode: String?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "subject_id") val subjectId: Long,
    @ColumnInfo(name = "type_number") val typeNumber: Int,
)

/**
 * Phase 4 Stage P4-D (Convention #71): pre-generated объяснения для тренажёров.
 *
 * Таблица создаётся скриптом `parser/scrapers/save_explanations_batch.py` в
 * `parser/corpus.db`. После копирования asset Room считывает её read-only через
 * `TrainerExplanationDao.get(word, kind)`. UNIQUE(word, kind, subtype) гарантирует
 * один pre-gen на каждое слово (subtype = категория тренажёра: nouns/verbs/t9/...).
 *
 * Schema через table-level PRIMARY KEY — иначе Room считает id nullable
 * (Convention #12, инцидент Stage 2 Fix). Default 0 на generated_at — для записей,
 * добавленных ДО трекинга времени.
 *
 * Phase 4 Stage P4-D2 hotfix (init-краш): индекс `idx_explanations_lookup`
 * физически создаётся в pre-packaged corpus.db и MIGRATION_2_3, поэтому ОБЯЗАТЕЛЬНО
 * декларировать его в @Entity — иначе Room schema validation сравнивает
 * объявленный набор индексов с реальным и бросает IllegalStateException
 * «Pre-packaged database has an invalid schema» при первом open. Тот же класс
 * проблем что Convention #12 (Stage 2 schema mismatch).
 */
@Entity(
    tableName = "trainer_explanations",
    indices = [
        Index(value = ["word", "kind"], name = "idx_explanations_lookup"),
    ],
)
data class TrainerExplanationEntity(
    @PrimaryKey val id: Long,
    val word: String,
    val kind: String,
    val subtype: String,
    val explanation: String?,
    val rule: String?,
    val examples: String?,
    val mnemonic: String?,
    @ColumnInfo(name = "generated_at", defaultValue = "0") val generatedAt: Long,
)
