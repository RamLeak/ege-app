# STAGE_P3-B.md — Главный экран Phase 3

> **Самая мотивирующая фича всего приложения.** Превращает «Главная» из заглушки в **центр подготовки к ЕГЭ**.
>
> Время: 6-8 часов.

---

## Что работает (НЕ ломать)

- Всё из Phase 2 + Stage P3-A.
- UserProfileStore, AppSettingsStore, TrainerProgressStore, FavoritesStore, BackupRepository.
- LocalDarkOverride + мгновенная смена темы.
- 4 таба в bottom-bar, аватарка в шапке.
- Размер APK 229 MB.

---

## Финальный layout главного экрана

```
┌────────────────────────────────────────┐
│                                    [D] │  ← Аватарка → Профиль
│  Привет, Daniel!                       │
│                                        │
│  🔥 7 дней   ·  📅 213 дней до ЕГЭ    │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │ ❝ Дорогу осилит идущий           │ │  ← Цитата дня
│  │      — Античная мудрость         │ │
│  └──────────────────────────────────┘ │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │  📊 Прогноз балла                │ │  ← Предиктор
│  │  Математика  72 /100  цель 80    │ │
│  │  ▓▓▓▓▓▓▓▓▓░░░  |                 │ │
│  │  Русский    85 /100  цель 80    │ │
│  │  ▓▓▓▓▓▓▓▓▓▓▓▓░  |                │ │
│  │           [Подробнее →]          │ │
│  └──────────────────────────────────┘ │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │  🎯 Слабые места                 │ │  ← Радар (LIST/DONUT/HEATMAP/RADAR_CHART)
│  │  [варианты по AppSettings]       │ │
│  │  [🎯 Решить слабые места]        │ │
│  └──────────────────────────────────┘ │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │  📅 Следующий пробник            │ │
│  │  Через 12 дней                ›  │ │
│  └──────────────────────────────────┘ │
└────────────────────────────────────────┘
   Главная   Решать   Журнал   Профиль
```

---

# Часть А — Цитаты дня (~1ч)

## А1. quotes.json

`android/app/src/main/assets/quotes.json`, ~30-40 КБ, 120-150 цитат.

```json
{
  "version": "1.0",
  "quotes": [
    {"text": "В науках столько истины, сколько в них математики.", "author": "Михаил Ломоносов", "category": "russian"},
    {"text": "Знание без приложения — мёртвый груз.", "author": "Дмитрий Менделеев", "category": "russian"},
    {"text": "Математик не может ограничиться технической стороной дела.", "author": "Андрей Колмогоров", "category": "russian"},
    {"text": "Метод важнее открытия.", "author": "Лев Ландау", "category": "russian"},
    {"text": "Математика — это язык, на котором написана Вселенная.", "author": "Галилео Галилей", "category": "math"},
    {"text": "Образование — это то, что остаётся после того, как забыто всё, чему учили.", "author": "Альберт Эйнштейн", "category": "philosophy"},
    {"text": "Долог путь поучений, краток и убедителен путь примеров.", "author": "Сенека", "category": "philosophy"},
    {"text": "Дорогу осилит идущий.", "author": "Античная мудрость", "category": "philosophy"},
    ...
  ]
}
```

**Состав:**
- 50 русских учёных (Ломоносов, Менделеев, Колмогоров, Ландау, Лобачевский, Ковалевская, Пуанкаре, Понтрягин, Капица, Тамм, Курчатов, Королёв, Сахаров, Глушков).
- 50 зарубежных учёных (Эйнштейн, Гаусс, Дирак, Архимед, Эвклид, Ферма, Лейбниц, Ньютон, Эйлер, Риман, Гильберт, Тьюринг, Шеннон, Фейнман, Гёдель, Нэш, Гротендик, Галилей).
- 30 мыслителей/философов (Сенека, Марк Аврелий, Конфуций, Аристотель, Платон, Достоевский, Толстой, Чехов).
- 20 о труде/дисциплине/преодолении (включая пару от спортсменов — пользователь сам пловец, это срезонирует).

Claude Code пишет все цитаты вручную (как делал rules.json в Stage 5).

## А2. QuotesRepository — детерминированная цитата дня

