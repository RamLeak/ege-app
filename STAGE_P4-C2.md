# STAGE_P4-C2.md — Мини-стейдж: 4 правки после теста P4-C

> 4 точечные правки после пользовательского теста Stage P4-C.
>
> Включает критичный фикс краша приложения.

---

## Что работает (НЕ ломать)

- Phase 1 + Phase 2 + Phase 3 + Phase 4 (Stage A+B+C).
- AnswerChecker, LatexCleaner, размер формул, NavHost spring, «Проверить→Далее», AI в тренажёрах (с auto-advance багом), trainerWordsLearned, Backup v1.6.
- Размер APK 230.8 MB.

---

# КРИТИЧНОЕ: ЧАСТЬ Г — Краш приложения

## Г1. Проблема

Пользователь сообщил: **«вылетело и написалось системное сообщение, что произошла ошибка и приложение закрыто из-за ошибки»**.

Это означает **uncaught exception** в любом из недавно изменённых файлов Stage P4-C:
- AnswerChecker (новый)
- LatexCleaner (новый)
- HtmlRenderer (изменён — формулы)
- AskAiBottomSheet (с customQuickQuestions)
- AccentTrainerScreen / WordBlankTrainerScreen (AI интеграция)
- UserStatsStore (trainerWordsLearned)
- ProblemDetailScreen (Проверить→Далее)

## Г2. Диагностика (СНАЧАЛА это)

### Г2.1 Получить логкат с Samsung

Если adb настроен и подключён к Samsung:
```bash
adb logcat -d -s AndroidRuntime:E *:F | tail -100
```

Это даст stack trace последнего краша. **Запиши stack trace в Concerns отчёта**.

Если adb не работает — поищи в коде potential crash points (см. ниже).

### Г2.2 Аудит potential crash points

Пройди по списку и **проверь каждый**:

**AnswerChecker.kt:**
```kotlin
fun isCorrect(userAnswer: String, correctAnswer: String, isMultipleChoice: Boolean = false): Boolean {
    // Защита от null/blank
    if (userAnswer.isBlank()) return false
    if (correctAnswer.isBlank()) return false
    // ... остальная логика
}
```

Особенно проверь:
- Что происходит если `correctAnswer = null` (Kotlin позволяет передавать `null` если тип `String?`).
- Что происходит если в `userAnswer` или `correctAnswer` есть **escape-символы** Regex (`?`, `*`, `+`).
- Что происходит если `correctAnswer = ""` (пустая строка).

**LatexCleaner.kt:**
```kotlin
fun clean(text: String): String {
    if (text.isBlank()) return text  // ЗАЩИТА
    // ...
}
```

Особенно проверь:
- Regex с `DOT_MATCHES_ALL` на гигантском тексте может медленно работать, но не падать.
- Что если `text` содержит **уже unicode-математические символы** (α, β, ≤) — некоторые символы не должны быть удалены.

**HtmlRenderer.kt:**
- Изменения в размерах формул могли сломать рендеринг на специфичных задачах с **отсутствующим natural size**.
- Проверь: `naturalWidth / naturalHeight` — деление на ноль?
- Если bitmap = null → null pointer?

**AskAiBottomSheet.kt:**
- `customQuickQuestions: List<QuickQuestion>?` — обращение к `customQuickQuestions[index]` без проверки на null?
- Тренажёры передают **новый параметр** — все вызовы где параметра НЕ было?

**Тренажёры (AccentTrainer/WordBlank):**
- Контекст AI берётся из `currentWord.word` или `currentWord.full` — что если **currentWord = null**?
- Что происходит когда `Verdict` сработал, но `viewModel.currentWord` уже = null (race condition)?

**ProblemDetailScreen.kt:**
- Кнопка «Далее →» — что если `hasNext = false` и пользователь нажимает?
- Что происходит на **последней задаче** в списке?

## Г3. Фиксы (применить ВСЕ для надёжности)

