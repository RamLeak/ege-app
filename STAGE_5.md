# STAGE_5.md — Финальная итерация Фазы 2

> **Это большая итерация.** 6 частей, делать последовательно. После неё Phase 2 закроется полностью.
>
> Финальный commit ОДИН, после пользовательского «работает». До этого — ничего не коммитить.
>
> Время: 10-14 часов работы Claude Code. Если контекст растёт выше 80% — сделай `/compact` между частями.

---

## Что работает (НЕ ломать)

- Все тренажёры (№4 ударения, №9 корни, №10 приставки, №11 суффиксы, №12 окончания).
- Apple-стиль (Inter, цвета, скругления, тени, slide-анимации).
- Логика проверки ответов в задачах.
- ColorMatrix luminance inversion для всех SVG/PNG.
- HtmlRenderer с правильной классификацией formula/illustration.
- Размер APK 219 MB.

---

# ЧАСТЬ А — Память тренажёров (Resume)

## А1. Проблема и решение

Тренажёры содержат много слов (от 230 до 847). За один сеанс не пройти. Сейчас при следующем заходе — начинается с нуля.

Решение: сохранять прогресс в DataStore. При повторном заходе — bottom sheet с выбором «Продолжить / Начать сначала». При «Начать сначала» — второй bottom sheet с подтверждением.

## А2. TrainerProgressStore

Новый файл `data/TrainerProgressStore.kt`:

```kotlin
class TrainerProgressStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("trainer_progress")
    
    private fun progressKey(trainerId: String) = stringPreferencesKey("progress_${trainerId}")
    
    suspend fun saveProgress(
        trainerId: String,
        position: Int,
        totalWords: Int,
        order: String,
        orderedIndicesJson: String? = null
    ) {
        context.dataStore.edit { prefs ->
            val json = """{"position":$position,"total":$totalWords,"order":"$order","indices":${orderedIndicesJson ?: "null"}}"""
            prefs[progressKey(trainerId)] = json
        }
    }
    
    suspend fun getProgress(trainerId: String): TrainerProgress? {
        val json = context.dataStore.data.map { it[progressKey(trainerId)] }.first() ?: return null
        return parseProgressJson(json)
    }
    
    suspend fun clearProgress(trainerId: String) {
        context.dataStore.edit { it.remove(progressKey(trainerId)) }
    }
}

data class TrainerProgress(
    val position: Int,
    val totalWords: Int,
    val order: String,
    val orderedIndices: List<Int>?
)
```

## А3. TrainerId формат

| Тренажёр | ID |
|---|---|
| Ударения, существительные | `accent_nouns` |
| Ударения, прилагательные | `accent_adjectives` |
| Ударения, глаголы | `accent_verbs` |
| Ударения, причастия | `accent_participles` |
| Ударения, деепричастия | `accent_gerunds` |
| Ударения, наречия | `accent_adverbs` |
| Ударения, все слова random | `accent_all_random` |
| Ударения, все слова А-Я | `accent_all_alphabetical` |
| Корни | `blank_9` |
| Приставки | `blank_10` |
| Суффиксы | `blank_11` |
| Окончания | `blank_12` |

## А4. Когда сохранять и сбрасывать

- При каждой смене position в ViewModel → save.
- При DisposableEffect.onDispose → save.
- При завершении (последнее слово) → clearProgress.
- При смене порядка через ⇆ А-Я → clearProgress, начать с 0.

## А5. UI bottom sheets

**ResumeBottomSheet** (когда есть сохранённый прогресс):

```
┌────────────────────────┐
│ Корни                  │
│                        │
│   📍 64dp в круге      │
│                        │
│ Ты остановился на 46-м │
│ слове из 847           │
│                        │
│ [Продолжить]           │ ← PrimaryButton
│ [Начать сначала]       │ ← SecondaryButton
└────────────────────────┘
```

**ConfirmStartOverBottomSheet** (после тапа «Начать сначала»):

