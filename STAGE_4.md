# STAGE_4.md — Polish 4 + Тренажёры русского №9-12

> **Две части подряд в одной итерации.**
>
> **Часть А (Polish 4):** 2 быстрые правки — гигантские формулы + перенос слогов. ~30-60 минут.
> **Часть Б (Тренажёры 9-12):** новые тренажёры для типов 9, 10, 11, 12 русского с парсингом слов из corpus.db и UI с подстановкой буквы. ~4-6 часов.
>
> Делать **последовательно**: сначала ВСЯ часть А до конца, потом часть Б.
>
> Финальный commit после ВСЕЙ итерации, после пользовательского «работает».

---

# ЧАСТЬ А — Polish 4 (последние правки Stage 3)

## А1. Гигантские формулы вылезают за экран

### Проблема

На скриншоте Math №1 «Касательная, хорда, секущая» (sdamgia_id=... показано в каталоге): формула `√3` рендерится **в размер 200×300dp** и частично выходит за экран снизу. При этом сама формула простая — должна быть inline 24sp.

Проблема в `classifyImg` или в рендере block-формул: маленькая формула классифицируется как block, и потом растягивается до полного maxHeight (360dp).

### Решение

#### А1.1 — Читать viewBox SVG чтобы определить естественный размер

```kotlin
// HtmlRenderer.kt или новый утилитный файл SvgUtils.kt

data class SvgSize(val width: Float, val height: Float)

fun readSvgViewBox(context: Context, assetPath: String): SvgSize? {
    return try {
        context.assets.open(assetPath).use { stream ->
            val bytes = stream.readNBytes(500)  // первые 500 байт достаточно
            val text = String(bytes, Charsets.UTF_8)
            
            // Ищем viewBox="x y width height"
            val regex = Regex("""viewBox\s*=\s*["']([\d.\-\s]+)["']""")
            val match = regex.find(text) ?: return@use null
            val parts = match.groupValues[1].trim().split(Regex("\\s+"))
            if (parts.size >= 4) {
                SvgSize(width = parts[2].toFloat(), height = parts[3].toFloat())
            } else null
        }
    } catch (e: Exception) {
        Log.w("SvgUtils", "Failed to read viewBox for $assetPath: ${e.message}")
        null
    }
}
```

#### А1.2 — Использовать естественный размер при рендере block-формул

В `BlockImg` компоненте:

```kotlin
@Composable
fun BlockImg(assetPath: String) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isFormula = assetPath.contains("_formulas/")
    
    // Читаем viewBox
    val viewBox = remember(assetPath) { readSvgViewBox(context, assetPath) }
    
    // Естественные dp по viewBox; конвертация ~1px ≈ 1dp для SVG
    val naturalWidth = viewBox?.width?.dp ?: 200.dp
    val naturalHeight = viewBox?.height?.dp ?: 100.dp
    
    // Для формул — НЕ растягивать. Использовать естественный размер с upper bound.
    // Для иллюстраций — растягиваем под экран, но не выше maxHeight.
    val maxHeightCap = if (isFormula) {
        // Формула: упор в естественный размер, кап на разумную высоту
        minOf(naturalHeight, 120.dp)  // даже большие формулы не больше 120dp
    } else {
        // Иллюстрация: до 360dp как раньше
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        minOf(screenHeight * 0.35f, 360.dp)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightCap)
            .padding(horizontal = 0.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        AsyncSvgOrPng(
            assetPath = assetPath,
            contentScale = if (isFormula) ContentScale.Fit else ContentScale.Fit,
            modifier = Modifier
                .let {
                    if (isFormula) {
                        // Формула — wrap content, не растягиваем
                        it.wrapContentSize()
                    } else {
                        it.fillMaxWidth()
                    }
                }
        )
    }
}
```

#### А1.3 — Дополнительно: ужесточить classifyImg

