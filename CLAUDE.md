# CLAUDE.md — Operational Memory for Claude Code

> Этот файл читается Claude Code автоматически в начале каждой сессии в проекте `ege-app`. Он содержит контекст, конвенции и правила, обязательные для соблюдения.

---

## Контекст пользователя (read-once, держи в голове)

Пользователь — 18-летний студент в Германии, готовится к ЕГЭ-2027 (русский язык + профильная математика). Цель: высокий балл для поступления на специалиста по информационной безопасности. Дополнительно — спор с родителями, что сдаст ЕГЭ.

- Python: базовый уровень — понимает код, делает мелкие правки, **не** пишет с нуля сложные системы.
- Kotlin / Compose: с нуля. Объясняй технические решения коротко и по делу, но не пропускай совсем.
- Время разработки: длинный горизонт (~13 месяцев до ЕГЭ). Это создаёт риск размытия мотивации — см. Safety Rules.

Когда сомневаешься — **спрашивай пользователя, не угадывай**. Особенно перед: bulk-сетевыми запросами, удалением файлов, сменой архитектуры.

---

## Проект: что строим

Личное Android-приложение для подготовки к ЕГЭ. **Два** предмета: профильная математика, русский. **Не** Google Play, **не** общедоступный продукт, **не** мульти-устройство.

Архитектура — два независимых подпроекта:

```
C:\Projects\ege-app\
├── CLAUDE.md                        ← этот файл
├── PROJECT_OVERVIEW.md              ← для будущих чатов в claude.ai (не трогать)
├── PHASE_N_PROMPT.md                ← инструкции текущей фазы
│
├── parser/                          (Python 3.13)
│   ├── fipi-recon-archive/          ← запасной парашют (НЕ удалять)
│   ├── scrapers/                    ← создашь в Фазе 1
│   ├── pipeline/                    ← fetch/parse/normalize/store
│   ├── cache/raw/                   ← idempotent кеш сырого HTML
│   ├── assets/                      ← скачанные картинки
│   ├── state.json                   ← чекпоинт парсера (last_seen, errors)
│   ├── selectors.yaml               ← CSS-селекторы вынесены, чтобы быстро чинить
│   ├── math.jsonl                   ← нормализованные задачи математики
│   ├── russian.jsonl                ← нормализованные задачи русского
│   ├── kim-fipi/                    ← открытые варианты КИМ ФИПИ (Stage 4)
│   ├── build_db.py                  ← JSONL → corpus.db
│   ├── corpus.db                    ← финальная SQLite БД
│   └── requirements.txt
│
└── android/                         (создаётся в Фазе 2)
    └── (стандартный Android Studio проект)
```

---

## Tech stack — обязательный

| Слой | Технология |
|---|---|
| Парсер | Python 3.13 + httpx + selectolax + PyYAML |
| Запасной парсер | Playwright (только если sdamgia включит Cloudflare) |
| Промежуточный формат | JSONL + папка `assets/` |
| БД | SQLite (на Android — через Room) |
| Mobile UI | Kotlin + Jetpack Compose |
| Рендер формул | **AsyncImage / Coil** — sdamgia отдаёт формулы как готовые SVG-картинки с контент-адресуемым URL (`/formula/svg/{2hex}/{32hex}.svg`) и alt-текстом на русском. MathJax и WebView **не нужны** — это пересмотрено по итогам Stage 0 разведки (см. секцию «Tech stack — заметки после Stage 0»). |
| HTTP в Android | Ktor Client |
| AI | Claude API напрямую, модели: `claude-haiku-4-5-20251001` по умолчанию, `claude-sonnet-4-7` по запросу |

---

## Схема БД — трёхуровневая иерархия задач

