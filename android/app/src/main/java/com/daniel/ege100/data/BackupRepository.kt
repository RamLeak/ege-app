package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Phase 3 Stage A part Д — экспорт/импорт пользовательских данных.
 *
 * JSON-снэпшот всех Store'ов в одном файле. Что бэкапим:
 *   - UserProfile (имя, дата рождения, целевой балл, дата ЕГЭ)
 *   - AppSettings (тема, радар, уведомления)
 *   - TrainerProgress (Map<trainerId, snapshot>)
 *   - Favorites (Set<problemId>)
 *   - AccentErrors (Set<word>)
 *   - WordBlankErrors (Map<typeNumber, Set<masked>>)
 *
 * Чего НЕ бэкапим:
 *   - corpus.db (192 MB, поставляется с APK)
 *   - assets/ (формулы, картинки)
 *
 * Файл размером ~1-50 KB зависит от объёма прогресса. Отправляется через
 * Intent.ACTION_SEND с FileProvider.
 */
@Serializable
data class BackupSnapshot(
    val version: String,
    val exportedAt: String,
    val appVersion: String,
    val profile: UserProfile,
    val settings: AppSettings,
    val trainerProgress: Map<String, TrainerProgress>,
    val favorites: List<Long>,
    val accentErrors: List<String>,
    val wordBlankErrors: Map<Int, List<String>>,
    // Phase 3 Stage B: добавлены stats и streak (v1.1).
    val userStats: UserStatsSnapshot = UserStatsSnapshot(),
    val streak: StreakState = StreakState(),
    // Phase 3 Stage C: добавлен error_log и attempt_log (v1.2).
    val errorLog: List<ErrorLogRecord> = emptyList(),
    val attemptLog: List<AttemptLogRecord> = emptyList(),
    // Phase 3 Stage FINAL: mock_exam_results + install_date + guards (v1.3).
    // Старые бэкапы парсятся OK благодаря default-значениям + ignoreUnknownKeys.
    val mockExamResults: List<MockExamResultRecord> = emptyList(),
    val installDate: String? = null,
    val guardsState: GuardsState = GuardsState(),
    // Phase 4 Stage B3: aiSettings (БЕЗ API ключей — Convention #40).
    // aiResponseCache НЕ в бэкапе — можно восстановить запросами, не критично.
    val aiSettings: AiSettings = AiSettings(),
    // Phase 5 Stage E5: SRS-стейт (карточки + streak). Старые бэкапы v1.0-v1.8
    // парсятся OK благодаря default-значениям + ignoreUnknownKeys.
    val srsStreak: com.daniel.ege100.srs.SrsStreakState = com.daniel.ege100.srs.SrsStreakState(),
    val srsCards: List<com.daniel.ege100.srs.SrsCardRecord> = emptyList(),
) {
    companion object {
        // Phase 5 Stage E5 — v1.9. Добавлены поля srsStreak + srsCards.
        // Старые бэкапы v1.0-v1.8 парсятся OK (default + ignoreUnknownKeys).
        const val CURRENT_VERSION = "1.9"
        /** Версии, которые мы можем восстановить (forward-compat). */
        private val SUPPORTED_VERSIONS = setOf(
            "1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9",
        )
        fun isSupported(version: String): Boolean = version in SUPPORTED_VERSIONS
    }
}

