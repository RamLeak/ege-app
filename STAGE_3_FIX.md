# STAGE_3_FIX.md — Исправление багов HtmlRenderer в Stage 3

> Документ создан после первой итерации Stage 3. Базовая функциональность работает (навигация, проверка ответа, NULL answer, UI), но `HtmlRenderer` ломается на основных типах контента.
>
> Это **критическая итерация** перед закрытием Stage 3.

---

## Что работает (НЕ ломать)

Эти моменты подтверждены скриншотами и работают правильно:

- Базовая навигация Catalog → Subject → Type → Subtype → Problem.
- Шапка с номером задачи, названием подвида, позицией X/Y.
- Стрелка назад (←), Bottom nav (3 таба) с подсветкой «Решать».
- TextField «Твой ответ», кнопка «Проверить» (синяя, iOS Blue).
- Кнопки «Правило» и «ИИ» (disabled, серые).
- Зелёная плашка «✅ Правильно» с spring-анимацией.
- Случай NULL answer (кнопка «Показать решение» вместо «Проверить»).
- Раскрытие/сворачивание «Авторское решение».
- Кнопки «← Предыдущая» / «Далее →».
- Тёмная тема, Apple-style padding/corners.
- Логика проверки ответа (нормализация, форматы number/string/alternatives/multipart/NULL).

**Не трогать ничего из этого.**

---

## Баги (точная диагностика по скриншотам)

### Баг #1 — HTML-комментарии вываливаются в текст

**Что видно:**
- `Решение<!--rule_info-->.`
- `Ответ: 13.<!--np--><!--np-->`
- `Ответ: 24.<!--np-->`

**Причина:** sdamgia использует `<!--np-->` (no-paragraph, разделитель блоков) и `<!--rule_info-->` (placeholder для встраивания справки о правиле). Браузеры скрывают HTML-комментарии. Наш парсер их пропускает в выход.

**Затронуто:** все 4 типа русского, math №6+ (в решениях), потенциально все задачи где есть `<!--np-->` или другие комментарии sdamgia.

---

### Баг #2 — Inline-формулы `<img class="tex">` не показываются

**Что видно (задача 26662, простейшие уравнения):**
- В БД: «Найдите корень уравнения: <img class="tex" src="...">».
- На экране: «Найдите корень уравнения:» **без формулы**.

**Что видно (задача 27238, планиметрия):**
- В БД: «В треугольнике ABC угол C равен 90°, <img class="tex"> AC=4.8</img>, <img class="tex">sin A=7/25</img>. Найдите <img class="tex">AB</img>.».
- На экране: «В треугольнике ABC угол C равен 90°, Найдите» — **проглочены AC=4.8, sin A=7/25, AB**.

**Причина (гипотезы):**
1. Coil не может загрузить SVG через `file:///android_asset/...`.
2. `InlineTextContent` placeholder не находит соответствующую `AnnotatedString` позицию.
3. Размер placeholder (24sp × 40sp) слишком мал, формула обрезается.
4. Парсинг `<img>` ломает структуру текста — текст после `<img>` теряется.

---

### Баг #3 — Block-картинки (большие иллюстрации) не показываются

**Что видно (задача 27238):**
- Должен быть чертёж треугольника ABC справа/снизу от условия.
- На экране — большая пустая область там где зарезервировано `maxHeight 250dp`.
- Файл `assets/27238/img_1.svg` реально существует (после фикса .png→.svg в Phase 1).

**Причина (гипотезы):**
1. Coil не находит файл по пути `file:///android_asset/27238/img_1.svg`.
2. Папка `assets/27238/` не копируется в APK при сборке (Gradle пропускает поддиректории).
3. Парсер не классифицирует `<img>` без `class="tex"` как block-картинку.

---

### Баг #4 — Текст вокруг inline-формул обрезается

Связан с Багом #2. Когда HtmlRenderer встречает `<img>`, он не только не показывает картинку, но и теряет окружающий текст.

