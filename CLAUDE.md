# CLAUDE.md — Операционная память Claude Code для проекта ege-app

## КТО ТЫ (если новая сессия)

Ты — **Claude Code**, работающий над проектом **ege-app** для Daniel (18 лет, Германия, готовится к ЕГЭ-2027). Работа всегда в директории `C:\Projects\ege-app`. Помощник Claude (через claude.ai) — это **второй ИИ**, с которым работает Daniel; он отвечает за стратегию, генерацию контента (pre-gen объяснений), архитектурные решения и мастер-промпты. Ты — за реализацию по инструкциям.

---

## ТЕКУЩЕЕ СОСТОЯНИЕ (на дату последнего обновления)

- **Дата:** 2026-05-20
- **Последний commit hash:** `0dca19031628645a8a2b73559de529e08b1b7b97`
- **Branch:** main (в синхроне с origin/main)
- **APK:** 221 MB (debug)
- **Последние 10 тегов** (по дате создания, новейшие сверху):
  ```
  phase-5-fix-3-glass-opacity
  phase-5-fix-2-stats-perf
  phase-5-fix-1-swipe-perf
  phase-5-srs-done
  phase-5-stage-e5-polish
  phase-5-stage-e4-practice-streak
  phase-5-stage-e3-ui
  phase-5-stage-e2-trainer-integration
  phase-5-stage-e1-data-layer
  phase-5-stage-d2-missing-pregen
  ```

### Untracked артефакты в репо (НЕ коммитить!)
- `parser/corpus.db.backup_*` — 3 файла по 192 MB (576 MB суммарно). Локальные snapshot'ы для отката, в git попадать не должны (лимит GitHub 100MB/файл).
- `*.md` спеки (`STAGE_P4-D*.md`, `PHASE_5_SRS_MASTER_PROMPT.md`, `HANDOFF_PREGEN.md`, `PREGEN_MASTER_PROMPT.md`) — пользовательские спецификации, исторически не коммитились.
- `parser/_missing_*.json`, `missing_all.json`, `all_words.json`, `test_sample.json`, `unprocessed_words.json` — промежуточные dump'ы.
- `android/app/src/main/assets/` — gitignored (corpus.db там 192 MB).

**Никогда не делай `git add -A` слепо** — снесёт пуш с этими backup'ами.

---

## ЧТО ЗАКРЫТО

- **Phase 1:** парсер sdamgia → corpus.db (10 272 задачи, 36 правил, 100% покрытие КИМ ФИПИ-2026).
- **Phase 2:** Android MVP (Kotlin 2.0.21 + Compose BOM 2024.12.01 + Room 2.6.1 + Coil + Ktor).
- **Phase 3:** главный экран (Header / Quote / Predictor / Radar / Mock / Streak), Профиль, Настройки, журнал ошибок, WorkManager push, бэкап через FileProvider, Safety Guards #5/#6.
- **Phase 4:** AI (3 провайдера) + пробники math/rus + 6 ФИПИ-вариантов + 8 новых тренажёров + ExplanationBottomSheet + конфетти + Liquid Glass nav + интеграция тренажёров в каталог. **752 pre-gen объяснений** на старте.
- **Phase 5 Stage D (Pre-gen):**
  - **D1** — rebuild из накопительного JSON (2613 рус). Total 2879.
  - **D2** — догенерация 307 missing (первые буквы алфавита). **Финальный total 3186.**
  - Покрытие: accent 229 (100%), math 37 (100%), word_blank.t9=833, t10=705, t11=516, t12=595, paronym=187, pleonasm=84.
  - corpus.db version 9.
- **Phase 5 Stage E (SRS):** E1-E5 закрыты.
  - **E1:** SM-2 алгоритм (pure Kotlin) + 11 unit-тестов, `SrsCardEntity` в `user_data.db` v5, MIGRATION_4_5.
  - **E2:** Автосоздание SRS-карточек при ошибках в 4 тренажёрах (Accent/WordBlank/Paronym/Pleonasm). Math не интегрирован (carry-over).
  - **E3:** `HomeSrsBlock` после Радара, `SrsReviewScreen` со state machine `Loading → Front → Back → Grade → Done`.
  - **E4:** Practice mode (для word_blank/paronym/pleonasm), `SrsStreakStore` (отдельный), секция «Повторения (SRS)» в Settings.
  - **E5:** scaleIn анимации, Backup v1.9 со SRS-полями. Phase 5 SRS закрыт тегом `phase-5-srs-done`.
