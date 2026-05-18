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
    // Phase 3 Stage B: добавлены stats и streak. Старые бэкапы (1.0) без них
    // парсятся OK благодаря default-значениям + ignoreUnknownKeys.
    val userStats: UserStatsSnapshot = UserStatsSnapshot(),
    val streak: StreakState = StreakState(),
) {
    companion object {
        const val CURRENT_VERSION = "1.1"
        /** Версии, которые мы можем восстановить (forward-compat). */
        private val SUPPORTED_VERSIONS = setOf("1.0", "1.1")
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
                ImportResult.Success(snapshot.exportedAt)
            } catch (e: Throwable) {
                ImportResult.Error(e.message ?: "Неизвестная ошибка")
            }
        }

    /**
     * Phase 3 Stage A part Д6 + Stage B расширение — сброс прогресса.
     *
     * Удаляет: тренажёры, избранное, ошибки, статистику попыток, streak.
     * Сохраняет: профиль, настройки.
     */
    suspend fun resetProgress(context: Context) = withContext(Dispatchers.IO) {
        TrainerProgressStore.clearAll(context)
        FavoritesStore.clearAll(context)
        AccentErrorsStore.clearAll(context)
        WordBlankErrorsStore.clearAll(context)
        UserStatsStore.clearAll(context)
        StreakStore.clearAll(context)
    }
}
