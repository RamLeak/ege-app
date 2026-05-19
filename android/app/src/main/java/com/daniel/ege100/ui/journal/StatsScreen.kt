package com.daniel.ege100.ui.journal

import android.app.Application
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.daniel.ege100.ui.common.SmoothLazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.CatalogDao
import com.daniel.ege100.data.DailyStat
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.FavoritesStore
import com.daniel.ege100.data.ProgressRepository
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.TypeAccuracy
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.daysWord
import com.daniel.ege100.ui.common.problemsWord
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.Separator
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// ---------------------------------------------------------------------------
// State + ViewModel
// ---------------------------------------------------------------------------

data class StatsUi(
    val loading: Boolean = true,
    val totalAttempts: Int = 0,
    val totalCorrect: Int = 0,
    val avgDurationMs: Long = 0L,
    val dailyStats: List<DailyStat> = emptyList(),
    val mathTypes: List<TypeWithTitle> = emptyList(),
    val rusTypes: List<TypeWithTitle> = emptyList(),
    val maxStreak: Int = 0,
    val typesCovered: Int = 0,
    val totalTypes: Int = 46,
    val wordsLearned: Int = 0,
    val favoritesCount: Int = 0,
    /**
     * Phase 4 Stage P4-D2 part В (Convention #66) — typeId → mastered.
     * Используется в TypeAccuracyRow чтобы поставить ✓ только когда ВСЕ
     * задачи типа решены правильно (а не по старой метрике
     * `attempts >= 15 && accuracy >= 70%`).
     */
    val masteredTypes: Set<Long> = emptySet(),
)