```kotlin
class QuotesRepository(private val context: Context) {
    private var cachedQuotes: List<Quote>? = null
    
    suspend fun getTodayQuote(): Quote = withContext(Dispatchers.IO) {
        val quotes = loadQuotes()
        // Детерминированно: индекс = epoch_day % size
        val daysSinceEpoch = LocalDate.now().toEpochDay()
        val index = (daysSinceEpoch % quotes.size).toInt()
        quotes[index]
    }
    
    private suspend fun loadQuotes(): List<Quote> {
        cachedQuotes?.let { return it }
        val json = context.assets.open("quotes.json").bufferedReader().use { it.readText() }
        cachedQuotes = Json.decodeFromString<QuotesDict>(json).quotes
        return cachedQuotes!!
    }
}

@Serializable data class QuotesDict(val version: String, val quotes: List<Quote>)
@Serializable data class Quote(val text: String, val author: String, val category: String = "general")
```

Преимущество детерминизма: **одна и та же цитата в один день для всех пользователей**. На следующий день — следующая. На 121 день — снова первая (если 120 цитат). Циклично, без сохранения «прочитанных».

## А3. QuoteCard

```kotlin
@Composable
fun QuoteCard(quote: Quote, modifier: Modifier = Modifier) {
    AppleCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("❝", fontSize = 48.sp, color = SystemBlue.copy(alpha = 0.3f), modifier = Modifier.offset(y = (-8).dp))
            Text(quote.text, style = MaterialTheme.typography.titleLarge, color = Label, lineHeight = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text("— ${quote.author}", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
        }
    }
}
```

---

# Часть Б — Шапка + аватарка (~30 мин)

## Б1. HomeHeader

```kotlin
@Composable
fun HomeHeader(name: String, streak: Int, daysUntilExam: Int, onAvatarClick: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (name.isNotBlank()) "Привет, $name!" else "Привет!",
                style = MaterialTheme.typography.displayLarge,
                color = Label,
                modifier = Modifier.weight(1f)
            )
            AvatarChip(initial = name.firstOrNull()?.toString() ?: "👤", size = 44.dp, onClick = onAvatarClick)
        }
        
        Spacer(Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricChip(
                emoji = "🔥",
                value = streak.toString(),
                label = pluralize(streak, "день", "дня", "дней"),
                color = if (streak >= 7) SystemOrange else LabelSecondary
            )
            Text("·", color = LabelTertiary)
            MetricChip(
                emoji = "📅",
                value = daysUntilExam.toString(),
                label = pluralize(daysUntilExam, "день", "дня", "дней") + " до ЕГЭ",
                color = LabelSecondary
            )
        }
    }
}

fun pluralize(n: Int, one: String, few: String, many: String): String {
    val abs = abs(n) % 100
    val n1 = abs % 10
    return when {
        abs in 11..14 -> many
        n1 == 1 -> one
        n1 in 2..4 -> few
        else -> many
    }
}
```

---

# Часть В — Streak трекинг (~1.5ч)

## В1. StreakStore

```kotlin
class StreakStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("streak")
    
    private val CURRENT = intPreferencesKey("current")
    private val MAX = intPreferencesKey("max")
    private val LAST_ACTIVE = stringPreferencesKey("last_active")
    private val TODAY_SOLVED = intPreferencesKey("today_solved")
    private val TODAY_DATE = stringPreferencesKey("today_date")
    
    val state: Flow<StreakState> = context.dataStore.data.map { prefs ->
        val today = LocalDate.now()
        val savedToday = prefs[TODAY_DATE]?.let(LocalDate::parse)
        val isToday = savedToday == today
        StreakState(
            currentStreak = prefs[CURRENT] ?: 0,
            maxStreak = prefs[MAX] ?: 0,
            todaySolvedCount = if (isToday) prefs[TODAY_SOLVED] ?: 0 else 0,
            lastActiveDate = prefs[LAST_ACTIVE]?.let(LocalDate::parse)
        )
    }
    
    suspend fun onProblemSolved() {
        val today = LocalDate.now()
        context.dataStore.edit { prefs ->
            val savedDate = prefs[TODAY_DATE]?.let(LocalDate::parse)
            val isToday = savedDate == today
            val currentSolved = if (isToday) prefs[TODAY_SOLVED] ?: 0 else 0
            val newSolved = currentSolved + 1
            
            prefs[TODAY_SOLVED] = newSolved
            prefs[TODAY_DATE] = today.toString()
            
            // Достигли 10 за сегодня — streak++
            if (newSolved == 10 && prefs[LAST_ACTIVE]?.let(LocalDate::parse) != today) {
                val lastActive = prefs[LAST_ACTIVE]?.let(LocalDate::parse)
                val current = prefs[CURRENT] ?: 0
                
                val newStreak = when {
                    lastActive == null -> 1
                    lastActive == today.minusDays(1) -> current + 1
                    else -> 1  // пропустили день
                }
                
                prefs[CURRENT] = newStreak
                prefs[MAX] = maxOf(prefs[MAX] ?: 0, newStreak)
                prefs[LAST_ACTIVE] = today.toString()
            }
        }
    }
    
    suspend fun checkValidity() {
        val today = LocalDate.now()
        context.dataStore.edit { prefs ->
            val lastActive = prefs[LAST_ACTIVE]?.let(LocalDate::parse) ?: return@edit
            if (ChronoUnit.DAYS.between(lastActive, today) > 1) {
                prefs[CURRENT] = 0
            }
        }
    }
}

data class StreakState(
    val currentStreak: Int,
    val maxStreak: Int,
    val todaySolvedCount: Int,
    val lastActiveDate: LocalDate?
)
```

