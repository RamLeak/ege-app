# STAGE_P4-C.md — Финальная полировка Phase 4

> **8 точечных правок после тестирования Stage P4-A+B.** Закрывает Phase 4 полностью.
>
> Время: 5-7 часов.
>
> Состав по критичности:
> - **Часть А** — Корректность ответов в русском (несколько правильных вариантов).
> - **Часть Б** — LaTeX в ответах AI (рендеринг + системный промпт).
> - **Часть В** — Размер формул в задачах (исправление масштабирования).
> - **Часть Г** — UX переходов («Проверить» → «Далее», плавность тапа).
> - **Часть Д** — AI в тренажёрах.
> - **Часть Е** — Fix «Слов в тренажёре» и Gemini error handling.

---

## Что работает (НЕ ломать)

- Phase 1 + Phase 2 + Phase 3 + Phase 4 (Stage A+B со всеми 3 fix).
- AI через OpenRouter, Gemini, Anthropic.
- SecureKeyStore.
- Бэкап v1.5.
- Пробники по предметам + ФИПИ варианты.
- Размер APK 230.8 MB.

---

# ЧАСТЬ А — Корректность ответов в русском (~1.5 часа)

## А1. Проблема

В заданиях ЕГЭ по русскому **часто несколько правильных ответов**. Например в №4 «Постановка ударения» нужно записать «несколько слов», в №8 «Синтаксические нормы» — «несколько чисел». Пользователь вводит **один из правильных ответов** — система говорит «неверно».

Это **критический баг** — пользователь теряет мотивацию.

## А2. Структура правильных ответов в БД

В `problems.short_answer` для русского хранится **строка**. Анализы реальных задач показывают форматы:
- `"14"` — одно правильное «14» (например «выпишите номера 1 и 4»).
- `"14|41"` — или 14, или 41 (порядок не важен).
- `"бороду"` — слово.
- `"бороду|боруду"` — варианты написания/ударения.
- `"123"` — это означает три числа подряд (1, 2, 3 как множество).

Точный формат нужно **посмотреть в реальных данных** — Claude Code должен сделать SQL:

```sql
SELECT subject_slug, problem_type_number, short_answer 
FROM problems 
WHERE subject_slug = 'rus' AND short_answer IS NOT NULL
LIMIT 200;
```

Чтобы увидеть реальные форматы и адаптировать парсер.

## А3. Новый парсер

`data/AnswerChecker.kt`:

```kotlin
object AnswerChecker {
    /**
     * Проверяет ответ пользователя против правильного.
     * Учитывает:
     * - Множественные правильные варианты через "|" или ";".
     * - Числовые ответы вида "14" могут означать "14" или "1 и 4" или "4 и 1" — 
     *   принимаем любую перестановку цифр для типов с множественным выбором.
     * - Регистр и пробелы игнорируем.
     */
    fun isCorrect(userAnswer: String, correctAnswer: String, isMultipleChoice: Boolean = false): Boolean {
        val cleaned = userAnswer.trim().lowercase().replace(Regex("\\s+"), "")
        
        // Множество правильных вариантов через |
        val correctVariants = correctAnswer.split("|", ";").map { 
            it.trim().lowercase().replace(Regex("\\s+"), "") 
        }
        
        // Прямое совпадение с любым вариантом
        if (correctVariants.any { it == cleaned }) return true
        
        // Если все символы — цифры, разрешаем перестановки
        if (cleaned.all { it.isDigit() }) {
            val cleanedSorted = cleaned.toCharArray().sorted().joinToString("")
            correctVariants.forEach { variant ->
                if (variant.all { it.isDigit() }) {
                    val variantSorted = variant.toCharArray().sorted().joinToString("")
                    if (cleanedSorted == variantSorted) return true
                }
            }
        }
        
        return false
    }
}
```

## А4. Заменить везде проверку

В `ProblemDetailViewModel.checkAnswer()`:

```kotlin
// БЫЛО:
// val isCorrect = userAnswer.trim() == problem.shortAnswer

// СТАЛО:
val isCorrect = AnswerChecker.isCorrect(
    userAnswer = userAnswer,
    correctAnswer = problem.shortAnswer ?: "",
    isMultipleChoice = problem.typeNumber in listOf(1, 2, 4, 5, 6, 7, 8, 24, 25)  // типы с множественным выбором
)
```

В `MockExamRunnerViewModel` тоже.

