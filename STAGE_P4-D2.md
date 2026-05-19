# STAGE_P4-D2.md — Полировка каталога + критичная диагностика крашей

> Мини-стейдж перед большим P4-D. 4 части.
>
> Состав:
> - **Часть А** — Подсветка задач в списке (зелёная решена / красная не решена) — по последней попытке.
> - **Часть Б** — Прогресс-бары под названиями типов и подвидов.
> - **Часть В** — Critery "Освоено типов" = ВСЕ задачи типа решены правильно.
> - **Часть Г** — КРИТИЧНО: диагностика и фикс крашей приложения.

---

## Что работает (НЕ ломать)

- Phase 1 + Phase 2 + Phase 3 + Phase 4 (Stage A+B+C+C2+C3).
- 12 тренажёров, AI, бэкап v1.7.
- Размер APK 231.2 MB.
- AnswerChecker, attempt_log, error_log, UserStatsStore.
- Иконка, плавность скролла, swipe-back, swipe between problems.

---

# КРИТИЧНОЕ — ЧАСТЬ Г: диагностика крашей

## Г1. Проблема

Пользователь сообщил: **«приложение иногда вылетает»**.

Это вторая жалоба на краш. В Stage P4-C2 был добавлен **глобальный crash handler** через `Thread.setDefaultUncaughtExceptionHandler` который пишет в `Log.e("EgeApp", ...)`. Но:
- Stack trace через adb получить НЕ удалось (Samsung без USB).
- Crash handler даёт лог только локально, пользователь не видит причину.
- Превентивные try/catch покрыли часть hot paths, но не все.

**Нужно сделать так чтобы пользователь сам мог собрать stack trace без adb.**

## Г2. Решение — самодиагностика приложения

### Г2.1 Записывать crash в файл при падении

В `EgeApplication.installCrashHandler()` расширить логику:

```kotlin
fun installCrashHandler() {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        try {
            // 1. Лог в Logcat
            android.util.Log.e("EgeApp", "UNCAUGHT EXCEPTION", exception)
            
            // 2. Запись в файл — пользователь сможет отправить через "Отправить лог краша"
            val crashLog = buildString {
                append("EGE100 Crash Report\n")
                append("====================\n")
                append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}\n")
                append("Thread: ${thread.name}\n")
                append("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("====================\n")
                append("Exception: ${exception.javaClass.simpleName}\n")
                append("Message: ${exception.message}\n")
                append("====================\n")
                append("Stack trace:\n")
                exception.stackTraceToString().take(8000)  // лимит
                append("\n====================\n")
                append("Recent app actions:\n")
                append(BreadcrumbLog.getRecentBreadcrumbs())  // последние 20 действий пользователя
            }
            
            // Сохраняем в files dir
            val crashDir = File(filesDir, "crashes")
            if (!crashDir.exists()) crashDir.mkdirs()
            val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
            crashFile.writeText(crashLog)
            
            // Ставим флаг что был краш — на следующем запуске покажем диалог
            getSharedPreferences("crash_state", MODE_PRIVATE).edit()
                .putBoolean("last_crash_unhandled", true)
                .putString("last_crash_file", crashFile.absolutePath)
                .apply()
        } catch (e: Throwable) {
            // Не должны рекурсивно крашиться
            android.util.Log.e("EgeApp", "Failed to write crash log", e)
        }
        
        // Передаём системному обработчику
        defaultHandler?.uncaughtException(thread, exception)
    }
}
```

### Г2.2 Breadcrumb log — последние 20 действий

`data/BreadcrumbLog.kt`:

```kotlin
object BreadcrumbLog {
    private val breadcrumbs = mutableListOf<String>()
    private const val MAX_SIZE = 20
    
    @Synchronized
    fun add(action: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date())
        breadcrumbs.add("[$timestamp] $action")
        if (breadcrumbs.size > MAX_SIZE) breadcrumbs.removeAt(0)
    }
    
    @Synchronized
    fun getRecentBreadcrumbs(): String {
        return breadcrumbs.joinToString("\n")
    }
}
```

Использовать в **критичных точках**:

```kotlin
// MainActivity.onCreate
BreadcrumbLog.add("App started")

// Navigation
BreadcrumbLog.add("Navigate to ${route::class.simpleName}")

// ProblemDetailViewModel.checkAnswer
BreadcrumbLog.add("Check answer: problemId=${problem.id}, userAnswer=${userAnswer.take(20)}")

// AskAiViewModel.ask
BreadcrumbLog.add("Ask AI: provider=${settings.activeProvider}, model=$modelId")

// AccentTrainerViewModel.onSyllableTap
BreadcrumbLog.add("AccentTap: word=${currentWord.word}, syllable=$syllableIndex")

// WordBlankTrainerViewModel.checkLetter
BreadcrumbLog.add("WordBlankCheck: word=${currentWord.full}, letter=$letter")

// HtmlRenderer на проблемных формулах
BreadcrumbLog.add("HtmlRender: <img src=${img.src}>")
```

### Г2.3 UI — диалог при следующем запуске после краша

В `MainActivity.onCreate`:

```kotlin
val prefs = getSharedPreferences("crash_state", MODE_PRIVATE)
if (prefs.getBoolean("last_crash_unhandled", false)) {
    val crashFilePath = prefs.getString("last_crash_file", null)
    // Сбрасываем флаг сразу — не показывать повторно
    prefs.edit().clear().apply()
    
    // Передаём в Compose чтобы показать AlertDialog
    showCrashRecoveryDialog = true
    crashLogFilePath = crashFilePath
}
```

`ui/common/CrashRecoveryDialog.kt`:

```kotlin
@Composable
fun CrashRecoveryDialog(
    crashFilePath: String,
    onDismiss: () -> Unit,
    onSendToTelegram: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Приложение вылетело") },
        text = {
            Text("В прошлый раз произошла ошибка. Чтобы её исправить, отправь лог разработчику через Telegram.")
        },
        confirmButton = {
            Button(onClick = onSendToTelegram) {
                Text("📤 Отправить лог")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
```

`onSendToTelegram` — открыть Intent.ACTION_SEND с файлом:

```kotlin
fun shareCrashLog(context: Context, crashFilePath: String) {
    val file = File(crashFilePath)
    if (!file.exists()) return
    
    val uri = FileProvider.getUriForFile(
        context, 
        "${context.packageName}.fileprovider",
        file
    )
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "EGE100 Crash Report")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(Intent.createChooser(intent, "Отправить crash log"))
}
```

### Г2.4 Кнопка "Отправить crash log" в Настройках

В `SettingsScreen` секция «Поддержка»:

```kotlin
AppleListRow(
    icon = "🐛",
    title = "Отправить crash log",
    subtitle = if (hasCrashLogs) "Найдено N логов" else "Логи отсутствуют",
    onClick = { 
        // Архивирует все crash файлы в zip и шарит
        sendAllCrashLogs(context)
    }
)
```

Это поможет если краш случился но пользователь не закрыл диалог.

### Г2.5 Превентивный аудит — обёртки try/catch

Помимо crash handler — пройти по **самым подозрительным местам** и обернуть try/catch с Log.

**Подозрительные места:**

1. **HtmlRenderer.kt** — рендеринг формул:
```kotlin
private fun renderImg(img: Element): @Composable () -> Unit = {
    try {
        // Существующая логика
    } catch (e: Exception) {
        BreadcrumbLog.add("HtmlRenderer.renderImg failed: ${e.message}")
        Log.e("HtmlRenderer", "Failed to render img src=${img.attr("src")}", e)
        // Показываем placeholder
        Text("[формула не отобразилась]", color = SystemRed)
    }
}
```

2. **AskAiViewModel.parseOnlineResponse** — парсинг JSON ответа AI:
```kotlin
private fun parseOnlineResponse(text: String): State {
    return try {
        val json = JSONObject(text)
        State(
            explanation = json.optString("explanation"),
            // ...
        )
    } catch (e: Exception) {
        Log.e("AskAi", "parseOnlineResponse failed", e)
        State(explanation = text, source = "online_ai_raw")
    }
}
```

3. **BackupRepository.applyBackup** — импорт может крашить:
```kotlin
suspend fun applyBackup(snapshot: BackupSnapshot): Result<Unit> {
    return try {
        // Существующая логика
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("Backup", "applyBackup failed", e)
        Result.failure(e)
    }
}
```