## В2. Подключение к местам решения

Везде где пользователь даёт ответ — вызвать `streakStore.onProblemSolved()`:

1. `ProblemDetailViewModel.checkAnswer` — при тапе «Проверить».
2. `AccentTrainerViewModel` — при Verdict (любой).
3. `WordBlankTrainerViewModel.checkAnswer` — при тапе «Проверить» в тренажёре.

## В3. На главном экране

```kotlin
val streakState by streakStore.state.collectAsState(StreakState.empty())
LaunchedEffect(Unit) { streakStore.checkValidity() }
```

Анимация при росте — `animateFloatAsState` для scale эмодзи 🔥 (1.0 → 1.4 → 1.0 spring).

## В4. Backup/Restore

Добавить в `BackupRepository` сохранение/восстановление `StreakStore`. И в `clearAll` — сбрасывать.

---

# Часть Г — Предиктор балла (~2ч)

## Г1. Таблица ФИПИ — hardcode

```kotlin
object FipiScoreTable {
    private val mathRawToTest = listOf(
        0 to 0, 1 to 5, 2 to 9, 3 to 14, 4 to 18, 5 to 23,
        6 to 27, 7 to 33, 8 to 39, 9 to 45, 10 to 50,
        11 to 56, 12 to 62, 13 to 68, 14 to 70, 15 to 72,
        16 to 74, 17 to 76, 18 to 78, 19 to 80, 20 to 82,
        21 to 84, 22 to 86, 23 to 88, 24 to 90, 25 to 92,
        26 to 94, 27 to 96, 28 to 97, 29 to 98, 30 to 99,
        31 to 100, 32 to 100
    )
    
    private val rusRawToTest = listOf(
        0 to 0, 5 to 9, 10 to 18, 15 to 27, 20 to 35,
        22 to 38, 25 to 42, 28 to 46, 30 to 49, 33 to 53,
        35 to 56, 37 to 59, 40 to 64, 42 to 67, 45 to 72,
        47 to 75, 48 to 76, 49 to 77, 50 to 78
    )
    
    fun rawToTest(subject: String, raw: Int): Int {
        val table = if (subject == "math") mathRawToTest else rusRawToTest
        return table.firstOrNull { it.first >= raw }?.second ?: 100
    }
}
```

## Г2. UserStatsStore (счётчики)

