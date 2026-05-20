package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.daniel.ege100.ui.common.SmoothLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelSecondary
import kotlinx.coroutines.launch

/**
 * Phase 3 Stage B — Главный экран.
 *
 * Структура:
 *   - HomeHeader (приветствие + аватарка + streak/exam)
 *   - QuoteCard
 *   - PredictorCard (math/rus)
 *   - RadarCard (LIST/DONUT/HEATMAP/RADAR_CHART)
 *   - MockExamPreviewCard
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onProfileClick: () -> Unit,
    onSubtypeClick: (subtypeId: Long, typeId: Long) -> Unit,
    onQuickTrainerStart: (problemIds: List<Long>) -> Unit,
    onMockExamCalendar: () -> Unit,
    onStartMockMath: () -> Unit,
    onStartMockRus: () -> Unit,
    onFipiVariants: () -> Unit,
    onSrsReview: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    // Phase 4 Stage P4-D2 hotfix — defensive try/catch на refresh.
    LaunchedEffect(Unit) {
        try { vm.refresh() } catch (e: Throwable) {
            android.util.Log.e("HomeScreen", "vm.refresh crashed", e)
        }
    }
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = Bg) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            SmoothLazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = 16.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("header") {
                    // Phase 3: добавим status-bar padding на самом контенте
                    // (а не через LargeTitleBar, поскольку шапка тут другая).
                    Spacer(Modifier.height(48.dp))
                    HomeHeader(
                        name = st.profile.name,
                        streak = st.streak.currentStreak,
                        daysUntilExam = st.profile.daysUntilExam(),
                        onAvatarClick = onProfileClick,
                    )
                }
                // Phase 3 Stage FINAL part В — Safety Rule #5 карточка сразу
                // под шапкой если за прошлую неделю было меньше 50 задач.
                if (st.guards.weeklyGuardActive) {
                    item("weekly_guard") {
                        WeeklyGuardCard(weekTotal = st.guards.weeklyAttemptsLastWeek)
                    }
                }
                if (st.loading) {
                    item("loading") {
                        Text(
                            text = "Загрузка…",
                            color = LabelSecondary,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else {
                    val totalAttempts = (st.mathResult?.rawScore ?: 0) +
                        (st.rusResult?.rawScore ?: 0) +
                        st.stats.sumOf { it.attempts }
                    if (totalAttempts == 0 && st.stats.all { it.attempts == 0 }) {
                        // Phase 3 Stage FINAL part Е — welcome для новых.
                        item("welcome") { WelcomeCard() }
                    }
                    st.quote?.let { q ->
                        item("quote") { QuoteCard(q) }
                    }
                    if (st.mathResult != null && st.rusResult != null) {
                        item("predictor") {
                            PredictorCard(
                                math = st.mathResult!!,
                                rus = st.rusResult!!,
                                targetScore = st.profile.targetScore,
                            )
                        }
                    }
                    item("radar") {
                        RadarCard(
                            style = st.settings.radarStyle,
                            stats = st.stats,
                            onSubtypeClick = { sid ->
                                val sub = st.stats.firstOrNull { it.subtypeId == sid }
                                if (sub != null) onSubtypeClick(sid, sub.typeId)
                            },
                            onSolveWeakClick = {
                                scope.launch {
                                    val ids = vm.composeWeakMix()
                                    if (ids.isNotEmpty()) onQuickTrainerStart(ids)
                                }
                            },
                            solveWeakEnabled = st.hasWeakMix,
                        )
                    }
                    // Phase 5 Stage E3 — блок «Повторение на сегодня» после Радара.
                    // Если карточек 0 — блок не отображается (Master prompt §1.4).
                    if (st.srsDueCount > 0) {
                        item("srs") {
                            HomeSrsBlock(
                                dueCount = st.srsDueCount,
                                streak = st.srsStreak,
                                onStart = onSrsReview,
                            )
                        }
                    }
                    item("mock") {
                        MockExamPreviewCard(
                            daysUntilNext = st.daysUntilNextMock,
                            onClick = onMockExamCalendar,
                        )
                    }
                    // Phase 4 Stage B3 — QuickActions: пробники + ФИПИ. Тренажёрные
                    // кнопки убраны в P4-D4 (Convention #81), тренажёры через каталог.
                    item("quick_actions") {
                        QuickActionsCard(
                            onStartMockMath = onStartMockMath,
                            onStartMockRus = onStartMockRus,
                            onFipiVariants = onFipiVariants,
                        )
                    }
                }
                item("footer_pad") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Phase 3 Stage FINAL part В — Safety Rule #6 8-week checkpoint модалка.
    if (st.guards.eightWeekGuardActive) {
        EightWeekCheckpointDialog(
            periodTotal = st.guards.eightWeekAttemptsLastPeriod,
            onConfirm = { vm.dismissEightWeekGuard() },
        )
    }
}