**Критично:** структура задач должна поддерживать радар по подвидам (Safety Rule #1).

```sql
CREATE TABLE subjects (
    id INTEGER PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,           -- 'mathb', 'rus'
    title TEXT NOT NULL,
    sdamgia_subdomain TEXT NOT NULL
);

CREATE TABLE problem_types (             -- ЕГЭ-номера: №1, №2, ..., №19, плюс «Дополнительные Д1..Д19»
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    number INTEGER NOT NULL,             -- 1..N для основных, 1..19 для supplementary математики
    title TEXT NOT NULL,                 -- «Планиметрия», «Чтение графиков и диаграмм», …
    description TEXT,
    is_supplementary INTEGER NOT NULL DEFAULT 0,  -- 0 = ЕГЭ-номер, 1 = «Задания Д1..Д19»
    UNIQUE(subject_id, number, title, is_supplementary)  -- title в ключе: у математики 2 разных типа с number=10 («C2. Сложная стереометрия» и «Графики функций»)
);

CREATE TABLE problem_subtypes (          -- темы КЭС: тригонометрия, показательные, ...
    id INTEGER PRIMARY KEY,
    type_id INTEGER NOT NULL REFERENCES problem_types(id),
    kes_code TEXT,                       -- '1.5' и т.д., если sdamgia/ФИПИ дают
    title TEXT NOT NULL,
    UNIQUE(type_id, title)
);

CREATE TABLE problems (
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),  -- денормализация для быстрого фильтра + UNIQUE-ключ
    sdamgia_id TEXT NOT NULL,
    prototype_id TEXT,                   -- если sdamgia группирует по прототипам
    type_id INTEGER NOT NULL REFERENCES problem_types(id),
    subtype_id INTEGER REFERENCES problem_subtypes(id),
    statement_html TEXT NOT NULL,
    answer TEXT,
    answer_format TEXT,                  -- 'number' | 'string' | 'multipart' | 'alternatives' | NULL
    images_json TEXT,                    -- JSON-массив относительных путей
    source TEXT,                         -- «ОБЗ ФИПИ», «РЕШУ ЕГЭ» — для русского из блока align-left. Для математики NULL (тематика — через subtype).
    difficulty TEXT,                     -- «обычная», «повышенная» — только для русского. Для математики NULL.
    scraped_at TEXT NOT NULL,
    raw_hash TEXT NOT NULL,
    UNIQUE(subject_id, sdamgia_id)       -- sdamgia использует общий ID-пул для разных предметов: на Stage 3 обнаружено 9 коллизий с math (низкие ID 902-915 и др., историческое наследие). Поэтому уникальность по паре.
);

CREATE TABLE solutions (
    problem_id INTEGER PRIMARY KEY REFERENCES problems(id),
    solution_html TEXT NOT NULL,
    explanation_text TEXT                -- plain-text для скармливания в Claude
);

CREATE TABLE user_progress (
    problem_id INTEGER PRIMARY KEY REFERENCES problems(id),
    status TEXT NOT NULL,                -- 'not_started' | 'attempted' | 'correct' | 'wrong' | 'reviewed'
    user_answer TEXT,
    attempts INTEGER DEFAULT 0,
    last_attempt_at TEXT,
    flagged INTEGER DEFAULT 0,
    used_ai INTEGER DEFAULT 0            -- если жал «Спросить ИИ» хотя бы раз
);

CREATE TABLE ai_conversations (
    id INTEGER PRIMARY KEY,
    problem_id INTEGER NOT NULL REFERENCES problems(id),
    user_question TEXT NOT NULL,
    ai_response TEXT NOT NULL,
    prompt_hash TEXT NOT NULL UNIQUE,    -- sha256(problem_id + normalize(question))
    model TEXT NOT NULL,
    tokens_in INTEGER,
    tokens_out INTEGER,
    cost_usd REAL,
    created_at TEXT NOT NULL
);

CREATE TABLE error_atoms (               -- Фаза 5: SRS по ловушкам
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,                 -- 'Забыл ОДЗ', 'Перепутал sin/cos', ...
    description TEXT,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    related_subtype_ids TEXT,            -- JSON-массив
    next_review_at TEXT,
    review_interval_days INTEGER DEFAULT 1,
    times_failed INTEGER DEFAULT 0
);

CREATE TABLE mock_exams (
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    source TEXT NOT NULL,                -- 'generated' | 'fipi-official'
    started_at TEXT,
    completed_at TEXT,
    raw_score INTEGER,                   -- первичный балл
    scaled_score INTEGER,                -- вторичный балл
    problem_ids_json TEXT NOT NULL,      -- список задач в варианте
    answers_json TEXT                    -- ответы пользователя
);

CREATE TABLE daily_streak (              -- счётчик дней подряд
    date TEXT PRIMARY KEY,               -- YYYY-MM-DD
    problems_solved INTEGER NOT NULL,
    streak_value INTEGER NOT NULL
);

-- Справочные правила (для русского — извлекаются из sdamgia,
-- для математики — пишутся вручную, см. примечание ниже).
CREATE TABLE rules (
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    title TEXT NOT NULL,                 -- «Союзы противопоставления», «Согласные в корне»
    content_html TEXT NOT NULL,          -- полный HTML блока «Правило» с тегами
    source TEXT,                         -- 'sdamgia' | 'user-curated'
    rule_hash TEXT NOT NULL UNIQUE       -- sha256(нормализованный_text) — для дедупликации
);

-- Связь many-to-many: одно правило часто прикреплено к десяткам задач,
-- одна задача может ссылаться на 0..N правил (обычно 1).
CREATE TABLE problem_rules (
    problem_id INTEGER NOT NULL REFERENCES problems(id),
    rule_id INTEGER NOT NULL REFERENCES rules(id),
    PRIMARY KEY (problem_id, rule_id)
);
CREATE INDEX idx_problem_rules_rule ON problem_rules(rule_id);

CREATE VIRTUAL TABLE problems_fts USING fts5(
    statement_html, content=problems, content_rowid=id
);
```

**Правила: разделение русского и математики.**
- **Русский:** правила извлекаются автоматически из блока «Правило» (скрытый `align-left > div span[font-weight:bold]` + соседний `div.pbody`) на каждой задаче. Скрипт `parser/scrapers/extract_rules.py` пост-процессит уже скачанные HTML в `cache/raw/`, дедуплицирует по `rule_hash`, заполняет `rules` и `problem_rules`. `rules.source = 'sdamgia'`.
- **Математика:** правила НЕ парсятся из sdamgia. У пользователя есть свои правила (рукописные/из учебников), которые будут добавлены вручную в `rules` с `source = 'user-curated'` на Фазе 3+. У задач математики поле «правило» в Android-UI скрыто пока в `problem_rules` нет записей для этой задачи.

---

## Конвенции парсера (обязательные)

1. **Rate limiting:** 1 запрос в 1.5-2 секунды + `random.uniform(0.5, 1.0)` джиттер. На 429/503 — экспоненциальный бэк-офф (1s, 2s, 4s, 8s, ...).
2. **User-Agent:** реальный десктоп Chrome, не «python-httpx».
3. **Чекпоинты:** каждые 50 успешных задач сохранять `state.json` со списком обработанных ID и временем. На рестарте — продолжать с места.
4. **Idempotent cache:** каждый успешно скачанный HTML кешировать в `cache/raw/{sdamgia_id}.html`. Парсинг повторно — без повторного сетевого запроса.
5. **Селекторы в `selectors.yaml`:** все CSS-селекторы вынесены. Это нужно чтобы починить парсер при изменении разметки sdamgia за минуты, а не часы.
6. **Картинки:** скачивать в `assets/{sdamgia_id}/img_N.{ext}`, в JSONL хранить только относительные пути.
7. **MathML:** не пытаться конвертировать в LaTeX. Хранить как есть, рендерить MathJax-ом в WebView на Android.
8. **Smoke-тесты:** в `parser/tests/smoke_selectors.py` хранить N эталонных задач с ожидаемыми полями. Прогонять перед каждым обновлением парсера и после любого изменения `selectors.yaml`.
9. **Запуск парсера в background — только через Claude Code `run_in_background=true`.** Bash `&` оставляет процесс-зомби после выхода shell (на Windows процесс не получает SIGHUP сразу) — параллельный запуск через `&` И `run_in_background` уже один раз дал 12 дубликатов в `math.jsonl` (см. инцидент 2026-05-16, исправлено dedup-скриптом). При resume всегда: проверить `Get-CimInstance Win32_Process -Filter Name="python.exe"` на висящие процессы ДО запуска нового.
10. **Угадывание расширения файла по URL — НЕЛЬЗЯ для иллюстраций sdamgia.** Для URL вида `https://math-ege.sdamgia.ru/get_file?id=N` в пути нет расширения, а Content-Type sdamgia не возвращает в стабильном виде. Старый normalize.py использовал fallback `.png` → 3128 из 3510 файлов оказались SVG под именем `.png` (см. инцидент 2026-05-17). Лечение пост-фактум: `parser/scrapers/fix_illustration_extensions.py` (читает magic bytes, переименовывает + точечно UPDATE'ит corpus.db). На будущее (при добавлении новых иллюстраций): определять реальный тип по magic bytes после скачивания, а не по URL.
11. **Inline `display:none` в HTML sdamgia.** Решения, правила и некоторые блоки задач отдаются с `style="...;display:none"` — на самом sdamgia они раскрываются собственным JS, которого у нас нет. При рендере HTML в любом нашем UI (view_corpus.html, Android WebView) **обязательно** прогонять через sanitize-функцию, которая удаляет декларацию `display:none` из inline styles. Эталонная реализация — `strip_display_none()` в `parser/export_view.py`.

---

## Safety Rules — шесть страховок из premortem (обязательные)

Эти правила не обсуждаются, они зашиты в архитектуру. Если возникает соблазн нарушить — **сначала спроси пользователя**.

### Rule 1 — Радар по подвидам, не по типам
Главный экран показывает ~50 секторов КЭС (тригонометрия, показательные функции, стереометрия и т.д.), **не** 19 (по номерам задач).
- Минимум **15 решённых задач** в подвиде для окрашивания сектора.
- Меньше — сектор серый, подпись «недостаточно данных».
- Это закрывает ложную уверенность из-за смещённой выборки (пользователь обычно решает любимые подвиды).

**Примечание после Stage 0 разведки (2026-05-16):**
- Для **математики** радар работает по `problem_subtypes.id` (~150 подвидов КЭС, есть в каталоге sdamgia).
- Для **русского** sdamgia не даёт семантической подкатегоризации внутри номера задачи (подвиды у русского — это источники: «Задания ФИПИ», «Задания демоверсий», «Задания для подготовки», «Задания тренировочных работ»). Поэтому для русского радар — по `problem_types.number` (27 секторов). Это сознательное упрощение; вернёмся к семантической подкатегоризации через Claude API только если радар русского окажется бесполезным на практике (Фаза 3+).

### Rule 2 — AI-замок на первой попытке
Кнопка «Спросить ИИ» на экране задачи **disabled**, пока пользователь не ввёл хотя бы один ответ (даже неправильный).
- В UI: «Сначала попробуй — потом спрашивай».
- Это закрывает риск AI как эмоциональной замены решения.

### Rule 3 — Календарь пробников зашит в app
16 пробников за год, **каждые 3 недели**. Первый — через **4 недели** после планируемого конца Фазы 5.
- На главном экране — счётчик «дней до следующего пробника».
- Дата хранится в БД, не в коде (пользователь может скорректировать).
- Это закрывает «год — это вечность».

### Rule 4 — Стресс-тест корпуса
В конце Фазы 1 (Stage 4 парсера): скачать **10 открытых вариантов КИМ ФИПИ 2026** в папку `parser/kim-fipi/`. Для каждой задачи проверить покрытие в `corpus.db` через FTS-поиск или семантический подбор.
- Покрытие <80% → парсер недотягивает, добирать.
- Это закрывает риск неполной/устаревшей базы.

### Rule 5 — Правило «50 задач в неделю»
В каждую неделю, в которой был хоть один git-коммит, в app должно быть решено **минимум 50 задач**.
- Реализация: в начале сессии Claude Code, если есть коммиты за последние 7 дней — проверить количество решённых задач в `user_progress` за тот же период.
- Меньше 50 → **код заморожен**: в UI Claude Code предупреждает пользователя и предлагает закрыть редактор.
- Это закрывает «sublime app, пустая голова».

### Rule 6 — Контрольная точка через 8 недель
Пользователь поставил себе в Google Calendar дату через 8 недель от старта Фазы 1.
- В этот день: проверить количество решённых задач в `user_progress`.
- Если <300 → переключение в pure-usage mode на месяц (никакого кода).
- Это закрывает медленную деградацию мотивации.

---

## Working with the user

- **Спрашивай, когда не уверен.** Особенно по: бюджету API, новым архитектурным решениям, удалению файлов, изменению структуры БД.
- **Не угадывай дедлайны фаз.** Если фаза затягивается — доложи и предложи варианты (сжать / упростить / отложить).
- **Один раз в начале фазы — стресс-тест.** В Фазе 1 это Stage 0 (разведка sdamgia). В Фазе 2 — тестовый запуск пустого Compose-проекта на эмуляторе.
- **Коммиты:** используй `git`. Каждый завершённый stage внутри фазы — один коммит. После полной фазы — тэг `phase-N-done`.
- **Логирование:** все сетевые запросы парсера логировать в `parser/logs/scraper.log` с timestamp. Это даёт пользователю доказательство «парсер шёл медленно, не делал ddos».

---

## Out of scope (не делать без явного запроса)

- Google Play, signing keys, ProGuard для релиза.
- Синхронизация между устройствами, бэкап в облако.
- Социальные функции (чаты, лидерборды, шаринг прогресса).
- Информатика как третий предмет (исключено осознанно).
- Базовая математика, биология, обществознание.
- Теория и курсы (app — про практику, теория — в учебниках/YouTube).
- **Дополнительные задания математики Д1..Д14 НЕ парсятся в Phase 1.** Причина: пользователь хочет тренироваться только на актуальном формате ЕГЭ-2026, чтобы избежать ложной уверенности от устаревших формулировок. Если Stage 4 покажет покрытие КИМ ФИПИ-2026 ниже 80% — рассмотрим добор Д-задач как fallback, но решение принимает пользователь, не Claude Code автономно.
- **Устаревшие задания русского (Д А7, Д А9, Д А10, Д А11, Д А12, Д А20, Д А24, Д В1-В7, а также «B13/B14/L. Великовой/C27» и прочие после разделителя) НЕ парсятся в Phase 1.** Причина та же: тренируемся только на актуальном формате ЕГЭ-2026. Маркер на sdamgia: всё, что в каталоге `/prob_catalog` идёт после узла `<h3>Дополнительные задания для подготовки</h3>` (в живом UI sdamgia может также показывать заголовок «Задания, не входящие в ЕГЭ этого года»). Парсер `parse_catalog` автоматически детектирует этот разделитель и помечает все типы после него как `is_supplementary=True`. Та же логика обхода работает для обоих предметов.

---

## Roadmap status

| Фаза | Описание | Статус |
|---|---|---|
| 1 | Парсер sdamgia → corpus.db (2 предмета) | ✅ **DONE 2026-05-17** (10272 задач, 36 правил, 100% покрытие КИМ ФИПИ-2026) |
| 2 | Android MVP — навигация, экран задачи, проверка ответа | ⏳ Ready (по `DESIGN_SPEC.md`) |
| 3 | Главный экран — предиктор балла, радар, streak, журнал ошибок, прогресс-бары | ⏸ Waiting |
| 4 | AI-кнопка, генератор варианта, импорт КИМ ФИПИ, история пробников | ⏸ Waiting |
| 5 | SRS по ошибкам-формулировкам | ⏸ Waiting |

После завершения каждой фазы — обновить эту таблицу в `CLAUDE.md`. Помечать ✅ Done.

### Stages внутри Фазы 1

| Stage | Описание | Статус |
|---|---|---|
| 0 | Разведка sdamgia (10/10 запросов, selectors.yaml) | ✅ Done 2026-05-16 |
| 1 | Парсер математики профильной (только основные №1..№19, 4863 задачи) | ✅ Done 2026-05-17, тег `phase-1-stage-1-done` |
| 2 | Парсер русского №1..№27 + extract_rules.py (5409 задач, 36 правил) | ✅ Done 2026-05-17, тег `phase-1-stage-2-done` |
| 3 | `build_db.py` → corpus.db + FTS5 + view_corpus.html | ✅ Done 2026-05-17, тег `phase-1-stage-3-done` |
| 4 | Стресс-тест по КИМ ФИПИ-2026 (Safety Rule #4) | ✅ Done 2026-05-17, тег `phase-1-stage-4-done`, **100% покрытие** |

**Stage 4 итоговые метрики (2026-05-17, Safety Rule #4 закрыт):**
- Источник: `parser/kim-fipi/{math_profile,russian}_demo_2026.pdf` (демоверсии ФИПИ-2026, скачаны с doc.fipi.ru, gitignored).
- Извлечение: 19 задач math (со всеми «ИЛИ»-вариантами) + 27 задач rus = **46 задач КИМ**.
- Метод: 2-колоночный crop PDF → split по номеру → split по «ИЛИ» → FTS5-запрос (топ-8 длинных русских слов с OR) → если хотя бы один variant находит матч в нужном предмете, задача считается покрытой.
- Результат: **math 19/19 (100%), rus 27/27 (100%), overall 46/46 (100%)**. Порог 80% закрыт с большим запасом.
- Качество спот-проверено: №16 «Кредит» → точный аналог в corpus (sdamgia_id 660700), №5 «Паронимы» → точное совпадение текста (sdamgia_id 59670), №1 планиметрия → корректные матчи на тип «Четырёхугольник, вписанный в окружность».
- Claude API **не понадобился**: 100% покрытие через FTS5. Бюджет $0 из доступных $3.
- Артефакт `parser/coverage_report.md` (104 KB, gitignored) — детальный отчёт с FTS5-запросами и top-3 матчами на каждый variant.

**Supplementary Д1..Д19 математики (~2986 задач) НЕ добирались** — раз основные №1..№19 дали 100% покрытие демо-варианта, эти типы не нужны. Решение зафиксировано в Out of scope.

## Phase 1 итог

Парсер sdamgia → corpus.db (2 предмета) завершён.

- **corpus.db 192 MB** на диске: 10272 задачи (4863 math + 5409 rus), 36 уникальных правил, 5288 привязок задача↔правило, FTS5-индекс по statement_html.
- **0 ошибок** во всех 4 стейджах. **0 банов** от sdamgia. **0 4xx/5xx**.
- Wall time всего парсинга: math ~14ч (с простоями) + rus 1.6ч + build_db 6с + Stage 4 ~30с.
- Артефакты на диске (gitignored): math.jsonl 46 MB, russian.jsonl 138 MB, russian_rules.jsonl 1.1 MB, parser/assets/ 308 MB (формулы + иллюстрации), parser/cache/raw/ 1.2 GB.
- Inventory кода в репо: `parser/pipeline/`, `parser/scrapers/{sdamgia_recon,math_profile,russian,extract_rules,fix_illustration_extensions,stage4_coverage,probe_cat292}.py`, `parser/build_db.py`, `parser/export_view.py`, `parser/tests/test_smoke.py` (32/32 passed), `parser/selectors.yaml`.

Готов к Phase 2 (Android MVP по `DESIGN_SPEC.md`).

**Stage 3 итоговые метрики (2026-05-17):**
- `parser/corpus.db` 192.27 MB, собирается за 6 секунд.
- 2 subjects, 46 problem_types, 223 problem_subtypes, **10272 problems**, 10272 solutions, 36 rules, 5288 problem_rules, 10272 entries в FTS5.
- Integrity: `PRAGMA integrity_check = ok`, `PRAGMA foreign_key_check = 0 issues`, 0 orphan solutions, 0 orphan problem_rules.
- Распределение: mathb 4863 (100%) + rus 5409 (100%).
- 9 коллизий sdamgia_id между math и rus (исторические низкие ID 902-915) — решено через `UNIQUE(subject_id, sdamgia_id)` вместо `sdamgia_id UNIQUE`. Расхождение со старой схемой зафиксировано в CLAUDE.md.
- `view_corpus.html` 833 KB — выборка 10 math + 10 rus с формулами/иллюстрациями/правилами. Открывается двойным кликом из `parser/`.

**Stage 2 итоговые метрики (2026-05-17):**
- 5409 / 5409 задач русского (100% покрытие main №1..№27), 101 / 101 подвидов, 0 ошибок.
- 1123 HTTP-запроса: 1123 успешных, 0 timeout, 0 4xx, 0 5xx, 0 банов.
- Wall time: 97.3 минут (1.6ч). Темп ~55 задач/мин — заметно быстрее математики из-за минимума формул в русском.
- 36 уникальных правил, 5288 привязок задача↔правило. Дедупликация ~147 задач на правило в среднем (max 325 для правила про сложносочинённое предложение).
- 121 задача без правила — это №1-3, у которых на sdamgia не выводится исходный текст (известное ограничение, см. Stage 0 анализ).
- Артефакты: `russian.jsonl` 138 MB, `russian_rules.jsonl` 1.1 MB, `russian_problem_meta.jsonl` 590 KB.
- Smoke: 32/32 зелёные (включая стресс на №5 паронимы).

**Stage 1 итоговые метрики (2026-05-17):**
- 4863 / 4863 задач (100% покрытие main математики), 146 / 146 подвидов.
- 19 типов ЕГЭ покрыты полностью (от №1 «Планиметрия» до №19 «Числа и их свойства»).
- 52 697 уникальных SVG-формул (дедупликация по хешу), 3139 иллюстраций для 2637 задач.
- 0 ошибок в `state.errors`. Из 1049 HTTP-запросов: 1040 успешных + 9 transient timeout, все retried OK. Банов / 4xx / 5xx — 0.
- Wall time: ~14ч (включая простой между сессиями), чистый сетевой бюджет ~8ч.
- Артефакты на диске: `parser/math.jsonl` 46 MB, `parser/cache/raw/` 345 MB (1038 HTML), `parser/assets/` 308 MB (формулы + иллюстрации).

**Решение по supplementary (2026-05-16):** в Stage 1 берём только основные №1..№19 математики (4863 задачи, ~1 час прогона), supplementary Д1..Д19 (ещё 2986 задач) откладываем до Stage 4. Логика: если покрытие КИМ ФИПИ-2026 ≥80% без supplementary — они не нужны (это «старая нумерация», частично пересекается с основными). Если <80% — доберём именно те Д-типы, которые покрывают пробелы. Триггер: stop-сигнал «объём >5000» сработал на 7849 задач (4863+2986).

---

## Tech stack — заметки после Stage 0 разведки

Изменения в архитектурных решениях по итогам разведки sdamgia 2026-05-16.

### Формулы — НЕ MathML, а SVG-картинки
**Что обнаружено:** sdamgia рендерит формулы на сервере и отдаёт как `<img class="tex">` с URL вида `https://ege.sdamgia.ru/formula/svg/{2hex}/{32hex}.svg` (контент-адресуемое хранилище, картинки дедуплицированы по хешу). Атрибут `alt` содержит **транскрипцию формулы на русском** («дробь: числитель: 4, знаменатель: 7 …»).

**Последствия:**
- На Android — отображение через AsyncImage / Coil. MathJax 4 в WebView **не нужен**. Это сильно упрощает Фазу 2 (меньше JS-инфраструктуры, нет проблем с производительностью WebView на старых телефонах).
- Парсер скачивает SVG в `assets/{sdamgia_id}/formulas/{hash}.svg`, в JSONL хранит относительные пути.
- В FTS5-индекс (Stage 3) попадает alt-текст формул вместе с обычным текстом условия — это бесплатный bonus к качеству поиска.
- Для Claude API (Stage 4 покрытие КИМ ФИПИ): alt-текст пригоден напрямую, ничего конвертировать не надо.

### React-SPA на корне поддоменов — НЕ наш интерфейс
Корни `https://math-ege.sdamgia.ru/` и `https://rus-ege.sdamgia.ru/` отдают пустой React-SPA shell (8 КБ, `<div class="Root">` + бандлы `static/js/main.*.chunk.js`).

**Парсим не SPA, а старый PHP-движок** на тех же поддоменах:
- `/prob_catalog` — дерево типов/подвидов (статичный HTML, 228 КБ math / 393 КБ rus).
- `/test?filter=all&category_id=N&page=K` — список задач подвида, **с полными условиями + решениями + ответами + КЭС-кодами**. Пагинация через `data-total`/`data-page`.
- `/problem?id=N` — одна задача (для smoke-тестов; штатный обход через `/test`).

Все три endpoint-а возвращают серверный HTML без JS-рендера. Парсятся httpx + selectolax. Cloudflare Turnstile в инфраструктуре есть, но при разведке (10 запросов) не активировался.

### Стратегия обхода Stage 1 — через /test, не /problem
В каждой странице `/test` отдаются полные задачи, не превью. Это значит:
- ~150 подвидов × среднее 2-4 страницы на подвид ≈ **~400-600 запросов** для всей математики, а не 2-4 тыс. как было бы при пообходе через `/problem?id=N`.
- При rate-limit 1.5-2с + jitter — это ~15-25 минут чистого обхода, не часы.
- Резервный `/problem?id=N` используется только для задач, которые упоминаются в каталоге, но не попали ни в один `/test` (теоретическая ситуация — проверим на Stage 1).

---

## Fallback plans

Процедуры на типовые аварийные ситуации с парсером. Если в будущей сессии случилась одна из них — следовать пошагово, не импровизировать.

### A. Бан от sdamgia (HTTP 403/429 устойчиво)

Если при работе парсера sdamgia возвращает 403/429 и exponential backoff (1, 2, 4, 8с в `fetch.py`) не помог в течение **>30 минут**:

1. **Остановить парсер.** Background-task через `TaskStop`. State.json уже сохраняется атомарно каждые 50 задач и при `mark_subtype_done` — последний чекпоинт безопасен.
2. **Спросить пользователя:** «Заходит ли sdamgia.ru из обычного браузера на этой машине прямо сейчас?»
3. **Если нет — IP забанен.** Варианты, которые предлагаешь пользователю (но **не выбираешь сам**):
   - а) Подождать 12-24 часа — у sdamgia баны обычно временные.
   - б) Перезагрузить роутер для смены динамического IP (если провайдер выдаёт динамику).
   - в) Включить VPN.
   - г) Переключиться на план B — открытый банк ФИПИ (см. раздел B ниже).