В тренажёрах ударений и пропусков — обычно один правильный ответ, **но** на всякий случай тоже использовать `AnswerChecker`.

## А5. Подсказка в UI

В `ProblemDetailScreen`, если в `problem.shortAnswer` содержится `|` или это очевидно multi-answer — показать **под полем ввода** мелким серым текстом:

```kotlin
if (problem.shortAnswer?.contains("|") == true || problem.typeNumber in multiAnswerTypes) {
    Text(
        "💡 Можно ввести любой из правильных вариантов",
        style = MaterialTheme.typography.bodySmall,
        color = LabelSecondary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
```

---

# ЧАСТЬ Б — LaTeX в ответах AI (~1.5 часа)

## Б1. Проблема

На скриншоте 3 ответ AI содержит `\(\frac{4}{7}\)`, `\[ \frac{52}{7} \]`, `\cdot` — это **LaTeX**. `SimpleMarkdownRenderer` не умеет рендерить LaTeX → выводит сырой текст. Пользователь видит «техно-кашу» и не может прочитать формулы.

## Б2. Два подхода

### Подход 1 (быстрый) — попросить AI НЕ использовать LaTeX

В `EGE100_SYSTEM_PROMPT` (`ai/Providers.kt`) добавить **жёсткие инструкции**:

```kotlin
const val EGE100_SYSTEM_PROMPT = """
Ты помогаешь школьнику разобраться с задачами ЕГЭ по математике или русскому.

КРИТИЧНО — НИКОГДА не используй LaTeX, MathML, или специальную математическую разметку. Это твоё САМОЕ ВАЖНОЕ правило.

ПРАВИЛА ФОРМУЛ:
- Дроби пиши через слэш: 4/7 (не \frac{4}{7}).
- Смешанные дроби словами или через дефис: 7 целых 3/7 (не 7\frac{3}{7}).
- Умножение точкой или звёздочкой: 4 · x или 4*x (не \cdot).
- Степени через ^: x^2 (не x^{2}).
- Корни словами: корень из 5 или √5 (не \sqrt{5}).
- Греческие буквы словами: альфа, бета, пи (не \alpha, \beta, \pi).
- Многоточие точками: ... (не \ldots).

Запрещённые символы: \, $, {, }, ^{, _{.

ПРИМЕР ХОРОШО:
Решим уравнение 4/7 · x = 7 целых 3/7.
Шаг 1: 7 целых 3/7 = 52/7.
Шаг 2: 4/7 · x = 52/7. Умножим обе части на 7: 4x = 52.
Шаг 3: x = 13.
Ответ: 13.

Объясняй по шагам, как в учебнике. Используй обычный markdown (## заголовки, - списки, **жирный**). Максимум 350 слов.
"""
```

### Подход 2 (полный) — отрендерить LaTeX в Markdown

Добавить **постпроцессинг ответа AI** перед отображением. Функция `cleanLatex(text: String): String`:

```kotlin
fun cleanLatex(input: String): String {
    var s = input
    
    // Inline LaTeX \(...\) → просто текст
    s = s.replace(Regex("""\\\((.+?)\\\)"""), "$1")
    
    // Display LaTeX \[...\] → текст на отдельной строке
    s = s.replace(Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL), "\n$1\n")
    
    // \frac{a}{b} → a/b
    s = s.replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}"""), "$1/$2")
    
    // \cdot → ·
    s = s.replace("\\cdot", "·")
    
    // \sqrt{x} → √x
    s = s.replace(Regex("""\\sqrt\{([^{}]+)\}"""), "√$1")
    
    // x^{2} → x^2 (если в скобках одно число)
    s = s.replace(Regex("""\^\{([^{}]+)\}"""), "^$1")
    
    // _{i} → _i
    s = s.replace(Regex("""_\{([^{}]+)\}"""), "_$1")
    
    // \alpha → α, \beta → β и т.д.
    val greek = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\theta" to "θ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\pi" to "π", "\\sigma" to "σ", "\\phi" to "φ", "\\omega" to "ω",
        "\\Alpha" to "Α", "\\Beta" to "Β", "\\Gamma" to "Γ", "\\Delta" to "Δ",
        "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Omega" to "Ω"
    )
    greek.forEach { (latex, char) -> s = s.replace(latex, char) }
    
    // \leq, \geq, \neq, \approx, \to
    s = s.replace("\\leq", "≤")
    s = s.replace("\\geq", "≥")
    s = s.replace("\\neq", "≠")
    s = s.replace("\\approx", "≈")
    s = s.replace("\\to", "→")
    s = s.replace("\\infty", "∞")
    s = s.replace("\\ldots", "...")
    
    // Удалить остатки чистых маркеров
    s = s.replace(Regex("""\\[a-zA-Z]+"""), "")
    s = s.replace(Regex("""\{|\}"""), "")
    
    return s
}
```