```kotlin
class UserStatsStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("user_stats")
    
    suspend fun recordAttempt(
        subject: String,
        typeNumber: Int,
        subtypeId: Long? = null,
        isCorrect: Boolean
    ) {
        context.dataStore.edit { prefs ->
            // По типу
            val totalKey = intPreferencesKey("type_total_${subject}_${typeNumber}")
            val correctKey = intPreferencesKey("type_correct_${subject}_${typeNumber}")
            prefs[totalKey] = (prefs[totalKey] ?: 0) + 1
            if (isCorrect) prefs[correctKey] = (prefs[correctKey] ?: 0) + 1
            
            // По подвиду
            subtypeId?.let { id ->
                val sTotalKey = intPreferencesKey("subtype_total_$id")
                val sCorrectKey = intPreferencesKey("subtype_correct_$id")
                prefs[sTotalKey] = (prefs[sTotalKey] ?: 0) + 1
                if (isCorrect) prefs[sCorrectKey] = (prefs[sCorrectKey] ?: 0) + 1
            }
        }
    }
    
    suspend fun getTypeStats(subject: String): List<TypeAccuracy> {
        val prefs = context.dataStore.data.first()
        val typesRange = if (subject == "math") 1..19 else 1..27
        return typesRange.map { n ->
            val total = prefs[intPreferencesKey("type_total_${subject}_$n")] ?: 0
            val correct = prefs[intPreferencesKey("type_correct_${subject}_$n")] ?: 0
            TypeAccuracy(n, total, correct, if (total > 0) correct.toFloat() / total else 0f)
        }
    }
    
    suspend fun getSubtypeStats(subtypeId: Long): Pair<Int, Int> {
        val prefs = context.dataStore.data.first()
        val total = prefs[intPreferencesKey("subtype_total_$subtypeId")] ?: 0
        val correct = prefs[intPreferencesKey("subtype_correct_$subtypeId")] ?: 0
        return total to correct
    }
}

data class TypeAccuracy(val typeNumber: Int, val attempts: Int, val correct: Int, val accuracy: Float)
```

Добавить в Backup/Restore. И в `clearAll` — сбрасывать.

## Г3. ScorePredictor

```kotlin
class ScorePredictor(private val statsStore: UserStatsStore) {
    suspend fun predictMath(): PredictorResult = predict("math", 1..19) { n ->
        when {
            n in 1..12 -> 1
            n in 13..15 -> 2
            n in 16..17 -> 3
            n in 18..19 -> 4
            else -> 1
        }
    }
    
    suspend fun predictRus(): PredictorResult = predict("rus", 1..26) { n ->
        when {
            n == 27 -> 24  // сочинение
            n in 1..26 -> 1
            else -> 1
        }
    }
    
    private suspend fun predict(subject: String, range: IntRange, maxScoreFor: (Int) -> Int): PredictorResult {
        val typeStats = statsStore.getTypeStats(subject)
        
        var expectedRaw = 0.0
        var totalCoverage = 0f
        
        typeStats.forEach { stat ->
            val maxScore = maxScoreFor(stat.typeNumber)
            val coverage = (stat.attempts.toFloat() / 15f).coerceIn(0f, 1f)
            
            val contribution = when {
                stat.attempts == 0 -> maxScore * 0.30  // нет данных — pessimistic estimate
                stat.accuracy >= 0.7f -> maxScore * 0.85
                stat.accuracy >= 0.5f -> maxScore * 0.55
                stat.accuracy >= 0.3f -> maxScore * 0.30
                else -> maxScore * 0.10
            }
            
            // Если coverage < 1, смешиваем с pessimistic
            val mixed = contribution * coverage + maxScore * 0.30 * (1 - coverage)
            expectedRaw += mixed
            totalCoverage += coverage
        }
        
        val avgCoverage = (totalCoverage / range.count()).coerceIn(0f, 1f)
        val testScore = FipiScoreTable.rawToTest(subject, expectedRaw.toInt())
        
        return PredictorResult(
            testScore = testScore,
            rawScore = expectedRaw.toInt(),
            confidence = avgCoverage,
            weakestTypes = typeStats.filter { it.attempts >= 5 }.sortedBy { it.accuracy }.take(3)
        )
    }
}

data class PredictorResult(
    val testScore: Int,
    val rawScore: Int,
    val confidence: Float,
    val weakestTypes: List<TypeAccuracy>
)
```

## Г4. PredictorCard UI

