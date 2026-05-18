package com.daniel.ege100.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.SystemBlue

/**
 * Stage 5 part Г — простой Markdown-рендер для правил.
 *
 * Поддерживает:
 *   - `## Заголовок`            → titleLarge, 20sp SemiBold, отступы.
 *   - `### Подзаголовок`        → 17sp SemiBold.
 *   - `- маркер` / `* маркер`   → буллет с SystemBlue.
 *   - `1. шаг`                  → нумерованный список.
 *   - `**жирный**`              → SemiBold inline.
 *   - `*курсив*` / `_курсив_`   → italic inline.
 *   - пустая строка             → отступ.
 *   - всё остальное             → обычный параграф.
 *
 * Формулы и сложная разметка не нужны — правила пишутся «школьным» Markdown.
 */
private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
private val NUMBERED_REGEX = Regex("^(\\d+)\\.\\s+(.*)$")

@Composable
fun SimpleMarkdownRenderer(markdown: String) {
    val lines = markdown.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        for (line in lines) {
            when {
                line.startsWith("### ") -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = parseInline(line.removePrefix("### ")),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Label,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                line.startsWith("## ") -> {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = parseInline(line.removePrefix("## ")),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Label,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                line.startsWith("# ") -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = parseInline(line.removePrefix("# ")),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Label,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val content = line.drop(2)
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Text(
                            "•",
                            color = SystemBlue,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = parseInline(content),
                            fontSize = 16.sp,
                            color = Label,
                            lineHeight = 23.sp,
                        )
                    }
                }
                NUMBERED_REGEX.matches(line) -> {
                    val m = NUMBERED_REGEX.find(line)!!
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Text(
                            "${m.groupValues[1]}.",
                            color = SystemBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = parseInline(m.groupValues[2]),
                            fontSize = 16.sp,
                            color = Label,
                            lineHeight = 23.sp,
                        )
                    }
                }
                line.isBlank() -> {
                    Spacer(Modifier.height(8.dp))
                }
                else -> {
                    Text(
                        text = parseInline(line),
                        fontSize = 16.sp,
                        color = Label,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var lastEnd = 0
    BOLD_REGEX.findAll(text).forEach { m ->
        append(text.substring(lastEnd, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(m.groupValues[1])
        }
        lastEnd = m.range.last + 1
    }
    if (lastEnd < text.length) append(text.substring(lastEnd))
}
