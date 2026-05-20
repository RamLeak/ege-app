package com.daniel.ege100.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daniel.ege100.data.AppSettings
import com.daniel.ege100.data.AppSettingsStore
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.GuardsState
import com.daniel.ege100.data.MockExamSchedule
import com.daniel.ege100.data.PredictorResult
import com.daniel.ege100.data.Quote
import com.daniel.ege100.data.QuotesRepository
import com.daniel.ege100.data.SafetyGuardsChecker
import com.daniel.ege100.data.SafetyGuardsStore
import com.daniel.ege100.data.ScorePredictor
import com.daniel.ege100.data.StreakState
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.SubtypeAccuracy
import com.daniel.ege100.data.SubtypeStatsRepository
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserProfile
import com.daniel.ege100.data.UserProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Phase 3 Stage B — стейт главного экрана.
 *
 * Reactive часть (profile, streak, settings) — обновляется мгновенно через
 * combine трёх Flow.
 *
 * Computed часть (quote, math/rus predictor, subtypeStats, weakMixPreview) —
 * пересчитывается при `refresh()` который зовётся из `LaunchedEffect(Unit)`
 * каждый раз когда HomeScreen становится видим.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val streak: StreakState = StreakState(),
    val settings: AppSettings = AppSettings(),
    val quote: Quote? = null,
    val mathResult: PredictorResult? = null,
    val rusResult: PredictorResult? = null,
    val stats: List<SubtypeAccuracy> = emptyList(),
    val daysUntilNextMock: Int = 28,
    val hasWeakMix: Boolean = false,
    val guards: GuardsState = GuardsState(),
    /**
     * Phase 5 Stage E3 — счётчик SRS-карточек на повторение. Пересчитывается
     * в refresh(). Если 0 — HomeSrsBlock не показывается.
     */
    val srsDueCount: Int = 0,
    /**
     * Phase 5 Stage E4 — текущий SRS-streak (отдельный от обычного StreakStore).
     */
    val srsStreak: Int = 0,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val attemptDao = UserDataDatabase.get(app).attemptLogDao()
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                UserProfileStore.profileFlow(app),
                StreakStore.stateFlow(app),
                AppSettingsStore.settingsFlow(app),
                SafetyGuardsStore.stateFlow(app),
            ) { profile, streak, settings, guards ->
                HomeReactivePart(profile, streak, settings, guards)
            }.collect { part ->
                _state.value = _state.value.copy(
                    profile = part.profile,
                    streak = part.streak,
                    settings = part.settings,
                    guards = part.guards,
                )
            }
        }

        // Streak-валидация + SafetyGuards проверки при старте.
        viewModelScope.launch {
            StreakStore.checkValidity(getApplication())
            // Phase 5 Stage E4 — обнуление SRS-streak если gap > 1 день.
            runCatching {
                com.daniel.ege100.srs.SrsStreakStore.checkValidity(getApplication())
            }
            SafetyGuardsChecker.checkWeekly(getApplication(), attemptDao)
            SafetyGuardsChecker.checkEightWeek(getApplication(), attemptDao)
        }
    }

    private data class HomeReactivePart(
        val profile: UserProfile,
        val streak: StreakState,
        val settings: AppSettings,
        val guards: GuardsState,
    )

    fun dismissEightWeekGuard() {
        viewModelScope.launch { SafetyGuardsStore.dismissEightWeek(getApplication()) }
    }

    /** Зовётся из HomeScreen.LaunchedEffect(Unit) — при первом show и при возвратах. */
    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val profile = UserProfileStore.snapshot(ctx)

            val quote = QuotesRepository.getTodayQuote(ctx)
            val math = ScorePredictor.predictMath(ctx)
            val rus = ScorePredictor.predictRus(ctx)
            val mathStats = SubtypeStatsRepository.getStatsForSubject(ctx, dao, "mathb", "math")
            val rusStats = SubtypeStatsRepository.getStatsForSubject(ctx, dao, "rus", "rus")
            val combined = mathStats + rusStats
            // Phase 3 Stage FINAL — реальный расчёт через MockExamSchedule.
            val daysUntilMock = MockExamSchedule.getDaysUntilNext(ctx, profile.examDateParsed) ?: 0

            // Phase 5 Stage E3 — счётчик SRS-карточек на повторение.
            val srsDue = runCatching {
                com.daniel.ege100.srs.SrsRepository.countDueToday(ctx)
            }.getOrDefault(0)
            // Phase 5 Stage E4 — текущий SRS-streak.
            val srsStreak = runCatching {
                com.daniel.ege100.srs.SrsStreakStore.snapshot(ctx).currentStreak
            }.getOrDefault(0)

            _state.value = _state.value.copy(
                loading = false,
                quote = quote,
                mathResult = math,
                rusResult = rus,
                stats = combined,
                daysUntilNextMock = daysUntilMock,
                hasWeakMix = combined.any { it.attempts > 0 },
                srsDueCount = srsDue,
                srsStreak = srsStreak,
            )
        }
    }

    /** Зовётся при тапе на «🎯 Решить слабые места». */
    suspend fun composeWeakMix(): List<Long> =
        SubtypeStatsRepository.composeWeakMix(getApplication(), dao)
}