Сейчас классификатор слишком агрессивно отправляет в block. Маленькие формулы как `√3` (alt длина ~3 символа, width ~30, height ~50) НЕ должны быть block.

```kotlin
fun classifyImg(img: Element, context: Context): ImgKind {
    val isFormula = img.hasClass("tex")
    val src = img.attr("src")
    val alt = img.attr("alt")

    if (!isFormula) return ImgKind.Block  // иллюстрации всегда block

    // Читаем viewBox для точной классификации
    val viewBox = readSvgViewBox(context, src)
    
    val isLargeFormula = when {
        viewBox != null -> {
            // Точная классификация по реальному размеру
            viewBox.height > 35f || viewBox.width > 200f
        }
        // Fallback на alt-эвристики если viewBox недоступен
        alt.length > 25 -> true
        alt.contains("=") && alt.length > 12 -> true
        alt.count { it == '/' } > 1 -> true
        alt.contains("→") || alt.contains("⇔") -> true
        else -> false
    }
    return if (isLargeFormula) ImgKind.Block else ImgKind.Inline
}
```

### Smoke-тест А1

| # | Задача | Должно быть |
|---|---|---|
| 1 | Math №1 «Касательная, хорда, секущая» 2/11 | `√3` маленькая, inline или маленький block. Чертёж круга крупный. |
| 2 | Math №13 (с длинным отрезком `[-11π/2; -4π]`) | Block-формула нормального размера, не растянутая. |
| 3 | Math №6 (с `4/7 x = 7 3/7`) | Block-формула нормального размера, читаемая. |

---

## А2. Слоги в тренажёре не помещаются в строку

### Проблема

На скриншоте «Причастия 9/29» слово «кровоточащий» = 5 слогов `[кро][во][то][ча][щий]`. Все 5 пытаются влезть в одну строку, последний слог `[щий]` **обрезается справа**.

### Решение

В `AccentTrainerScreen.kt` использовать `FlowRow` с правильным переносом:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyllableRow(
    syllables: List<Syllable>,
    state: SyllableTapState,
    word: AccentWord,
    onSyllableTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = Int.MAX_VALUE  // без жёсткого лимита, переносить по ширине
    ) {
        syllables.forEachIndexed { idx, syl ->
            SyllableCell(
                syllable = syl,
                index = idx,
                state = state,
                word = word,
                onTap = { onSyllableTap(idx) }
            )
        }
    }
}
```

**Ключевые моменты:**
- `horizontalArrangement` с `Alignment.CenterHorizontally` чтобы строки центрировались.
- `verticalArrangement` для отступа между строками (если будет 2 строки).
- НЕ использовать `Row` с `Modifier.horizontalScroll` — это даёт скролл, а нам нужен перенос.

Если 5 слогов не влезают — будет 2 строки: 4 слога + 1 слог по центру. Это нормальная UX.

### Дополнительная опция — уменьшить шрифт для длинных слов

Если слово ≥ 5 слогов **И** все слоги вместе занимают больше ширины экрана, уменьшить шрифт:

```kotlin
val fontSizeForWord = when {
    syllables.size <= 4 -> 32.sp
    syllables.size == 5 -> 28.sp
    syllables.size >= 6 -> 24.sp
}
```

Это даст 6-8 слогов влезать в одну строку (например `[ве][ро][и][спо][ве][да][ни][е]` для «вероисповедание» — 8 слогов).

### Smoke-тест А2

| # | Слово | Слогов | Должно быть |
|---|---|---|---|
| 1 | банты | 2 | 1 строка, крупный шрифт |
| 2 | каталог | 3 | 1 строка |
| 3 | аэропорты | 5 | 1 строка, чуть меньше шрифт |
| 4 | кровоточащий | 5 | 1 строка (НЕ обрезается «щий») |
| 5 | вероисповедание | 8 | 2 строки или 1 со ужатым шрифтом, всё видно |

---

## После части А — НЕ собирать APK, НЕ коммитить

Сразу переходить к части Б. Финальный APK будет в самом конце.

---

# ЧАСТЬ Б — Тренажёры русского №9-12

## Что это

Типы 9-12 ЕГЭ по русскому языку — это **орфографические задания** с пропущенными буквами:

| № | Тема | Пример задания |
|---|---|---|
| **9** | Правописание корней | «р..стение» (а/о) → «растение» |
| **10** | Правописание приставок | «пр..митивный» (е/и) → «примитивный» |
| **11** | Правописание суффиксов (кроме -Н-/-НН-) | «ноч..вать» (е/ё) → «ночевать» |
| **12** | Правописание окончаний и суффиксов причастий | «бор..щийся» (ю/я) → «борющийся» |

В реальном ЕГЭ задание выглядит так:
> «Укажите варианты ответов, в которых во всех словах одного ряда пропущена одна и та же буква. Запишите номера ответов.»
> Далее идут 5 пунктов, в каждом по 3-5 слов с пропусками.

Мы упрощаем UX для тренажёра:
- Показываем **одно слово** с пропуском (например `пр..митивный`).
- Пользователь должен ввести **одну букву** на клавиатуре (или выбрать из 2-3 вариантов).
- Сразу видно правильно/неправильно.
- Через 1 сек авто-переход (если правильно) или ручной переход (если неправильно).

## Б1. Сбор данных — парсинг слов из corpus.db

### Где брать слова

У нас в `corpus.db` есть задачи с `type_number IN (9, 10, 11, 12)` для русского. В каждой задаче в `statement_html` есть **список слов с пропусками**. Например:

```html
<p>Укажите варианты, в которых пропущена буква А:</p>
<ol>
  <li>р..сти, пол..гать, выр..щенный</li>
  <li>заг..р, к..сательная</li>
  ...