- **Phase 5 perf-fix (3 коммита):**
  - **fix-1** (`phase-5-fix-1-swipe-perf`): `Channel<Float>(CONFLATED)` в drag handlers + `pointerInput(Unit)` с `rememberUpdatedState` — устранение coroutine flood и пересоздания gesture detector'а.
  - **fix-2** (`phase-5-fix-2-stats-perf`): `UserStatsStore.getAllSubtypeStats()` — один DataStore read вместо 77, `preloadedStats` параметр в `SubtypeStatsRepository.getStatsForSubject`. `measureTimeMillis` логи через `adb logcat -s HomePerf:D`.
  - **fix-3** (`phase-5-fix-3-glass-opacity`): opacity подложки Liquid Glass `0.92 → 0.78` — blur стал визуально оправдан.

---

## ЧТО ОСТАЛОСЬ — Phase 5.5 Cleanup Sprint

**Мастер-промпт лежит в корне репозитория:** `PHASE_5_5_CLEANUP_SPRINT.md`
**Init-промпт:** `PHASE_5_5_SPRINT_INIT_PROMPT.txt`

> ⚠️ На момент написания этого handoff'а файлы ещё **не созданы** — Daniel принесёт их из claude.ai или положит вручную. В новой сессии: проверь существование, если нет — спроси.

**4 stage'а:**

- **Stage A (B2): distractor'ы для слов с одной кнопкой** (`t11.автостраховщик` и аналоги). ~2-3 часа.
- **Stage B (B1): фикс math ключей** в `TrigTrainerViewModel` и аналогах. ~30-45 мин. Стратегия (a) — фикс ключей в ViewModel'ях, **НЕ перегенерация словаря**.
- **Stage C (B3): догенерация paronym beyond 187.** ~1.5 часа Claude Code + **ПАУЗА для генерации Daniel'ом в чате Claude.ai**.
- **Stage D: миграция AskAi → ExplanationBottomSheet** в `ProblemDetailScreen` + 5 экранов `MockExamRunner`. ~2-3 часа.

### Важно про Stage C
Дойди до создания `missing_paronym_round2.json` (Шаг 3 в мастер-промпте), потом **ОСТАНОВИСЬ** и отчитайся Daniel'у. Он отнесёт JSON в claude.ai, помощник Claude сгенерирует pre-gen, Daniel вернёт `paronym_round2_pregen.json` для заливки. Только после возврата файла — заливка через тот же pipeline что P5-D2.

---

## ЧТО НЕ ДЕЛАТЬ

- **Phase 6 collocation/grammar.rus7** (269 слов, задание №7 ЕГЭ) — **ОТЛОЖЕНО**. Не трогать без явного запроса от Daniel.
- **Не ломать SRS логику** (E1-E5) — работает, протестировано на устройстве. Если перфоманс-фиксы потребуют там правок, согласуй с Daniel'ом.
- **Math: не перегенерировать словарь.** Только фикс ключей в ViewModel'ях (Stage B стратегия (a)).
- **Не делать crash-fix'ы вне scope sprint'а.** Если нашёл баг — вынести в backlog отдельным сообщением Daniel'у.
- **Не коммитить backup'ы corpus.db** (3 файла по 192 MB) и временные JSON-ы из root'а.
- **Никогда `git push --force` без явного запроса.**
- **Никогда `--no-verify`** на pre-commit hook (если такой появится).

---

## КАК НАЧАТЬ В НОВОЙ СЕССИИ

1. **Прочитай этот `CLAUDE.md` полностью.**
2. Проверь существование `PHASE_5_5_CLEANUP_SPRINT.md` в корне:
   - Если есть — прочитай его.
   - Если нет — спроси Daniel'а, ждёшь ли мастер-промпт.
