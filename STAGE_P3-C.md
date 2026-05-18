# STAGE_P3-C.md — Журнал ошибок + Детальная статистика

> **Третья итерация Phase 3.** Превращает Журнал из 3-х stub'ов в **полноценный аналитический центр** для работы над ошибками.
>
> Время: 4-5 часов.
>
> Состав: БД-таблицы попыток и ошибок + полный список ошибок с фильтрами + «Перерешать» + детальная статистика + экспорт CSV.

---

## Что работает (НЕ ломать)

- Phase 2 + P3-A + P3-B.
- StreakStore, UserStatsStore (счётчики типов/подвидов), ScorePredictor.
- Главный экран с радаром, цитатами, предиктором.
- Избранное (уже есть в Журнале).
- BackupRepository v1.1.
- Размер APK 229 MB.

---

## Финальный layout Журнала

```
┌──────────────────────────────────┐
│ Журнал                           │  ← LargeTitleBar
│                                  │
│  Сводка                          │
│  ┌────────────────────────────┐ │
│  │ Сегодня решено: 12         │ │  ← Карточка дня
│  │ Точность: 75% (9/12)       │ │
│  │ Всего: 487 задач           │ │
│  └────────────────────────────┘ │
│                                  │
│  Разделы                         │
│  ┌────────────────────────────┐ │
│  │ ⭐ Избранные         (12) ›│ │  ← было
│  │ 📝 Ошибки           (47) ›│ │  ← делаем
│  │ 📊 Статистика           ›│ │  ← делаем
│  │ 📤 Экспорт CSV          ›│ │  ← делаем
│  └────────────────────────────┘ │
│                                  │
└──────────────────────────────────┘
```

---

# Часть А — БД-таблицы для отслеживания (~1 час)

## А1. ErrorLogEntity

В существующем `ProblemDatabase` (Room) добавить таблицу для журнала ошибок:

```kotlin
// data/db/ErrorLogEntity.kt

@Entity(
    tableName = "error_log",
    foreignKeys = [ForeignKey(
        entity = ProblemEntity::class,
        parentColumns = ["id"],
        childColumns = ["problem_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("problem_id"), Index("timestamp")]
)
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long,
    @ColumnInfo(name = "user_answer") val userAnswer: String,  // что ввёл
    @ColumnInfo(name = "correct_answer") val correctAnswer: String,  // правильный
    @ColumnInfo(name = "timestamp") val timestamp: Long,  // millis
    @ColumnInfo(name = "is_resolved") val isResolved: Boolean = false  // перерешена ли
)

@Dao
interface ErrorLogDao {
    @Insert
    suspend fun insert(error: ErrorLogEntity): Long
    
    @Query("SELECT * FROM error_log ORDER BY timestamp DESC")
    fun getAllOrderedByTime(): Flow<List<ErrorLogEntity>>
    
    @Query("""
        SELECT el.*, p.statement_html, p.type_id, p.subtype_id, p.sdamgia_id 
        FROM error_log el
        JOIN problems p ON el.problem_id = p.id
        ORDER BY el.timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getErrorsWithProblems(limit: Int, offset: Int): Flow<List<ErrorWithProblem>>
    
    @Query("SELECT COUNT(*) FROM error_log WHERE is_resolved = 0")
    fun getUnresolvedCount(): Flow<Int>
    
    @Query("UPDATE error_log SET is_resolved = 1 WHERE id = :errorId")
    suspend fun markResolved(errorId: Long)
    
    @Query("DELETE FROM error_log WHERE id = :errorId")
    suspend fun delete(errorId: Long)
    
    @Query("DELETE FROM error_log")
    suspend fun deleteAll()
    
    // Для статистики
    @Query("""
        SELECT p.type_id, p.subtype_id, pt.number AS type_number, st.title AS subtype_title,
               COUNT(*) AS error_count
        FROM error_log el
        JOIN problems p ON el.problem_id = p.id
        JOIN problem_types pt ON p.type_id = pt.id
        JOIN problem_subtypes st ON p.subtype_id = st.id
        GROUP BY p.subtype_id
        ORDER BY error_count DESC
    """)
    suspend fun getErrorsByType(): List<TypeErrorCount>
}

data class ErrorWithProblem(
    val id: Long,
    val problemId: Long,
    val userAnswer: String,
    val correctAnswer: String,
    val timestamp: Long,
    val isResolved: Boolean,
    val statementHtml: String,
    val typeId: Long,
    val subtypeId: Long?,
    val sdamgiaId: String
)

data class TypeErrorCount(
    val typeId: Long,
    val subtypeId: Long?,
    val typeNumber: Int,
    val subtypeTitle: String,
    val errorCount: Int
)
```