## Б3. Применение

В `AskAiViewModel`:

```kotlin
when (val response = provider.ask(...)) {
    is AiResponse.Success -> {
        val cleanedText = cleanLatex(response.text)  // ← NEW
        // ... сохранение в кеш, state.response = cleanedText
    }
}
```

В кеш сохраняем **уже очищенный** текст.

**Делаем оба подхода одновременно** — Подход 1 (просим AI не использовать LaTeX) + Подход 2 (фоллбек если всё равно использует). Это **двойная защита**.

---

# ЧАСТЬ В — Размер формул в задачах (~1.5 часа)

## В1. Проблема (скриншот 1)

В Math №6 условие `4/7 · x = 7 3/7` отображается **крошечно** относительно заголовка «Найдите корень уравнения:». Формула — это `<img>` элемент с фиксированным размером ~14px, отрендеренный в `WebView` или `HtmlRenderer` без масштабирования.

## В2. Анализ — какой рендер используется

Открыть `HtmlRenderer.kt`. Там должна быть логика обработки `<img>` тегов. Скорее всего:

```kotlin
// Гипотеза: формулы рендерятся через AndroidSvg или Coil-SVG/PNG, размер = naturalSize.
val intrinsicWidth = bitmap.width.dp  // обычно 50-100dp для маленьких формул
val intrinsicHeight = bitmap.height.dp
```

И этот размер используется прямо. На больших экранах формула **зажата** в небольшую область.

## В3. Решение — minSize и scaleFactor

Добавить **минимальный размер формул и иллюстраций**:

```kotlin
// HtmlRenderer.kt при отрисовке <img>:
val displayDensity = LocalDensity.current.density  // обычно 2.5-3.5 на телефонах
val baseScale = 1.6f  // увеличить базово
val minHeight = 28.dp  // минимум 28dp высоты для inline формул
val targetHeight = (naturalHeight * baseScale).coerceAtLeast(minHeight.value)
val targetWidth = naturalWidth * (targetHeight / naturalHeight)

Image(
    bitmap = bitmap.asImageBitmap(),
    modifier = Modifier
        .height(targetHeight.dp)
        .width(targetWidth.dp),
    contentScale = ContentScale.Fit
)
```

## В4. Различать formula vs illustration