</ol>
```

Наша задача — **извлечь эти слова с пропусками** и для каждого определить правильную букву.

### Подход

В `parser/scrapers/` создать новый скрипт `extract_word_blanks.py`:

```python
"""
Извлекает слова с пропусками для тренажёров №9-12 русского.

Алгоритм:
1. SELECT statement_html, solution_html FROM problems 
   WHERE type_number IN (9,10,11,12) AND subject='rus'
2. Из statement_html через regex найти все слова с .. или с двоеточием перед гласной
3. Из solution_html (где обычно «Правильно: растение, полагать, выращенный») 
   извлечь правильные написания.
4. Сопоставить: для каждого слова с пропуском найти полное слово в решении.
5. Восстановить какая буква была пропущена.
6. Сохранить в JSON по типам.

Output: parser/word_blanks.json со структурой:
{
  "version": "ege-2026",
  "types": {
    "9": {
      "title": "Правописание корней",
      "words": [
        {"masked": "р..сти", "answer": "а", "full": "расти", "rule_hint": "корень -раст-/-ращ-/-рос-"},
        {"masked": "пол..гать", "answer": "а", "full": "полагать", "rule_hint": "корень -лаг-/-лож-"},
        ...
      ]
    },
    "10": {...},
    "11": {...},
    "12": {...}
  }
}
"""

import sqlite3, re, json, os
from collections import Counter

# Регулярки для поиска слов с пропусками
WORD_WITH_BLANK = re.compile(r"\b\w*\.\.+\w*\b", re.UNICODE)

def parse_problems(db_path):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("""
        SELECT p.id, p.sdamgia_id, pt.number, p.statement_html, p.solution_html
        FROM problems p
        JOIN problem_types pt ON p.type_id = pt.id
        JOIN subjects s ON pt.subject_id = s.id
        WHERE s.slug = 'rus' AND pt.number IN (9, 10, 11, 12)
    """)
    rows = cur.fetchall()
    conn.close()
    return rows

def extract_blanks(html):
    """Найти все слова вида 'р..сти' в HTML."""
    text = re.sub(r'<[^>]+>', ' ', html or '')
    return WORD_WITH_BLANK.findall(text)

