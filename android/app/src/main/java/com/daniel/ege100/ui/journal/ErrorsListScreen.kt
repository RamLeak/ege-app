package com.daniel.ege100.ui.journal

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.ErrorLogEntity
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// State + ViewModel
// ---------------------------------------------------------------------------

enum class ErrorFilter(val label: String) {
    ALL("Все"),
    UNRESOLVED("Только неперерешённые"),
    LAST_WEEK("За последнюю неделю"),
    BY_TYPE_MATH("Только математика"),
    BY_TYPE_RUS("Только русский"),
}

/**
 * View-model-данные одной ошибки. Объединяет error_log (user_answer / correct
 * / timestamp / resolved) и задачу из corpus.db (statement / type info).
 * Если задача в corpus.db не найдена — `problem == null` (после миграции
 * парсера могут быть orphan'ы; UI показывает «задача недоступна»).
 */
data class ErrorRow(
    val error: ErrorLogEntity,
    val problem: ProblemEntity?,
    val typeNumber: Int?,
    val subjectSlug: String?,
)

data class ErrorsListState(
    val loading: Boolean = true,
    val rows: List<ErrorRow> = emptyList(),
    val totalCount: Int = 0,
    val unresolvedCount: Int = 0,
    val filter: ErrorFilter = ErrorFilter.ALL,
)

class ErrorsListViewModel(app: Application) : AndroidViewModel(app) {
    private val catalogDao = EgeDatabase.get(app).catalogDao()
    private val userDb = UserDataDatabase.get(app)
    private val errorLogDao = userDb.errorLogDao()

    private val _state = MutableStateFlow(ErrorsListState())
    val state: StateFlow<ErrorsListState> = _state.asStateFlow()

    init { refresh() }

    fun setFilter(filter: ErrorFilter) {
        _state.value = _state.value.copy(filter = filter)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val filter = _state.value.filter
            val now = System.currentTimeMillis()
            val weekMs = 7L * 24 * 60 * 60 * 1000

            val raw = when (filter) {
                ErrorFilter.ALL -> errorLogDao.getRecent(limit = 500)
                ErrorFilter.UNRESOLVED -> errorLogDao.getUnresolved(limit = 500)
                ErrorFilter.LAST_WEEK -> errorLogDao.getSince(now - weekMs, 500)
                ErrorFilter.BY_TYPE_MATH, ErrorFilter.BY_TYPE_RUS -> errorLogDao.getRecent(limit = 1000)
            }
            // Pre-load problem details + subject for каждой ошибки.
            val rows = raw.map { e ->
                val problem = catalogDao.getProblem(e.problemId)
                val type = problem?.let { catalogDao.getType(it.typeId) }
                val subject = problem?.let { catalogDao.getSubject(it.subjectId) }
                ErrorRow(
                    error = e,
                    problem = problem,
                    typeNumber = type?.number,
                    subjectSlug = subject?.slug,
                )
            }
            val filtered = when (filter) {
                ErrorFilter.BY_TYPE_MATH -> rows.filter { it.subjectSlug == "mathb" }
                ErrorFilter.BY_TYPE_RUS -> rows.filter { it.subjectSlug == "rus" }
                else -> rows
            }
            val total = errorLogDao.getTotalCount()
            val unresolved = errorLogDao.getUnresolvedCount()
            _state.value = ErrorsListState(
                loading = false,
                rows = filtered,
                totalCount = total,
                unresolvedCount = unresolved,
                filter = filter,
            )
        }
    }

    fun delete(errorId: Long) {
        viewModelScope.launch {
            errorLogDao.delete(errorId)
            refresh()
        }
    }

    fun markResolved(errorId: Long) {
        viewModelScope.launch {
            errorLogDao.markResolved(errorId)
            refresh()
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun ErrorsListScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRetry: (problemId: Long, typeId: Long, subtypeId: Long?) -> Unit,
    vm: ErrorsListViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Ошибки",
                subtitle = "${st.totalCount} всего · ${st.unresolvedCount} неперерешённых",
                onBack = onBack,
                rightContent = {
                    FilterChip(
                        label = st.filter.label,
                        active = st.filter != ErrorFilter.ALL,
                        onClick = { showFilterSheet = true },
                    )
                },
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
            when {
                st.loading -> Text(
                    "Загрузка…",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                st.rows.isEmpty() -> EmptyState(
                    emoji = "🎯",
                    title = if (st.filter == ErrorFilter.ALL) "Пока ошибок нет" else "Ничего не нашлось",
                    subtitle = if (st.filter == ErrorFilter.ALL) {
                        "Реши задачи в каталоге — сюда попадут ошибки для повторного прохождения."
                    } else {
                        "Попробуй сменить фильтр."
                    },
                )
                else -> SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(st.rows, key = { it.error.id }) { row ->
                        ErrorCard(
                            row = row,
                            onRetry = {
                                val p = row.problem ?: return@ErrorCard
                                onRetry(p.id, p.typeId, p.subtypeId)
                            },
                            onDelete = { vm.delete(row.error.id) },
                            onMarkResolved = { vm.markResolved(row.error.id) },
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            current = st.filter,
            onSelect = { vm.setFilter(it); showFilterSheet = false },
            onDismiss = { showFilterSheet = false },
        )
    }
}

// ---------------------------------------------------------------------------
// ErrorCard
// ---------------------------------------------------------------------------

private val DATE_FORMAT_RU = SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru"))

private fun formatTimestamp(ms: Long): String = DATE_FORMAT_RU.format(Date(ms))

private val HTML_TAGS = Regex("<[^>]+>")
private val NBSP = Regex("&nbsp;|&#160;")
private val WHITESPACE = Regex("\\s+")

private fun previewText(html: String, limit: Int = 140): String {
    val cleaned = html.replace(HTML_TAGS, " ").replace(NBSP, " ").replace(WHITESPACE, " ").trim()
    return if (cleaned.length <= limit) cleaned else cleaned.take(limit).trimEnd() + "…"
}

private fun subjectBadge(slug: String?): String = when (slug) {
    "mathb" -> "Мат"
    "rus" -> "Рус"
    else -> "?"
}

@Composable
private fun ErrorCard(
    row: ErrorRow,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onMarkResolved: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // Phase 3 Stage C2 правка 1: правильный ответ скрыт под тап.
    // Если задача уже перерешана (isResolved) — пользователь ответ уже знает,
    // показываем сразу без тапа.
    var revealCorrect by remember(row.error.id) { mutableStateOf(row.error.isResolved) }
    AppleCard(paddingDp = 16) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(subjectSlug = row.subjectSlug, typeNumber = row.typeNumber)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(row.error.timestamp),
                    fontSize = 12.sp,
                    color = LabelTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (row.error.isResolved) {
                    ResolvedBadge()
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = row.problem?.statementHtml?.let { previewText(it) } ?: "Задача недоступна",
                fontSize = 15.sp,
                color = Label,
                maxLines = 3,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(12.dp))
            AnswerBlock(
                userAnswer = row.error.userAnswer,
                correctAnswer = row.error.correctAnswer,
                revealed = revealCorrect,
                onReveal = { revealCorrect = true },
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "🔁 Перерешать",
                    onClick = onRetry,
                    enabled = row.problem != null,
                    modifier = Modifier.weight(1f),
                )
                IconCircleButton(
                    emoji = if (row.error.isResolved) "↺" else "✓",
                    tint = if (row.error.isResolved) LabelTertiary else SystemGreen,
                    onClick = onMarkResolved,
                )
                IconCircleButton(emoji = "🗑", tint = SystemRed, onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onDelete()
                })
            }
        }
    }
}