```kotlin
@Composable
fun PredictorCard(math: PredictorResult, rus: PredictorResult, targetScore: Int, onDetails: () -> Unit) {
    AppleCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text("Прогноз балла", style = MaterialTheme.typography.titleLarge, color = Label)
            }
            Spacer(Modifier.height(16.dp))
            PredictorRow("Математика", math, targetScore)
            Spacer(Modifier.height(12.dp))
            PredictorRow("Русский", rus, targetScore)
            Spacer(Modifier.height(16.dp))
            TertiaryButton("Подробнее →", onClick = onDetails, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PredictorRow(label: String, result: PredictorResult, target: Int) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Label, modifier = Modifier.weight(1f))
            Text(
                "${result.testScore}",
                style = MaterialTheme.typography.headlineMedium,
                color = when {
                    result.testScore >= target -> SystemGreen
                    result.testScore >= target - 10 -> SystemOrange
                    else -> SystemRed
                },
                fontWeight = FontWeight.Bold
            )
            Text(" / 100", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Text("цель: $target", style = MaterialTheme.typography.bodySmall, color = LabelTertiary)
        Spacer(Modifier.height(6.dp))
        // Прогресс-бар + маркер цели
        AppleProgressBar(progress = result.testScore / 100f)
        if (result.confidence < 0.3f) {
            Spacer(Modifier.height(4.dp))
            Text("Реши больше задач для точного прогноза", style = MaterialTheme.typography.bodySmall, color = LabelTertiary)
        }
    }
}
```

---

# Часть Д — Радар слабых мест (~2ч)

## Д1. SubtypeStatsRepository

```kotlin
class SubtypeStatsRepository(
    private val problemDao: ProblemDao,
    private val statsStore: UserStatsStore
) {
    suspend fun getSubtypeStats(subject: String): List<SubtypeAccuracy> {
        val subtypes = problemDao.getSubtypesBySubject(subject)
        return subtypes.map { sub ->
            val (total, correct) = statsStore.getSubtypeStats(sub.id)
            val accuracy = if (total > 0) correct.toFloat() / total else 0f
            SubtypeAccuracy(
                subtypeId = sub.id,
                subjectSlug = subject,
                typeNumber = sub.typeNumber,
                subtypeTitle = sub.title,
                kesCode = sub.kesCode,
                attempts = total,
                correct = correct,
                accuracy = accuracy,
                severity = when {
                    total < 15 -> Severity.GRAY
                    accuracy < 0.60f -> Severity.RED
                    accuracy < 0.80f -> Severity.YELLOW
                    else -> Severity.GREEN
                }
            )
        }
    }
}

data class SubtypeAccuracy(
    val subtypeId: Long, val subjectSlug: String, val typeNumber: Int,
    val subtypeTitle: String, val kesCode: String?,
    val attempts: Int, val correct: Int, val accuracy: Float,
    val severity: Severity
)

enum class Severity { GRAY, RED, YELLOW, GREEN }
```

## Д2. RadarCard с переключением 4 вариантов

```kotlin
@Composable
fun RadarCard(
    style: RadarStyle,  // из AppSettingsStore
    mathStats: List<SubtypeAccuracy>,
    rusStats: List<SubtypeAccuracy>,
    onSubtypeClick: (Long) -> Unit,
    onSolveWeakClick: () -> Unit
) {
    val allStats = mathStats + rusStats
    AppleCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text("Слабые места", style = MaterialTheme.typography.titleLarge, color = Label)
            }
            Spacer(Modifier.height(16.dp))
            when (style) {
                RadarStyle.LIST -> RadarList(allStats, onSubtypeClick)
                RadarStyle.DONUT -> RadarDonut(allStats, onSubtypeClick)
                RadarStyle.HEATMAP -> RadarHeatmap(allStats, onSubtypeClick)
                RadarStyle.RADAR_CHART -> RadarChart(allStats)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("🎯 Решить слабые места", onClick = onSolveWeakClick, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

### LIST — ограничить до топ-15 слабых

```kotlin
@Composable
fun RadarList(stats: List<SubtypeAccuracy>, onClick: (Long) -> Unit) {
    // Сначала показываем только не-серые, отсортированные по severity (RED → YELLOW → GREEN)
    val sorted = stats.filter { it.severity != Severity.GRAY }
        .sortedWith(compareBy({ it.severity.ordinal * -1 }, { it.accuracy }))  // RED первый
        .take(15)
    
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sorted.forEach { sub -> SubtypeRow(sub, onClick = { onClick(sub.subtypeId) }) }
        
        if (sorted.isEmpty()) {
            Text(
                "Реши минимум 15 задач в каком-нибудь подвиде, чтобы он появился здесь.",
                style = MaterialTheme.typography.bodyMedium,
                color = LabelSecondary
            )
        }
    }
}