def restore_word(masked, candidates):
    """
    masked: 'р..сти'
    candidates: ['растение', 'роса', 'расти', 'ростовщик']
    
    Returns ('а', 'расти') если можно восстановить, иначе None.
    """
    # masked → regex: 'р\w+сти' (где \w+ заменит '..')
    pattern = re.compile('^' + masked.replace('..', r'(\w+)') + r'$', re.UNICODE)
    
    for cand in candidates:
        m = pattern.match(cand)
        if m:
            return m.group(1), cand  # (буква/буквы, полное слово)
    return None

# ... остальная логика парсинга
```

**Ожидаемый результат:** примерно **300-800 уникальных слов** на тип. Это нормально для тренажёра — больше чем словник ФИПИ.

### Проблема — определение правильного ответа

В реальных задачах ответ не всегда легко извлечь автоматически. Пути решения:

1. **Hard mode:** парсить solution_html и сопоставлять слова — может дать 70-80% покрытия.
2. **Soft mode:** для слов которые не удалось автоматически — добавить fallback с использованием Claude API (один раз, ~$1 на все 4 типа). API запросом дать список слов с пропусками, получить правильные буквы.
3. **Manual mode:** если автоматика не работает — собрать 100-200 самых частых слов вручную (как делали для ударений). Это утомительно.

**Рекомендую:** Claude Code сначала пробует автоматический парсинг. Если получается ≥80% — этого достаточно. Если меньше — расскажет в отчёте и предложит fallback.

### Output

После работы скрипта в проекте появится `parser/word_blanks.json` (ожидаемый размер: 50-200 КБ).

Этот файл копируется в `android/app/src/main/assets/word_blanks.json` (по аналогии с `accent_words.json`).

---

## Б2. UI тренажёров

### Б2.1. Точка входа

Аналогично тренажёру ударений (#4): для каждого из типов 9-12 русского в `SubtypesScreen` сверху списка подвидов добавить карточку:

```kotlin
if (subjectSlug == "rus" && typeNumber in listOf(9, 10, 11, 12)) {
    item {
        AppleListRow(
            icon = trainerIconFor(typeNumber),
            title = "Тренажёр: ${trainerTitleFor(typeNumber)}",
            subtitle = "${wordCountFor(typeNumber)} слов · ввод буквы",
            onClick = { navController.navigate(WordBlankTrainerRoute(typeNumber)) }
        )
    }
}

fun trainerIconFor(n: Int) = when(n) {
    9  -> "🌱"  // корни
    10 -> "🧱"  // приставки  
    11 -> "🎀"  // суффиксы
    12 -> "🌀"  // окончания и причастия
    else -> "✏️"
}