```
┌────────────────────────┐
│ ⚠️ 64dp в красном круге│
│                        │
│ Уверен?                │
│                        │
│ Прогресс будет сброшен.│
│ Список ошибок          │
│ сохранится.            │
│                        │
│ [Да, начать сначала]   │ ← DangerButton (новый, красный)
│ [Отмена]               │ ← SecondaryButton
└────────────────────────┘
```

ModalBottomSheet с `RoundedCornerShape(topStart=28dp, topEnd=28dp)`, фон BgElevated.

## А6. DangerButton — новый компонент в AppleButton.kt

Аналог PrimaryButton но фон `SystemRed` вместо `SystemBlue`. Текст белый 17sp SemiBold. PressableSurface scale 0.97 spring + haptic.

---

# ЧАСТЬ Б — Стрелка ← опустить + Edge swipe back

## Б1. Стрелка ниже

В `Scaffolds.kt` функция `BackButton`:

```kotlin
@Composable
private fun BackButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 56.dp)  // высота 48→56dp
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.BottomCenter  // иконка к низу
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            tint = SystemBlue,
            modifier = Modifier
                .size(28.dp)
                .padding(bottom = 8.dp)
        )
    }
}
```

## Б2. Edge swipe back

Новый файл `ui/modifiers/EdgeSwipeBack.kt`:

```kotlin
fun Modifier.edgeSwipeBack(
    enabled: Boolean = true,
    onSwipeBack: () -> Unit,
    edgeWidthDp: Dp = 24.dp,
    triggerDistanceDp: Dp = 100.dp
): Modifier = composed {
    if (!enabled) return@composed this
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { edgeWidthDp.toPx() }
    val triggerPx = with(density) { triggerDistanceDp.toPx() }
    
    pointerInput(Unit) {
        awaitEachGesture {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val startX = firstDown.position.x
            if (startX >= edgeWidthPx) return@awaitEachGesture
            
            var totalDragX = 0f
            while (true) {
                val event = awaitPointerEvent()
                val drag = event.changes.firstOrNull()?.positionChange()?.x ?: 0f
                totalDragX += drag
                if (event.changes.all { !it.pressed }) {
                    if (totalDragX > triggerPx) onSwipeBack()
                    break
                }
            }
        }
    }
}
```

Обернуть `NavHost` в `EgeApp.kt`:

```kotlin
NavHost(
    navController = navController,
    startDestination = ...,
    modifier = Modifier.edgeSwipeBack(
        onSwipeBack = {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    )
) { ... }
```

В тренажёрах ничего отдельно не делать — edge swipe и тренажёрные свайпы не конфликтуют (тренажёрный свайп начинается с любого места, edge — только x<24dp).

---

# ЧАСТЬ В — Заготовка правил (rules.json)

## В1. Что делаем

**Подход «один раз заготовить — потом пользоваться».** Вместо вызова Claude API каждый раз при тапе «Правило» в задаче — генерируем правила **заранее**, **один раз**, сохраняем в JSON, кладём в assets. AI API не нужен в runtime для правил.

## В2. Содержание правил

Для каждого типа задач (19 математика + 27 русский = **46 правил**) нужен текст правила вида:

```
# Простейшие уравнения (математика, тип 6)

## Основные виды
- Линейные: ax = b → x = b/a (при a ≠ 0).
- Квадратные: ax² + bx + c = 0 → дискриминант D = b² − 4ac.
- Показательные: aˣ = b → x = log_a(b).
- Логарифмические: log_a(x) = b → x = a^b.
- Тригонометрические: sin x = a → x = (−1)ⁿ·arcsin(a) + πn, n ∈ ℤ.

## Алгоритм решения
1. Определить тип уравнения.
2. Привести к стандартному виду.
3. Применить соответствующую формулу.
4. Проверить ОДЗ.

## Частые ошибки
- Забыли ОДЗ (особенно в логарифмах и корнях).
- Неправильное применение формулы.
- Потеря корней при возведении в квадрат.

## Пример
4/7 · x = 7 3/7
Приведём к общему знаменателю: 4x/7 = 52/7
Умножим на 7: 4x = 52
x = 13
```