### Г3.1 try/catch в критичных местах

В `AskAiViewModel.ask()`:
```kotlin
fun ask(question: String, problemContext: String) {
    viewModelScope.launch {
        try {
            // ... текущая логика
        } catch (e: Exception) {
            android.util.Log.e("AskAi", "Crash in ask()", e)
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Внутренняя ошибка: ${e.message?.take(100)}"
            )
        }
    }
}
```

В `AnswerChecker.isCorrect()`:
```kotlin
fun isCorrect(userAnswer: String, correctAnswer: String?, isMultipleChoice: Boolean = false): Boolean {
    // Все возможные null/empty случаи
    if (userAnswer.isBlank()) return false
    if (correctAnswer.isNullOrBlank()) return false
    
    try {
        // ... текущая логика
    } catch (e: Exception) {
        android.util.Log.e("AnswerChecker", "isCorrect crash for user='$userAnswer' correct='$correctAnswer'", e)
        // Fallback на простое сравнение
        return userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
    }
}
```

В `LatexCleaner.clean()`:
```kotlin
fun clean(text: String): String {
    if (text.isBlank()) return text
    try {
        // ... все 3 прохода
        return result
    } catch (e: Exception) {
        android.util.Log.e("LatexCleaner", "clean() crash", e)
        return text  // Возвращаем исходный текст если чистка падает
    }
}
```

### Г3.2 Глобальный crash handler

В `EgeApplication.onCreate()`:
```kotlin
override fun onCreate() {
    super.onCreate()
    
    // Глобальный обработчик некаченных исключений
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        android.util.Log.e("EgeApp", "UNCAUGHT EXCEPTION", exception)
        // Можно дополнительно записать в DataStore last_crash_info для пользовательской диагностики
        defaultHandler?.uncaughtException(thread, exception)
    }
    
    // ... остальная инициализация
}
```

После следующего краша в `adb logcat -s EgeApp` будет полный stack trace.

---

# ЧАСТЬ А — AI в тренажёрах не закрывается auto-advance (~30 мин)

## А1. Проблема

Concerns #1 P4-C: после `Verdict.Correct` через 1 секунду происходит `goNext()`. Если пользователь успел открыть `AskAiBottomSheet` — окно закрывается потому что экран ушёл на следующее слово.

## А2. Фикс

В `AccentTrainerViewModel` и `WordBlankTrainerViewModel`:

```kotlin
private var pendingAdvanceJob: Job? = null

fun onVerdict(isRight: Boolean) {
    // ... текущая логика записи в статистику и т.д.
    
    if (isRight) {
        pendingAdvanceJob = viewModelScope.launch {
            delay(1000L)
            goNext()
        }
    }
}

// Когда пользователь открывает AskAi — отменяем auto-advance
fun onAskAiOpened() {
    pendingAdvanceJob?.cancel()
    pendingAdvanceJob = null
}

// Когда AskAi закрывается — возобновляем auto-advance (если ответ был правильный)
fun onAskAiClosed(wasRight: Boolean) {
    if (wasRight) {
        pendingAdvanceJob = viewModelScope.launch {
            delay(500L)  // короче, пользователь уже видел AI
            goNext()
        }
    }
}
```

В `AccentTrainerScreen` / `WordBlankTrainerScreen`:

```kotlin
if (showAi) {
    LaunchedEffect(Unit) { viewModel.onAskAiOpened() }
    
    AskAiBottomSheet(
        ...,
        onDismiss = {
            showAi = false
            viewModel.onAskAiClosed(state.lastVerdictWasRight)
        }
    )
}
```

Теперь:
- Открыл AI → auto-advance отменён.
- Закрыл AI → auto-advance запустится через 500ms (если ответ был верным).
- Если ответ был неверным — auto-advance вообще не было.

---

# ЧАСТЬ Б — Кнопки букв в тренажёрах 9-12 (~1.5 часа)

## Б1. Проблема