@Composable
private fun TypeBadge(subjectSlug: String?, typeNumber: Int?) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SystemBlueTint)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "${subjectBadge(subjectSlug)} №${typeNumber ?: "?"}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = SystemBlue,
        )
    }
}

@Composable
private fun ResolvedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SystemGreenTint)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "✓ Перерешана",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SystemGreen,
        )
    }
}

/**
 * Phase 3 Stage C2 правка 1 — правильный ответ скрыт по умолчанию.
 *
 * Строка с ответом пользователя видна сразу (красным). Правильный ответ
 * либо скрыт под маленькой ссылкой «Показать правильный ответ», либо
 * раскрыт (если пользователь нажал ссылку или задача уже isResolved).
 *
 * Это сохраняет смысл кнопки «Перерешать» — пользователь идёт решать,
 * не зная ответа.
 */
@Composable
private fun AnswerBlock(
    userAnswer: String,
    correctAnswer: String,
    revealed: Boolean,
    onReveal: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Твой: ", fontSize = 13.sp, color = LabelSecondary)
            Text(
                text = userAnswer.ifBlank { "—" },
                fontSize = 14.sp,
                color = SystemRed,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        if (revealed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Верно: ", fontSize = 13.sp, color = LabelSecondary)
                Text(
                    text = correctAnswer,
                    fontSize = 14.sp,
                    color = SystemGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Text(
                text = "Показать правильный ответ",
                fontSize = 13.sp,
                color = SystemBlue,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onReveal() }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun IconCircleButton(emoji: String, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
            .clickable { onClick() },
    ) {
        Text(emoji, fontSize = 18.sp, color = tint)
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) SystemBlueTint else BgElevated2)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Фильтр: $label",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) SystemBlue else LabelSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    current: ErrorFilter,
    onSelect: (ErrorFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                "Фильтр ошибок",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(12.dp))
            ErrorFilter.values().forEach { f ->
                FilterRow(label = f.label, selected = f == current, onClick = { onSelect(f) })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FilterRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) SystemBlue else Color.Transparent),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = Label,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = SystemBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Text(emoji, fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                fontSize = 15.sp,
                color = LabelSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}