## В3. Источники правил

Claude Code должен сгенерировать тексты правил используя свои знания. Это **знания общеобразовательного уровня** — формулы решения уравнений, орфографические правила, синтаксические нормы — есть в любом учебнике 10-11 класса.

Сгенерировать **полную ясную справку 200-500 слов** для каждого типа в формате Markdown.

## В4. Список типов

### Математика (19 типов профильной)

| № | Тема |
|---|---|
| 1 | Планиметрия (простая) |
| 2 | Векторы |
| 3 | Стереометрия (простая) |
| 4 | Теория вероятностей |
| 5 | Простейшие текстовые задачи |
| 6 | Простейшие уравнения |
| 7 | Производная и первообразная |
| 8 | Прикладные текстовые задачи (физический смысл) |
| 9 | Преобразования выражений |
| 10 | Прикладные задачи |
| 11 | Текстовые задачи |
| 12 | Графики и функции |
| 13 | Уравнения смешанного типа |
| 14 | Стереометрия (профильная) |
| 15 | Неравенства |
| 16 | Финансовая математика |
| 17 | Планиметрия (профильная) |
| 18 | Задача с параметром |
| 19 | Числа и их свойства |

### Русский язык (27 типов)

| № | Тема |
|---|---|
| 1 | Главная информация в тексте |
| 2 | Средства связи предложений в тексте |
| 3 | Лексическое значение слова |
| 4 | Орфоэпические нормы (ударение) |
| 5 | Лексические нормы (паронимы) |
| 6 | Лексические нормы (плеоназмы) |
| 7 | Морфологические нормы |
| 8 | Синтаксические нормы |
| 9 | Правописание корней |
| 10 | Правописание приставок |
| 11 | Правописание суффиксов |
| 12 | Правописание окончаний и причастий |
| 13 | Правописание НЕ и НИ |
| 14 | Слитное, раздельное, дефисное написание |
| 15 | Правописание -Н- и -НН- |
| 16 | Знаки препинания в сложносочинённом |
| 17 | Знаки препинания в предложениях с обособленными |
| 18 | Знаки препинания при словах, не связанных грамматически |
| 19 | Знаки препинания в сложноподчинённом |
| 20 | Знаки препинания в сложном с разными видами связи |
| 21 | Пунктуационный анализ |
| 22 | Текст как речевое произведение (смысловая и композиционная целостность) |
| 23 | Функционально-смысловые типы речи |
| 24 | Лексический анализ |
| 25 | Средства связи предложений в тексте |
| 26 | Языковые средства выразительности |
| 27 | Сочинение |

**Если фактические названия типов в БД отличаются** (`subtype.title` или `subtype.kes_code` или `type.title`) — Claude Code должен **сначала запросить БД** чтобы получить актуальные названия, а потом писать правила. SQL:

```sql
SELECT pt.number, pt.title, s.slug 
FROM problem_types pt 
JOIN subjects s ON pt.subject_id = s.id 
ORDER BY s.slug, pt.number;
```

## В5. Формат rules.json

```json
{
  "version": "ege-2026",
  "rules": {
    "math_1": {
      "subject": "math",
      "type_number": 1,
      "title": "Планиметрия (простая)",
      "markdown": "## Основные формулы\n\n- Теорема Пифагора: c² = a² + b²\n- Площадь треугольника: S = (1/2) · a · h\n...\n"
    },
    "math_6": {
      "subject": "math",
      "type_number": 6,
      "title": "Простейшие уравнения",
      "markdown": "..."
    },
    "rus_9": {
      "subject": "rus",
      "type_number": 9,
      "title": "Правописание корней",
      "markdown": "## Корни с чередованием\n\n- -лаг-/-лож-: пишется А перед Г, О перед Ж (полагать, положить).\n- -раст-/-ращ-/-рос-: А перед СТ и Щ, О перед С (растение, выращенный, росла).\n- -кас-/-кос-: А если за корнем -а- (касаться), О если нет (коснуться).\n..."
    }
  }
}
```