Пример: в задаче 27238 после «угол C равен 90°,» теряется всё до «Найдите». Это значит ручной парсер обрывает обработку параграфа на первом `<img>`.

---

### Баг #5 (косметика) — `sdamgia_id` в UI

На всех скриншотах внизу карточек видно отладочную надпись `sdamgia_id: 26662` серым курсивом. Должна быть удалена — это артефакт от ранней разработки, не должен присутствовать в production UI.

---

## План исправления

### Шаг 1 — Выпилить HTML-комментарии (быстрый win)

Перед `splitBlocks(html)` применить:

```kotlin
val cleanedHtml = html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
```

Это покрывает `<!--np-->`, `<!--rule_info-->`, и любые другие комментарии sdamgia.

**Важно:** применять ТОЛЬКО к verbatim HTML из БД, не к внутренним обработчикам.

**Затронутые поля:** `problem.statement_html`, `solution.content_html`. Применять обе.

---

### Шаг 2 — Удалить sdamgia_id из ProblemDetailScreen

В `ProblemDetailScreen.kt` найти и удалить блок:

```kotlin
Text(
    text = "sdamgia_id: ${problem.sdamgia_id}",
    ...
)
```

или эквивалент. Просто удалить — никакого «спрятать в debug режиме», убрать полностью.

---

### Шаг 3 — Перейти на надёжный HTML-парсер (Jsoup)

Ручной regex-парсер сейчас глотает текст вокруг `<img>`. Это нужно лечить системно.

**Подключить Jsoup:**
```
implementation("org.jsoup:jsoup:1.18.1")
```

**Новая логика `parseBlock`:**

```kotlin
val doc = Jsoup.parse(blockHtml)
val body = doc.body()
val segments = mutableListOf<Segment>()

for (node in body.childNodes()) {
    when (node) {
        is TextNode -> segments += TextSegment(node.text())
        is Element -> when (node.tagName().lowercase()) {
            "img" -> {
                val src = node.attr("src")
                val isInline = node.hasClass("tex") || isSmallSvg(src)
                if (isInline) segments += InlineImgSegment(src)
                else segments += BlockImgSegment(src)
            }
            "i", "em" -> segments += TextSegment(node.text(), italic = true)
            "b", "strong" -> segments += TextSegment(node.text(), bold = true)
            "sub" -> segments += TextSegment(node.text(), baseline = Subscript)
            "sup" -> segments += TextSegment(node.text(), baseline = Superscript)
            "br" -> segments += LineBreakSegment
            else -> // recursive into children if has text
        }
    }
}
```

Jsoup надёжно обрабатывает «текст `<img>` текст `<img>` текст» — мы итерируемся по childNodes в правильном порядке и не теряем ничего.

---

### Шаг 4 — Отладить рендер SVG через Coil

**Гипотеза А:** Coil не находит файл.