sealed class ImportResult {
    data class Success(val exportedAt: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

object BackupRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportBackup(context: Context, appVersion: String = "0.1.0"): String =
        withContext(Dispatchers.IO) {
            val userDb = UserDataDatabase.get(context)
            val errorLog = userDb.errorLogDao().getAllForExport().map(ErrorLogRecord::fromEntity)
            val attemptLog = userDb.attemptLogDao().getAllForExport().map(AttemptLogRecord::fromEntity)
            val mockResults = userDb.mockExamResultDao().getAll().map(MockExamResultRecord::fromEntity)
            val installDate = MockExamSchedule.peekInstallDate(context)?.toString()
            val guards = SafetyGuardsStore.snapshot(context)
            val aiSettings = AiSettingsStore.snapshot(context)
            // Phase 5 Stage E5 — SRS снепшоты.
            val srsStreak = com.daniel.ege100.srs.SrsStreakStore.snapshot(context)
            val srsCards = com.daniel.ege100.srs.SrsRepository.snapshot(context)
            val snapshot = BackupSnapshot(
                version = BackupSnapshot.CURRENT_VERSION,
                exportedAt = Instant.now().toString(),
                appVersion = appVersion,
                profile = UserProfileStore.snapshot(context),
                settings = AppSettingsStore.snapshot(context),
                trainerProgress = TrainerProgressStore.getAllProgress(context),
                favorites = FavoritesStore.snapshot(context).sorted().toList(),
                accentErrors = AccentErrorsStore.snapshot(context).sorted().toList(),
                wordBlankErrors = WordBlankErrorsStore.getAll(context)
                    .mapValues { it.value.sorted().toList() },
                userStats = UserStatsStore.snapshot(context),
                streak = StreakStore.snapshot(context),
                errorLog = errorLog,
                attemptLog = attemptLog,
                mockExamResults = mockResults,
                installDate = installDate,
                guardsState = guards,
                aiSettings = aiSettings,
                srsStreak = srsStreak,
                srsCards = srsCards,
            )
            json.encodeToString(BackupSnapshot.serializer(), snapshot)
        }

    /**
     * Парсит и валидирует JSON-бэкап. **Не применяет** изменения — это делает
     * `applyBackup` после подтверждения пользователем в bottom sheet.
     */
    suspend fun parseBackup(content: String): Result<BackupSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = json.decodeFromString(BackupSnapshot.serializer(), content)
            require(BackupSnapshot.isSupported(parsed.version)) {
                "Неподдерживаемая версия: ${parsed.version}"
            }
            parsed
        }
    }

    /**
     * Применяет ранее распарсенный snapshot к Store'ам. Текущие данные
     * полностью заменяются.
     */
    suspend fun applyBackup(context: Context, snapshot: BackupSnapshot): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                UserProfileStore.restore(context, snapshot.profile)
                AppSettingsStore.restore(context, snapshot.settings)
                TrainerProgressStore.restoreAll(context, snapshot.trainerProgress)
                FavoritesStore.restore(context, snapshot.favorites.toSet())
                AccentErrorsStore.restore(context, snapshot.accentErrors.toSet())
                WordBlankErrorsStore.restore(
                    context,
                    snapshot.wordBlankErrors.mapValues { it.value.toSet() },
                )
                UserStatsStore.restore(context, snapshot.userStats)
                StreakStore.restore(context, snapshot.streak)
                // Phase 3 Stage C: восстановление error_log и attempt_log.
                // deleteAll сначала — чтобы не дублировать (autoGenerate id).
                val userDb = UserDataDatabase.get(context)
                val errorDao = userDb.errorLogDao()
                val attemptDao = userDb.attemptLogDao()
                val mockDao = userDb.mockExamResultDao()
                errorDao.deleteAll()
                attemptDao.deleteAll()
                mockDao.deleteAll()
                snapshot.errorLog.forEach { errorDao.insert(it.toEntity()) }
                snapshot.attemptLog.forEach { attemptDao.insert(it.toEntity()) }
                // Phase 4 B3: пропускаем записи с total=0 (старые v1.3 после миграции).
                snapshot.mockExamResults
                    .filter { it.total > 0 && it.scheduledDate.isNotBlank() }
                    .forEach { mockDao.insert(it.toEntity()) }
                // Phase 3 Stage FINAL: install_date + guards.
                snapshot.installDate
                    ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                    ?.let { MockExamSchedule.restoreInstallDate(context, it) }
                SafetyGuardsStore.restore(context, snapshot.guardsState)
                // Phase 4 B3: aiSettings без ключей.
                AiSettingsStore.restore(context, snapshot.aiSettings)
                // Phase 5 Stage E5: SRS-карточки + streak.
                com.daniel.ege100.srs.SrsStreakStore.restore(context, snapshot.srsStreak)
                com.daniel.ege100.srs.SrsRepository.restore(context, snapshot.srsCards)
                ImportResult.Success(snapshot.exportedAt)
            } catch (e: Throwable) {
                ImportResult.Error(e.message ?: "Неизвестная ошибка")
            }
        }

    /**
     * Phase 3 Stage A part Д6 + Stage B + Stage C + Stage FINAL — сброс прогресса.
     *
     * Удаляет: тренажёры, избранное, словарные ошибки, статистику попыток,
     * streak, error_log, attempt_log, mock_exam_results, guards.
     * Сохраняет: профиль, настройки, **install_date** (это техническая дата,
     * не пользовательская — расписание пробников считается от неё).
     */
    suspend fun resetProgress(context: Context) = withContext(Dispatchers.IO) {
        TrainerProgressStore.clearAll(context)
        FavoritesStore.clearAll(context)
        AccentErrorsStore.clearAll(context)
        WordBlankErrorsStore.clearAll(context)
        UserStatsStore.clearAll(context)
        StreakStore.clearAll(context)
        SafetyGuardsStore.clearAll(context)
        val userDb = UserDataDatabase.get(context)
        userDb.errorLogDao().deleteAll()
        userDb.attemptLogDao().deleteAll()
        userDb.mockExamResultDao().deleteAll()
        userDb.aiResponseCacheDao().deleteAll()
        // Phase 4 B3: aiSettings и SecureKeyStore.
        // aiSettings reset до дефолтов (OpenRouter + deepseek free).
        AiSettingsStore.clearAll(context)
        // API ключи также сбрасываем — пользователь "сбрасывает прогресс",
        // ключи привязаны к нему, не к устройству на новом начале.
        SecureKeyStore(context).clearAll()
        // Phase 5 Stage E5 — SRS-карточки + streak.
        userDb.srsCardDao().deleteAll()
        com.daniel.ege100.srs.SrsStreakStore.reset(context)
    }
}
