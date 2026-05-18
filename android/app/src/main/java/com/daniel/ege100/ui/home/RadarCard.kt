package com.daniel.ege100.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.data.RadarStyle
import com.daniel.ege100.data.Severity
import com.daniel.ege100.data.SubtypeAccuracy
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phase 3 Stage B part Д — радар слабых мест.
 *
 * Стиль определяется AppSettings.radarStyle. При смене в Настройках UI
 * перекомпозируется мгновенно (Convention #21 + collectAsState в HomeViewModel).
 *
 *   LIST          — список топ-15 слабых с прогресс-барами по severity.
 *   DONUT         — Canvas-кольцо с топ-10 секторов + центр «N слабых».
 *   HEATMAP       — сетка 7×N с цветными плитками.
 *   RADAR_CHART   — Canvas-лепестковая по 7 крупным темам (агрегация).
 */
@Composable
fun RadarCard(
    style: RadarStyle,
    stats: List<SubtypeAccuracy>,
    onSubtypeClick: (Long) -> Unit,
    onSolveWeakClick: () -> Unit,
    solveWeakEnabled: Boolean,
) {
    AppleCard(paddingDp = 22) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 24.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Слабые места",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
            }
            Spacer(Modifier.height(16.dp))
            when (style) {
                RadarStyle.LIST -> RadarList(stats, onSubtypeClick)
                RadarStyle.DONUT -> RadarDonut(stats, onSubtypeClick)
                RadarStyle.HEATMAP -> RadarHeatmap(stats, onSubtypeClick)
                RadarStyle.RADAR_CHART -> RadarChart(stats)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (solveWeakEnabled) "🎯 Решить слабые места" else "Недостаточно данных",
                onClick = onSolveWeakClick,
                enabled = solveWeakEnabled,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// LIST — топ-15 слабых, отсортированных RED → YELLOW → GREEN
// ---------------------------------------------------------------------------

@Composable
private fun RadarList(stats: List<SubtypeAccuracy>, onClick: (Long) -> Unit) {
    val sorted = stats.filter { it.severity != Severity.GRAY }
        .sortedWith(compareBy({ -it.severity.ordinal }, { it.accuracy }))
        .take(15)
    if (sorted.isEmpty()) {
        EmptyHint("Реши минимум 15 задач в каком-нибудь подвиде, чтобы он появился здесь.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sorted.forEach { sub ->
            SubtypeRow(sub = sub, onClick = { onClick(sub.subtypeId) })
        }
    }
}

@Composable
private fun SubtypeRow(sub: SubtypeAccuracy, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(severityColor(sub.severity)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.subtypeTitle,
                    fontSize = 15.sp,
                    color = Label,
                    maxLines = 1,
                )
                Text(
                    text = "${subjectShort(sub.subjectSlug)} №${sub.typeNumber}  ·  ${sub.attempts} попыток",
                    fontSize = 12.sp,
                    color = LabelTertiary,
                )
            }
            Text(
                text = "${(sub.accuracy * 100).toInt()}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = severityColor(sub.severity),
            )
        }
        Spacer(Modifier.height(6.dp))
        AppleProgressBar(progress = sub.accuracy, height = 4.dp)
    }
}

private fun subjectShort(slug: String): String = when (slug) {
    "mathb" -> "Мат"
    "rus" -> "Рус"
    else -> slug
}

// ---------------------------------------------------------------------------
// DONUT — топ-10 секторов в Canvas-кольце
// ---------------------------------------------------------------------------

@Composable
private fun RadarDonut(stats: List<SubtypeAccuracy>, onClick: (Long) -> Unit) {
    val top = stats.filter { it.severity != Severity.GRAY }
        .sortedBy { it.accuracy }
        .take(10)
    if (top.isEmpty()) {
        EmptyHint("Реши минимум 15 задач в подвиде, чтобы он попал в радар.")
        return
    }
    Column {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            // Соберём «срезы» — каждый занимает 360/n градусов.
            val n = top.size
            // Цвета берём из @Composable getters заранее, чтобы Canvas blocking
            // не ругался.
            val colors = top.map { severityColor(it.severity) }
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)) {
                val sweepPerItem = 360f / n
                val strokeWidth = 28.dp.toPx()
                val pad = strokeWidth / 2 + 4.dp.toPx()
                val arcSize = Size(size.width - 2 * pad, size.height - 2 * pad)
                val arcOffset = Offset(pad, pad)
                colors.forEachIndexed { i, c ->
                    drawArc(
                        color = c,
                        startAngle = -90f + i * sweepPerItem + 1f,
                        sweepAngle = sweepPerItem - 4f,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${top.size}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                )
                Text(
                    text = "слабых подвидов",
                    fontSize = 13.sp,
                    color = LabelSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Легенда — top-5.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            top.take(5).forEach { sub -> DonutLegendRow(sub, onClick = { onClick(sub.subtypeId) }) }
        }
        if (top.size > 5) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "и ещё ${top.size - 5}",
                fontSize = 12.sp,
                color = LabelTertiary,
            )
        }
    }
}