## В6. Создание файла

Claude Code создаёт скрипт `parser/scrapers/generate_rules.py`:

```python
"""
Генерирует rules.json со справками для всех 46 типов задач.

Использует встроенные знания Claude (без вызова API в runtime).
Тексты пишутся вручную в скрипте как структурированные Markdown-строки.
"""

import json

RULES = {
    "math_1": {
        "subject": "math",
        "type_number": 1,
        "title": "Планиметрия (простая)",
        "markdown": """## Основные формулы
- Теорема Пифагора: a² + b² = c² (для прямоугольного треугольника).
- Площадь треугольника: S = (1/2) · a · h_a = (1/2) · a · b · sin(γ).
- Теорема синусов: a/sin A = b/sin B = c/sin C = 2R.
- Теорема косинусов: c² = a² + b² − 2ab·cos C.
- Вписанная окружность: r = S / p (где p — полупериметр).
- Описанная окружность: R = abc / (4S).

## Алгоритм решения
1. Сделать чертёж (или внимательно изучить данный).
2. Обозначить известное и неизвестное.
3. Применить подходящую формулу или теорему.
4. Проверить ответ на правдоподобие.

## Частые ошибки
- Применение теоремы Пифагора к не-прямоугольному треугольнику.
- Путаница между радиусом описанной и вписанной окружности.
- Забытый множитель 1/2 в формуле площади.
"""
    },
    "math_6": {
        "subject": "math",
        "type_number": 6,
        "title": "Простейшие уравнения",
        "markdown": """## Виды уравнений и формулы
- Линейные: ax + b = 0 → x = −b/a.
- Квадратные: ax² + bx + c = 0 → D = b² − 4ac, x = (−b ± √D) / 2a.
- Показательные: a^x = b → x = log_a(b).
- Логарифмические: log_a(x) = b → x = a^b (при x > 0).
- Тригонометрические: sin x = a → x = (−1)ⁿ·arcsin(a) + πn.

## Алгоритм
1. Определить тип уравнения.
2. Привести к стандартному виду.
3. Применить формулу.
4. Проверить ОДЗ.

## Частые ошибки
- Забыли ОДЗ (логарифмы, корни, дроби).
- Потеря корней при возведении в квадрат.
"""
    },
    # ... все 46 правил ...
}

with open("../rules.json", "w", encoding="utf-8") as f:
    json.dump({"version": "ege-2026", "rules": RULES}, f, ensure_ascii=False, indent=2)
```

**ВАЖНО:** Claude Code должен **наполнить весь словарь RULES со всеми 46 правилами**. Это самая трудоёмкая часть работы. Каждое правило — 200-500 слов Markdown. Тексты пишутся **по знаниям общеобразовательной школы 10-11 класса**, ничего секретного нет.

После запуска скрипта `python generate_rules.py` создаётся `parser/rules.json` (~100-200 КБ).

Файл копируется в `android/app/src/main/assets/rules.json`.

---

# ЧАСТЬ Г — Кнопка «Правило» в задачах

## Г1. Логика

В `ProblemDetailScreen` сейчас есть кнопка «📋 Правило» (Secondary), но она вероятно ничего не делает или показывает stub. Реализовать:

- Кнопка «Правило» → загружается `rules.json` (или достаётся из памяти если уже загружен).
- Ищется правило по ключу `${subject}_${type_number}` (например `math_6` или `rus_9`).
- Показывается **ModalBottomSheet** с тайтлом «Правило · ${type.title}» и markdown-контентом.
- Markdown рендерится через простой парсер (или библиотеку `markdown-compose` если есть).

## Г2. RulesRepository