@Composable
fun SubtypeRow(sub: SubtypeAccuracy, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(severityColor(sub.severity)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(sub.subtypeTitle, style = MaterialTheme.typography.bodyLarge, color = Label, maxLines = 1)
            Text("№${sub.typeNumber}", style = MaterialTheme.typography.bodySmall, color = LabelTertiary)
        }
        Text("${(sub.accuracy * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = severityColor(sub.severity), fontWeight = FontWeight.SemiBold)
    }
    AppleProgressBar(progress = sub.accuracy, modifier = Modifier.height(3.dp), trackColorOverride = LabelTertiary.copy(alpha = 0.2f), barColorOverride = severityColor(sub.severity))
}
```

### DONUT — топ-10 секторов

```kotlin
@Composable
fun RadarDonut(stats: List<SubtypeAccuracy>, onClick: (Long) -> Unit) {
    val top = stats.filter { it.severity != Severity.GRAY }.sortedBy { it.accuracy }.take(10)
    if (top.isEmpty()) {
        Text("Недостаточно данных", color = LabelSecondary)
        return
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val sweepPerItem = 360f / top.size
            top.forEachIndexed { i, sub ->
                drawArc(
                    color = severityColor(sub.severity),
                    startAngle = -90f + i * sweepPerItem,
                    sweepAngle = sweepPerItem - 3f,
                    useCenter = false,
                    style = Stroke(width = 30.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(30.dp.toPx(), 30.dp.toPx()),
                    size = Size(size.width - 60.dp.toPx(), size.height - 60.dp.toPx())
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${top.size}", style = MaterialTheme.typography.displayLarge, color = Label)
            Text("слабых", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
        }
    }
    Spacer(Modifier.height(12.dp))
    // Легенда — список под кругом
    top.take(5).forEach { sub -> SubtypeMiniRow(sub, onClick = { onClick(sub.subtypeId) }) }
}
```

### HEATMAP — сетка 7×N

```kotlin
@Composable
fun RadarHeatmap(stats: List<SubtypeAccuracy>, onClick: (Long) -> Unit) {
    val cols = 7
    val rows = (stats.size + cols - 1) / cols
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { rowIdx ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(cols) { colIdx ->
                    val sub = stats.getOrNull(rowIdx * cols + colIdx)
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (sub != null) severityColor(sub.severity).copy(alpha = 0.85f) else Color.Transparent)
                            .clickable(enabled = sub != null) { sub?.let { onClick(it.subtypeId) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sub != null && sub.severity != Severity.GRAY) {
                            Text("${sub.typeNumber}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
            }
        }
    }
    // Подпись
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(SystemRed, "<60%")
        LegendDot(SystemOrange, "60-80%")
        LegendDot(SystemGreen, ">80%")
        LegendDot(LabelTertiary, "<15 решений")
    }
}
```

### RADAR_CHART — лепестковая по 7 крупным темам

```kotlin
@Composable
fun RadarChart(stats: List<SubtypeAccuracy>) {
    // Агрегируем 50 подвидов до 7 крупных тем (по type_number)
    // Math: алгебра (1-12), геометрия (1,3,14,17), стереометрия (3,14), тригонометрия, и т.д.
    // Rus: орфография (9-15), пунктуация (16-21), текст (1-3,22-26), синтаксис (7-8)
    
    val themes = aggregateThemes(stats)  // List<ThemeAccuracy(name, accuracy)> size = 7
    if (themes.isEmpty()) {
        Text("Недостаточно данных", color = LabelSecondary)
        return
    }
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 * 0.7f
            val n = themes.size
            
            // Сетка 5 уровней
            for (level in 1..5) {
                val r = radius * level / 5f
                val path = Path()
                for (i in 0 until n) {
                    val angle = 2 * PI * i / n - PI / 2
                    val x = center.x + (r * cos(angle)).toFloat()
                    val y = center.y + (r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = LabelTertiary.copy(alpha = 0.15f), style = Stroke(1.dp.toPx()))
            }
            
            // Данные
            val dataPath = Path()
            themes.forEachIndexed { i, theme ->
                val r = radius * theme.accuracy
                val angle = 2 * PI * i / n - PI / 2
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y + (r * sin(angle)).toFloat()
                if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            }
            dataPath.close()
            drawPath(dataPath, color = SystemBlue.copy(alpha = 0.3f))
            drawPath(dataPath, color = SystemBlue, style = Stroke(2.dp.toPx()))
        }
    }
    // Подписи тем
    Spacer(Modifier.height(8.dp))
    themes.forEach { theme ->
        Row {
            Text(theme.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Label)
            Text("${(theme.accuracy * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
        }
    }
}
```

`aggregateThemes(stats)` — функция группировки 50 подвидов в 7 крупных тем. Конкретные группы можно посоветоваться или Claude Code решит сам.

---

# Часть Е — Быстрый тренажёр из радара (~1.5ч)

## Е1. Тап на сектор → подвид

В `RadarList`, `RadarDonut`, `RadarHeatmap` — onClick передаёт `subtypeId`.

В `HomeScreen`:
```kotlin
onSubtypeClick = { subtypeId ->
    val subtype = problemDao.getSubtype(subtypeId)
    navController.navigate(ProblemListRoute(subtype.typeId, subtypeId))
}
```

Открывается обычный список задач этого подвида.

## Е2. Кнопка «Решить слабые места»

```kotlin
// ui/quick/QuickTrainerViewModel.kt

class QuickTrainerViewModel(
    private val problemDao: ProblemDao,
    private val statsRepo: SubtypeStatsRepository
) : ViewModel() {
    
    suspend fun composeWeakMix(): List<Long> {  // problemIds
        val mathStats = statsRepo.getSubtypeStats("math")
        val rusStats = statsRepo.getSubtypeStats("rus")
        val all = mathStats + rusStats
        
        val redSubtypes = all.filter { it.severity == Severity.RED }.sortedBy { it.accuracy }.take(3)
        
        if (redSubtypes.isEmpty()) {
            // Нет красных — берём подвиды с малым покрытием
            val undertest = all.filter { it.attempts < 15 }.shuffled().take(3)
            return undertest.flatMap { problemDao.getRandomProblems(it.subtypeId, limit = 4) }.take(10)
        }
        
        return redSubtypes.flatMap { sub ->
            problemDao.getRandomProblems(sub.subtypeId, limit = 4).shuffled().take(3)
        }.take(10)
    }
}
```

## Е3. QuickTrainerScreen

Простая реализация — переиспользуем `ProblemDetailScreen`, передаём ему **список problemIds** вместо одного:

```kotlin
@Serializable
data class QuickTrainerRoute(val problemIds: List<Long>)

@Composable
fun QuickTrainerScreen(problemIds: List<Long>) {
    var currentIndex by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    
    if (currentIndex >= problemIds.size) {
        QuickTrainerCompleteScreen(
            total = problemIds.size,
            correct = correctCount,
            onAgain = { /* compose new mix */ },
            onHome = { navController.popBackStack(HomeRoute) }
        )
        return
    }
    
    ProblemDetailScreen(
        problemId = problemIds[currentIndex],
        titleOverride = "Быстрый тренажёр · ${currentIndex + 1} из ${problemIds.size}",
        onAnswered = { isCorrect ->
            if (isCorrect) correctCount++
            currentIndex++
        }
    )
}
```

`ProblemDetailScreen` нужно расширить параметром `onAnswered: (Boolean) -> Unit` чтобы знать когда переходить.

## Е4. Отчёт

```kotlin
@Composable
fun QuickTrainerCompleteScreen(total: Int, correct: Int, onAgain: () -> Unit, onHome: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("✓", fontSize = 64.sp, color = SystemGreen)
            Spacer(Modifier.height(16.dp))
            Text("Готово!", style = MaterialTheme.typography.displayLarge, color = Label)
            Spacer(Modifier.height(8.dp))
            Text("Решено: $correct из $total", style = MaterialTheme.typography.titleLarge, color = LabelSecondary)
            Spacer(Modifier.height(32.dp))
            PrimaryButton("Ещё раз", onClick = onAgain, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            SecondaryButton("На главный", onClick = onHome, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

---

# Часть Ж — Карточка пробников (~30 мин)

Простой превью:

```kotlin
@Composable
fun MockExamPreviewCard(daysUntil: Int, onClick: () -> Unit) {
    AppleCard(onClick = onClick) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📅", fontSize = 32.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Следующий пробник", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
                Text(
                    "Через $daysUntil ${pluralize(daysUntil, "день", "дня", "дней")}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Label
                )
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = LabelTertiary)
        }
    }
}
```

Расчёт `daysUntil`:
- Первый пробник через 4 недели после установки приложения.
- Дальше каждые 3 недели.
- Найти ближайший в будущем.

Полный календарь — Stage P3-D.

---

# Финальная сборка HomeScreen

```kotlin
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            HomeHeader(
                name = state.profile.name,
                streak = state.streak.currentStreak,
                daysUntilExam = state.profile.daysUntilExam,
                onAvatarClick = { navController.navigate(ProfileRoute) }
            )
        }
        item { QuoteCard(state.quote) }
        item { PredictorCard(state.mathResult, state.rusResult, state.profile.targetScore) {
            navController.navigate(StatsRoute)  // в P3-C
        }}
        item {
            RadarCard(
                style = state.settings.radarStyle,
                mathStats = state.mathStats,
                rusStats = state.rusStats,
                onSubtypeClick = { id -> navController.navigateToSubtype(id) },
                onSolveWeakClick = { 
                    viewModel.composeWeakMix { ids ->
                        navController.navigate(QuickTrainerRoute(ids))
                    }
                }
            )
        }
        item { MockExamPreviewCard(state.daysUntilNextMock) {
            navController.navigate(MockExamCalendarRoute)  // в P3-D
        }}
    }
}
```

---

# Smoke-тесты

| # | Что |
|---|---|
| 1 | Главный экран: "Привет, Daniel!" + аватарка справа. |
| 2 | 🔥 N дней + 📅 N дней до ЕГЭ (с правильным склонением). |
| 3 | Цитата дня — текст + автор + кавычка. |
| 4 | На следующий день цитата другая. |
| 5 | Реши 10 задач за день — streak ↑ на 1 (со spring-bounce). |
| 6 | Пропусти день — streak обнулился (max сохранился). |
| 7 | Предиктор: math + rus с цифрами, целью, цветом (зелёный/оранж/красный). |
| 8 | Малое покрытие — confidence-надпись. |
| 9 | Радар LIST: топ-15 слабых, цветные точки, %. |
| 10 | Настройки → Радар → Круговая → главный обновился: donut. |
| 11 | Heatmap 7×N с цветовыми клетками. |
| 12 | Лепестковая по 7 темам с заливкой. |
| 13 | Тап на сектор → ProblemListScreen этого подвида. |
| 14 | Кнопка "🎯 Решить слабые места" → 10 задач из топ-3 RED подвидов. |
| 15 | После 10 задач → отчёт "Решено N/10". |
| 16 | Карточка "Следующий пробник через N дней" видна. |

---

# Зависимости

Никаких новых.

---

# Что обязательно подключить

В **каждый** ViewModel где пользователь даёт ответ:

```kotlin
viewModelScope.launch {
    userStatsStore.recordAttempt(subject, typeNumber, subtypeId, isCorrect)
    streakStore.onProblemSolved()
}
```

Места:
1. `ProblemDetailViewModel.checkAnswer`.
2. `AccentTrainerViewModel.tapSyllable` (при Verdict).
3. `WordBlankTrainerViewModel.checkAnswer`.

---

# Backup/Restore

В `BackupRepository.exportBackup` добавить:
- `UserStatsStore` snapshot.
- `StreakStore` snapshot.
- `QuotesRepository` ничего (детерминированно).

В `BackupRepository.applyBackup` — восстановить.

В `BackupRepository.resetProgress` — очистить.

---

# После итерации

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского "работает".
- В отчёте:
  - Сколько цитат в quotes.json.
  - Скриншоты 4 вариантов радара.
  - Размер APK.
  - 16 smoke-тестов.

После "работает":
- Один commit Stage P3-B + tag `phase-3-stage-b-done` + push.
- Conventions #24 (Streak logic с 10/day и onProblemSolved hook), #25 (Predictor algorithm с FipiScoreTable), #26 (Radar 4 styles pattern с AppSettings binding).

---

# Last update

Главный экран Phase 3. После этого приложение превращается в полноценный мотивационный центр подготовки к ЕГЭ.