fun trainerTitleFor(n: Int) = when(n) {
    9  -> "Корни"
    10 -> "Приставки"
    11 -> "Суффиксы"
    12 -> "Окончания и причастия"
    else -> "Орфография"
}
```

### Б2.2. Маршрут

```kotlin
@Serializable
data class WordBlankTrainerRoute(
    val typeNumber: Int  // 9, 10, 11, 12
)
```

### Б2.3. Главный экран тренажёра

```
┌──────────────────────────────────┐
│ ←                          ⇆ А-Я │
│                                   │
│  Корни                            │   ← LargeTitleBar
│  47 из 312                        │
│  ▓▓▓░░░░░░░░░░░░░░░░░░░░          │
│                                   │
│                                   │
│           р..сти                  │   ← слово 56sp Bold,
│             ↑                      │     пропуск выделен синим
│                                   │
│  Какая буква пропущена?           │   ← подсказка 15sp Secondary
│                                   │
│                                   │
│  ┌──────────────────────────┐     │   ← TextField для ввода
│  │  _                       │     │     одна буква
│  └──────────────────────────┘     │
│                                   │
│  ┌──────────────────────────┐     │
│  │       Проверить           │     │   ← Primary button
│  └──────────────────────────┘     │
│                                   │
│  ← Свайп вправо: предыдущее       │
│  Свайп влево: следующее →         │
└──────────────────────────────────┘
```

### Б2.4. Логика ввода

**Вариант A — Полный ввод текстом:**

```kotlin
@Composable
fun WordBlankTrainerScreen(...) {
    var userInput by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val word = state.currentWord
    
    IosTextField(
        value = userInput,
        onValueChange = { newValue ->
            // Принимаем только 1-3 символа русских букв
            if (newValue.length <= 3 && newValue.all { it in 'а'..'я' || it == 'ё' }) {
                userInput = newValue
            }
        },
        placeholder = "Введи букву",
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        )
    )
    
    PrimaryButton(
        text = "Проверить",
        onClick = {
            viewModel.checkAnswer(userInput.trim().lowercase())
        }
    )
}
```

**Вариант Б — Кнопки вариантов** (если хочется проще):

```kotlin
// Показываем 3-4 кнопки с возможными буквами
Row(...) {
    listOf("а", "о", "е", "и").forEach { letter ->
        SecondaryButton(text = letter, onClick = { viewModel.checkAnswer(letter) })
    }
}
```

**Я рекомендую Вариант A** (ввод текстом). Это:
- Похоже на реальный ЕГЭ.
- Быстрее (не надо искать кнопку).
- Тренирует моторную память (ты пишешь букву пальцем, она запоминается).

### Б2.5. Состояние и анимации

```kotlin
sealed class BlankInputState {
    object Empty : BlankInputState()
    data class Typing(val text: String) : BlankInputState()
    data class Verdict(
        val userAnswer: String,
        val correctAnswer: String,
        val isRight: Boolean
    ) : BlankInputState()
}
```

При нажатии «Проверить»:

**Правильно:**
- Слово рендерится полностью: `р**а**сти` (правильная буква зелёная и подчёркнута).
- Большая плашка `✓ Верно` (spring scale 0.5 → 1.0).
- Haptic LongPress.
- Через 1 секунду авто-переход на следующее слово.

**Неверно:**
- Слово рендерится: `р**а**сти` (правильная буква зелёная), под ним `Ты ввёл: о` (неверная буква красная).
- Плашка `✕ Неверно`.
- Haptic TextHandleMove.
- Если у слова есть `rule_hint` — показать его как подсказку: «Корень -раст-/-ращ-/-рос-».
- Свайп влево = следующее (как в тренажёре ударений). Кнопки «Далее» НЕТ.

### Б2.6. Свайпы

Те же что в тренажёре ударений:
- Свайп влево → следующее слово.
- Свайп вправо → предыдущее.
- Авто-переход через 1 сек при правильном.

### Б2.7. Сохранение ошибок

DataStore по образцу `AccentErrorsStore`:

```kotlin
class WordBlankErrorsStore(context: Context) {
    private val dataStore = ...
    
    suspend fun saveError(typeNumber: Int, word: String, userAnswer: String) { ... }
    suspend fun getErrors(typeNumber: Int): Set<WordBlankError> { ... }
}
```

В Stage 3 в UI режим «только ошибки» НЕ добавлять — это для Phase 3 (журнал ошибок). Но **записывать** данные уже сейчас.

---

## Б3. ViewModel

```kotlin
class WordBlankTrainerViewModel(
    private val typeNumber: Int,
    private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(WordBlankTrainerState.empty(typeNumber))
    val state: StateFlow<WordBlankTrainerState> = _state.asStateFlow()
    private val errorsStore = WordBlankErrorsStore(context)

    fun loadWords() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = context.assets.open("word_blanks.json").bufferedReader().use { it.readText() }
            val dict = Json.decodeFromString<WordBlanksDict>(json)
            val words = dict.types[typeNumber.toString()]?.words ?: emptyList()
            _state.value = _state.value.copy(allWords = words.sortedBy { it.full })
        }
    }
    
    fun checkAnswer(userInput: String) { ... }
    fun goNext() { ... }
    fun goPrev() { ... }
    fun toggleOrder() { ... }  // А-Я ↔ random
}