4. **Решение принимает пользователь, не Claude Code автономно.** Это не баг для самостоятельного фикса, а инфраструктурный выбор с компромиссами (время vs стоимость VPN vs изменение архитектуры парсера).
5. **При возобновлении после паузы** — запускать `python parser/scrapers/math_profile.py` как обычно. Восстановление работает само:
   - `state.completed_subtypes` пропускает уже завершённые подвиды.
   - `parser/cache/raw/` хранит все ранее скачанные `/test`-страницы — повторных сетевых запросов на них не будет (`fetch.py` сначала проверяет кеш).
   - `JsonlWriter.has(sdamgia_id)` пропускает уже сохранённые задачи внутри незавершённого подвида.
   - `parser/assets/_formulas/{hash}.svg` тоже не перекачивается, если файл уже на диске.

### B. Переход на открытый банк ФИПИ

Когда применять:
- Sdamgia блокирует парсер на >2 дня подряд (после всех вариантов из секции A).
- На sdamgia сменилась разметка и чинить `selectors.yaml` дольше суток.

В `parser/fipi-recon-archive/` лежат файлы разведки ФИПИ из старой чатовой беседы:
- `fipi_recon.py`, `fipi_recon2.py` — рабочие скрипты, тащат HTML с `ege.fipi.ru/bank/`.
- `recon/`, `recon2/` — скачанные HTML-страницы для анализа структуры.