4. **MockExamRunnerViewModel** — навигация по задачам:
```kotlin
fun goNext() {
    try {
        val nextIdx = currentIdx + 1
        if (nextIdx < problemIds.size) {
            currentIdx = nextIdx
            loadProblem(problemIds[nextIdx])
        } else {
            finishExam()
        }
    } catch (e: Exception) {
        Log.e("MockExam", "goNext failed", e)
        BreadcrumbLog.add("MockExam.goNext failed: ${e.message}")
    }
}
```

5. **SwipeableProblemContent + SwipeBackContainer** — gesture handling:

Уже есть в Concerns P4-C3: «SwipeableProblemContent + LazyColumn coexistence — оба используют pointerInput внутри». Это **потенциальная причина крашей**.

Решение: в `SwipeableProblemContent` обернуть `pointerInput` в try/catch:
```kotlin
.pointerInput(hasPrev, hasNext) {
    try {
        detectHorizontalDragGestures(...) {...}
    } catch (e: Exception) {
        Log.e("SwipeGesture", "Gesture handler crashed", e)
    }
}
```

### Г2.6 Ограничения

**Чего не получится:**
- Если краш в нативном коде (ANR, OOM) — крашхандлер не успеет записать.
- Если краш сразу при старте — диалог не покажется.

**Что покрываем:**
- Все Kotlin/Java исключения в Compose UI.
- Краш в любом ViewModel.
- Краш в Repository/Dao.

---

# ЧАСТЬ А — Подсветка задач в списке (~1 час)

## А1. Логика

В `ProblemListScreen` каждая карточка задачи получает **цветной фон** на основе последней попытки:

- **Светло-зелёный** — последняя попытка была правильной.
- **Светло-красный** — последняя попытка была неправильной (нужно перерешать).
- **Серый/нейтральный** — задачу ещё не решал.

## А2. SQL — последняя попытка

```kotlin
@Query("""
    SELECT al.problem_id, al.is_correct
    FROM attempt_log al
    INNER JOIN (
        SELECT problem_id, MAX(timestamp) as max_ts
        FROM attempt_log
        WHERE problem_id IN (:problemIds)
        GROUP BY problem_id
    ) latest ON al.problem_id = latest.problem_id AND al.timestamp = latest.max_ts
""")
suspend fun getLastAttempts(problemIds: List<Long>): List<LastAttemptInfo>

data class LastAttemptInfo(
    @ColumnInfo(name = "problem_id") val problemId: Long,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean
)
```

## А3. ViewModel

`ProblemListViewModel`:

```kotlin
data class ProblemListState(
    val problems: List<ProblemPreview> = emptyList(),
    val lastAttempts: Map<Long, Boolean> = emptyMap()
)

fun load(typeId: Long, subtypeId: Long?) {
    viewModelScope.launch {
        val problems = problemDao.getByTypeAndSubtype(typeId, subtypeId)
        val ids = problems.map { it.id }
        val attempts = attemptLogDao.getLastAttempts(ids)
        val attemptsMap = attempts.associate { it.problemId to it.isCorrect }
        _state.value = _state.value.copy(
            problems = problems,
            lastAttempts = attemptsMap
        )
    }
}
```

## А4. UI

