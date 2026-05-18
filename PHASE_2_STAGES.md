# PHASE_2_STAGES.md — Stage Prompts for Phase 2

> Этот файл содержит детальные промпты для каждого Stage Фазы 2 (Android MVP).
> Промпты в Claude Code отправляются в формате: «Phase 2 Stage X. Подробности в PHASE_2_STAGES.md секция X. Поехали.»
>
> Claude Code читает соответствующую секцию из этого файла и выполняет задачу.

---

## Общие принципы Фазы 2

Применяются ко всем Stage'ам:

1. **Перед UI-кодом обязательно** читай соответствующую секцию `DESIGN_SPEC.md`.
2. **Соблюдай Convention #12** из CLAUDE.md (table-level PRIMARY KEY) при добавлении новых @Entity.
3. **Auto Mode** — действуй автономно. Останавливайся для отчёта только в чек-пойнтах.
4. **После Stage** — НЕ коммитить пока, жду подтверждения «работает» от пользователя после установки APK на Samsung Galaxy.
5. **Только тёмная тема** в Stage 1-4 (светлая — Stage 5).
6. **Деплой через Telegram**, не USB. APK кладётся на `C:\Projects\ege-app\android\app\build\outputs\apk\debug\app-debug.apk`.
7. **Smoke-тесты на эмуляторе** перед сборкой APK (на твоей стороне). Финальная проверка — на физическом Samsung Galaxy (на стороне пользователя).
8. **Размер APK** не должен превышать 250 МБ. Если растёт больше — стоп и доложи.
9. **Spring-анимации** (DESIGN_SPEC §4) — обязательны там где указано.
10. **Apple-style** — padding 16-24dp, corners 16-20dp на карточках, 14dp на кнопках.

---

## Stage 3 — Полноценный экран задачи

### Цель

Превратить заглушку `ProblemDetailScreen` в работающий экран задачи с рендером формул, проверкой ответа и раскрывающимся решением. Это самый большой Stage Фазы 2.

### Что читать перед началом

- `DESIGN_SPEC.md` секции **6.6, 6.7, 6.8** (экран задачи + поведение проверки).
- `DESIGN_SPEC.md` секция **7** (взаимодействия).
- `DESIGN_SPEC.md` секция **4** (анимации).
- `CLAUDE.md` Convention #12 (table-level PRIMARY KEY).

### Задачи

#### 1. Инфраструктура БД