В существующей логике (Convention #16 readSvgSize) уже различают `BlockFormula` (центральная большая формула) и inline формулы. Нужно проверить чтобы:
- **Inline формулы** в строке (как `4/7` посреди текста) — minHeight 28dp.
- **Block-формулы** (центральные, на отдельной строке) — minHeight 48dp, ширина растягивается до 90% контейнера.
- **Illustrations** (чертежи, графики) — minHeight 120dp, ширина до 100% контейнера.

## В5. Проверка результата

После фикса Math №6 должна выглядеть так:
- Заголовок «Найдите корень уравнения:» — как сейчас.
- Формула `4/7 · x = 7 3/7` — **в 2-2.5 раза крупнее** чем сейчас.

Также проверить:
- Math №1 (Планиметрия) — чертежи должны быть видны полностью.
- Math №14 (Стереометрия) — большие чертежи на 90% ширины.
- Math №18 (Параметр) — много текста + малые формулы — все читаемо.
- Math №19 (Числа) — обычно текстовые задачи без формул, не должно ничего поломаться.

---

# ЧАСТЬ Г — UX переходов (~1 час)

## Г1. Плавность тапа на задание (пункт 4)

В `ProblemListScreen` при тапе на задачу — мгновенный переход без анимации. Хочется плавнее.

Добавить **slide-spring анимацию перехода** между экранами через `NavHost`:

```kotlin
NavHost(
    ...,
    enterTransition = { 
        slideInHorizontally(
            initialOffsetX = { it / 3 },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(280))
    },
    exitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / 5 },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeOut(tween(220))
    },
    popEnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -it / 5 },
            animationSpec = spring(...)
        ) + fadeIn(tween(280))
    },
    popExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = spring(...)
        ) + fadeOut(tween(220))
    }
)
```

Должно ощущаться как iOS — мягкий slide справа налево.

## Г2. «Проверить» → «Далее» при правильном (пункт 5)

В `ProblemDetailScreen` сейчас:
- Кнопка «Проверить» внизу-средне.
- Кнопка «Далее →» — в самом низу экрана (нужно тянуться).

Когда пользователь правильно решил — экономия движения через **замену кнопки**:

```kotlin
// state.verdict:
// - null (не проверено) → кнопка "Проверить"
// - WRONG → кнопка "Проверить" (пользователь хочет попробовать снова после правки)
// - CORRECT → кнопка "Далее →"

PrimaryButton(
    text = when (verdict) {
        Verdict.CORRECT -> "Далее →"
        else -> "Проверить"
    },
    onClick = when (verdict) {
        Verdict.CORRECT -> { viewModel.goToNext() }
        else -> { viewModel.checkAnswer() }
    },
    enabled = userAnswer.isNotBlank() || verdict == Verdict.CORRECT
)
```

Когда `verdict = CORRECT` — кнопка меняется на «Далее →», тап в том же месте → следующая задача. Палец **не двигается**.

Дополнительно: после `CORRECT` — показывать **зелёный галочный baгет** «✓ Верно» вместо обычного состояния. Можно с лёгкой spring-анимацией.

---

# ЧАСТЬ Д — AI в тренажёрах (~1 час)

## Д1. Куда добавить

3 экрана:
- `AccentTrainerScreen` (тренажёр ударений).
- `WordBlankTrainerScreen` (тренажёр пропусков букв в №9-12).

После Verdict (правильно или неверно) — кнопка **«🤖 Спросить ИИ»** становится активной.

## Д2. Контекст для AI

Для тренажёра ударений:
```
problemContext = "Слово: ${currentWord.text}. Правильное ударение на слог: ${currentWord.correctSyllable}. Пользователь поставил ударение на: ${userTappedSyllable}."
```

Для тренажёра пропусков:
```
problemContext = "Слово с пропуском: ${currentWord.template}. Правильная буква: ${currentWord.correctLetter}. Пользователь ввёл: ${userInput}."
```

## Д3. Быстрые вопросы

Для тренажёров другие быстрые вопросы:
- **«Почему именно эта буква/слог?»**
- **«Какое правило?»**
- **«Похожие примеры»**
- **«Запомнить»** (мнемоника).

## Д4. UI

Используем тот же `AskAiBottomSheet` что в задачах. Просто **передаём другой контекст** и другие быстрые вопросы.

---

# ЧАСТЬ Е — Прочее (~1 час)

## Е1. Fix «Слов в тренажёре» (пункт 7)

На скриншоте 3 (из предыдущего теста): «Слов в тренажёре: 0». Это **счётчик решённых слов** во всех тренажёрах. Должен быть положительный.

**Гипотеза:** Где-то в `AccentTrainerViewModel` или `WordBlankTrainerViewModel` после `Verdict.CORRECT` не вызывается обновление `UserStatsStore` или `AchievementsState`.

**Фикс:** В каждом тренажёре после `Verdict.CORRECT`:

```kotlin
viewModelScope.launch {
    userStatsStore.incrementTrainerWordsLearned()
    streakStore.onProblemSolved()
}
```

И в `UserStatsStore`:

```kotlin
suspend fun incrementTrainerWordsLearned() {
    context.dataStore.edit { prefs ->
        val key = intPreferencesKey("trainer_words_learned")
        prefs[key] = (prefs[key] ?: 0) + 1
    }
}

suspend fun getTrainerWordsLearned(): Int {
    return context.dataStore.data.map { it[intPreferencesKey("trainer_words_learned")] ?: 0 }.first()
}
```

В `AchievementsRow` показывать значение через этот метод.

Также добавить в Backup v1.6.

## Е2. Gemini error handling (пункт 2)

Скриншот 2: сырая HTML страница ошибки 502 от Google. Нужно **обрабатывать non-JSON ответы**.

В `GeminiProvider.ask()`:

```kotlin
when (response.code) {
    400, 401, 403 -> AiResponse.Error("Неверный API ключ", isAuthError = true)
    429 -> AiResponse.Error("Превышен лимит (1500/день). Завтра обнулится.", isRateLimit = true)
    500, 502, 503, 504 -> AiResponse.Error("Google Gemini временно недоступен. Попробуй через пару минут или переключись на OpenRouter.", isRateLimit = false)
    in 200..299 -> {
        val body = response.body!!.string()
        try {
            val json = JSONObject(body)
            // ... обычный парсинг
        } catch (e: JSONException) {
            AiResponse.Error("Неожиданный ответ от сервера Gemini. Попробуй позже.")
        }
    }
    else -> AiResponse.Error("Ошибка ${response.code}")
}
```

Аналогично для всех 3 провайдеров — если получаем не-JSON в успешном ответе, ловим JSONException.

---

# Backup v1.6

`BackupSnapshot v1.6` добавить:
- `trainerWordsLearned: Int = 0`

`SUPPORTED_VERSIONS = ["1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6"]`.

---

# Smoke-тесты

## Часть А — Корректность

| # | Что |
|---|---|
| 1 | Рус №4 с двумя правильными ударениями ("слово1\|слово2") — оба варианта принимаются. |
| 2 | Рус №8 с ответом "14" — введи "41" → тоже верно (перестановка цифр). |
| 3 | Math №1 с одним ответом "12.5" — введи "12.5" → верно, "12,5" тоже принимается. |
| 4 | Подсказка "Можно ввести любой из вариантов" показывается под полем когда есть `|` в ответе. |

## Часть Б — LaTeX

| # | Что |
|---|---|
| 5 | Math №6 → AI «Объясни решение» → формулы выглядят как 4/7 · x = 7 3/7, БЕЗ \frac{}{}. |
| 6 | Если AI вдруг пришлёт \frac{a}{b} — UI преобразует в a/b. |
| 7 | \cdot → ·, \sqrt{x} → √x, \alpha → α. |

## Часть В — Формулы

| # | Что |
|---|---|
| 8 | Math №6 формула `4/7 x = 7 3/7` видна нормально (не крошечная). |
| 9 | Math №1 чертёж читаем без зума. |
| 10 | Math №14 стереометрический чертёж занимает 90% ширины. |
| 11 | Math №19 текстовая задача — текст обычный, ничего не поломалось. |

## Часть Г — UX

| # | Что |
|---|---|
| 12 | Тап на задачу из списка → плавный slide-in справа. |
| 13 | Возврат назад → плавный slide-out вправо. |
| 14 | Math №6 → введи правильный ответ → «Проверить» → кнопка стала «Далее →». |
| 15 | Тап «Далее →» в той же позиции → следующая задача без движения пальца. |
| 16 | Math №6 → введи неверный ответ → кнопка осталась «Проверить» (для повтора). |

## Часть Д — AI в тренажёрах

| # | Что |
|---|---|
| 17 | Тренажёр ударений → после Verdict кнопка «🤖 ИИ» enabled. |
| 18 | AskAi контекст содержит "Слово: ... Правильное ударение: ...". |
| 19 | Тренажёр пропусков №10 → AskAi → контекст с правильной буквой. |

## Часть Е — Прочее

| # | Что |
|---|---|
| 20 | Реши 5 слов в тренажёре №4 → Статистика → «Слов в тренажёре: 5». |
| 21 | Gemini 502 ошибка → понятное сообщение «Google Gemini временно недоступен», не сырой HTML. |
| 22 | Backup v1.6 включает trainerWordsLearned. |
| 23 | Импорт старого v1.5 работает (trainerWordsLearned default 0). |

---

# Финальные действия

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского «работает».
- В отчёте:
  - Изменения по 8 пунктам.
  - 23 smoke-теста.
  - Размер APK.
  - Concerns если есть.

После «работает»:
- Один commit Stage P4-C + tag `phase-4-stage-c-done` + tag `phase-4-done` (закрытие Phase 4!).
- Conventions #48-54:
  - #48: AnswerChecker с поддержкой множественных вариантов и перестановок цифр.
  - #49: cleanLatex для AI ответов + системный промпт против LaTeX.
  - #50: Размер формул через minHeight + scaleFactor (inline 28dp, block 48dp, illustration 120dp).
  - #51: NavHost slide-spring transitions.
  - #52: «Проверить» → «Далее →» при verdict=CORRECT.
  - #53: AI в тренажёрах с контекстом конкретного слова.
  - #54: Gemini/OpenRouter/Anthropic error handling с JSONException fallback.

После P4-C → Phase 4 ЗАКРЫТА. Останется только Phase 5 (SRS Spaced Repetition).

---

# Last update

Stage P4-C — финальная полировка Phase 4. 8 пунктов после пользовательского тестирования.