Что в ФИПИ:
- Структура URL: `https://ege.fipi.ru/bank/questions.php?proj={PROJ_ID}&page={N}&pagesize=100`.
- PROJ_ID для математики профильной: `AC437B34557F88EA4115D2F374B0A07B`.
- PROJ_ID для русского: `AF0ED3F2557F8FFC4C06F80B6803FD26`.
- Кодировка: windows-1251 → сохранять `r.content` как байты, не `r.text`.
- SSL: `verify=False` (у ФИПИ сломана цепочка сертификатов, для GET публичного HTML — безопасно).
- В HTML нет правильных ответов — проверка через POST на `solve.php` или однократное прогона через Claude API.

---

## Budget & API limits

- **Claude API hard limit:** $15/мес (выставляется в `console.anthropic.com`).
- **Дефолтная модель в app:** `claude-haiku-4-5-20251001` (дёшево).
- **Опциональная:** `claude-sonnet-4-7` по запросу пользователя (флаг «Подробно» в UI).
- **Кеш ответов:** обязателен. `prompt_hash = sha256(problem_id + normalize(question))`. Тот же вопрос второй раз — из БД, токены не тратятся.
- **Индикатор стоимости:** в шапке Settings — «Потрачено в этом месяце: $X.YZ / $15».

---