- Добавь `@Entity` для таблицы `solutions` (id, problem_id FK, content_html, answer_short). **Используй table-level PRIMARY KEY** (Convention #12).
- Расширь `CatalogDao` новыми suspend-запросами:
  - `getProblemWithSolution(id)`: возвращает Problem + Solution
  - `getNextProblemInSubtype(problemId, subtypeId)`: следующая задача
  - `getPrevProblemInSubtype(problemId, subtypeId)`: предыдущая
  - `getNextProblemInType(problemId, typeId)`: для тренажёра без подвида
  - `getPrevProblemInType(problemId, typeId)`
- **НЕ добавляй** @Entity для rules / problem_rules — это Stage 4.

#### 2. Рендер условия и решения (HtmlRenderer)

Создай переиспользуемый Compose-компонент `HtmlRenderer(html: String, modifier: Modifier)`.

- Парсинг HTML: текст + inline `<img>` теги.
- SVG берутся из `app/src/main/assets/` через AssetManager:
  - Формулы: `assets/_formulas/{hh}/{hash}.svg`
  - Иллюстрации: `assets/{sdamgia_id}/img_N.svg`
- Рендер через Coil 2.7 с SVG decoder:
  - `ImageLoader.Builder.components { add(SvgDecoder.Factory()) }`
  - `AsyncImage` с `ImageRequest` на путь `"file:///android_asset/..."`
- Текст с inline-картинками: используй `AnnotatedString` + `InlineTextContent` с placeholder'ами.
- **Размер inline-формулы:** высота строки текста (~24sp).
- **Размер чертежа (large img):** full-width, maxHeight 250dp.
- **Алгоритм inline vs block:** маленькие img (height < 40px по svg viewBox) inline, большие — отдельным блоком на новой строке.

#### 3. UI экрана задачи (DESIGN_SPEC §6.6, §6.7, §6.8)

**Шапка (ScreenTopBar):**
```
← (back) | №[type_number] · [subtype_title или type_title] · [position]/[total] | ⭐ (серая) | ⋯ (no-op)
```

**Тело (LazyColumn):**
- Условие задачи через `HtmlRenderer(problem.statement_html)`.
- Поле ввода ответа: `OutlinedTextField`, label "Твой ответ", `singleLine = true`.
- Кнопка `[Проверить]` — `Button`, full-width, iOS Blue (#0A84FF), corners 14dp, height 50dp.
- Ряд кнопок:
  - `[📋 Правило]` — disabled, серая (Stage 4).
  - `[🤖 ИИ]` — disabled (Safety Rule #2).
- Плашка результата (после нажатия «Проверить»):
  - Правильно: зелёный bg `rgba(48,209,88,0.15)`, text "✅ Правильно", spring-анимация появления.
  - Неверно: красный bg `rgba(255,69,58,0.15)`, text "❌ Неверно (правильный: ...)".
- `ExpandableSolution` компонент:
  - Состояние `expanded: Boolean`.
  - Заголовок "Авторское решение ▼" — тап раскрывает.
  - Контент через `HtmlRenderer(solution.content_html)`.
  - Spring-анимация раскрытия (dampingRatio = 0.7, stiffness = Medium).
- Метаданные (если есть в БД):
  - "Источник: [problem.source]" (если не null).
  - "Сложность: ●●○" (визуализация по problem.difficulty, если не null).
  - 14sp, textTertiary цвет.

**Низ (sticky bottom row):**
- `[← Предыдущая]` `[Далее →]` — full-width row, secondary стиль, 50dp height каждая.
- Если первая задача — «Предыдущая» disabled.
- Если последняя — «Далее» disabled.

#### 4. Поведение проверки ответа (DESIGN_SPEC §6.7, §6.8)

**Нормализация answer перед сравнением:**
- Trim пробелы.
- Lowercase.
- Замени запятую на точку (для чисел: "1,5" → "1.5").
- Убери множественные пробелы.

**Логика:**

| Состояние | Действия |
|---|---|
| Правильно (введённый == normalize(answer_short)) | Зелёная плашка. Решение раскрываемо ТАПОМ (не авто). Кнопка ИИ становится активной (visually). |
| Неверно (введённый != answer_short && не пустой) | Красная плашка. Решение раскрывается **АВТОМАТИЧЕСКИ**. Кнопка ИИ активна. Журнал ошибок — Phase 3 (пока не логируется). |
| Пустой ответ при «Проверить» | Кнопка «Проверить» меняется на «Показать решение». При тапе — раскрывается решение, ИИ остаётся disabled. |

#### 5. Навигация между задачами

- При тапе `[Далее →]`: подгрузи следующую задачу через DAO, обнови state экрана (problemId, statement, solution, position, total).
- **НЕ делай** новый `popBackStack`/`navigate` — обновляй ViewModel-state на текущем экране.
- Состояние ответа сбрасывается (поле очищается, плашка скрывается, решение свёрнуто).
- Если `subtypeId` передан в route → используй `getNextProblemInSubtype`.
- Если `subtypeId == null` (открыли «🎯 Все задачи типа») → `getNextProblemInType`.
- `position`/`total` в шапке отражает позицию в текущей выборке.

#### 6. UI принципы (DESIGN_SPEC §1-4)

- Apple-style padding 16-24dp везде.
- Corners 16-20dp на карточках, 14dp на кнопках.
- Только тёмная тема.
- Spring анимации для:
  - Раскрытия решения.
  - Появления плашки результата.
  - Перехода между задачами (fade + slide).
- Шрифты:
  - Условие: 17sp Regular.
  - Поле ответа: 17sp.
  - Метаданные: 14sp Regular.
  - Заголовок шапки: 17sp Semibold.
  - Решение: 16sp.
  - Плашка результата: 17sp Semibold.

#### 7. ViewModel

Создай `ProblemDetailViewModel(problemId, subtypeId?, typeId)`.

- **State:** `ProblemUiState (currentProblem, solution, position, total, userAnswer, checkResult, isSolutionExpanded)`.
- **Actions:** `checkAnswer`, `expandSolution`, `goNext`, `goPrev`.

#### 8. Smoke-тесты (на эмуляторе перед сборкой APK)

- Открой задачу №6 math (sdamgia_id 26662, простейшее уравнение).
  - Введи правильный ответ "13", тап «Проверить» → зелёная плашка.
  - Раскрой решение → формулы рендерятся.
  - Тап «Далее» → загружается следующая задача того же подвида.
- Открой задачу №1 math (sdamgia_id 27238, планиметрия с чертежом).
  - Чертёж рендерится full-width.
- Открой любую задачу №4 русского (ударения).
  - Текст без формул читается.

#### 9. APK и Git

- `gradlew assembleDebug`.
- **НЕ коммитить** пока — жду «работает» от пользователя.

### Что не делать в Stage 3

- ❌ Реальное содержимое кнопки «Правило» (Stage 4) — только disabled.
- ❌ Логика «Избранное» (Stage 4) — звезда серая.
- ❌ Свайпы между задачами (Stage 4) — только кнопки.
- ❌ Запись в журнал ошибок (Phase 3) — только визуальная плашка.
- ❌ Реальный AI-чат (Phase 4).
- ❌ Светлая тема (Stage 5).
- ❌ Тренажёр ударений №4 (Phase 3 — отдельный экран).

### Останавливайся для отчёта если

- SVG не рендерятся через Coil (нужно другое решение, например AndroidSvg).
- @Entity для solutions падает с Room schema mismatch (проверь Convention #12).
- HTML-парсинг даёт неожиданные результаты (например теги `<math>` или `<table>`).
- В корпусе обнаружились задачи без answer_short.
- Размер APK >250 MB.
- Существенное отклонение от DESIGN_SPEC.md.

### Финальный отчёт должен содержать

- Структуру новых файлов в `android/app/src/main/java/com/daniel/ege100/`.
- Версии новых зависимостей (если добавлял).
- Как реализован `HtmlRenderer` (краткое описание).
- Список 5 проверенных задач: №6 math, №1 math, №13 math, №4 rus, №27 rus.
- Путь к APK, размер.
- Известные ограничения (если есть).

---

## Stage 4 — Правило, Избранное, Свайпы

*Будет добавлено после завершения Stage 3.*

---

## Stage 5 — Полировка

*Будет добавлено после завершения Stage 4.*

---

## Last update

Создан после Phase 2 Stage 2 ✅, перед стартом Stage 3.
