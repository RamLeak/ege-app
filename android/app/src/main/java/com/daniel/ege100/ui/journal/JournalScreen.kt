package com.daniel.ege100.ui.journal

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.daniel.ege100.ui.common.SmoothLazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.FavoritesStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.problemsWord
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.Separator
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 3 Stage C — обновлённый Журнал.
 *
 *   JournalSummaryCard сверху (сегодня + точность + всего)
 *   ───────────────────────────
 *   ⭐ Избранные задачи (N)
 *   📝 Ошибки (всего · неперерешённые)
 *   📊 Статистика
 *   📤 Экспорт CSV
 */
data class JournalUi(
    val loading: Boolean = true,
    val todaySolved: Int = 0,
    val todayCorrect: Int = 0,
    val totalSolved: Int = 0,
    val favoritesCount: Int = 0,
    val errorsCount: Int = 0,
    val unresolvedErrors: Int = 0,
)

class JournalViewModel(app: Application) : AndroidViewModel(app) {
    private val userDb = UserDataDatabase.get(app)
    private val attemptDao = userDb.attemptLogDao()
    private val errorDao = userDb.errorLogDao()

    private val _state = MutableStateFlow(JournalUi())
    val state: StateFlow<JournalUi> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val startOfToday = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val todayTotal = attemptDao.getCountSince(startOfToday)
            val todayCorrect = attemptDao.getCorrectCountSince(startOfToday)
            val total = attemptDao.getTotalCount()
            val favs = FavoritesStore.snapshot(getApplication()).size
            val errorsTotal = errorDao.getTotalCount()
            val unresolved = errorDao.getUnresolvedCount()
            _state.value = JournalUi(
                loading = false,
                todaySolved = todayTotal,
                todayCorrect = todayCorrect,
                totalSolved = total,
                favoritesCount = favs,
                errorsCount = errorsTotal,
                unresolvedErrors = unresolved,
            )
        }
    }
}

@Composable
fun JournalScreen(
    contentPadding: PaddingValues,
    onFavoritesClick: () -> Unit,
    onErrorsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onCsvExportClick: () -> Unit,
    vm: JournalViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = { LargeTitleBar(title = "Журнал", subtitle = "Прогресс и работа над ошибками") },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            SmoothLazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("summary") {
                    JournalSummaryCard(
                        todaySolved = st.todaySolved,
                        todayCorrect = st.todayCorrect,
                        totalSolved = st.totalSolved,
                    )
                }
                item("sections_title") { SectionLabel("Разделы") }
                item("favorites") {
                    AppleListRow(
                        title = "Избранные задачи",
                        subtitle = if (st.favoritesCount > 0) "${st.favoritesCount} ${problemsWord(st.favoritesCount)}" else "Пока пусто",
                        leadingEmoji = "⭐",
                        leadingTint = Color(0x26FFD60A),
                        onClick = onFavoritesClick,
                    )
                }
                item("errors") {
                    AppleListRow(
                        title = "Ошибки",
                        subtitle = if (st.errorsCount == 0) "Пока пусто" else
                            "${st.errorsCount} всего · ${st.unresolvedErrors} неперерешённых",
                        leadingEmoji = "📝",
                        leadingTint = Color(0x26FF453A),
                        onClick = onErrorsClick,
                    )
                }
                item("stats") {
                    AppleListRow(
                        title = "Статистика",
                        subtitle = "Графики, анализ по типам, достижения",
                        leadingEmoji = "📊",
                        leadingTint = Color(0x260A84FF),
                        onClick = onStatsClick,
                    )
                }
                item("csv") {
                    AppleListRow(
                        title = "Экспорт CSV",
                        subtitle = "Сырая история попыток для Excel/Sheets",
                        leadingEmoji = "📤",
                        leadingTint = Color(0x2630D158),
                        onClick = onCsvExportClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalSummaryCard(
    todaySolved: Int,
    todayCorrect: Int,
    totalSolved: Int,
) {
    val accuracy = if (todaySolved > 0) todayCorrect.toFloat() / todaySolved else 0f
    val accuracyColor = when {
        todaySolved == 0 -> LabelTertiary
        accuracy >= 0.8f -> SystemGreen
        accuracy >= 0.6f -> SystemOrange
        else -> SystemRed
    }
    AppleCard(paddingDp = 22) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Сегодня",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LabelSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$todaySolved",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                    lineHeight = 52.sp,
                )
                Text(
                    text = " ${problemsWord(todaySolved)}",
                    fontSize = 17.sp,
                    color = LabelSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (todaySolved > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Точность ${(accuracy * 100).toInt()}% · $todayCorrect из $todaySolved",
                    fontSize = 14.sp,
                    color = accuracyColor,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Реши хотя бы 10 для streak",
                    fontSize = 14.sp,
                    color = LabelTertiary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Separator),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Всего за всё время: $totalSolved ${problemsWord(totalSolved)}",
                fontSize = 14.sp,
                color = LabelSecondary,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LabelTertiary,
        modifier = Modifier.padding(start = 4.dp),
    )
}
