package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
    vm: HomeViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = Bg) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            LazyColumn(
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
                if (st.loading) {
                    item("loading") {
                        Text(
                            text = "Загрузка…",
                            color = LabelSecondary,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else {
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
                    item("mock") {
                        MockExamPreviewCard(
                            daysUntilNext = st.daysUntilNextMock,
                            onClick = {
                                // P3-D — пока без действия. Можно открыть toast но это лишнее.
                            },
                        )
                    }
                }
                item("footer_pad") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
