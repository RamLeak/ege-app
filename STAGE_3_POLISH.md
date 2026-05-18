# STAGE_3_POLISH.md — Финальная итерация Stage 3

> Stage 3 функционально работает (баги HtmlRenderer ушли после первой итерации), но визуально это **не Apple-стиль**. Плюс отсутствует тренажёр №4 русского. Эта итерация закрывает оба пункта.
>
> **Время не ограничено.** 4-8 часов, главное — **результат на уровне iOS-приложения**.

---

## Что работает (НЕ трогать)

- Базовая навигация Catalog → Subject → Type → Subtype → Problem.
- @Entity для solutions, Room.createFromAsset с table-level PK.
- DAO с пагинацией и Next/Prev.
- Логика проверки ответа (4 формата + NULL).
- ViewModel `ProblemDetailViewModel`.
- AAPT2 ignoreAssetsPattern, asset.srcDirs, compression БД.
- Jsoup HTML-парсер.
- AndroidSVG + PNG fallback в loader.
- Выпиливание `<!--...-->` и `display:none` в sanitize.
- Размер APK (218 MB).

---

## Часть А — HtmlRenderer fix (быстрые wins)

### А1. Принудительный цвет текста (фикс серого условия)

В рендере каждого `TextSegment` **принудительно** применять `MaterialTheme.colorScheme.onBackground`:

```kotlin
withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) {
    append(segment.text)
}
```

Игнорировать любые inline-стили цвета из sdamgia. В `sanitize`:

```kotlin
private val INLINE_COLOR_REGEX = Regex("color\\s*:[^;\"]*[;\"]?", RegexOption.IGNORE_CASE)
```

Это **фикс Проблемы #2** (светло-серый текст на задаче «Вписанные окружности»).

### А2. Inline vs block — переосмыслить

Текущая логика: `class="tex"` → inline, остальное → block. Слишком грубо. Длинные многошаговые формулы пытаются влезть inline и становятся каше-строкой.

Новый алгоритм:

```kotlin
fun classifyImg(img: Element): ImgKind {
    val isFormula = img.hasClass("tex")
    val alt = img.attr("alt")

    if (!isFormula) return ImgKind.Block  // иллюстрации всегда block

    val isLargeFormula = when {
        alt.length > 25 -> true                    // длинный alt → block
        alt.contains("=") && alt.length > 12 -> true  // уравнение → block
        alt.count { it == '/' } > 1 -> true        // несколько дробей → block
        alt.contains("→") || alt.contains("⇔") -> true  // многошаговое → block
        else -> false
    }
    return if (isLargeFormula) ImgKind.Block else ImgKind.Inline
}
```

### А3. Размер inline-формул

- height = `baseFontSize × 1.4` ≈ 24sp при базе 17sp.
- width = адаптивный по `readSvgViewBox` (первые 200 байт SVG), fallback 80sp.
- `Placeholder` с `PlaceholderVerticalAlign.Center`.

### А4. Размер block-картинок

- maxHeight адаптивный: `min(screenHeight × 0.35, 360dp)`.
- Полная ширина с padding 20dp слева-справа.
- Угловое скругление 14dp.
- Лёгкий border 1dp `onSurface.copy(alpha = 0.08f)`.
- ContentScale.Fit (не Crop) — чертежи должны быть видны полностью.

---

## Часть Б — Apple-визуал (главная работа)

Сравнивай с iOS-приложениями (Notes, Reminders, Settings, Health).

### Б1. Цветовая система

```kotlin
val Bg = Color(0xFF000000)
val BgElevated = Color(0xFF1C1C1E)
val BgElevated2 = Color(0xFF2C2C2E)
val Separator = Color(0x14FFFFFF)

val Label = Color(0xFFFFFFFF)
val LabelSecondary = Color(0xFFEBEBF5).copy(alpha = 0.60f)
val LabelTertiary = Color(0xFFEBEBF5).copy(alpha = 0.30f)

val SystemBlue = Color(0xFF0A84FF)
val SystemGreen = Color(0xFF30D158)
val SystemRed = Color(0xFFFF453A)
val SystemOrange = Color(0xFFFF9F0A)
val SystemYellow = Color(0xFFFFD60A)
```

