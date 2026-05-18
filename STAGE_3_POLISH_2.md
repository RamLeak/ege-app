# STAGE_3_POLISH_2.md — Финальные правки Stage 3

> После Stage 3 Polish 1 пользователь увидел приложение на Samsung Galaxy. Общий курс правильный (Apple-стиль выдержан). Осталось **9 точечных правок**, после которых Stage 3 закроется окончательно.
>
> **КРИТИЧНОЕ:** Правка #9 — иллюстрации (чертежи) в приложении НЕ показываются НИ ОДНОЙ задачи. Пользователь проверил математику №1, №3, №13, №14 — везде только формулы, никаких чертежей. Это блокирующий баг для математики, особенно стереометрии и планиметрии.
>
> Время не ограничено. 3-6 часов на чистую работу.

---

## Что работает (НЕ трогать)

- Apple-стиль (Inter шрифт, плашка результата с большой иконкой, шапка с большим заголовком, тени, скругления).
- Тренажёр №4: 3-state логика тапов (None → FirstTap → Verdict), переключатель А-Я ↔ 🎲, авто-переход через 1 сек после правильного, акут над правильной буквой, DataStore для ошибок.
- Логика проверки ответа в задачах (4 формата + NULL).
- HtmlRenderer структура (Jsoup parsing, AnnotatedString + InlineTextContent).
- **SVG-формулы РАБОТАЮТ** — видны на всех скриншотах. Только их цвет нужно инвертировать (правка #1).
- ВСЯ навигация Catalog → Subject → Type → Subtype → Problem.
- Размер APK 218 MB.

---

## Правка #1 — Инверсия цвета SVG-формул в тёмной теме

**Проблема:** SVG-формулы из sdamgia имеют зашитый `fill="black"`. На чёрном фоне они невидимы (сливаются).

**Решение:** в `SvgLoader` применять `ColorMatrixColorFilter` инверсии когда тема тёмная.

```kotlin
fun renderSvgToBitmap(
    assetPath: String,
    width: Int,
    height: Int,
    invertColors: Boolean
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    context.assets.open(assetPath).use { stream ->
        SVG.getFromInputStream(stream).renderToCanvas(Canvas(bitmap))
    }
    
    if (invertColors) {
        val invertMatrix = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
             0f,-1f, 0f, 0f, 255f,
             0f, 0f,-1f, 0f, 255f,
             0f, 0f, 0f, 1f,   0f
        ))
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(invertMatrix) }
        val inverted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(inverted).drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
        return inverted
    }
    return bitmap
}
```

**Применяется к:**
- Inline-формулам и block-формулам в условиях и решениях.
- НО **только если это формула** (файл из `_formulas/` ИЛИ `<img class="tex">`).
- **Цветные иллюстрации (диаграммы, графики) — НЕ инвертировать**, потому что они могут содержать цветные элементы которые потеряют смысл при инверсии.

```kotlin
val shouldInvert = isDark && (
    assetPath.contains("_formulas/") || isTexFormula
)
```

---

## Правка #2 — Принудительный белый текст в условиях/решениях

В `HtmlRenderer` через `withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground))` цвет принудительно применяется ко всем `TextSegment`. Если где-то sdamgia вставила `<span style="color: ...">` — наша sanitize-регулярка `INLINE_COLOR_REGEX` это вычищает.

Это уже должно быть после Stage 3 Polish 1, но **проверить ещё раз** что работает в решениях, не только в условиях.

---

## Правка #3 — Перенос тренажёра №4 в SubtypesScreen

**Сейчас:** Карточка «🔤 Тренажёр ударений» в `TypesScreen` русского над списком 27 типов.

**Стало:** Карточка должна быть **внутри типа №4 русского** в `SubtypesScreen` — над списком подвидов.

```kotlin
// SubtypesScreen.kt
if (typeNumber == 4 && subjectSlug == "rus") {
    item {
        AppleListRow(
            icon = "🔤",
            title = "Тренажёр ударений",
            subtitle = "Словник ФИПИ · 230 слов",
            onClick = { navController.navigate(AccentCategoriesRoute) }
        )
    }
    item { Spacer(Modifier.height(8.dp)) }
}
// Дальше обычный список подвидов
```

В `TypesScreen.kt` карточку убрать.

---

## Правка #4 — Третий режим «🔤 Все по алфавиту»

В `AccentCategoriesScreen` после `🎲 Все слова (230)` добавить:

```
🔤 Все по алфавиту (230) — А→Я подряд
```

`AccentTrainerRoute` принимает `defaultOrder: String? = null`. ViewModel: если null → random, если "alphabetical" → sortedBy word.

Toggle ⇆ А-Я ↔ 🎲 работает как раньше.

---

## Правка #5 — Слоговой режим в тренажёре (Вариант В)

**Применяется ко ВСЕМ словам**, не только длинным. Консистентная UX.

### Алгоритм деления

```kotlin
val VOWELS = setOf('а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я')

fun syllabify(word: String): List<Syllable> {
    val syllables = mutableListOf<Syllable>()
    var startIdx = 0
    
    word.forEachIndexed { i, ch ->
        if (ch in VOWELS) {
            val isLastChar = i == word.length - 1
            val nextIsLastConsonant = (i + 1 == word.length - 1) && (word.last() !in VOWELS)
            val end = if (isLastChar || nextIsLastConsonant) word.length else i + 1
            syllables.add(Syllable(
                text = word.substring(startIdx, end),
                startIndexInWord = startIdx,
                endIndexInWord = end - 1
            ))
            startIdx = end
            if (end == word.length) return@forEachIndexed
        }
    }
    return syllables
}

data class Syllable(
    val text: String,
    val startIndexInWord: Int,
    val endIndexInWord: Int
)
```

Примеры:
- `каталог` → `[ка][та][лог]`
- `аэропорты` → `[а][э][ро][пор][ты]`
- `вероисповедание` → `[ве][ро][и][спо][ве][да][ни][е]`

### UX

1. Слово показывается как набор слогов-кнопок (FlowRow, перенос на новую строку при необходимости).
2. Слог = кнопка 60dp height, фон `BgElevated2`, corner 14dp, шрифт 32sp Bold, padding 14dp×12dp.
3. Тап на слог → он раскрывается (scale 1.05 spring). Гласные внутри становятся отдельно тапаемыми (40dp×40dp кнопки с фоном `BgElevated3`, corner 8dp). Согласные внутри — просто текст без тап-функции.
4. Тап на другой слог = свернуть текущий, раскрыть новый.
5. Тап на гласную = FirstTap (подсветка).
6. Второй тап на ту же гласную = Verdict.
7. **Спец-случай:** если в слоге ровно одна гласная — раскрыть сразу при первом тапе слога (без дополнительного шага).

При неверном ответе — правильный слог тоже раскрывается, показывает правильную гласную с акутом ´.

---

## Правка #6 — Свайпы в тренажёре

**Свайп влево** = следующее слово.
**Свайп вправо** = предыдущее слово.
Авто-переход при правильном ответе остаётся (через 1 сек).

```kotlin
Modifier.pointerInput(currentWordIndex) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragEnd = {
            when {
                totalDrag < -80.dp.toPx() -> viewModel.goNext()
                totalDrag > 80.dp.toPx()  -> viewModel.goPrev()
            }
            totalDrag = 0f
        },
        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount }
    )
}
```

Анимация: текущее слово уезжает в сторону свайпа + затухает, новое заезжает с другой стороны. Spring (MediumBouncy, Medium).

---

## Правка #7 — Убрать кнопку «Далее» при неверном ответе

При неверном ответе показывается плашка «✕ Неверно. Правильно: аэропОрты».
- **Убрать** PrimaryButton «Далее →» снизу.
- Заменить на текст-подсказку под плашкой: «Свайпни влево для следующего слова».
- Навигация исключительно свайпами.

---

## Правка #8 — Увеличить тап-зону стрелки ← в шапке

```kotlin
IconButton(
    onClick = onBack,
    modifier = Modifier.size(48.dp).padding(start = 4.dp)
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Назад",
        tint = SystemBlue,
        modifier = Modifier.size(28.dp)
    )
}
```

Применить во всех `LargeTitleBar` и `SmallTitleBar` с back-стрелкой.

---

## ПРАВКА #9 (КРИТИЧНАЯ) — Иллюстрации НЕ показываются

### Проблема

Пользователь подтвердил: **в приложении НЕТ НИ ОДНОЙ задачи с чертежом/иллюстрацией.** Проверил математику №1 (Планиметрия), №3 (Стереометрия), №13 (Тригонометрия), №14 (Стереометрия). Везде только формулы (SVG inline/block работают), но **никаких чертежей**.

В Phase 1 мы собрали **3510 иллюстраций**. Они есть на диске в `parser/assets/{sdamgia_id}/img_N.{svg|png}`. В APK они также включены (это проверено в Stage 3 fix — 56,209 entries в assets).

Значит проблема в **рендере**, не в наличии файлов.

### Этап А — Диагностика (обязательная, в Logcat)

Сделай отдельный diagnostic-режим с debug-логированием для иллюстраций.

#### А1. Найти задачи с иллюстрациями в БД

```sql
SELECT p.id, p.sdamgia_id, p.type_number, 
       substr(p.statement_html, 1, 500) as html_preview
FROM problems p 
JOIN problem_types pt ON p.type_id = pt.id
JOIN subjects s ON pt.subject_id = s.id
WHERE s.slug = 'mathb'
  AND p.statement_html LIKE '%<img%'
  AND p.statement_html NOT LIKE '%class="tex"%'
LIMIT 20
```

Логировать результаты. Должны быть задачи где `<img>` без `class="tex"` — это иллюстрации.

Если запрос вернёт 0 строк — все `<img>` в БД с `class="tex"`. Значит наш парсер в Phase 1 не сохранил иллюстрации как `<img>` в HTML.

#### А2. Проверить что файлы реально в APK

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "assets/[0-9]+/" | head -30
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "assets/[0-9]+/" | wc -l
```

Должно быть **3000+** файлов вида `assets/{number}/img_N.{svg|png}`. Если их меньше — проблема в packaging.

#### А3. Логирование в HtmlRenderer

Добавить debug-логи в HtmlRenderer для всех `<img>` тегов:

```kotlin
Log.d("HtmlRenderer", "Processing img: src='${img.attr("src")}', class='${img.attr("class")}', alt='${img.attr("alt")}'")

when (kind) {
    ImgKind.Inline -> Log.d("HtmlRenderer", "  → classified as INLINE formula")
    ImgKind.Block  -> Log.d("HtmlRenderer", "  → classified as BLOCK")
}
```

И в загрузчике SVG:

```kotlin
Log.d("SvgLoader", "Loading: $assetPath")
try {
    val bitmap = renderSvgToBitmap(...)
    Log.d("SvgLoader", "  → loaded successfully, ${bitmap.width}×${bitmap.height}")
} catch (e: Exception) {
    Log.e("SvgLoader", "  → FAILED: ${e.message}")
}
```

#### А4. Открыть конкретную задачу и снять логи

Конкретная задача для диагностики — **Math №3 Стереометрия, первая задача первого подвида**. У стереометрии чертёж почти всегда есть.

В отчёте Claude Code должны быть:
1. Какой `<img>` найден в statement_html этой задачи (полный src, class, alt).
2. Как HtmlRenderer его классифицировал (inline/block).
3. Был ли успех при загрузке SVG/PNG.
4. Если был fail — точное исключение.

### Этап Б — Возможные причины и фиксы

В зависимости от того что покажет диагностика, может быть один из этих сценариев:

#### Сценарий 1 — URL в HTML не соответствует пути в assets

Например в БД хранится `<img src="https://pic-ege.sdamgia.ru/get_file?id=12345">`, а файл лежит в `assets/29384/img_1.svg`.

**Фикс:** добавить транслятор URL → asset path. Скорее всего парсер уже это делает (есть `sdamgia_id` для задачи), но проверить.

```kotlin
fun resolveImgSrc(srcAttr: String, problemSdamgiaId: String): String {
    // Если src уже asset path — вернуть как есть
    if (srcAttr.startsWith("_formulas/") || srcAttr.matches(Regex("\\d+/img_\\d+\\..*"))) {
        return srcAttr
    }
    // Если src — sdamgia URL, преобразовать
    if (srcAttr.contains("pic-ege.sdamgia.ru") || srcAttr.contains("get_file")) {
        // Логика преобразования...
        return "$problemSdamgiaId/img_1.svg"  // или вызов parser-функции
    }
    return srcAttr
}
```

#### Сценарий 2 — Расширение файла не определено

Файл может быть `assets/12345/img_1` без расширения. Тогда AndroidSVG и BitmapFactory оба не сработают.

**Фикс:** в Phase 1 был `fix_illustration_extensions.py` — он проходил по всем иллюстрациям и определял реальный Content-Type (SVG vs PNG vs JPG), переименовывал файлы. Проверить что после Phase 1 у всех файлов есть расширения:

```bash
find parser/assets -type f ! -name "*.svg" ! -name "*.png" ! -name "*.jpg" ! -name "*.json" | head
```

Если есть файлы без расширения — это баг Phase 1, надо перезапустить `fix_illustration_extensions.py`.

#### Сценарий 3 — Block-картинка классифицирована неправильно

В правке #2 алгоритма classifyImg:
```kotlin
if (!isFormula) return ImgKind.Block  // иллюстрации всегда block
```

Это должно работать — все `<img>` без `class="tex"` идут в block. Но если **в HTML вообще нет block-картинок** (все теги имеют class="tex" или отсутствуют) — значит парсер в Phase 1 не сохранил иллюстрации.

#### Сценарий 4 — Block-картинка попадает в render, но не отображается

Например AsyncImage с `data = "file:///android_asset/12345/img_1.svg"` возвращает onError. Но AndroidSVG прямой вызов работает.

**Фикс:** убедиться что для block-картинок тоже используется AndroidSVG direct (как для formulas в Stage 3 fix), не Coil-SVG.

#### Сценарий 5 — Папки `_formulas/` показываются, но `12345/` нет

В Stage 3 fix мы переопределили `ignoreAssetsPattern` чтобы убрать `_*` из ignored. Но возможно мы оставили какой-то другой паттерн который рубит numeric-folders.

Проверить:
```kotlin
androidResources {
    ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
}
```

Тут не должно быть ничего что блокирует числовые папки. Если в APK реально нет `assets/12345/img_1.svg` — нужно расследование Gradle assets pipeline.

### Этап В — Smoke-тесты после фикса

| # | Задача | Что должно быть |
|---|---|---|
| 1 | Math №3 Стереометрия (любая) | Чертёж куба/пирамиды/призмы виден |
| 2 | Math №14 Стереометрия профильная | Чертёж стереометрической фигуры |
| 3 | Math №2 Векторы | Координатная плоскость с векторами |
| 4 | Math №1 Планиметрия (некоторые) | Треугольник/окружность |
| 5 | Math №18 Параметр | График функции |
| 6 | Math №16 Финансовая | График/таблица (некоторые задачи) |

Достаточно если **хотя бы у 50% задач с предполагаемой иллюстрацией** реально виден чертёж. У некоторых задач иллюстраций нет в исходнике sdamgia — это нормально.

### Этап Г — Полное расширение файлов

Пользователь сказал «проверь чтобы картинки были с полным расширением». Это значит:

1. Прогнать команду:
```bash
find parser/assets -type f ! -name "*.svg" ! -name "*.png" ! -name "*.jpg" ! -name "*.json" 2>/dev/null
```

2. Если есть файлы без расширения — перезапустить логику из `parser/scrapers/fix_illustration_extensions.py` (она должна определять Content-Type по magic bytes).

3. Если в HTML БД есть `<img src="...img_1">` без `.svg` — добавить fallback в HtmlRenderer: попробовать `.svg`, потом `.png`, потом `.jpg`.

---

## Smoke-тесты после итерации

| # | Что проверить |
|---|---|
| 1 | Формулы в условиях задач БЕЛЫЕ на тёмном фоне. |
| 2 | Формулы в решениях БЕЛЫЕ на тёмном фоне. |
| 3 | Каталог Русского: НЕТ карточки тренажёра. Только 27 типов. |
| 4 | Тип №4 русского: внутри есть карточка «🔤 Тренажёр ударений». |
| 5 | Категории тренажёра: 6 + «🎲 Все слова (230)» + «🔤 Все по алфавиту (230)». |
| 6 | Слова в тренажёре показываются как слоги-кнопки. |
| 7 | Тап на слог → раскрытие → тап на гласную → подсветка → второй тап → verdict. |
| 8 | «вероисповедание» помещается на экран как слоги. |
| 9 | Свайп влево → следующее слово. Свайп вправо → предыдущее. |
| 10 | При неверном ответе НЕТ кнопки «Далее». Только текст «Свайпни влево». |
| 11 | Стрелка ← в шапке легко тапается (тап-зона 48dp). |
| 12 | **Math №3 Стереометрия: видны чертежи кубов/пирамид.** |
| 13 | **Math №14: видны чертежи.** |
| 14 | **Math №18: видны графики функций.** |
| 15 | Все картинки имеют расширения .svg/.png/.jpg (нет файлов без расширения). |

---

## Зависимости

Никаких новых не нужно.

---

## Время

3-6 часов. Правка #9 (диагностика чертежей) — может быть самой долгой, поэтому начни с неё. Если найдёшь корневую причину в первые 30 минут — фикс может быть простым. Если запутаешься — расследуй methodically, не вслепую.

---

## После итерации

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского «работает» после Samsung.
- В отчёте:
  - Что было корневой причиной отсутствия чертежей (Сценарий 1/2/3/4/5 или другое).
  - Точные изменения по каждой правке.
  - Sample logcat для одной задачи Math №3 (диагностика #9).
  - Чек-лист 15 пунктов выше.
  - Путь к APK, размер.

---

## Если что-то не получается

- Если иллюстрации **физически нет в parser/assets/** — это означает что Phase 1 их не собрала, нужно перезапустить парсер. Доложи.
- Если иллюстрации есть на диске, но не в APK — Gradle assets packaging проблема. Доложи.
- Если в APK, но не рендерятся — проблема в HtmlRenderer или SvgLoader. Доложи с логами.
- Если слоговой режим не подходит для каких-то слов из словника — пришли список странных делений.
