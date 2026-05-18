package com.daniel.ege100.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase 3 Stage C part Д — экспорт всех попыток в CSV для Excel/Google Sheets.
 *
 * Формат строки:
 *   timestamp_ms,date_iso,subject,type_number,subtype_id,problem_id,is_correct,duration_ms,source
 *
 * UTF-8 **с BOM** (`﻿`) — это критично для Excel: без BOM кириллица
 * открывается как кракозябры. Google Sheets читает корректно в обоих случаях,
 * но универсальный вариант — добавлять BOM.
 *
 * Файл создаётся в `cacheDir/ege100_attempts_<date>.csv`, расшаривается через
 * FileProvider (Convention #23) с MIME `text/csv`. Можно сохранить в Telegram,
 * Drive, локальный файлы — пользователь сам открывает в Excel/Sheets.
 */
object CsvExporter {
    private const val FILE_PREFIX = "ege100_attempts_"
    private const val MIME = "text/csv"
    private const val UTF8_BOM = "﻿"

    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    suspend fun exportAttempts(context: Context): Intent = withContext(Dispatchers.IO) {
        val dao = UserDataDatabase.get(context).attemptLogDao()
        val attempts = dao.getAllForExport()
        val csv = buildCsv(attempts)

        val fileName = "$FILE_PREFIX${LocalDate.now()}.csv"
        val file = File(context.cacheDir, fileName).apply {
            writeText(csv, Charsets.UTF_8)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "EGE100 — история попыток (${attempts.size})")
            putExtra(Intent.EXTRA_TEXT, "${attempts.size} попыток за всё время")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Поделиться CSV")
    }

    private fun buildCsv(attempts: List<AttemptLogEntity>): String = buildString {
        append(UTF8_BOM)
        appendLine("timestamp_ms,date,subject,type_number,subtype_id,problem_id,is_correct,duration_ms,source")
        for (a in attempts) {
            val date = DATE_FORMAT.format(Instant.ofEpochMilli(a.timestamp))
            append(a.timestamp).append(',')
            append('"').append(date).append('"').append(',')
            append(a.subject).append(',')
            append(a.typeNumber).append(',')
            append(a.subtypeId?.toString().orEmpty()).append(',')
            append(a.problemId?.toString().orEmpty()).append(',')
            append(if (a.isCorrect) 1 else 0).append(',')
            append(a.durationMs).append(',')
            append(a.source).append('\n')
        }
    }
}