В тренажёрах правописания (Корни №9, Приставки №10, Суффиксы №11, Окончания №12) пользователь **вручную вводит букву** в TextField. Это:
- Медленно — нужно открывать клавиатуру, тапать, прятать.
- Раздражает на мобильном.
- Не похоже на «тренажёр» (как в Anki).

## Б2. Решение

Заменить TextField на **2-3 кнопки** с вариантами букв.

## Б3. Где взять варианты

**Подход A (быстрый):** Hardcode в коде типичные пары для каждого подвида:

```kotlin
// data/WordBlankChoices.kt
object WordBlankChoices {
    /**
     * Для каждого подвида тренажёра возвращает список букв-кандидатов.
     * Включает правильную + наиболее вероятный неправильный вариант.
     */
    fun choicesFor(subtypeSlug: String, correctLetter: String): List<String> {
        val map = mapOf(
            // Корни (№9)
            "kasn-kosn" to listOf("а", "о"),
            "polag-polozh" to listOf("а", "о"),
            "rast-rost" to listOf("а", "о"),
            "ber-bir" to listOf("е", "и"),
            "mer-mir" to listOf("е", "и"),
            "der-dir" to listOf("е", "и"),
            "ter-tir" to listOf("е", "и"),
            "per-pir" to listOf("е", "и"),
            "blest-blist" to listOf("е", "и"),
            "stel-stil" to listOf("е", "и"),
            "zheg-zhig" to listOf("е", "и"),
            "skoch-skak" to listOf("а", "о"),
            "ravn-rovn" to listOf("а", "о"),
            "mak-mok" to listOf("а", "о"),
            // Приставки (№10) — «и/ы», «з/с», «пре-/при-»
            "iz-is" to listOf("з", "с"),
            "vz-vs" to listOf("з", "с"),
            "raz-ras" to listOf("з", "с"),
            "bez-bes" to listOf("з", "с"),
            "pre-pri" to listOf("е", "и"),
            // Суффиксы (№11) — «е/и», «к/ск»
            "ec-ic" to listOf("е", "и"),
            "ev-iv" to listOf("е", "и"),
            "n-nn" to listOf("н", "нн"),
            // Окончания (№12)
            "lash-lat" to listOf("а", "я"),
            "ish-it" to listOf("е", "и"),
            "ush-yush" to listOf("у", "ю"),
        )
        
        // Если нашли точный subtype — возвращаем
        val key = subtypeSlug.lowercase()
        val direct = map[key]
        if (direct != null) {
            // Убедимся что правильный ответ в списке
            if (correctLetter in direct) return direct
            return direct + correctLetter
        }
        
        // Fallback: показываем правильный + типичные альтернативы
        return generateFallbackChoices(correctLetter)
    }
    
    /**
     * Если конкретного маппинга нет — генерируем разумные варианты.
     */
    private fun generateFallbackChoices(correct: String): List<String> {
        return when (correct.lowercase()) {
            "а" -> listOf("а", "о")
            "о" -> listOf("а", "о")
            "е" -> listOf("е", "и")
            "и" -> listOf("е", "и")
            "у" -> listOf("у", "ю")
            "ю" -> listOf("у", "ю")
            "я" -> listOf("я", "а")
            "з" -> listOf("з", "с")
            "с" -> listOf("з", "с")
            "н" -> listOf("н", "нн")
            "нн" -> listOf("н", "нн")
            else -> listOf(correct)  // single button если ничего не знаем
        }
    }
}
```

**Подход B (динамический):** Парсить `correctLetter` из БД и для каждого слова **программно** определять варианты. Сложнее. Используем Подход A.

## Б4. UI — LetterChoiceRow