Добавить debug-логирование в `HtmlRenderer`:

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data("file:///android_asset/$assetPath")
        .listener(
            onStart = { Log.d("HtmlRenderer", "Loading: $assetPath") },
            onError = { _, error -> Log.e("HtmlRenderer", "Error loading $assetPath", error.throwable) },
            onSuccess = { _, _ -> Log.d("HtmlRenderer", "Loaded: $assetPath") }
        )
        .build(),
    ...
)
```

Запустить на эмуляторе, посмотреть logcat. Возможные исходы:

- **Loading + Error «File not found»** → проблема в пути или копировании assets.
- **Loading + Error «Decoder not found»** → SvgDecoder.Factory() не подключён или не работает.
- **Никакого Loading** → парсер не доходит до AsyncImage, проблема в HtmlRenderer.

**Альтернативный путь (если Coil-SVG капризничает):**

Использовать AndroidSVG напрямую:

```
implementation("com.caverock:androidsvg-aar:1.4")
```

```kotlin
val context = LocalContext.current
val bitmap = remember(assetPath) {
    context.assets.open(assetPath).use { stream ->
        val svg = SVG.getFromInputStream(stream)
        val bmp = Bitmap.createBitmap(svg.documentWidth.toInt(), svg.documentHeight.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).let { svg.renderToCanvas(it) }
        bmp
    }
}
Image(bitmap = bitmap.asImageBitmap(), ...)
```

Это **более прямой** способ — читаем asset, парсим SVG, рендерим в Bitmap, показываем как обычное Image. Меньше слоёв = меньше точек отказа.

---

### Шаг 5 — Проверить копирование assets/{sdamgia_id}/ в APK

Sanity-check для Бага #3.

После сборки APK выполнить:

```bash
unzip -l android/app/build/outputs/apk/debug/app-debug.apk | grep "assets/27238"
```

Если в выводе есть `assets/27238/img_1.svg` — Gradle копирует правильно, проблема в коде. Если нет — поправить `android` блок в `build.gradle.kts`:

```kotlin
android {
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}
```

И/или `aaptOptions { noCompress("svg") }` если SVG как-то странно обрабатываются.

---

### Шаг 6 — Размер inline-формул

Поднять placeholder с 24sp × 40sp до **28sp × 60sp** для лучшей читаемости. Для длинных формул вроде `sin A = 7/25` или `[-π; π/2]` — 40sp ширины мало.

Альтернатива: динамический placeholder по реальному размеру SVG viewBox. Но это сложнее, сделать в Stage 5 (полировка).

---

## Smoke-тесты после исправления

После сборки APK проверить (на эмуляторе или дать пользователю):

| # | Задача | Что должно быть |
|---|---|---|
| 1 | 26662 (Простейшие уравнения) | Условие с формулой 4/7 x = 7 3/7. Решение БЕЗ `<!--np-->`. |
| 2 | 27238 (Планиметрия) | Условие с AC=4.8, sin A=7/25, AB. Чертёж треугольника full-width. |
| 3 | Любая №13 math | Условие с уравнением cos/sin как картинки. |
| 4 | Любая №4 русского | Текст БЕЗ `<!--np-->`. Слова с ударениями нормальные. |
| 5 | Любая №19 math | NULL answer → кнопка «Показать решение». |
| 6 | Любая задача | Внизу карточки НЕТ `sdamgia_id: ...`. |

---

## Что можно подключать

В этой итерации **разрешено**:

- **Jsoup** (`org.jsoup:jsoup:1.18.1`) — надёжный HTML-парсер.
- **AndroidSVG** (`com.caverock:androidsvg-aar:1.4`) — если Coil-SVG капризничает.
- **android.util.Log** — для debug-логирования при разработке (НЕ оставлять в production).

---

## Время

**НЕ ограничивать 30-60 минутами.** Это серьёзная итерация. Делать столько сколько надо чтобы починить **правильно**:

- Логировать.
- Экспериментировать.
- Проверять каждое предположение.

Лучше потратить 3 часа сейчас и закрыть Stage 3 чисто, чем за 30 минут и потом возвращаться 5 раз.

---

## После исправления

- `gradlew assembleDebug`.
- **НЕ коммитить пока** — жду от пользователя «работает» после повторной проверки на Samsung.
- В отчёте указать:
  - Какие именно изменения сделал в HtmlRenderer (точно, с описанием алгоритма).
  - Подключил ли Jsoup и/или AndroidSVG (с версиями).
  - Куда добавил выпиливание HTML-комментариев.
  - Что обнаружил при debug загрузки SVG (если логировал).
  - Удалил ли строку sdamgia_id (подтверждение).
  - Путь к новому APK + размер.

---

## Если что-то идёт не так

Останавливайся для отчёта если:

- Jsoup не справляется с какой-то HTML-структурой sdamgia (нужно увидеть конкретный пример).
- AndroidSVG не загружает какой-то конкретный SVG-файл (нужен debug этого файла).
- Размер APK >250 МБ.
- Любая неожиданная проблема которая требует архитектурного решения.