```kotlin
// data/RulesRepository.kt

@Serializable
data class RulesDict(
    val version: String,
    val rules: Map<String, RuleEntry>
)

@Serializable
data class RuleEntry(
    val subject: String,
    val type_number: Int,
    val title: String,
    val markdown: String
)

class RulesRepository(private val context: Context) {
    private var cached: RulesDict? = null
    
    suspend fun getAllRules(): RulesDict {
        cached?.let { return it }
        val json = withContext(Dispatchers.IO) {
            context.assets.open("rules.json").bufferedReader().use { it.readText() }
        }
        cached = Json.decodeFromString(json)
        return cached!!
    }
    
    suspend fun getRule(subject: String, typeNumber: Int): RuleEntry? {
        val all = getAllRules()
        return all.rules["${subject}_${typeNumber}"]
    }
}
```

## Г3. UI — RuleBottomSheet

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBottomSheet(
    rule: RuleEntry,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated,
        modifier = Modifier.fillMaxHeight(0.85f)  // занимает 85% высоты экрана
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "📋 Правило",
                style = MaterialTheme.typography.bodyMedium,
                color = LabelSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                rule.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Label
            )
            Spacer(Modifier.height(20.dp))
            
            // Markdown rendering
            SimpleMarkdownRenderer(rule.markdown)
            
            Spacer(Modifier.height(32.dp))
        }
    }
}
```

## Г4. SimpleMarkdownRenderer

Простой рендерер для базового Markdown (`##`, `-`, `**bold**`, инлайн-код):

```kotlin
@Composable
fun SimpleMarkdownRenderer(markdown: String) {
    Column {
        markdown.split("\n").forEach { line ->
            when {
                line.startsWith("## ") -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        color = Label
                    )
                    Spacer(Modifier.height(8.dp))
                }
                line.startsWith("- ") -> {
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("• ", color = SystemBlue, fontSize = 17.sp)
                        // обработка **bold** внутри
                        Text(
                            buildAnnotatedStringFromBoldMarkers(line.removePrefix("- ")),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Label
                        )
                    }
                }
                line.isBlank() -> Spacer(Modifier.height(8.dp))
                else -> {
                    Text(
                        buildAnnotatedStringFromBoldMarkers(line),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Label,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

fun buildAnnotatedStringFromBoldMarkers(line: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    var lastIdx = 0
    regex.findAll(line).forEach { match ->
        append(line.substring(lastIdx, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(match.groupValues[1])
        }
        lastIdx = match.range.last + 1
    }
    append(line.substring(lastIdx))
}
```

Подключение в `ProblemDetailScreen`:

```kotlin
var showRule by remember { mutableStateOf(false) }
val rule = viewModel.getRuleForCurrent()  // загружает по subject + type_number

SecondaryButton(
    text = "📋 Правило",
    onClick = { showRule = true }
)

if (showRule && rule != null) {
    RuleBottomSheet(rule = rule, onDismiss = { showRule = false })
}
```

---

# ЧАСТЬ Д — Избранное (звёздочка)

## Д1. UI

В шапке `ProblemDetailScreen` справа от заголовка — иконка **звёздочки** (рядом с ⋯). При тапе:
- Незаполненная звезда ☆ → заполненная ⭐ (с лёгким bounce анимации).
- Haptic LongPress.
- Сохранить в DataStore.

## Д2. FavoritesStore

```kotlin
class FavoritesStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("favorites")
    private val favoritesKey = stringSetPreferencesKey("favorite_problem_ids")
    
    suspend fun toggleFavorite(problemId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (problemId in current) current - problemId else current + problemId
        }
    }
    
    fun isFavorite(problemId: String): Flow<Boolean> =
        context.dataStore.data.map { (it[favoritesKey] ?: emptySet()).contains(problemId) }
    
    fun getAllFavorites(): Flow<Set<String>> =
        context.dataStore.data.map { it[favoritesKey] ?: emptySet() }
}
```

## Д3. Экран «Избранные задачи»

В табе «Журнал» (сейчас stub) — добавить новый раздел сверху:

```
Журнал
─────────────
⭐ Избранные задачи (12)        ›
📝 Ошибки (47)                   ›
📊 Статистика (Phase 3)         ›
```

Тап на «Избранные задачи» → новый экран `FavoritesScreen` со списком задач, отмеченных звездой. Каждая задача — превью (как в `ProblemListScreen`), тап ведёт на `ProblemDetailScreen`.

Запрос к БД: `SELECT * FROM problems WHERE id IN (:favoriteIds)`.

## Д4. Stub для «Ошибки» и «Статистика»

В `JournalScreen`:
- Звезда + Избранные = работает.
- 📝 Ошибки → пока stub «Phase 3 — журнал ошибок».
- 📊 Статистика → пока stub «Phase 3 — статистика и прогресс».

---

# ЧАСТЬ Е — Свайпы в задачах (между задачами)

## Е1. Логика

В `ProblemDetailScreen` сейчас есть кнопки «← Предыдущая» / «Далее →» снизу. Добавить **горизонтальные свайпы**:

- Свайп влево (с центра экрана) = следующая задача.
- Свайп вправо (с центра экрана) = предыдущая задача.
- НЕ конфликтует с edge swipe back (он только с x<24dp от левого края).

## Е2. Реализация

Аналогично свайпам в тренажёрах:

```kotlin
Modifier.pointerInput(currentProblemId) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            // Игнорировать если свайп начат от левого края (это edge swipe)
            if (offset.x < 24.dp.toPx()) {
                totalDrag = Float.NaN  // флаг что игнорируем
            } else {
                totalDrag = 0f
            }
        },
        onDragEnd = {
            if (!totalDrag.isNaN()) {
                when {
                    totalDrag < -100.dp.toPx() -> viewModel.goToNext()
                    totalDrag > 100.dp.toPx() -> viewModel.goToPrev()
                }
            }
            totalDrag = 0f
        },
        onHorizontalDrag = { _, dragAmount ->
            if (!totalDrag.isNaN()) totalDrag += dragAmount
        }
    )
}
```

Применить к `Column` с условием задачи (не к шапке и не к кнопкам внизу).

AnimatedContent с slide для перехода между задачами (как в тренажёре).

## Е3. Кнопки «Предыдущая» / «Далее» — оставить

Свайпы — дополнительный способ, кнопки остаются для тех кто не любит свайпы.

---

# ЧАСТЬ Ж — Светлая тема

## Ж1. Что делаем

- В `Theme.kt` добавить `lightColorScheme` с iOS-light палитрой.
- Светлая тема активируется **по системной теме** (если у пользователя на Android выбрана светлая — приложение светлое).
- В будущем (Phase 3) — переключатель в Профиле «Авто / Тёмная / Светлая».

## Ж2. Светлая палитра

```kotlin
val LightBg = Color(0xFFFFFFFF)
val LightBgElevated = Color(0xFFF2F2F7)        // iOS systemGroupedBackground
val LightBgElevated2 = Color(0xFFE5E5EA)
val LightSeparator = Color(0x143C3C43)
val LightLabel = Color(0xFF000000)
val LightLabelSecondary = Color(0x993C3C43)    // 60% opacity
val LightLabelTertiary = Color(0x4D3C3C43)     // 30% opacity

val LightSystemBlue = Color(0xFF007AFF)        // чуть темнее чем дарк-вариант
val LightSystemGreen = Color(0xFF34C759)
val LightSystemRed = Color(0xFFFF3B30)
val LightSystemOrange = Color(0xFFFF9500)
```

## Ж3. EgeTheme

```kotlin
@Composable
fun EgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = appleTypography(),
        content = content
    )
}
```

## Ж4. Инверсия SVG

В `HtmlRenderer.kt` luminance inversion применяется ТОЛЬКО при `isDarkTheme = true`:

```kotlin
val isDark = isSystemInDarkTheme()
val shouldInvert = isDark && isFormulaPath(src)
// Или для иллюстраций:
val shouldInvertIllustration = isDark  // в светлой теме иллюстрации идут как есть
```