Контраст между `Bg` (#000) и `BgElevated` (#1C1C1E) около 11%. Для крупных карточек достаточно, но мелкие элементы (TextField) должны быть на `BgElevated2` чтобы выделяться.

### Б2. Шрифт — Inter через Google Fonts

```kotlin
implementation("androidx.compose.ui:ui-text-google-fonts:1.7.6")

val Provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)
val Inter = GoogleFont("Inter")
val InterFamily = FontFamily(
    Font(googleFont = Inter, fontProvider = Provider, weight = FontWeight.Normal),
    Font(googleFont = Inter, fontProvider = Provider, weight = FontWeight.Medium),
    Font(googleFont = Inter, fontProvider = Provider, weight = FontWeight.SemiBold),
    Font(googleFont = Inter, fontProvider = Provider, weight = FontWeight.Bold),
)
```

Typography:

```kotlin
Typography(
    displayLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 41.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
)
```

Если Google Fonts провайдер не работает — fallback на **bundled Inter ttf** в `res/font/`.

### Б3. Отступы

| Что | Стало |
|---|---|
| Горизонтальный padding экрана | **20dp** |
| Между крупными секциями | **24dp** |
| Между связанными блоками | **12dp** |
| Внутренний padding карточек | **20dp** |
| Между TextField и кнопкой | **16dp** |

### Б4. Скругления

| Элемент | Радиус |
|---|---|
| Крупные карточки | **20dp** |
| Bottom sheets | **24dp** (верхние углы) |
| Primary кнопки | **14dp** |
| Secondary кнопки | **12dp** |
| TextField | **12dp** |

### Б5. Тени

```kotlin
Modifier.shadow(
    elevation = 12.dp,
    shape = RoundedCornerShape(20.dp),
    spotColor = Color.Black.copy(alpha = 0.5f),
    ambientColor = Color.Transparent
)
```

Для bottom sheet — elevation 20.dp.

### Б6. Кнопки

**Primary («Проверить»):**
- Full-width, height 52dp, SystemBlue заливка, Color.White 17sp Semibold, corner 14dp.
- Тап: scale 0.97 + lightener overlay.
- Только одна primary на экран.

**Secondary («Правило», «ИИ»):**
- Tinted: bg `SystemBlue.copy(alpha = 0.12f)`, text SystemBlue 17sp Medium.
- Disabled: bg `SystemBlue.copy(alpha = 0.08f)`, text LabelTertiary.
- Height 44dp, corner 12dp.

**Tertiary («Предыдущая», «Далее»):**
- Borderless с текстом SystemBlue, иконки 16dp, height 44dp.

**Кнопка «Авторское решение»:**
- 17sp Medium SystemBlue + иконка ▼/▲.
- spring(dampingRatio=0.75, stiffness=Spring.StiffnessMediumLow).

### Б7. Поле ввода — iOS-style

```kotlin
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(BgElevated2, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = TextStyle(color = Label, fontSize = 17.sp, fontFamily = InterFamily, lineHeight = 22.sp),
        cursorBrush = SolidColor(SystemBlue),
        singleLine = true,
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = LabelTertiary, fontSize = 17.sp, fontFamily = InterFamily)
            inner()
        }
    )
}
```

### Б8. Шапка экрана задачи — большой заголовок iOS-style

```
┌──────────────────────────────────┐
│ ←                          ⭐  ⋯ │
│                                   │
│  №6                       1/70    │   ← 34sp Bold + 13sp LabelTertiary справа
│  Простейшие уравнения             │   ← 15sp LabelSecondary
│                                   │
└──────────────────────────────────┘
   padding: 20dp горизонталь, 12dp низ
```

### Б9. Плашка результата — большая карточка

```
┌──────────────────────────────────┐
│  ✓                                │   ← Иконка 32dp в круге SystemGreen
│  Правильно                        │   ← 22sp Semibold
│  Можешь посмотреть решение или    │   ← 15sp LabelSecondary
│  перейти к следующей задаче       │
└──────────────────────────────────┘
   padding 20dp, corner 20dp, bg SystemGreen.copy(alpha=0.15f)
```

Spring-появление: scale 0.85 → 1.0 + fade.

### Б10. Bottom-bar

- Высота 80dp.
- Фон: `Bg.copy(alpha = 0.85f)` + ideally backdrop blur.
- Иконки 24dp + подпись 11sp Medium.
- Активный: SystemBlue. Неактивный: LabelTertiary.
- Тап: haptic LongPress + scale 0.92.
- Сверху bottom-bar — separator 1dp `LabelTertiary.copy(alpha=0.18f)`.

### Б11. Spring-анимации везде

```kotlin
val springSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)
```

Применить к:
- Переход между задачами (Next/Prev): fade + slide horizontal.
- Раскрытие решения.
- Появление плашки результата.
- Тап на кнопки: scale 0.97 на 60ms → spring back.
- Bottom-bar активация таба.

### Б12. Тактильная отдача

```kotlin
val haptic = LocalHapticFeedback.current
haptic.performHapticFeedback(HapticFeedbackType.LongPress)
```

- «Проверить» правильно: `LongPress`.
- «Проверить» неверно: `TextHandleMove`.
- Раскрытие решения: `LongPress`.
- Тап таба bottom-bar: `LongPress`.
- В тренажёре №4 буквы: правильно/неверно.

---

## Часть В — Тренажёр №4 ударений

### В1. Источник данных — реальный словарь ФИПИ

Файл `accent_words.json` уже сконвертирован из словника ФИПИ ЕГЭ 2023. **230 слов в 6 категориях**.

**Положить в:** `android/app/src/main/assets/accent_words.json`

Структура:

```json
{
  "version": "ege-2023",
  "source": "Орфоэпический словник ФИПИ ЕГЭ 2023",
  "categories": [
    {
      "id": "nouns",
      "title": "Существительные",
      "words": [
        {"word": "аэропорты", "stressed_index": 5},
        {"word": "банты", "stressed_index": 1},
        ...
      ]
    },
    {"id": "adjectives", "title": "Прилагательные", "words": [...]},
    {"id": "verbs", "title": "Глаголы", "words": [...]},
    {"id": "participles", "title": "Причастия", "words": [...]},
    {"id": "gerunds", "title": "Деепричастия", "words": [...]},
    {"id": "adverbs", "title": "Наречия", "words": [...]}
  ]
}
```

- `word`: слово в нижнем регистре (например "каталог").
- `stressed_index`: 0-индексированная позиция ударной гласной (для "каталог" = 5, буква 'о').

| Категория | Слов |
|---|---|
| Существительные | 65 |
| Прилагательные | 11 |
| Глаголы | 107 |
| Причастия | 29 |
| Деепричастия | 8 |
| Наречия | 10 |

### В2. Точка входа — карточка в каталоге Русского

В `TypesScreen` (когда `subjectId = rus`) — **первая карточка сверху**, ДО списка типов:

```
┌──────────────────────────────────┐
│ 🔤  Тренажёр ударений             │
│     230 слов · словарь ФИПИ       │  ›
└──────────────────────────────────┘
```

Стиль: фон `BgElevated`, иконка 32dp в круге `SystemBlue.copy(alpha=0.15f)`, заголовок 17sp Semibold, подзаголовок 13sp LabelSecondary.

Под этой карточкой — обычный список 27 типов.

### В3. Экран выбора категории

Тап на карточку → `AccentTrainerCategoriesScreen`:

```
┌──────────────────────────────────┐
│ ← Тренажёр ударений               │
│                                   │
│  Выбери раздел для тренировки     │
│                                   │
│  ┌──────────────────────────┐     │
│  │ 📦 Существительные   65 ›│     │
│  │ 🎨 Прилагательные    11 ›│     │
│  │ ⚡ Глаголы          107 ›│     │
│  │ 🔄 Причастия         29 ›│     │
│  │ 🎯 Деепричастия       8 ›│     │
│  │ 🏃 Наречия           10 ›│     │
│  └──────────────────────────┘     │
│                                   │
│  ─────────────────────────        │
│                                   │
│  ┌──────────────────────────┐     │
│  │ 🎲 Все слова (230)        │     │
│  │    Перемешать все        ›│     │
│  └──────────────────────────┘     │
│                                   │
└──────────────────────────────────┘
```

Каждая категория — тапаемая, ведёт на `AccentTrainerScreen` с фильтром. «Все слова» — без фильтра, случайный порядок.

### В4. Экран тренажёра — UI

```
┌──────────────────────────────────┐
│ ←  Существительные · 47/65  ⇆ А-Я │
│  ▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░          │
│                                    │
│                                    │
│         к а т а л о г              │   ← 56sp Bold
│                                    │   каждая гласная тапаема (≥56×56dp)
│                                    │
│                                    │
│  Тапни на ударный гласный,         │   ← 15sp LabelSecondary
│  тапни ещё раз чтобы подтвердить   │
│                                    │
└──────────────────────────────────┘
```

После второго тапа:

**Правильно:**
```
         к а т а л [О] г             ← Зелёная подсветка
         ✓ Верно                     ← 22sp SystemGreen + spring fade-in
         (через 1 секунду авто-переход)
```

**Неверно:**
```
         к [А] т а л [О] г           ← Красная на выбранной, зелёная на правильной
         ✕ Неверно. Правильно: каталОг
         [Далее →]                   ← Активная кнопка (нет авто-перехода)
```

### В5. Логика тапов

```kotlin
sealed class LetterTapState {
    object None : LetterTapState()
    data class FirstTap(val index: Int) : LetterTapState()
    data class Verdict(val selected: Int, val correct: Int, val isRight: Boolean) : LetterTapState()
}

val VOWELS = setOf('а','е','ё','и','о','у','ы','э','ю','я')

fun onLetterTap(letterIndex: Int, word: AccentWord, state: LetterTapState): LetterTapState {
    val ch = word.word[letterIndex]
    if (ch !in VOWELS) return state  // согласные игнорируем
    
    return when (state) {
        is LetterTapState.None -> LetterTapState.FirstTap(letterIndex)
        is LetterTapState.FirstTap -> {
            if (state.index == letterIndex) {
                // второй тап на ту же букву = выбор
                LetterTapState.Verdict(
                    selected = letterIndex,
                    correct = word.stressed_index,
                    isRight = letterIndex == word.stressed_index
                )
            } else {
                // тап на другую гласную = новая подсветка
                LetterTapState.FirstTap(letterIndex)
            }
        }
        is LetterTapState.Verdict -> state  // verdict уже показан
    }
}
```

### В6. Авто-переход после правильного

```kotlin
LaunchedEffect(verdict) {
    if (verdict is Verdict && verdict.isRight) {
        delay(1000L)
        viewModel.goNext()
    }
}
```

При неверном — авто-перехода нет, ручной тап «Далее».

### В7. Порядок слов

```kotlin
data class AccentTrainerState(
    val allWords: List<AccentWord>,
    val order: Order = Order.Alphabetical,
    val orderedIndices: List<Int>,
    val currentPosition: Int = 0,
    val tapState: LetterTapState = LetterTapState.None,
    val errors: Set<String> = emptySet()
)

enum class Order { Alphabetical, Random }
```

Переключатель `⇆ А-Я ↔ Случайно`:
- На Random: `Collections.shuffle(orderedIndices)`, `currentPosition = 0`.
- На Alphabetical: `orderedIndices = allWords.indices.sortedBy { allWords[it].word }`.

### В8. Сохранение ошибок (для Phase 3)

```kotlin
implementation("androidx.datastore:datastore-preferences:1.1.1")
```

Сохранять `errors: Set<String>` в DataStore. В Stage 3 UI режим «только ошибки» НЕ добавлять — это Phase 3. Но **записывать** уже сейчас, чтобы данные накапливались.

### В9. Навигация

```kotlin
@Serializable
data object AccentCategoriesRoute

@Serializable
data class AccentTrainerRoute(val categoryId: String? = null)  // null = все
```

Добавить в NavHost.

---

## Smoke-тесты после итерации

| # | Что проверить |
|---|---|
| 1 | Задача №6 (26662): `4/7 x = 7 3/7` крупная. Решение — block-картинка, не каше-строка. |
| 2 | Задача №13: `[-11π/2; -4π]` — block-картинка. |
| 3 | Задача №1 «Вписанные окружности»: текст ярко-белый, не серый. |
| 4 | Задача №1 (27238): чертёж видно полностью, не обрезан. |
| 5 | Каталог Русского: сверху карточка «🔤 Тренажёр ударений · 230 слов». |
| 6 | Экран категорий: 6 категорий с правильным количеством (65/11/107/29/8/10) + «Все слова (230)». |
| 7 | Тренажёр существительных: первое слово «аэропорты», подсказка по тапу. |
| 8 | Тап на согласную → ничего. Тап на гласную → подсветка. Тап на другую гласную → переподсветка. |
| 9 | Тап правильной гласной 2 раза → ✓, через 1 сек переход. |
| 10 | Тап неверной 2 раза → ✕, правильная подсветится зелёным, авто-перехода нет, «Далее» активна. |
| 11 | Переключатель ⇆ А-Я → случайный → А-Я работает, позиция сбрасывается. |
| 12 | Шрифт Inter применён везде, заголовки 22-34sp Bold. |
| 13 | Тактильная отдача при «Проверить» (правильно/неверно — разная). |

---

## Зависимости

```kotlin
implementation("androidx.compose.ui:ui-text-google-fonts:1.7.6")
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")  // если ещё нет
```

---

## Время

**Не ограничиваю.** 4-8 часов на качественную итерацию.

Если по дороге понадобится выделить общие компоненты (`AppleCard`, `AppleButton`, `AppleTextField`, `AppleScaffold`) — делай. Это **итерация полировки**, можно перерабатывать структуру.

---

## После итерации

- `gradlew assembleDebug`.
- **НЕ коммитить.** Жду от пользователя «работает на Samsung, нравится».
- В отчёте:
  - Структура новых файлов (тренажёр + UI-компоненты).
  - Добавленные зависимости.
  - Описание Apple-style системы.
  - Реализация тренажёра №4.
  - Чек-лист 13 пунктов выше.
  - Путь к APK, размер.

---

## Если не получается

- Google Fonts провайдер не работает → bundled Inter ttf в `res/font/`.
- HtmlRenderer на конкретной задаче ломается → пришли sdamgia_id, пользователь даст screenshot.
- Архитектурный вопрос → обсуди до реализации.