## Last update

2026-05-17 — **Phase 1 ✅ DONE.** Stage 4 закрыт: стресс-тест по КИМ ФИПИ-2026 показал 100% покрытие (46/46 задач: 19 math + 27 rus) через FTS5-поиск. Claude API не понадобился, бюджет $0. Тег `phase-1-stage-4-done`. Скрипт `parser/scrapers/stage4_coverage.py` — извлекает задачи из 2-колоночных PDF демоверсий ФИПИ, делит по «ИЛИ»-вариантам, матчит в corpus.db. Артефакт `parser/coverage_report.md` (104 KB, gitignored) с детализацией по каждой задаче. Roadmap: Фаза 1 ✅, Фаза 2 (Android MVP по `DESIGN_SPEC.md`) ready.

2026-05-17 — Косметика post-Stage 3 (после ручной проверки view_corpus.html). Два бага исправлены без re-scrape:
- 89% иллюстраций были SVG под именем `.png` (3128 из 3510 файлов) — добавлен `parser/scrapers/fix_illustration_extensions.py`, переименовал файлы и точечно обновил corpus.db (2637 + 688 + 2063 UPDATE на images_json/statement_html/solution_html). На уровне конвенций добавлено правило #10.
- HTML sdamgia отдаёт блоки с inline `display:none` — `parser/export_view.py` теперь прогоняет через `strip_display_none()`. На уровне конвенций добавлено правило #11 — обязательное для всех будущих UI (включая Android WebView).