## А2. AttemptLogEntity (для статистики)

Для **детальной** статистики (Stage P3-C часть Г) нужны **все** попытки, не только ошибки:

```kotlin
@Entity(
    tableName = "attempt_log",
    indices = [Index("timestamp"), Index("problem_id"), Index("subject")]
)
data class AttemptLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long?,  // null для тренажёров
    @ColumnInfo(name = "subject") val subject: String,  // "math" | "rus"
    @ColumnInfo(name = "type_number") val typeNumber: Int,
    @ColumnInfo(name = "subtype_id") val subtypeId: Long?,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,  // время на решение
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "source") val source: String  // "problem", "accent_trainer", "wordblank_trainer", "quick_trainer"
)

@Dao
interface AttemptLogDao {
    @Insert
    suspend fun insert(attempt: AttemptLogEntity)
    
    @Query("SELECT COUNT(*) FROM attempt_log")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM attempt_log WHERE timestamp >= :sinceMs")
    suspend fun getCountSince(sinceMs: Long): Int
    
    @Query("SELECT COUNT(*) FROM attempt_log WHERE is_correct = 1 AND timestamp >= :sinceMs")
    suspend fun getCorrectCountSince(sinceMs: Long): Int
    
    // Подсчёт по дням за последние N дней (для графика)
    @Query("""
        SELECT DATE(timestamp/1000, 'unixepoch') AS day,
               COUNT(*) AS total,
               SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correct,
               AVG(duration_ms) AS avg_duration
        FROM attempt_log
        WHERE timestamp >= :sinceMs
        GROUP BY day
        ORDER BY day
    """)
    suspend fun getDailyStats(sinceMs: Long): List<DailyStat>
    
    // Для экспорта CSV
    @Query("SELECT * FROM attempt_log ORDER BY timestamp DESC")
    suspend fun getAllForExport(): List<AttemptLogEntity>
    
    @Query("DELETE FROM attempt_log")
    suspend fun deleteAll()
}

data class DailyStat(
    val day: String,
    val total: Int,
    val correct: Int,
    val avgDuration: Long
)
```

## А3. Миграция БД

`ProblemDatabase` — добавить **schema version 3** (после Stage 2 был 2). Поскольку corpus.db **read-only**, новые таблицы должны быть в **отдельной БД** или через миграцию которая создаёт их на runtime.

**Простой подход:** создать **отдельную БД** `user_data.db` для журналов:

```kotlin
@Database(
    entities = [ErrorLogEntity::class, AttemptLogEntity::class],
    version = 1,
    exportSchema = false  // user-data БД, не importable
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun attemptLogDao(): AttemptLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: UserDataDatabase? = null
        
        fun getInstance(context: Context): UserDataDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserDataDatabase::class.java,
                    "user_data.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

Эта БД создаётся пустой при первом запуске, **не конфликтует** с `corpus.db`, и легко бэкапится через тот же экспорт JSON.

## А4. Hook в местах решения

В **3 ViewModel** где пользователь даёт ответ — добавить запись в `attempt_log` и при ошибке в `error_log`:

```kotlin
// ProblemDetailViewModel
fun checkAnswer() {
    val startTime = problemStartTime  // когда задача открылась
    val durationMs = System.currentTimeMillis() - startTime
    val isCorrect = userAnswer.trim() == problem.shortAnswer
    
    viewModelScope.launch {
        // 1. user_stats (счётчики) — уже было
        userStatsStore.recordAttempt(subject, typeNumber, subtypeId, isCorrect)
        
        // 2. streak — уже было
        streakStore.onProblemSolved()
        
        // 3. attempt_log (новое)
        attemptLogDao.insert(AttemptLogEntity(
            problemId = problem.id,
            subject = subject,
            typeNumber = typeNumber,
            subtypeId = subtypeId,
            isCorrect = isCorrect,
            durationMs = durationMs,
            timestamp = System.currentTimeMillis(),
            source = "problem"
        ))
        
        // 4. error_log (новое) — только если ошибка
        if (!isCorrect && problem.shortAnswer != null) {
            errorLogDao.insert(ErrorLogEntity(
                problemId = problem.id,
                userAnswer = userAnswer.trim(),
                correctAnswer = problem.shortAnswer!!,
                timestamp = System.currentTimeMillis(),
                isResolved = false
            ))
        }
    }
}
```

Аналогично в `AccentTrainerViewModel` (при Verdict) и `WordBlankTrainerViewModel` — но **только attempt_log без error_log** (для тренажёров уже есть отдельные ErrorStores по слову). Source: `accent_trainer` / `wordblank_trainer`.

---

# Часть Б — Журнал ошибок (UI) (~1 час)

## Б1. ErrorsListScreen

Открывается из Журнала → «📝 Ошибки (47) ›».

```kotlin
@Composable
fun ErrorsListScreen(
    navController: NavController,
    viewModel: ErrorsListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        LargeTitleBar(
            title = "Ошибки",
            subtitle = "${state.totalCount} всего · ${state.unresolvedCount} непререшённых",
            onBack = { navController.popBackStack() },
            trailing = {
                FilterChip(
                    selected = state.filter != ErrorFilter.ALL,
                    onClick = { /* открыть bottom sheet фильтров */ },
                    label = { Text("Фильтр") }
                )
            }
        )
        
        if (state.errors.isEmpty()) {
            EmptyState(
                emoji = "🎯",
                title = "Пока ошибок нет",
                subtitle = "Реши задачи в каталоге — здесь будут собираться твои ошибки для работы над ними."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.errors) { error ->
                    ErrorCard(
                        error = error,
                        onResolve = { viewModel.markResolved(error.id) },
                        onDelete = { viewModel.delete(error.id) },
                        onRetry = { navController.navigate(ProblemDetailRoute(error.problemId, fromErrors = true)) }
                    )
                }
            }
        }
    }
}
```

## Б2. ErrorCard

```kotlin
@Composable
fun ErrorCard(
    error: ErrorWithProblem,
    onResolve: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Badge: subject + type
                Box(
                    modifier = Modifier
                        .background(SystemBlueTint, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "№${getTypeNumberFromTypeId(error.typeId)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SystemBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(8.dp))
                
                // Дата
                Text(
                    formatTimestamp(error.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = LabelTertiary,
                    modifier = Modifier.weight(1f)
                )
                
                if (error.isResolved) {
                    Box(
                        modifier = Modifier
                            .background(SystemGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("✓ Перерешана", color = SystemGreen, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Превью условия (первые 100 символов)
            Text(
                extractTextFromHtml(error.statementHtml).take(120) + "...",
                style = MaterialTheme.typography.bodyLarge,
                color = Label,
                maxLines = 2
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Сравнение ответов
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Твой:", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
                Spacer(Modifier.width(8.dp))
                Text(
                    error.userAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SystemRed,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(16.dp))
                Text("→ Правильно:", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
                Spacer(Modifier.width(8.dp))
                Text(
                    error.correctAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SystemGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Кнопки действий
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "🔁 Перерешать",
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = LabelTertiary)
                }
            }
        }
    }
}
```

## Б3. ErrorFilter

```kotlin
enum class ErrorFilter { 
    ALL, 
    UNRESOLVED,           // только непререшённые
    LAST_WEEK,            // за последнюю неделю
    BY_TYPE_MATH,         // только математика
    BY_TYPE_RUS           // только русский
}
```

FilterChip → bottom sheet с радио-кнопками.

## Б4. Логика «Перерешать»

При тапе на 🔁 → открывается `ProblemDetailScreen` с `fromErrors = true` параметром:

```kotlin
// ProblemDetailScreen
@Composable
fun ProblemDetailScreen(
    problemId: Long,
    fromErrors: Boolean = false,
    ...
) {
    // ...
    
    if (fromErrors) {
        // Не показывать "Предыдущая/Далее" внизу
        // После правильного ответа — кнопка "Готово, я понял" → возврат + markResolved
        // Если снова неверно — кнопка "Попробовать ещё" + новая запись в error_log
    }
}
```

При правильном ответе — `errorLogDao.markResolved(errorId)` и возврат в список (ошибка теперь зелёная «✓ Перерешана»).

## Б5. EmptyState

Если ошибок нет:

```kotlin
@Composable
fun EmptyState(emoji: String, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Text(emoji, fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Label)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LabelSecondary, textAlign = TextAlign.Center)
        }
    }
}
```

---

# Часть В — Журнал главный экран обновляется (~30 мин)

## В1. Карточка сводки сверху

```kotlin
@Composable
fun JournalSummaryCard(
    todaySolved: Int,
    todayAccuracy: Float,
    totalSolved: Int
) {
    AppleCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Сегодня", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
            Spacer(Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$todaySolved",
                    style = MaterialTheme.typography.displayLarge,
                    color = Label,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "задач",
                    style = MaterialTheme.typography.titleLarge,
                    color = LabelSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                "Точность: ${(todayAccuracy * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = if (todayAccuracy >= 0.8) SystemGreen else if (todayAccuracy >= 0.6) SystemOrange else SystemRed
            )
            
            Divider(Modifier.padding(vertical = 12.dp), color = LabelTertiary.copy(alpha = 0.2f))
            
            Text("Всего: $totalSolved задач", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
        }
    }
}
```

## В2. JournalScreen — обновить

```kotlin
@Composable
fun JournalScreen(navController: NavController, viewModel: JournalViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { LargeTitleBar(title = "Журнал") }
        
        item {
            JournalSummaryCard(
                todaySolved = state.todaySolved,
                todayAccuracy = state.todayAccuracy,
                totalSolved = state.totalSolved
            )
        }
        
        item { SectionHeader("Разделы") }
        
        item {
            Column {
                AppleListRow(
                    icon = "⭐",
                    title = "Избранные",
                    subtitle = "${state.favoritesCount} задач",
                    onClick = { navController.navigate(FavoritesRoute) }
                )
                AppleListRow(
                    icon = "📝",
                    title = "Ошибки",
                    subtitle = "${state.errorsCount} всего · ${state.unresolvedErrorsCount} непререшённых",
                    onClick = { navController.navigate(ErrorsListRoute) }
                )
                AppleListRow(
                    icon = "📊",
                    title = "Статистика",
                    subtitle = "Графики и анализ",
                    onClick = { navController.navigate(StatsRoute) }
                )
                AppleListRow(
                    icon = "📤",
                    title = "Экспорт CSV",
                    subtitle = "Скачать всю историю попыток",
                    onClick = { viewModel.exportCsv(/* trigger share intent */) }
                )
            }
        }
    }
}
```

---

# Часть Г — Детальная статистика (~1.5 часа)

## Г1. StatsScreen — структура

```
┌──────────────────────────────────┐
│ ← Статистика                     │
│                                  │
│  📊 Всего решено: 487            │  ← OverviewCard
│  Правильных: 312 (64%)           │
│  Время среднее: 2:14             │
│                                  │
│  📈 Активность за 30 дней        │  ← ActivityChart (line/bar)
│  ▆▃▅▇▆▂▅▆▇▆▃▅▇█▆▅▃▇█▆▅▆       │
│                                  │
│  🎯 По типам (математика)        │  ← TypeAccuracyTable
│  №1 Планиметрия      ▓▓▓░░ 65% │
│  №2 Векторы          ▓▓▓▓░ 78% │
│  №3 Стереометрия     ▓░░░░ 32% │
│  ...                             │
│                                  │
│  🎯 По типам (русский)           │
│  №1 Главная инфо     ▓▓▓▓▓ 95% │
│  ...                             │
│                                  │
│  🏆 Достижения                   │  ← AchievementsRow
│  🔥 Максимальный streak: 12 дней│
│  💪 Решено типов: 23 из 46       │
│                                  │
└──────────────────────────────────┘
```

## Г2. ActivityChart (Compose Canvas)

```kotlin
@Composable
fun ActivityChart(dailyStats: List<DailyStat>) {
    if (dailyStats.isEmpty()) return
    
    val maxTotal = dailyStats.maxOf { it.total }.coerceAtLeast(1)
    
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / dailyStats.size * 0.7f
        val gap = size.width / dailyStats.size * 0.3f
        
        dailyStats.forEachIndexed { i, stat ->
            val height = (stat.total.toFloat() / maxTotal) * size.height
            val x = i * (barWidth + gap)
            val y = size.height - height
            
            // Background bar (light grey)
            drawRect(
                color = LabelTertiary.copy(alpha = 0.15f),
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(4f)
            )
            
            // Actual bar (blue with accuracy gradient)
            val barColor = when {
                stat.correct.toFloat() / stat.total >= 0.8 -> SystemGreen
                stat.correct.toFloat() / stat.total >= 0.6 -> SystemBlue
                else -> SystemOrange
            }
            
            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(4f)
            )
        }
    }
    
    // Подписи (последние 7 дней по датам)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dailyStats.takeLast(7).forEach { stat ->
            Text(
                stat.day.takeLast(2),  // "21"
                style = MaterialTheme.typography.bodySmall,
                color = LabelTertiary
            )
        }
    }
}
```

## Г3. TypeAccuracyTable

```kotlin
@Composable
fun TypeAccuracyTable(subject: String, stats: List<TypeAccuracy>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.filter { it.attempts > 0 }.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "№${type.typeNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LabelSecondary,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    getTypeTitle(subject, type.typeNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Label,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                AppleProgressBar(
                    progress = type.accuracy,
                    modifier = Modifier.width(80.dp).height(4.dp),
                    barColorOverride = if (type.accuracy >= 0.8f) SystemGreen 
                                       else if (type.accuracy >= 0.6f) SystemBlue 
                                       else SystemOrange
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(type.accuracy * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = LabelSecondary,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
```

## Г4. AchievementsRow

```kotlin
@Composable
fun AchievementsRow(state: AchievementsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AchievementCard(emoji = "🔥", title = "Максимальный streak", value = "${state.maxStreak} дней")
        AchievementCard(emoji = "💪", title = "Освоено типов", value = "${state.typesCovered} из 46")
        AchievementCard(emoji = "📚", title = "Слов в тренажёрах", value = "${state.wordsLearned}")
        AchievementCard(emoji = "⭐", title = "Избранных задач", value = "${state.favoritesCount}")
    }
}
```

---

# Часть Д — Экспорт CSV (~30 мин)

## Д1. CsvExporter

```kotlin
// data/CsvExporter.kt

class CsvExporter(
    private val attemptLogDao: AttemptLogDao,
    private val context: Context
) {
    suspend fun exportAttempts(): Uri = withContext(Dispatchers.IO) {
        val attempts = attemptLogDao.getAllForExport()
        val csv = buildString {
            // Header
            appendLine("timestamp,date,subject,type_number,subtype_id,is_correct,duration_ms,source")
            attempts.forEach { a ->
                val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(a.timestamp), ZoneId.systemDefault())
                appendLine("${a.timestamp},${date},${a.subject},${a.typeNumber},${a.subtypeId ?: ""},${a.isCorrect},${a.durationMs},${a.source}")
            }
        }
        
        val file = File(context.cacheDir, "ege100_attempts_${LocalDate.now()}.csv")
        file.writeText(csv)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    
    suspend fun exportSummary(): Uri = withContext(Dispatchers.IO) {
        // Сводка: только по типам
        val mathStats = computeTypeStats("math")
        val rusStats = computeTypeStats("rus")
        val csv = buildString {
            appendLine("subject,type_number,type_title,total_attempts,correct,accuracy_percent")
            mathStats.forEach { s -> appendLine("math,${s.typeNumber},\"${s.title}\",${s.total},${s.correct},${(s.accuracy*100).toInt()}") }
            rusStats.forEach { s -> appendLine("rus,${s.typeNumber},\"${s.title}\",${s.total},${s.correct},${(s.accuracy*100).toInt()}") }
        }
        // ... write file + return Uri
    }
}
```

## Д2. UI экспорта

```kotlin
// JournalViewModel
fun exportCsv() {
    viewModelScope.launch {
        val uri = csvExporter.exportAttempts()
        // Открываем системный share-sheet
        _events.emit(JournalEvent.ShareFile(uri, "text/csv"))
    }
}
```

```kotlin
// JournalScreen
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is JournalEvent.ShareFile -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = event.mimeType
                    putExtra(Intent.EXTRA_STREAM, event.uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Статистика EGE100")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Сохранить CSV"))
            }
        }
    }
}
```

Файл `ege100_attempts_2026-05-21.csv` можно открыть в Excel или Google Sheets и анализировать самостоятельно.

---

# Часть Е — Backup расширен (~15 мин)

В `BackupSnapshot` v1.2 добавить:

```kotlin
@Serializable
data class BackupSnapshot(
    val version: String = "1.2",
    val exportedAt: String,
    val profile: UserProfile,
    val settings: AppSettings,
    val trainerProgress: Map<String, TrainerProgress>,
    val favorites: List<String>,
    val accentErrors: Map<String, Set<String>>,
    val wordBlankErrors: Map<String, Set<String>>,
    val userStats: UserStatsSnapshot? = null,  // P3-B
    val streak: StreakState? = null,            // P3-B
    val errorLog: List<ErrorLogEntity>? = null,    // P3-C NEW
    val attemptLog: List<AttemptLogEntity>? = null // P3-C NEW
)