```kotlin
@Composable
fun LetterChoiceRow(
    choices: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean = true
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        choices.forEach { letter ->
            LetterChoiceButton(
                letter = letter,
                isSelected = letter == selected,
                onClick = { onSelect(letter) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LetterChoiceButton(
    letter: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    PressableSurface(
        onClick = {
            if (enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        scale = 0.96f,
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) SystemBlue 
                else BgElevated
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                letter,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                color = if (isSelected) Color.White else Label,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
```

## Б5. Интеграция в WordBlankTrainerScreen

Заменить блок с `IosTextField` + кнопка «Проверить» на:

```kotlin
val choices = remember(currentWord) {
    WordBlankChoices.choicesFor(currentWord.subtypeSlug, currentWord.correctLetter)
}

var selectedLetter by remember(currentWord) { mutableStateOf<String?>(null) }

LetterChoiceRow(
    choices = choices,
    selected = selectedLetter,
    onSelect = { letter ->
        selectedLetter = letter
        // Сразу проверяем (без отдельной кнопки "Проверить")
        viewModel.checkAnswer(letter)
    },
    enabled = state.verdict == null  // disabled после Verdict
)
```

**Сразу проверяем при тапе** — экономит один клик. Если неверно — нужно дать возможность попробовать другой вариант **только если их 2-3**. Сейчас даём один шанс — если ошибся, состояние Verdict.Wrong, ждём пока пользователь нажмёт «Далее».

## Б6. Альтернатива — оставить TextField для тех кто хочет

В Профиле → Настройки → раздел «Тренажёры»:
- Переключатель «**Кнопки выбора букв** (вместо ручного ввода)» — по умолчанию ON.

Это даёт пользователю выбор. Но **по умолчанию ON** — потому что это лучший UX.

`AppSettings` получает поле `useLetterChoices: Boolean = true`.

---

# ЧАСТЬ В — Лимиты AI (~30 мин)

## В1. Проблема

Пользователь видит «превышен лимит» хотя мало запросов делал. Возможные причины:
- **Наш `dailyLimit = 50`** — может быть превышен (счётчик в `AiSettingsStore.todayUsage`).
- **Лимит провайдера** — у OpenRouter free 200/день, у Gemini free 1500/день.
- **Кеш не работает** — каждый одинаковый запрос тратит лимит.

## В2. Фиксы

### В2.1 Различать «наш лимит» vs «лимит провайдера»

В `AskAiViewModel.ask()`:

```kotlin
// Лимит провайдера (429 от API)
when (response) {
    is AiResponse.Error -> {
        if (response.isRateLimit) {
            _state.value = _state.value.copy(
                error = "Превышен лимит провайдера. Попробуй позже или переключись на другой провайдер в Настройках.",
                errorIsRateLimit = true
            )
        } else if (response.isAuthError) {
            _state.value = _state.value.copy(error = "Неверный API ключ", errorIsAuth = true)
        } else {
            _state.value = _state.value.copy(error = response.message)
        }
    }
}

// Наш лимит (todayUsage >= dailyLimit) — ДО запроса
if (!settingsStore.canMakeRequest()) {
    val current = settings.todayUsage
    val limit = settings.dailyLimit
    _state.value = _state.value.copy(
        error = "Достигнут твой дневной лимит ($current/$limit). Увеличить в Настройках → AI → Лимит."
    )
    return@launch
}
```

### В2.2 Кнопка «Сбросить счётчик сегодня»

В Настройках → AI → строка «Лимит в день» при тапе → bottom sheet:

```kotlin
@Composable
fun DailyLimitBottomSheet(
    currentLimit: Int,
    todayUsage: Int,
    onLimitChange: (Int) -> Unit,
    onResetTodayUsage: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(...) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Лимит запросов в день")
            
            // ... текущий лимит с stepper ±5 ±10
            
            Spacer(Modifier.height(16.dp))
            
            // Текущее использование
            Text("Сегодня использовано: $todayUsage из $currentLimit", color = LabelSecondary)
            
            Spacer(Modifier.height(16.dp))
            
            // Кнопка сброса (полезно если случайно потратил много)
            TertiaryButton(
                "Сбросить счётчик сегодня",
                onClick = { onResetTodayUsage() }
            )
        }
    }
}
```