@Composable
private fun DonutLegendRow(sub: SubtypeAccuracy, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(severityColor(sub.severity)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = sub.subtypeTitle,
            fontSize = 14.sp,
            color = Label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = "${(sub.accuracy * 100).toInt()}%",
            fontSize = 13.sp,
            color = severityColor(sub.severity),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------------------
// HEATMAP — сетка 7×N плиток
// ---------------------------------------------------------------------------

@Composable
private fun RadarHeatmap(stats: List<SubtypeAccuracy>, onClick: (Long) -> Unit) {
    val visible = stats.filter { it.attempts > 0 || it.severity != Severity.GRAY }
    if (visible.isEmpty()) {
        EmptyHint("Реши хотя бы несколько задач, чтобы появились тепловые клетки.")
        return
    }
    val cols = 7
    val rows = (visible.size + cols - 1) / cols
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { rowIdx ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(cols) { colIdx ->
                    val sub = visible.getOrNull(rowIdx * cols + colIdx)
                    HeatCell(sub = sub, onClick = sub?.let { { onClick(it.subtypeId) } })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendDot(SystemRed, "<60%")
            LegendDot(SystemOrange, "60-80%")
            LegendDot(SystemGreen, ">80%")
            LegendDot(LabelTertiary, "<15")
        }
    }
}

@Composable
private fun HeatCell(sub: SubtypeAccuracy?, onClick: (() -> Unit)?) {
    val color = sub?.let { severityColor(it.severity).copy(alpha = 0.85f) } ?: Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .let { if (onClick != null) it.clickable { onClick() } else it },
    ) {
        if (sub != null && sub.severity != Severity.GRAY) {
            Text(
                text = "${sub.typeNumber}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = LabelSecondary)
    }
}

// ---------------------------------------------------------------------------
// RADAR_CHART — лепестковая по 7 крупным темам
// ---------------------------------------------------------------------------

private data class ThemeAccuracy(val name: String, val accuracy: Float, val coverage: Float)

/**
 * Группируем подвиды в 7 крупных тем:
 *   Math: Алгебра (1-2,6-7,9-12), Геометрия (1,3,14,17), Вероятность (4-5),
 *         Сложные уравнения/неравенства (13,15), Финансы (16), Параметр (18), Числа (19).
 * Rus:   Орфография (9-15), Пунктуация (16-21), Лексика (3,5,6,25),
 *         Морфо/синтаксис (7,8), Текст (1,2,22-24,26), Ударение (4),
 *         Сочинение (27).
 *
 * Тут мы делаем общий радар по обоим предметам — берём 7 крупных тем
 * (math + rus вперемешку), у каждого — agg accuracy = weighted by attempts.
 */
private fun aggregateThemes(stats: List<SubtypeAccuracy>): List<ThemeAccuracy> {
    val groups: Map<String, (SubtypeAccuracy) -> Boolean> = linkedMapOf(
        "Алгебра" to { it.subjectSlug == "mathb" && it.typeNumber in listOf(2, 6, 7, 9, 10, 11, 12) },
        "Геометрия" to { it.subjectSlug == "mathb" && it.typeNumber in listOf(1, 3, 14, 17) },
        "Вероятность" to { it.subjectSlug == "mathb" && it.typeNumber in 4..5 },
        "Параметр и числа" to { it.subjectSlug == "mathb" && it.typeNumber in listOf(13, 15, 16, 18, 19) },
        "Орфография" to { it.subjectSlug == "rus" && it.typeNumber in 9..15 },
        "Пунктуация" to { it.subjectSlug == "rus" && it.typeNumber in 16..21 },
        "Текст и лексика" to {
            it.subjectSlug == "rus" && it.typeNumber in listOf(1, 2, 3, 5, 6, 22, 23, 24, 25, 26, 27)
        },
    )
    return groups.map { (name, filter) ->
        val members = stats.filter(filter)
        val totalAttempts = members.sumOf { it.attempts }
        val totalCorrect = members.sumOf { it.correct }
        val accuracy = if (totalAttempts > 0) totalCorrect.toFloat() / totalAttempts else 0f
        val coverage = (totalAttempts / 30f).coerceIn(0f, 1f)
        ThemeAccuracy(name, accuracy, coverage)
    }
}

@Composable
private fun RadarChart(stats: List<SubtypeAccuracy>) {
    val themes = aggregateThemes(stats)
    val totalAttempts = stats.sumOf { it.attempts }
    if (totalAttempts < 10) {
        EmptyHint("Реши хотя бы 10 задач для лепестковой диаграммы.")
        return
    }
    val n = themes.size
    val ringColor = LabelTertiary.copy(alpha = 0.20f)
    val fillColor = SystemBlue.copy(alpha = 0.25f)
    val strokeColor = SystemBlue
    Column {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val radius = size.minDimension / 2 * 0.85f

                // Сетка — 5 концентрических полигонов.
                for (level in 1..5) {
                    val r = radius * level / 5f
                    val path = Path()
                    for (i in 0 until n) {
                        val angle = 2 * PI * i / n - PI / 2
                        val x = cx + (r * cos(angle)).toFloat()
                        val y = cy + (r * sin(angle)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path = path, color = ringColor, style = Stroke(width = 1.dp.toPx()))
                }

                // Данные.
                val dataPath = Path()
                themes.forEachIndexed { i, theme ->
                    val r = radius * theme.accuracy.coerceIn(0f, 1f)
                    val angle = 2 * PI * i / n - PI / 2
                    val x = cx + (r * cos(angle)).toFloat()
                    val y = cy + (r * sin(angle)).toFloat()
                    if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                }
                dataPath.close()
                drawPath(path = dataPath, color = fillColor)
                drawPath(path = dataPath, color = strokeColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        // Подписи под диаграммой.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            themes.forEach { theme ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = theme.name,
                        fontSize = 13.sp,
                        color = if (theme.coverage > 0f) Label else LabelTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (theme.coverage > 0f) "${(theme.accuracy * 100).toInt()}%" else "—",
                        fontSize = 13.sp,
                        color = if (theme.coverage > 0f) Label else LabelTertiary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = LabelSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 8.dp),
    )
}