3. **Дождись от Daniel инструкции** (он скопирует содержимое `PHASE_5_5_SPRINT_INIT_PROMPT.txt` или скажет напрямую).
4. Начинай с **Stage A**.
5. **После каждой stage** — отчёт Daniel'у в чат с hash коммита и тегом.
6. Перед коммитом — точечный `git add <files>`, **никогда `-A`**. Push после явного апрува.

---

## АРХИТЕКТУРА (краткая шпаргалка)

### Базы данных
- **БД словаря (read-only):** `parser/corpus.db`, копия в `android/app/src/main/assets/corpus.db`. Управляется Room через `createFromAsset`, **version 9** (после P5-D2). Содержит `subjects` / `problem_types` / `problem_subtypes` / `problems` (10 272) / `solutions` / `rules` / `problem_rules` / `trainer_explanations` (3186 строк pre-gen, P5-D2) + FTS5 индекс.
- **User БД:** `UserDataDatabase` (Room, файл `user_data.db`), **version 5** (после E1). Таблицы: `error_log`, `attempt_log`, `mock_exam_results`, `ai_response_cache`, `srs_cards`.
- **DataStore Preferences (8 файлов):** `app_settings` (тема/radar/SRS лимит/practice toggle), `user_stats` (typeStats/subtypeStats/trainerWordsLearned/trainersCompleted), `streak` (обычный), `srs_streak` (отдельный SRS-streak), `user_profile` (имя/birth/target/exam date), `trainer_progress_*` (по trainerId), `favorites`, `accent_errors`, `wordblank_errors_*` (по типу).
- **EncryptedSharedPreferences:** `ai_secure_keys` — API ключи AI-провайдеров (Tink AES-256). **Никогда не в backup**, исключён из Auto Backup (Convention #40).

### Тренажёры
- **17 тренажёров** через `TrainerCatalogMapping` (Convention #80) — открываются из каталога `Решать → Предмет → Тип`:
  - Accent ударений: №4 (`AccentTrainerScreen`) — 6 категорий + all-режимы.
  - Word_blank орфография: №9-12 (`WordBlankTrainerScreen`).
  - Paronym: №5 (`ParonymTrainerScreen`).
  - Pleonasm: №6 (`PleonasmTrainerScreen`).
  - Word collocation: №7 (`WordCollocationTrainerScreen`).
  - Math: 5 тренажёров (Tri/ShortMult/LogPower/Derivatives/Geometry) через базовый `MathChoiceViewModel`.
- **Resume:** все 17 через `TrainerProgressStore` (Conventions #18, #93, #94). UI — `ResumeBottomSheet`.

### Pre-gen объяснений
- **Чтение:** `ExplanationBottomSheet` → `ExplanationViewModel` → `TrainerExplanationDao.get(word, kind)` + fallback на `getExact(word, kind, subtype)`. SQL exact match без normalization (Convention #87).
- **Ключи в БД** (3186 строк):
  - accent: `word` = lowercase без ударений (`"торты"`), `kind=accent`, `subtype=nouns/verbs/...`.
  - word_blank: `word` = `word.full` lowercase (`"абажур"`), `kind=word_blank`, `subtype=t9/t10/t11/t12`.
  - paronym: `word` = `${wrong}/${correct}`.lowercase() (`"одел/надел"`), `kind=paronym`, `subtype=rus5`.
  - pleonasm: `word` = `extra_word`, `kind=pleonasm`, `subtype=rus6`.
  - math: `word` = `"0"/"30"/"120"` и т.п. — **mismatch с TrigTrainer queries `"sin(30°)"`** (carry-over B1, Stage B этого спринта).

### SRS (Phase 5 Stage E)
- `srs/SrsAlgorithm.kt` — pure Kotlin SM-2. Допустимы grade'ы 0/3/4/5. EF clamp ≥ 1.3.
- `srs/SrsCardEntity.kt` — Entity + DAO в `user_data.db`. UNIQUE(`word`, `kind`, `subtype`).
- `srs/SrsRepository.kt` — `addCardOnMistake` (INSERT OR IGNORE), `getDueCards`, `submitReview`, `getTextsForCard` (cross-database lookup из corpus.db).
- `srs/SrsStreakStore.kt` — DataStore `srs_streak`, отдельный от обычного `StreakStore` (Convention #98).
- `ui/srs/SrsReviewScreen.kt` — state machine `Loading → Front → Back → Practice → Grade → Done`.
- `ui/home/HomeSrsBlock.kt` — карточка после Радара с счётчиком due cards + streak.
- `ui/profile/SrsSettingsSheets.kt` — `SrsDailyLimitBottomSheet` (stepper 10..200) + `SrsStreakResetBottomSheet`.

### Главный экран
- `ui/home/HomeScreen.kt` — LazyColumn с Header + QuoteCard + PredictorCard + RadarCard + **HomeSrsBlock** + MockExamPreviewCard + QuickActionsCard.
- `HomeViewModel.refresh()` — batch read через `UserStatsStore.getAllSubtypeStats()` (perf-fix-2). `measureTimeMillis` логи через `Log.d("HomePerf", ...)`.

### Liquid Glass nav
- `ui/nav/LiquidGlassBottomNav.kt` — двухслойная архитектура (Convention #92):
  - Слой 1 (backdrop): `Box.matchParentSize().liquidGlassBackground(isDark)` с `RenderEffect.createBlurEffect(8f)` на Android 12+.
  - Слой 2 (иконки): `Row.matchParentSize()` поверх backdrop, БЕЗ blur'а.
- Opacity подложки: **0.78** (после perf-fix-3) для видимости blur'а.

---

## CONVENTIONS (важные)

Не переписывай, добавляй новые в конец. Полный список #1-#103.

### Парсер (#1-#13) — Phase 1
1. Rate limiting 1.5-2 сек + jitter, exponential backoff на 429/503.
2. User-Agent — реальный Chrome.
3. Чекпоинты каждые 50 задач в `state.json`.
4. Idempotent cache в `cache/raw/{sdamgia_id}.html`.
5. Селекторы в `selectors.yaml`.
6. Картинки в `assets/{sdamgia_id}/img_N.{ext}`.
7. MathML — не конвертировать в LaTeX (отказались в пользу SVG из sdamgia).
8. Smoke-тесты с эталонными задачами перед каждым обновлением парсера.
9. Background-парсинг **только через Claude Code `run_in_background=true`**, не bash `&`.
10. Расширение файла иллюстраций — по magic bytes, не по URL.
11. `display:none` в HTML sdamgia — обязательно snipить перед рендером.
12. **DDL primary key — table-level, не inline.** Иначе Room schema mismatch с pre-packaged DB.
13. HTML refs в БД должны соответствовать файлам на диске — `fix_html_image_refs.py` после `build_db.py`.

### Android (#14-#23) — Phase 2-3
14. Luminance inversion для SVG в тёмной теме (попиксельный, цветные элементы сохраняются).
15. UI-система — Apple-style примитивы (AppleButton/Card/ListRow/Scaffolds).
16. `readSvgSize` для определения естественного размера формул.
17. Тренажёры русского — единый паттерн (JSON в assets / Repository / ErrorsStore / Routes / Trainer entry / ViewModel / UI).
18. **TrainerProgressStore resume** — двухстадийный sheet, `persistProgress` в `goNext`/`goPrev`, `clearProgress` на completion и `toggleOrder`.
19. Rules JSON pattern — офлайн в `parser/rules.json`, генерится Python-скриптом без AI в runtime.
20. (Удалено, Convention #62 заменила.)
21. **`LocalDarkOverride`** — мгновенная смена темы без перезапуска Activity.
22. **`BackupSnapshot` pattern** — каждый Store имеет `snapshot/restore/clearAll`.
23. **FileProvider + ACTION_SEND** для экспорта/импорта пользовательских данных.

### Главный экран + Phase 3 (#24-#37)
24. `StreakStore.onProblemSolved` hook в 3 ViewModel'ях.
25. `ScorePredictor` + `FipiScoreTable` — алгоритм прогноза балла.
26. Радар 4 стиля (LIST/DONUT/HEATMAP/RADAR_CHART).
27. **`BackupSnapshot` версионирование** — default-значения новых полей + `ignoreUnknownKeys`.
28. **`UserDataDatabase` отдельная от corpus.db.** Cross-database joins невозможны.
29. **AttemptLog + ErrorLog hook** в 3 ViewModel'ях рядом со streak.
30. **CsvExporter** + UTF-8 BOM (для Excel).
31. `AnswerBlock` с `revealed` флагом — правильные ответы скрыты.
32. `NotEnoughDataHint` для радара когда `coloredCount == 0`.
33. **`MockExamSchedule`** — расписание от install_date.
34. **WorkManager periodic workers** + AppSettings gating.
35. **SafetyGuards #5 + #6** (50/week + 300/8weeks).
36. **BackupSnapshot v1.3** + install_date + guards.
37. ~~`typesCovered` критерий — 15+ attempts И accuracy ≥ 70%~~ (заменено Convention #66 — solved==total).

### Phase 4 (#38-#67)
38. **`AiProvider` interface** + 3 провайдера (OpenRouter/Gemini/Anthropic).
39. **`SecureKeyStore`** через EncryptedSharedPreferences.
40. **API-ключи исключены из всех видов бэкапа** (BackupRepository, Auto Backup, D2D Migration).
41. `AiSettingsStore` + `AiResponseCache` + daily limit с auto-reset.
42. **`MockExamRunner`** — раздельные `composeMath`/`composeRus`.
43. **FIPI variants pre-bundled** через `parse_fipi_variants.py`.
44. **`mock_exam_results.source`** + `TrendChart`.
45. INTERNET + ACCESS_NETWORK_STATE permissions в AndroidManifest для AI.
46. OpenRouter free models rotation — `openrouter/free` как default auto-router.
47. AI context fix — Jsoup парсинг HTML + защита от пустого + диагностика.
48. **`AnswerChecker`** — единая проверка ответов с поддержкой множественных вариантов через `|`.
49. **`LatexCleaner`** + усиленный `EGE100_SYSTEM_PROMPT` — двойная защита от LaTeX.
50. Размер формул в `HtmlRenderer` — minHeight + scaleFactor.
51. NavHost slide-spring 0.85f — iOS-look.
52. «Проверить» → «Далее →» при verdict=CORRECT — экономия движения.
53. AI в тренажёрах + кастомные quick questions.
54. `trainerWordsLearned` + AI error handling + Backup v1.6.
55. **Defensive `try/catch`** + global crash handler.
56. `pendingAdvanceJob` — auto-advance координирует с modal sheets.
57. `WordBlankChoices` + `LetterChoiceRow` — кнопки букв вместо TextField.
58. `AppSettings.useLetterChoices` toggle.
59. **Два типа AI-лимитов:** внутренний vs провайдерский.
60. Adaptive icon через Pillow-скрипт из PNG-исходника.
61. **`SmoothLazyColumn`** + `frictionMultiplier 0.7` — iOS-style fling.
62. **`SwipeBackContainer`** — iOS-style swipe-back с visual feedback.
63. **`SwipeableProblemContent`** + резинка /3 + onboarding overlay.
64. `AttemptStatus` подсветка задач в `ProblemListScreen`.
65. **`ProgressRepository`** + прогресс-бары двух уровней (cross-db через Kotlin).
66. **`typesCovered = (solved == total && total > 0)`** — жёсткий критерий «освоенного» типа.
67. **`BreadcrumbLog`** + tracing последних 20 действий.

### Phase 4 Stage D (#68-#90)
68. **`CrashLog` + `CrashRecoveryDialog`** + лимит 5 файлов.
69. **`offset(y=...)` вместо `padding(top=...)`** для негативного визуального nudging.
70. **`SafeMode` crash-loop protection** (3+ крашей за 30с → safe-mode UI без зависимостей).
71. **Pre-gen объяснений** через `trainer_explanations` таблицу + кастомный subagent.
72. **`WordTapInSentenceTrainer`** — общая компонента для тренажёров «тап на слово».
73. **Источник новых русских тренажёров = corpus.db** (РешуЕГЭ), не Opus-генерация.
74. **`MathChoiceTrainer`** + 5 матем тренажёров с 2×2 grid.
75. **`CongratulationDialog`** + Canvas-конфетти.
76. **`trainersCompleted: Set<String>`** через `stringSetPreferencesKey`.
77. **`BackupSnapshot v1.8`** + `UserStatsSnapshot.trainersCompleted`.
78. **Init-safety:** `@Entity` indices ДОЛЖНЫ соответствовать pre-packaged DB + defensive `runCatching` в init-цепочке.
79. **Multi-choice grammar тренажёр** через `SentenceChoiceTrainer` + `build_grammar_v2.py`. *(тренажёр №8 удалён в P4-D6, см. Convention #90)*
80. **`TrainerCatalogMapping`** + интеграция тренажёров в `SubtypesScreen`.
81. **Удаление `AllTrainersScreen`** — тренажёры только через каталог.
82. **Многосвязные привязки матем тренажёров.**
83. **`WordCollocationTrainer`** — двухшаговая логика «выбор + ввод».
84. **№8 переиспользует `SentenceChoiceTrainer` от старого «№7»** *(затем удалён в P4-D6, Convention #90).*
85. **`correctAnswers: List<String>`** для tolerant input checking.
86. **Asset bump через version → destructive recreate** — обновление pre-packaged corpus.db. `fallbackToDestructiveMigration()` на Room builder.
87. **Pre-gen lookup в старых тренажёрах через `ExplanationBottomSheet`.**
88. **`ExplanationBottomSheet` redesign** — drag handle, pill tabs, inline source badge, адаптивная высота.
89. **`LiquidGlassBottomNav`** — настоящий backdrop blur через `RenderEffect`.
90. **№8 multi-choice grammar тренажёр полностью удалён** (P4-D6).
91. **Pre-gen continuation pattern** — пакетная генерация + idempotent pipeline.
92. **`LiquidGlassBottomNav` — двухслойная архитектура.** Blur ТОЛЬКО на backdrop через `matchParentSize`, иконки поверх — резкие.
93. **`TrainerProgressStore` интеграция в новые 9 тренажёров.**
94. **Items в стабильном порядке (без shuffle).** `.sortedBy { problem_id }`, shuffle только при `acceptStartOver()`.
95. **Pre-gen continuation P4-D7 night session** — +179 объяснений (573 → 752).

### Phase 5 (#96-#103)
96. **SRS архитектура — SM-2 в pure Kotlin + UNIQUE(word, kind, subtype) + cross-database fetch объяснений.** `SrsAlgorithm`, `SrsCardEntity`, `SrsRepository`. `UserDataDatabase` v4→v5 + MIGRATION_4_5. Auto-create в 4 тренажёрах через `viewModelScope.launch + runCatching`. Math НЕ интегрируется (B1).
97. **`SrsReviewScreen` state machine + Practice optional + auto-skip на race.** `Loading → Front → Back → Practice? → Grade → Done`. Preload текстов и practice data в `start()`. `HomeSrsBlock` только при `srsDueCount > 0`.
98. **`SrsStreakStore` — отдельный от обычного `StreakStore`.** Хранится в DataStore `srs_streak`. Инкрементит current при grade ≥ 3, idempotent в дне. `checkValidity` в `HomeViewModel.init`.
99. **`BackupSnapshot v1.9` + SRS integration.** `srsStreak` + `srsCards: List<SrsCardRecord>` с default. SUPPORTED_VERSIONS = ["1.0".."1.9"].
100. **`Channel<Float>(CONFLATED)` для drag-tracking** (Phase 5 perf-fix-1). Заменяет `scope.launch { offsetX.snapTo(...) }` на каждое drag event. Один LaunchedEffect collect'ит канал → `Animatable.snapTo`. `trySend` дешёвый non-suspend; CONFLATED автоматически дропает старые непрочитанные значения. Применено в `SwipeableProblemContent` + `SwipeBackContainer`. **При добавлении новых drag-based gesture handlers — использовать тот же паттерн.**
101. **`pointerInput(Unit)` + `rememberUpdatedState`** (Phase 5 perf-fix-1 P2). `pointerInput(hasPrev, hasNext)` пересоздавал gesture detector целиком при смене availability — отменял текущие coroutine, регистрировал новый listener. С `rememberUpdatedState` свежие значения замыкаемых callback'ов и флагов забираются без пересоздания detector'а. **При смене сигнатуры callback'ов в gesture handler'ах — всегда оборачивать в `rememberUpdatedState`.**
102. **Batch DataStore reads через `getAllSubtypeStats`** (Phase 5 perf-fix-2). Раньше `SubtypeStatsRepository.getStatsForSubject` делал 77 sequential `prefs.first()` reads (50 math + 27 rus subtypes). Сейчас один `prefs.first()` + парсинг всех ключей `subtype_total_<id>` / `subtype_correct_<id>` в `Map<Long, Pair<Int, Int>>`. `getStatsForSubject` принимает опциональный `preloadedStats` параметр — `HomeViewModel.refresh()` передаёт один Map в оба вызова (math + rus). **При добавлении новых статистик с per-id ключами в DataStore — использовать тот же паттерн batch-read.** `measureTimeMillis` логи в `HomeViewModel.refresh()` через `Log.d("HomePerf", ...)` — можно убрать в P6+ если стабильно.
103. **Liquid Glass opacity tuning** (Phase 5 perf-fix-3). Opacity `0.92 → 0.78`. При 0.92 backdrop был почти непрозрачным и blur 8px физически не виден — GPU тратил 2-5 ms/кадр впустую. При 0.78 эффект виден, GPU расход оправдан. **При тюнинге Glass-эффектов в P6+: opacity 0.7-0.82 — баланс читаемости и видимости backdrop'а. Не поднимать к 0.9+ без причины.**

---

## РОЛИ

- **Daniel** — заказчик, тестирует на физическом устройстве (Samsung Galaxy), принимает решения. Не имеет USB-отладки — приносит логи через Telegram share. Кодит мало, понимает Python базово, Kotlin/Compose — с нуля.
- **Claude (claude.ai, через чат на сайте)** — стратегия, генерация контента (pre-gen объяснений), мастер-промпты, premortem, project-architect. Daniel приносит ему отчёты Claude Code → получает следующие промпты.
- **Claude Code (это ты)** — реализация по промптам, git операции, build, тестирование SQL, unit-тесты. **НЕ принимаешь стратегические решения сам** — спрашиваешь Daniel'а или ждёшь промпта от помощника Claude.

---

## BACKLOG (не блокирует, фоновое)

- **Phase 6:** collocation/grammar.rus7 (269 слов, опционально).
- Возможные новые баги, которые Daniel найдёт при использовании.
- **`getSubtypeStats(id)`** оставлен в `UserStatsStore` для других callers — если не используется, можно удалить после аудита в P6+.

---

## КАК КОНТАКТИРОВАТЬ С DANIEL В ПРОБЛЕМНЫХ СИТУАЦИЯХ

Если возникла **неоднозначность**, **неожиданное состояние БД**, **конфликт в git**, или **непонятно как поступить** — **НЕ гадай**. ОСТАНОВИСЬ, опиши ситуацию Daniel'у одним сообщением:

- что обнаружил,
- какие варианты,
- какой рекомендуешь.

Жди явного решения.

**Типовые ситуации, требующие паузы:**
- Конфликт schema при бампе corpus.db (`Pre-packaged database has an invalid schema`).
- Невозможность откатить backup (файлы отсутствуют).
- Тест на устройстве показал краш, не повторяющийся в SQL-проверках.
- Помощник Claude дал противоречивые инструкции с историей CLAUDE.md.
- Daniel'овская инструкция вступает в конфликт с safety rules (force push, --no-verify, скрытие ключей в логах).

---

## БЫСТРАЯ СПРАВКА

| Команда | Назначение |
|---|---|
| `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` | Перед gradle, иначе ругается на java |
| `cd "C:/Projects/ege-app/android" && ./gradlew.bat assembleDebug` | Сборка debug APK |
| `cd "C:/Projects/ege-app/android" && ./gradlew.bat testDebugUnitTest` | Unit-тесты (E1 их добавил) |
| `adb logcat -s HomePerf:D` | Логи perf-замеров (perf-fix-2) |
| `adb logcat -s EgeApp AndroidRuntime:E -d -t 200` | Crash logs (Convention #67) |
| `sqlite3 parser/corpus.db "SELECT COUNT(*) FROM trainer_explanations"` | Размер pre-gen (должно быть 3186) |

---

**END OF CLAUDE.md.**