В `AiSettingsStore`:
```kotlin
suspend fun resetTodayUsage() {
    context.dataStore.edit { prefs ->
        prefs[intPreferencesKey("today_usage")] = 0
    }
}
```

### В2.3 Дефолтный лимит увеличить

Сейчас 50. Слишком мало для активной подготовки. **Поднять до 200** (как у OpenRouter):

```kotlin
val dailyLimit = prefs[intPreferencesKey("daily_limit")] ?: 200  // было 50
```

### В2.4 Подсчёт через кеш — не инкрементить при cache hit

В `AskAiViewModel.ask()`:
```kotlin
val cached = cache.get(cacheKey)
if (cached != null) {
    _state.value = _state.value.copy(response = cached, isLoading = false)
    return@launch  // НЕ вызываем incrementTodayUsage!
}
// ... остальное
when (response) {
    is AiResponse.Success -> {
        settingsStore.incrementTodayUsage()  // ТОЛЬКО при реальном запросе
        cache.put(...)
    }
}
```

Проверь что **сейчас это так и есть**, не должно дублироваться при кеш-хите.

---

# Smoke-тесты

| # | Что |
|---|---|
| 1 | Приложение запускается стабильно, не падает на главном экране. |
| 2 | Math №6 → проверить ответ → не падает. |
| 3 | AI в задаче → не падает (даже если AnswerChecker получит странный ввод). |
| 4 | Тренажёр ударений → AI открыт → авто-переход НЕ срабатывает → пользователь читает → закрывает → автопереход через 500ms (если ответ был верным). |
| 5 | Тренажёр №9 «Корни» → видны 2-3 кнопки букв (а/о или е/и) → тап → проверка → быстрая обратная связь. |
| 6 | Тренажёр №10 «Приставки» → видны кнопки з/с → тап. |
| 7 | Тренажёр №11 «Суффиксы» → видны кнопки. |
| 8 | Тренажёр №12 «Окончания» → видны кнопки. |
| 9 | В Настройках можно переключить «Кнопки выбора букв» на OFF → возвращается TextField. |
| 10 | Превышение лимита провайдера (429) → понятное сообщение «Превышен лимит провайдера». |
| 11 | Превышение НАШЕГО лимита → сообщение «Достигнут твой дневной лимит N/M. Увеличить в Настройках». |
| 12 | Настройки → AI → Лимит → видна строка «Сегодня использовано: N из M». |
| 13 | Кнопка «Сбросить счётчик сегодня» → todayUsage становится 0. |
| 14 | Дефолтный лимит для новых пользователей = 200. |
| 15 | Кеш-хит не увеличивает todayUsage. |

---

# Финальные действия

- `gradlew assembleDebug`.
- НЕ коммитить.
- В отчёте:
  - Структура изменённых файлов.
  - Stack trace последнего краша (если adb доступен).
  - 15 smoke-тестов.
  - Concerns (если новые появились).

После «работает» — один commit Stage P4-C2 + tag `phase-4-stage-c2-done` + push.

Conventions:
- #55: try/catch + crash handler + потенциальные null-checks в новых классах P4-C (AnswerChecker, LatexCleaner).
- #56: pendingAdvanceJob pattern в тренажёрах для координации auto-advance с modal bottom sheets.
- #57: WordBlankChoices hardcoded map с fallback generator + LetterChoiceRow UI.
- #58: AppSettings.useLetterChoices toggle (default true).
- #59: 2 типа AI-лимитов (наш `todayUsage/dailyLimit` vs провайдерский 429) с разными сообщениями + reset кнопка.

После P4-C2 — финальный коммит **закрывает Phase 4 полностью** (tag `phase-4-done`).

---

# Last update

Stage P4-C2 — мини-стейдж с критичным фиксом краша + 3 UX правки.