```kotlin
@Composable
fun ProblemPreviewCard(
    problem: ProblemPreview,
    attemptStatus: AttemptStatus,
    onClick: () -> Unit
) {
    val isDark = LocalDarkOverride.current
    
    val cardColor = when (attemptStatus) {
        AttemptStatus.CORRECT -> if (isDark) Color(0xFF1A3320) else Color(0xFFE8F5E9)
        AttemptStatus.WRONG -> if (isDark) Color(0xFF3A1F22) else Color(0xFFFFEBEE)
        AttemptStatus.NOT_ATTEMPTED -> BgElevated
    }
    
    val borderColor = when (attemptStatus) {
        AttemptStatus.CORRECT -> SystemGreen.copy(alpha = 0.25f)
        AttemptStatus.WRONG -> SystemRed.copy(alpha = 0.25f)
        AttemptStatus.NOT_ATTEMPTED -> Color.Transparent
    }
    
    val statusIcon = when (attemptStatus) {
        AttemptStatus.CORRECT -> "✓"
        AttemptStatus.WRONG -> "✗"
        AttemptStatus.NOT_ATTEMPTED -> "○"
    }
    
    val statusColor = when (attemptStatus) {
        AttemptStatus.CORRECT -> SystemGreen
        AttemptStatus.WRONG -> SystemRed
        AttemptStatus.NOT_ATTEMPTED -> LabelTertiary
    }
    
    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp)),
        backgroundColorOverride = cardColor,
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(statusIcon, color = statusColor, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extractTextFromHtml(problem.statementHtml).take(80) + "...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Label,
                    maxLines = 2
                )
            }
            Text("›", color = LabelTertiary)
        }
    }
}

enum class AttemptStatus { CORRECT, WRONG, NOT_ATTEMPTED }
```

## А5. Подключение

```kotlin
items(state.problems, key = { it.id }) { problem ->
    val attemptStatus = when (state.lastAttempts[problem.id]) {
        true -> AttemptStatus.CORRECT
        false -> AttemptStatus.WRONG
        null -> AttemptStatus.NOT_ATTEMPTED
    }
    ProblemPreviewCard(
        problem = problem,
        attemptStatus = attemptStatus,
        onClick = { navController.navigate(ProblemDetailRoute(problem.id)) }
    )
}
```

---

# ЧАСТЬ Б — Прогресс-бары под типами и подвидами (~1 час)

## Б1. Иерархия

Прогресс-бары на **двух уровнях**:

```
Каталог типов:
┌──────────────────────────────────┐
│ №6 Простейшие уравнения      ›  │
│  ▓▓▓▓▓░░░░░ 50% (35/70)         │
└──────────────────────────────────┘

Тап → внутри типа:
┌──────────────────────────────────┐
│ №6 Простейшие уравнения          │
│                                  │
│ • Иррациональные уравнения    ›  │
│   ▓▓▓▓░░░░ 40% (8/20)            │
│                                  │
│ • Логарифмические уравнения   ›  │
│   ▓▓▓▓▓▓░░ 70% (14/20)           │
└──────────────────────────────────┘
```

## Б2. SQL для типов

```kotlin
@Query("""
    SELECT 
        pt.id as type_id,
        pt.number as type_number,
        pt.title as type_title,
        COUNT(DISTINCT p.id) as total,
        COUNT(DISTINCT CASE 
            WHEN al.is_correct = 1 AND al.timestamp = (
                SELECT MAX(timestamp) FROM attempt_log WHERE problem_id = p.id
            ) THEN p.id 
        END) as solved
    FROM problem_types pt
    LEFT JOIN problems p ON p.type_id = pt.id
    LEFT JOIN attempt_log al ON al.problem_id = p.id
    WHERE pt.subject_slug = :subjectSlug
    GROUP BY pt.id, pt.number, pt.title
    ORDER BY pt.number
""")
suspend fun getTypeProgress(subjectSlug: String): List<TypeProgress>

data class TypeProgress(
    @ColumnInfo(name = "type_id") val typeId: Long,
    @ColumnInfo(name = "type_number") val typeNumber: Int,
    @ColumnInfo(name = "type_title") val typeTitle: String,
    val total: Int,
    val solved: Int
)
```

## Б3. SQL для подвидов

Аналогично для `getSubtypeProgress(typeId: Long)`.

## Б4. UI карточки типа

```kotlin
@Composable
fun TypeCard(typeProgress: TypeProgress, onClick: () -> Unit) {
    val progress = if (typeProgress.total > 0) {
        typeProgress.solved.toFloat() / typeProgress.total.toFloat()
    } else 0f
    
    val barColor = when {
        progress >= 0.8f -> SystemGreen
        progress >= 0.5f -> SystemBlue
        progress > 0f -> SystemOrange
        else -> LabelTertiary.copy(alpha = 0.3f)
    }
    
    AppleCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "№${typeProgress.typeNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SystemBlue,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    typeProgress.typeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Label,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = LabelTertiary)
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppleProgressBar(
                    progress = progress,
                    barColorOverride = barColor,
                    modifier = Modifier.weight(1f).height(6.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${typeProgress.solved}/${typeProgress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LabelSecondary,
                    modifier = Modifier.widthIn(min = 48.dp)
                )
            }
        }
    }
}
```