data class WordBlankTrainerState(
    val typeNumber: Int,
    val typeTitle: String,
    val allWords: List<WordBlank>,
    val order: Order = Order.Alphabetical,
    val orderedIndices: List<Int> = emptyList(),
    val currentPosition: Int = 0,
    val inputState: BlankInputState = BlankInputState.Empty,
    val errors: Set<String> = emptySet()
)
```

---

## Б4. Smoke-тесты Часть Б

| # | Что проверить |
|---|---|
| 1 | В каталоге Русского №9 (Правописание корней) сверху есть карточка «🌱 Тренажёр: Корни». |
| 2 | Аналогично для №10 (приставки), №11 (суффиксы), №12 (окончания). |
| 3 | Тап на тренажёр №9 → новый экран с заголовком «Корни», первое слово по алфавиту. |
| 4 | Слово показано с пропуском (`..`) на месте буквы. |
| 5 | Можно ввести букву в TextField (только русские буквы 1-3 символа). |
| 6 | Нажатие «Проверить» → правильный ответ → ✓ + через 1 сек следующее слово. |
| 7 | Неверный ответ → ✕ + красная подсветка + подсказка-правило + свайп влево. |
| 8 | Свайп влево/вправо работает. |
| 9 | Авто-переход после правильного работает. |
| 10 | Аналогично для типов 10, 11, 12. |

---

# Финальные действия после ВСЕЙ итерации (Часть А + Б)

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского «работает» после Samsung.
- В отчёте:
  - **Часть А:**
    - Что сделано для гигантских формул (как реализован readSvgViewBox).
    - Что сделано для слогов (FlowRow + размер шрифта по длине слова).
    - Smoke-тесты А1 и А2 пройдены.
  - **Часть Б:**
    - Структура `extract_word_blanks.py`.
    - Сколько слов собрано на каждый тип (9/10/11/12).
    - Сколько процентов слов автоматически распарсилось (~80%+?).
    - Если меньше 80% — что предлагаешь делать (fallback на Claude API, manual).
    - Файл `word_blanks.json` создан и подключён в assets.
    - 4 новых экрана + новый ViewModel + новый ErrorsStore.
    - Smoke-тесты Б пройдены.
  - Путь к APK, размер.

---

# После «работает на Samsung»

Закроется **сразу 2 milestone**:
1. Stage 3 (commit + tag `phase-2-stage-3-done`).
2. Stage 4 (commit + tag `phase-2-stage-4-done`).

Обновится **CLAUDE.md** с новыми Conventions:
- #13: HTML refs fix в pipeline.
- #14: SVG luminance inversion для всех изображений в темной теме.
- #15: AppleButton/AppleCard/AppleListRow/AppleTextField система.
- #16: readSvgViewBox для определения естественного размера формул.
- #17: Тренажёры русского — единый паттерн (категории → trainer экран → state machine + DataStore).

---

# Если что-то не получается

- **Часть А:**
  - viewBox не читается из SVG → пропустить, использовать alt-эвристики как fallback.
  - FlowRow не переносит слоги → попробовать `LazyVerticalGrid` с adaptive columns.

- **Часть Б:**
  - Автопарсинг даёт <50% покрытия → остановиться и спросить пользователя.
  - В corpus.db не нашлось задач №9-12 русского → проверить запрос, может слаги другие.
  - JSON получился >500 КБ → разбить по типам на 4 отдельных файла.
  - Сложные слова с двумя пропусками (например `пр..кр..тить`) → пропускать на старте, для упрощения.

---

# Last update

Объединённая итерация Polish 4 + Stage 4. После неё Stage 3 и Stage 4 закроются вместе.