2026-05-17 — Stage 3 закрыт. Создан `parser/build_db.py` — собирает `parser/corpus.db` (192 MB) из math.jsonl + russian.jsonl + russian_rules.jsonl + russian_problem_meta.jsonl + parser/assets/. Все 10272 задачи в `problems`, FTS5-индекс готов, 0 orphan записей, integrity_check ok. Создан `parser/export_view.py` — генерирует `parser/view_corpus.html` (10 math + 10 rus задач для визуальной проверки). Минорное отклонение от изначальной схемы: добавлен `subject_id` в `problems` и UNIQUE через пару `(subject_id, sdamgia_id)` — 9 коллизий ID между math и rus (историческое наследие sdamgia). Тег `phase-1-stage-3-done`.

2026-05-17 — Stage 2 закрыт. 5409 задач русского №1..№27 (100% покрытие main) в `parser/russian.jsonl`, 36 уникальных правил в `parser/russian_rules.jsonl`, per-problem метаданные (источник/сложность/rule_hash) в `parser/russian_problem_meta.jsonl`. Тег `phase-1-stage-2-done`. 0 ошибок, 0 банов, 0 4xx-5xx. Изменения в схеме БД: добавлены таблицы `rules` + `problem_rules` (many-to-many), колонки `problems.source` и `problems.difficulty` (только для русского). Math rules — user-curated, не парсятся из sdamgia. Парсер `parse_catalog` теперь автоматически детектирует разделитель `<h3>Дополнительные задания для подготовки</h3>` и помечает всё после него как supplementary — работает для обоих предметов. Smoke 32/32 зелёные.

2026-05-17 — Stage 1 закрыт. 4863 задачи математики (100% покрытие main №1..№19) лежат в `parser/math.jsonl`, формулы и иллюстрации в `parser/assets/`. Тег `phase-1-stage-1-done`. 0 ошибок, 0 банов. Добавлена конвенция #9 в «Конвенции парсера» (не использовать bash `&` для запуска парсера — только `run_in_background`). Подвиды Д1..Д19 (~2986 задач) отложены до Stage 4 по решению пользователя. Stage 2 (русский) ожидает мини-разведки от пользователя.

2026-05-16 — Stage 0 разведки sdamgia закрыт. Обновлены: схема `problem_types` (добавлен `is_supplementary` для «Дополнительных заданий Д1..Д14»), Safety Rule #1 (примечание про русский — радар по `problem_types.number`), Tech stack (формулы — SVG, не MathML), Roadmap (Stage 0 ✅, Stage 1 ready). Создан `parser/selectors.yaml`.