val SUPPORTED_VERSIONS = listOf("1.0", "1.1", "1.2")
```

В `BackupRepository.exportBackup` собрать данные из `ErrorLogDao` и `AttemptLogDao`.

В `applyBackup` — восстановить (с `deleteAll` сначала чтобы избежать дубликатов).

В `resetProgress` — очистить `error_log` и `attempt_log`.

---

# Smoke-тесты

| # | Что |
|---|---|
| 1 | Журнал — карточка сводки сверху: "Сегодня N задач, точность M%, всего K". |
| 2 | 4 раздела в Журнале: Избранные / Ошибки / Статистика / Экспорт CSV. |
| 3 | Реши задачу Math №6 неверно → ошибка появилась в Журнале → Ошибки. |
| 4 | ErrorCard: видны условие (превью), твой ответ красным, правильный зелёным, кнопка "Перерешать". |
| 5 | Тап "Перерешать" → открывается та же задача с пустым полем. |
| 6 | Решил правильно → "Готово, я понял" → возврат в список → ошибка теперь "✓ Перерешана". |
| 7 | Решил снова неверно → новая запись в error_log, старая остаётся (можно ещё раз перерешать). |
| 8 | Фильтр "Только непререшённые" → перерешённые скрываются. |
| 9 | Журнал → Статистика → OverviewCard с общими цифрами. |
| 10 | ActivityChart показывает столбцы за последние 30 дней. |
| 11 | TypeAccuracyTable: для math 19 строк, для rus 27 строк (только с попытками). |
| 12 | AchievementsRow: max streak, освоено типов, слов, избранных. |
| 13 | Журнал → "📤 Экспорт CSV" → share-sheet → выбор Telegram → файл `ege100_attempts_*.csv` пришёл. |
| 14 | Открой CSV в Excel/Google Sheets — все попытки видны с timestamp/subject/type/correct/duration. |
| 15 | Бэкап v1.2 включает error_log и attempt_log. После сброса прогресса они тоже очищаются. |
| 16 | Импорт старого бэкапа v1.0/v1.1 работает (новые поля = null). |

---

# Зависимости

```kotlin
// build.gradle.kts — должно уже быть из Phase 2
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
```

---

# После итерации

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского "работает".
- В отчёте:
  - Размер APK.
  - 16 smoke-тестов.
  - Структура новых файлов.

После "работает":
- Commit + tag `phase-3-stage-c-done` + push.
- Conventions #28 (UserDataDatabase отдельная от corpus.db), #29 (Backup v1.2 backward-compat), #30 (CsvExporter pattern для FileProvider share).

---

# Last update

Журнал → центр работы над ошибками + детальная аналитика прогресса + экспорт сырых данных для самостоятельного анализа.

Дальше: **Stage P3-D** (календарь пробников + push + контрольные точки страховок #5 и #6).