В светлой теме формулы и иллюстрации **не инвертируются** (потому что они изначально чёрные на белом, что и нужно).

## Ж5. Проверка

Переключи системную тему на Samsung (Настройки → Дисплей → Светлая/Тёмная). Приложение должно подхватить.

В светлой теме:
- Фон белый, карточки светло-серые.
- Формулы и чертежи чёрные (без инверсии).
- Текст чёрный.
- Кнопки SystemBlue остаются синими (чуть темнее оттенок).
- Tinted-кнопки на светло-голубом фоне.

---

# ЧАСТЬ З — Иконка приложения

## З1. Концепт

Приложение для подготовки к ЕГЭ — **серьёзный учебный инструмент**. Но не скучный.

Идеи концептов (выбери один по своему вкусу или предложи свой):

**Концепт A — Геометрия:**
- Тёмно-синий фон.
- Стилизованная треугольник + окружность (как в задаче «Вписанная окружность»).
- Линии белые тонкие.

**Концепт B — Буква «E» как ЕГЭ:**
- Минималистично — большая стилизованная «E» на градиентном фоне.
- Под буквой мелким — «ЕГЭ» или «100».

**Концепт C — Формула:**
- Тёмный фон, центрировано — `x = ?` или `∫` или другой математический символ.

**Концепт D — Книга + Карандаш:**
- Стилизованная иконка раскрытой книги/тетради.

## З2. Реализация

Claude Code:
1. Создаёт SVG-исходник иконки (можно через `androidx.compose.ui.graphics.vector` или внешний SVG в `res/drawable`).
2. Использует Android Studio's Asset Studio (или эквивалент через Gradle) для генерации adaptive icon: foreground + background.
3. Размеры mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi автоматически создаются.
4. Также создаётся `ic_launcher_round.xml` для One UI Samsung.
5. В `AndroidManifest.xml` подключается `@mipmap/ic_launcher`.

## З3. Цвета иконки

- Background: тёмно-синий `#0A1E3F` или градиент `#0A1E3F` → `#1C3A6E`.
- Foreground: белый/SystemBlue `#0A84FF`.

## З4. Если есть готовая иконка — пользоваться ей

Если пользователь до запуска промпта положит файл `icon_source.png` или `icon_source.svg` в корне проекта — использовать его как foreground вместо генерации.

---

# Smoke-тесты ВСЕЙ итерации

## Часть А — Память

| # | Что |
|---|---|
| 1 | Тренажёр №4 Существительные → решить 5 слов → выйти. Зайти снова → bottom sheet «Ты остановился на 6-м слове». |
| 2 | Тап «Продолжить» → откроется на 6-м слове. |
| 3 | Зайти снова → «Начать сначала» → ВТОРОЙ bottom sheet с подтверждением. |
| 4 | «Отмена» → возврат к первому sheet. |
| 5 | «Да, начать сначала» → старт с 1-го. |
| 6 | То же для тренажёра №9 (847 слов). |
| 7 | То же для №10, №11, №12. |
| 8 | При завершении всех слов (последнее) → следующий заход НЕ показывает sheet (clearProgress сработал). |

## Часть Б — Стрелка + Edge swipe

| # | Что |
|---|---|
| 9 | Стрелка ← визуально ниже чем раньше, тап-зона удобная. |
| 10 | На любом экране задачи: свайп от левого края → возврат. |
| 11 | На главном табе: свайп от левого края → ничего. |
| 12 | В середине экрана задачи: свайп вправо → переход к предыдущей задаче (не back). |

## Часть В + Г — Правила

| # | Что |
|---|---|
| 13 | В корне проекта появился `parser/rules.json` с 46 правилами. |
| 14 | В `android/app/src/main/assets/rules.json` тоже есть. |
| 15 | Math №6 → кнопка 📋 Правило → bottom sheet с правилом «Простейшие уравнения». |
| 16 | Маркдаун рендерится: заголовки, списки, **жирный** курсив. |
| 17 | Math №13 (другая тема) → другое правило. |
| 18 | Рус №9 → правило «Правописание корней». |
| 19 | Закрыть bottom sheet (свайп вниз или кнопка X) → возврат к задаче. |