## Б5. UI карточки подвида

Аналогично — `SubtypeCard` принимает `SubtypeProgress` с тем же набором полей.

---

# ЧАСТЬ В — Critery "Освоено типов" (~30 мин)

## В1. Новая логика

Тип освоен только когда **ВСЕ задачи** типа решены правильно:

```kotlin
suspend fun computeTypesCovered(): Int {
    val mathProgress = problemDao.getTypeProgress("mathb")
    val rusProgress = problemDao.getTypeProgress("rus")
    val all = mathProgress + rusProgress
    
    return all.count { it.total > 0 && it.solved == it.total }
}
```

## В2. UI Statistics

```kotlin
AchievementCard(
    emoji = "💪",
    title = "Освоено типов",
    value = "$typesCovered из 46",
    hint = "Все задачи типа решены правильно"
)
```

## В3. TypeAccuracyTable метка ✓

```kotlin
val isMastered = type.solved == type.total && type.total > 0
if (isMastered) {
    Icon(Icons.Default.Check, contentDescription = "Освоен", tint = SystemGreen)
}
```

---

# Smoke-тесты

## Часть А — Подсветка
| # | Что |
|---|---|
| 1 | Math №6 → список 70 задач. Сначала все серые (○). |
| 2 | Реши задачу №3 правильно → возврат → карточка №3 светло-зелёная (✓). |
| 3 | Реши задачу №5 неверно → возврат → карточка №5 светло-красная (✗). |
| 4 | Реши задачу №5 правильно → карточка стала зелёной (последняя попытка важнее). |

## Часть Б — Прогресс-бары
| # | Что |
|---|---|
| 5 | Каталог → Математика → каждый тип имеет прогресс-бар + "N/M". |
| 6 | Math №6 → внутрь типа → каждый подвид имеет свой прогресс-бар. |
| 7 | Цвет бара зависит от %: серый 0, оранжевый <50, синий 50-80, зелёный 80+. |

## Часть В — typesCovered
| # | Что |
|---|---|
| 8 | Тип где решены НЕ ВСЕ → "Освоено: 0 из 46". |
| 9 | Тип где решены ВСЕ задачи правильно → "Освоено: 1 из 46". |
| 10 | Hint: "Все задачи типа решены правильно". |

## Часть Г — Диагностика крашей
| # | Что |
|---|---|
| 11 | EgeApplication.installCrashHandler пишет crash log в files/crashes/. |
| 12 | После краша при следующем запуске — диалог "Приложение вылетело" с кнопкой "Отправить лог". |
| 13 | Тап "Отправить лог" → Intent.ACTION_SEND с файлом → Telegram. |
| 14 | Настройки → "Отправить crash log" → если файлы есть, открывает share-sheet. |
| 15 | BreadcrumbLog содержит последние 20 действий пользователя в crash report. |
| 16 | try/catch в HtmlRenderer.renderImg показывает "[формула не отобразилась]" вместо краша. |
| 17 | try/catch в SwipeableProblemContent.pointerInput не падает на конфликте жестов. |

---

# Финальные действия

- `gradlew assembleDebug`.
- НЕ коммитить.
- В отчёте:
  - Изменённые файлы.
  - Размер APK (ожидание +0.1-0.2 MB).
  - 17 smoke-тестов.
  - Список добавленных try/catch с указанием места.

После «работает» — один commit Stage P4-D2 + tag `phase-4-stage-d2-done` + push + Conventions #64-68:
- #64: AttemptStatus подсветка задач в списке (по последней попытке).
- #65: Прогресс-бары на двух уровнях (типы + подвиды).
- #66: typesCovered = (solved == total && total > 0).
- #67: BreadcrumbLog паттерн для tracing последних 20 действий пользователя.
- #68: CrashRecoveryDialog + кнопка "Отправить crash log" в Настройках.

---

# Last update

Stage P4-D2 — мини-полировка каталога перед большой P4-D + критичная диагностика крашей через breadcrumb + crash dialog.
