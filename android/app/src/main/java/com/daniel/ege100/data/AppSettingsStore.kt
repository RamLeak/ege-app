package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Phase 3 Stage A part Г — настройки приложения.
 *
 * Хранится в отдельном DataStore. Все настройки изменяемые, применяются
 * мгновенно через collectAsState в MainActivity (тема) или per-screen
 * (радар, уведомления).
 */
enum class ThemeMode { AUTO, DARK, LIGHT }

enum class RadarStyle { LIST, DONUT, HEATMAP, RADAR_CHART }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val radarStyle: RadarStyle = RadarStyle.LIST,
    val notifyMockExams: Boolean = true,
    val notifyStreak: Boolean = true,
    val notifyReminders: Boolean = true,
    /**
     * Phase 4 Stage P4-C2 part Б (Convention #58) — в тренажёрах №9-12
     * показывать кнопки 2-3 букв вместо текстового поля. Default = true:
     * это лучший mobile UX (тап один раз vs «открыть клавиатуру → найти
     * букву → закрыть»).
     */
    val useLetterChoices: Boolean = true,
    /**
     * Phase 4 Stage P4-C3 part В2 (Convention #63) — onboarding-подсказка
     * про свайпы (между задачами + edge swipe-back) была показана
     * пользователю. Default = false → при первом открытии
     * ProblemDetail/Trainer выводится tooltip, после показа = true.
     */
    val swipeHintsShown: Boolean = false,
)

private val Context.appSettingsStore by preferencesDataStore("app_settings")

object AppSettingsStore {
    private val THEME = stringPreferencesKey("theme_mode")
    private val RADAR = stringPreferencesKey("radar_style")
    private val NOTIFY_MOCK = booleanPreferencesKey("notify_mock_exams")
    private val NOTIFY_STREAK = booleanPreferencesKey("notify_streak")
    private val NOTIFY_REMINDERS = booleanPreferencesKey("notify_reminders")
    private val USE_LETTER_CHOICES = booleanPreferencesKey("use_letter_choices")
    private val SWIPE_HINTS_SHOWN = booleanPreferencesKey("swipe_hints_shown")

    fun settingsFlow(context: Context): Flow<AppSettings> =
        context.appSettingsStore.data.map { prefs ->
            AppSettings(
                themeMode = parseEnum(prefs[THEME], ThemeMode.AUTO),
                radarStyle = parseEnum(prefs[RADAR], RadarStyle.LIST),
                notifyMockExams = prefs[NOTIFY_MOCK] ?: true,
                notifyStreak = prefs[NOTIFY_STREAK] ?: true,
                notifyReminders = prefs[NOTIFY_REMINDERS] ?: true,
                useLetterChoices = prefs[USE_LETTER_CHOICES] ?: true,
                swipeHintsShown = prefs[SWIPE_HINTS_SHOWN] ?: false,
            )
        }

    suspend fun snapshot(context: Context): AppSettings =
        settingsFlow(context).first()

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.appSettingsStore.edit { it[THEME] = mode.name }
    }

    suspend fun setRadarStyle(context: Context, style: RadarStyle) {
        context.appSettingsStore.edit { it[RADAR] = style.name }
    }

    suspend fun setNotifyMockExams(context: Context, value: Boolean) {
        context.appSettingsStore.edit { it[NOTIFY_MOCK] = value }
    }

    suspend fun setNotifyStreak(context: Context, value: Boolean) {
        context.appSettingsStore.edit { it[NOTIFY_STREAK] = value }
    }

    suspend fun setNotifyReminders(context: Context, value: Boolean) {
        context.appSettingsStore.edit { it[NOTIFY_REMINDERS] = value }
    }

    suspend fun setUseLetterChoices(context: Context, value: Boolean) {
        context.appSettingsStore.edit { it[USE_LETTER_CHOICES] = value }
    }

    /** Phase 4 Stage P4-C3 part В2 — пометить onboarding-подсказку показанной. */
    suspend fun markSwipeHintsShown(context: Context) {
        context.appSettingsStore.edit { it[SWIPE_HINTS_SHOWN] = true }
    }

    /** Stage P3-A part Д: восстановление из бэкапа. */
    suspend fun restore(context: Context, settings: AppSettings) {
        context.appSettingsStore.edit { prefs ->
            prefs[THEME] = settings.themeMode.name
            prefs[RADAR] = settings.radarStyle.name
            prefs[NOTIFY_MOCK] = settings.notifyMockExams
            prefs[NOTIFY_STREAK] = settings.notifyStreak
            prefs[NOTIFY_REMINDERS] = settings.notifyReminders
            prefs[USE_LETTER_CHOICES] = settings.useLetterChoices
            prefs[SWIPE_HINTS_SHOWN] = settings.swipeHintsShown
        }
    }
}

private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T =
    raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