## Часть Д — Избранное

| # | Что |
|---|---|
| 20 | В шапке задачи справа — иконка звезды ☆. |
| 21 | Тап на звезду → заполненная ⭐ + лёгкий bounce. |
| 22 | Перейти на «Журнал» → новый раздел сверху «⭐ Избранные задачи (1)». |
| 23 | Тап → экран со списком избранных. |
| 24 | Тап на задачу → открывается ProblemDetailScreen этой задачи. |
| 25 | В этой задаче звезда уже заполненная ⭐. |
| 26 | Тап на звезду → снова ☆. Возврат в Журнал → счётчик уменьшился. |

## Часть Е — Свайпы

| # | Что |
|---|---|
| 27 | На задаче Math №6: свайп влево с середины экрана → следующая задача (slide-анимация). |
| 28 | Свайп вправо с середины → предыдущая. |
| 29 | Свайп от левого края (edge) → возврат назад, а не предыдущая задача. |
| 30 | Кнопки «← Предыдущая» / «Далее →» внизу продолжают работать. |

## Часть Ж — Светлая тема

| # | Что |
|---|---|
| 31 | Настройки Samsung → Светлая тема → приложение становится светлым. |
| 32 | Фон белый, карточки светло-серые. |
| 33 | Формулы чёрные на светлом (без инверсии). |
| 34 | Чертежи чёрные на светлом (без инверсии). |
| 35 | Текст чёрный. |
| 36 | Кнопки SystemBlue остались синими. |
| 37 | Обратно в тёмную тему → всё снова инвертировано как было. |

## Часть З — Иконка

| # | Что |
|---|---|
| 38 | После установки на главном экране Samsung — новая иконка приложения. |
| 39 | Иконка адаптивная (если зажать — squircle или круг). |
| 40 | Иконка на сером фоне выглядит чисто, контрастно. |

---

# Финальные действия после ВСЕЙ итерации

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского «работает».
- В отчёте:
  - Структура файлов (новые в `data/`, `ui/wordblank/`, `ui/journal/`, `parser/`).
  - rules.json — сколько правил написано, средний размер.
  - Конкретно какой концепт иконки выбрал и как реализовал.
  - 40 smoke-тестов выше — все ли пройдены логически.
  - Путь к APK, размер (будет немного больше из-за rules.json и иконки).

После «работает» на Samsung:
1. Один commit всего Stage 5.
2. Tag `phase-2-stage-5-done`.
3. Tag `phase-2-done` (закрытие всей Phase 2).
4. Push.
5. Обновление CLAUDE.md с финальными Conventions (#18 Trainer Resume pattern, #19 Rules JSON pattern, #20 Edge swipe + horizontal swipe coexistence).
6. `/compact` для подготовки к Phase 3.

---

# Если что-то не получается

- **Часть А:** ModalBottomSheet API через Material3 — проверь BOM версию.
- **Часть В:** Если правила получаются слишком длинными (>500 слов) или слишком короткими (<100) — нормализовать. Можно остановиться и спросить.
- **Часть Г:** Markdown-рендеринг даёт странности на сложных формулах — упростить, формулы остаются обычным текстом.
- **Часть Е:** Конфликт edge swipe vs problem swipe → увеличь edgeWidthDp до 32dp или introduce delay.
- **Часть Ж:** Какой-то компонент жёстко привязан к темному фону → найди и исправь через MaterialTheme.colorScheme.
- **Часть З:** Adaptive icon не генерируется → создай статичный PNG в mipmap-*.

---

# Last update

Финальная итерация Phase 2. Memory + UX + Rules + Favorites + Swipes + Light theme + Icon.

После «работает» — `phase-2-done` тег, Phase 2 закрыта.