data class TypeWithTitle(
    val accuracy: TypeAccuracy,
    val title: String?,
    /** Phase 4 Stage P4-D2 part В: освоен ли тип целиком (solved == total). */
    val mastered: Boolean = false,
    /** total задач в типе из corpus.db — для отображения N/M в строке. */
    val totalProblems: Int = 0,
    /** solved (последняя попытка верная) — для N/M. */
    val solvedProblems: Int = 0,
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val userDb = UserDataDatabase.get(app)
    private val attemptDao = userDb.attemptLogDao()
    private val catalogDao: CatalogDao = EgeDatabase.get(app).catalogDao()

    private val _state = MutableStateFlow(StatsUi())
    val state: StateFlow<StatsUi> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val now = System.currentTimeMillis()
            val daysMs = 30L * 24 * 60 * 60 * 1000
            val totalAttempts = attemptDao.getTotalCount()
            val totalCorrect = attemptDao.getCorrectCountSince(0L)
            val avgDuration = (attemptDao.getAverageDurationMs() ?: 0.0).toLong()
            val daily = attemptDao.getDailyStats(now - daysMs)

            // Type stats для math и rus.
            val mathStats = UserStatsStore.getTypeStats(ctx, "math", 19)
            val rusStats = UserStatsStore.getTypeStats(ctx, "rus", 27)

            // Подтягиваем title типов из corpus.db + прогресс (Phase 4 P4-D2 В).
            val mathSubject = catalogDao.getSubjectBySlug("mathb")
            val rusSubject = catalogDao.getSubjectBySlug("rus")
            val mathProgress = mathSubject?.let { ProgressRepository.getTypeProgress(ctx, it.id) } ?: emptyMap()
            val rusProgress = rusSubject?.let { ProgressRepository.getTypeProgress(ctx, it.id) } ?: emptyMap()
            val mathTypes = if (mathSubject != null) {
                val typeRows = catalogDao.getTypesBySubject(mathSubject.id)
                val titles = typeRows.associate { it.number to it.title }
                val typeIdByNumber = typeRows.associate { it.number to it.id }
                mathStats.map { acc ->
                    val tid = typeIdByNumber[acc.typeNumber]
                    val p = tid?.let { mathProgress[it] }
                    TypeWithTitle(
                        accuracy = acc,
                        title = titles[acc.typeNumber],
                        mastered = p?.isMastered ?: false,
                        totalProblems = p?.total ?: 0,
                        solvedProblems = p?.solved ?: 0,
                    )
                }
            } else emptyList()
            val rusTypes = if (rusSubject != null) {
                val typeRows = catalogDao.getTypesBySubject(rusSubject.id)
                val titles = typeRows.associate { it.number to it.title }
                val typeIdByNumber = typeRows.associate { it.number to it.id }
                rusStats.map { acc ->
                    val tid = typeIdByNumber[acc.typeNumber]
                    val p = tid?.let { rusProgress[it] }
                    TypeWithTitle(
                        accuracy = acc,
                        title = titles[acc.typeNumber],
                        mastered = p?.isMastered ?: false,
                        totalProblems = p?.total ?: 0,
                        solvedProblems = p?.solved ?: 0,
                    )
                }
            } else emptyList()

            // Achievements.
            val streak = StreakStore.snapshot(ctx)
            // Phase 4 Stage P4-C part Е1 (Convention #54) — fix «Слов в тренажёре».
            // Раньше суммировали число ошибок (accentErrors + wordBlankErrors),
            // что отрицательно мотивирует и часто остаётся 0 у активного
            // пользователя. Теперь используем явный счётчик правильных
            // ответов в тренажёрах из UserStatsStore.
            val wordsLearned = UserStatsStore.getTrainerWordsLearned(ctx)
            val favs = FavoritesStore.snapshot(ctx).size
            // Phase 4 Stage P4-D2 part В (Convention #66): «освоено» = ВСЕ
            // задачи типа решены правильно по последней попытке. Раньше
            // (Convention #37) было «15+ попыток И accuracy ≥ 70%» — это
            // мягкая метрика, но в каталоге пользователь видит прогресс-бар
            // и хочет конкретный «100%» badge.
            val typesCovered = mathProgress.values.count { it.isMastered } +
                rusProgress.values.count { it.isMastered }

            _state.value = StatsUi(
                loading = false,
                totalAttempts = totalAttempts,
                totalCorrect = totalCorrect,
                avgDurationMs = avgDuration,
                dailyStats = daily,
                mathTypes = mathTypes,
                rusTypes = rusTypes,
                maxStreak = streak.maxStreak,
                typesCovered = typesCovered,
                totalTypes = 46,
                wordsLearned = wordsLearned,
                favoritesCount = favs,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun StatsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    vm: StatsViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Статистика",
                subtitle = "Анализ за всё время и за 30 дней",
                onBack = onBack,
            )
        },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            if (st.loading) {
                Text(
                    "Загрузка…",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (st.totalAttempts == 0) {
                EmptyState(
                    emoji = "📊",
                    title = "Нет данных",
                    subtitle = "Реши хотя бы одну задачу — и здесь появятся графики и таблицы.",
                )
            } else {
                SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item("overview") {
                        OverviewCard(
                            total = st.totalAttempts,
                            correct = st.totalCorrect,
                            avgMs = st.avgDurationMs,
                        )
                    }
                    item("activity_title") { SectionTitle("📈 Активность за 30 дней") }
                    item("activity_chart") {
                        ActivityChartCard(dailyStats = st.dailyStats)
                    }
                    item("math_title") { SectionTitle("🎯 По типам · Математика") }
                    item("math_table") {
                        TypeAccuracyTableCard(types = st.mathTypes, emptyText = "Нет попыток в математике")
                    }
                    item("rus_title") { SectionTitle("🎯 По типам · Русский") }
                    item("rus_table") {
                        TypeAccuracyTableCard(types = st.rusTypes, emptyText = "Нет попыток в русском")
                    }
                    item("achievements_title") { SectionTitle("🏆 Достижения") }
                    item("achievements") {
                        AchievementsRow(
                            maxStreak = st.maxStreak,
                            typesCovered = st.typesCovered,
                            totalTypes = st.totalTypes,
                            wordsLearned = st.wordsLearned,
                            favoritesCount = st.favoritesCount,
                        )
                    }
                    item("footer_pad") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LabelTertiary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// OverviewCard
// ---------------------------------------------------------------------------

@Composable
private fun OverviewCard(total: Int, correct: Int, avgMs: Long) {
    val accuracy = if (total > 0) correct.toFloat() / total else 0f
    AppleCard(paddingDp = 22) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "📊 Обзор", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Label)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$total",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                )
                Text(
                    text = " ${problemsWord(total)} всего",
                    fontSize = 14.sp,
                    color = LabelSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            val accuracyColor = when {
                accuracy >= 0.8f -> SystemGreen
                accuracy >= 0.6f -> SystemOrange
                else -> SystemRed
            }
            Text(
                text = "Правильных $correct (${(accuracy * 100).toInt()}%)",
                fontSize = 14.sp,
                color = accuracyColor,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            AppleProgressBar(progress = accuracy)
            if (avgMs > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Среднее время на задачу: ${formatDuration(avgMs)}",
                    fontSize = 12.sp,
                    color = LabelTertiary,
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s} c"
}

// ---------------------------------------------------------------------------
// ActivityChart
// ---------------------------------------------------------------------------

/**
 * Compose Canvas-столбцы за 30 дней. Если данных нет за день — серая
 * фоновая ячейка. Цвет колонки определяется accuracy: ≥80% green,
 * ≥60% blue, иначе orange.
 */
@Composable
private fun ActivityChartCard(dailyStats: List<DailyStat>) {
    AppleCard(paddingDp = 18) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (dailyStats.isEmpty()) {
                Text(
                    text = "Нет активности за 30 дней",
                    fontSize = 14.sp,
                    color = LabelSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                return@AppleCard
            }

            // Достроим до 30 дней назад → сегодня (даже если в DailyStat не каждый день).
            val daysWindow = buildList {
                val today = LocalDate.now()
                for (i in 29 downTo 0) {
                    val date = today.minusDays(i.toLong()).toString()
                    val match = dailyStats.firstOrNull { it.day == date }
                    add(match ?: DailyStat(day = date, total = 0, correct = 0))
                }
            }
            val maxTotal = daysWindow.maxOf { it.total }.coerceAtLeast(1)
            val accentColors = daysWindow.map { stat ->
                if (stat.total == 0) LabelTertiary.copy(alpha = 0.15f) else {
                    val acc = stat.correct.toFloat() / stat.total
                    when {
                        acc >= 0.8f -> SystemGreen
                        acc >= 0.6f -> SystemBlue
                        else -> SystemOrange
                    }
                }
            }
            val trackColor = LabelTertiary.copy(alpha = 0.12f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val n = daysWindow.size
                val totalGap = size.width * 0.20f
                val barW = (size.width - totalGap) / n
                val gap = totalGap / (n - 1).coerceAtLeast(1)
                val cornerRadius = CornerRadius(4f, 4f)
                daysWindow.forEachIndexed { i, stat ->
                    val x = i * (barW + gap)
                    val height = (stat.total.toFloat() / maxTotal) * size.height
                    val y = size.height - height
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x, 0f),
                        size = Size(barW, size.height),
                        cornerRadius = cornerRadius,
                    )
                    if (height > 0f) {
                        drawRoundRect(
                            color = accentColors[i],
                            topLeft = Offset(x, y),
                            size = Size(barW, height),
                            cornerRadius = cornerRadius,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Подписи: первый, середина, последний.
            Row(modifier = Modifier.fillMaxWidth()) {
                LabelDate(daysWindow.first().day, Modifier.weight(1f), TextAlign.Start)
                LabelDate(daysWindow[daysWindow.size / 2].day, Modifier.weight(1f), TextAlign.Center)
                LabelDate(daysWindow.last().day, Modifier.weight(1f), TextAlign.End)
            }
            Spacer(Modifier.height(8.dp))
            val totalCount = daysWindow.sumOf { it.total }
            val correctCount = daysWindow.sumOf { it.correct }
            val acc = if (totalCount > 0) correctCount.toFloat() / totalCount else 0f
            Text(
                text = "$totalCount ${problemsWord(totalCount)} за 30 дней · точность ${(acc * 100).toInt()}%",
                fontSize = 12.sp,
                color = LabelTertiary,
            )
        }
    }
}

@Composable
private fun LabelDate(date: String, modifier: Modifier, align: TextAlign) {
    // "2026-05-18" → "18.05"
    val short = runCatching {
        val parts = date.split('-')
        "${parts[2]}.${parts[1]}"
    }.getOrDefault(date)
    Text(
        text = short,
        fontSize = 11.sp,
        color = LabelTertiary,
        textAlign = align,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// TypeAccuracyTable
// ---------------------------------------------------------------------------

@Composable
private fun TypeAccuracyTableCard(types: List<TypeWithTitle>, emptyText: String) {
    AppleCard(paddingDp = 16) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val rows = types.filter { it.accuracy.attempts > 0 }
            if (rows.isEmpty()) {
                Text(
                    text = emptyText,
                    fontSize = 14.sp,
                    color = LabelSecondary,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                rows.forEachIndexed { idx, tw ->
                    TypeAccuracyRow(tw)
                    if (idx != rows.size - 1) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Separator),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeAccuracyRow(tw: TypeWithTitle) {
    val acc = tw.accuracy.accuracy
    // Phase 4 Stage P4-D2 part В (Convention #66): ✓ метка только если ВСЕ
    // задачи типа решены правильно — это та же логика что в каталоге.
    val mastered = tw.mastered
    val color = when {
        acc >= 0.8f -> SystemGreen
        acc >= 0.6f -> SystemBlue
        else -> SystemOrange
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "№${tw.accuracy.typeNumber}",
                fontSize = 14.sp,
                color = LabelSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(40.dp),
            )
            Text(
                text = tw.title ?: "тип ${tw.accuracy.typeNumber}",
                fontSize = 14.sp,
                color = Label,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (mastered) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "✓",
                    fontSize = 14.sp,
                    color = SystemGreen,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(acc * 100).toInt()}%",
                fontSize = 13.sp,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(40.dp))
            AppleProgressBar(progress = acc, height = 4.dp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${tw.accuracy.correct}/${tw.accuracy.attempts}",
                fontSize = 11.sp,
                color = LabelTertiary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// AchievementsRow
// ---------------------------------------------------------------------------

@Composable
private fun AchievementsRow(
    maxStreak: Int,
    typesCovered: Int,
    totalTypes: Int,
    wordsLearned: Int,
    favoritesCount: Int,
) {
    AppleCard(paddingDp = 16) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AchievementRow(
                emoji = "🔥",
                title = "Максимальный streak",
                value = "$maxStreak ${daysWord(maxStreak)}",
                accent = maxStreak > 0,
            )
            ThinDivider()
            AchievementRow(
                emoji = "💪",
                title = "Освоено типов",
                value = "$typesCovered из $totalTypes",
                hint = "Все задачи типа решены правильно",
                accent = typesCovered > 0,
            )
            ThinDivider()
            AchievementRow(
                emoji = "📚",
                title = "Слов в тренажёрах",
                value = "$wordsLearned",
                accent = wordsLearned > 0,
            )
            ThinDivider()
            AchievementRow(
                emoji = "⭐",
                title = "Избранных задач",
                value = "$favoritesCount",
                accent = favoritesCount > 0,
            )
        }
    }
}

@Composable
private fun AchievementRow(
    emoji: String,
    title: String,
    value: String,
    hint: String? = null,
    accent: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SystemBlueTint),
        ) {
            Text(text = emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = Label,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    color = LabelTertiary,
                )
            }
        }
        // Phase 3 Stage FINAL part Е: серый цвет когда значение «нулевое»
        // (Convention #37 для typesCovered=0 + единый паттерн для всех).
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) SystemBlue else LabelSecondary,
        )
    }
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Separator),
    )
}

