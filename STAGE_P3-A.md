# STAGE_P3-A.md — Polish-долги + Профиль + Настройки + Бэкап

> **Первая итерация Phase 3.** Закрывает 3 polish-долга из Phase 2 + строит Профиль, Настройки и систему резервных копий.
>
> Время: 4-6 часов. После — закроется новой версией приложения с защитой прогресса от потери.

---

## Что работает (НЕ ломать)

- Всё что было в Phase 2: каталог, задачи, тренажёры, правила, избранное, светлая тема, иконка.
- Conventions #1-20 из CLAUDE.md.
- Размер APK 228 MB.

---

# Часть А — Polish-долги из Phase 2

## А1. Прогресс-бар видимый в обеих темах

### Проблема

На скриншотах Phase 2 (светлая тема): прогресс-бар в тренажёре едва различим. Заполненная часть голубая, незаполненная сливается с фоном.

### Решение

Создать `AppleProgressBar` компонент (новый):

```kotlin
// ui/common/AppleProgressBar.kt

@Composable
fun AppleProgressBar(
    progress: Float,  // 0f..1f
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val isDark = isSystemInDarkTheme()
    val trackColor = if (isDark) {
        Color(0xFF2C2C2E)  // BgElevated2 dark
    } else {
        Color(0xFFE5E5EA)  // LightBgElevated2
    }
    val barColor = SystemBlue  // одинаковый в обеих темах
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "progress"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(height / 2))
                .background(barColor)
        )
    }
}
```

**Изменения:**
- Высота **6dp** (было 2-4dp) — заметнее.
- Track цвет всегда видим (BgElevated2 в тёмной, LightBgElevated2 в светлой).
- Spring-анимация при изменении значения.
- Полностью скруглён (capsule).

Заменить во всех тренажёрах: `AccentTrainerScreen`, `WordBlankTrainerScreen`.

## А2. Унификация шапки WordBlankTrainerScreen

### Проблема

На скриншоте Phase 2 (`Приставки 1 из 715`): стрелка ← и переключатель ⇆ А-Я прижаты к самому верху экрана. Это потому что в `WordBlankTrainerScreen` шапка реализована **не через `LargeTitleBar`**, а через свой Row.

### Решение

Переписать шапку `WordBlankTrainerScreen` чтобы использовала тот же `LargeTitleBar` что в `AccentTrainerScreen`. Стрелка ← через `BackButton(48×56dp BottomCenter)` будет автоматически опущена ниже.

Также добавить `LargeTitleBar` параметр для **trailing-слота** (если ещё нет) — туда положить переключатель ⇆ А-Я. Сейчас он висит «прижатым к верху» вне LargeTitleBar — это и есть проблема.

```kotlin
LargeTitleBar(
    title = trainerState.typeTitle,  // "Приставки"
    subtitle = "${currentPos} из ${totalWords}",
    onBack = { navController.popBackStack() },
    trailing = {
        OrderToggleChip(
            order = trainerState.order,
            onToggle = { viewModel.toggleOrder() }
        )
    }
)
```

`OrderToggleChip` тоже должен иметь правильный padding-top чтобы не прижиматься к самому верху экрана.

## А3. Свайпы плавнее

В `AccentTrainerScreen` и `WordBlankTrainerScreen`:

```kotlin
val swipeSpring = spring<IntOffset>(
    dampingRatio = 0.85f,        // было 0.75f — меньше bounce
    stiffness = Spring.StiffnessMediumLow,  // было Medium — медленнее, плавнее
    visibilityThreshold = IntOffset(1, 1)
)

AnimatedContent(
    targetState = currentWordIndex,
    transitionSpec = {
        if (isForward) {
            (slideInHorizontally(swipeSpring) { it } + fadeIn(tween(280)))
                .togetherWith(slideOutHorizontally(swipeSpring) { -it / 3 } + fadeOut(tween(280)))
        } else {
            (slideInHorizontally(swipeSpring) { -it } + fadeIn(tween(280)))
                .togetherWith(slideOutHorizontally(swipeSpring) { it / 3 } + fadeOut(tween(280)))
        }
    }
) { ... }
```

Длительность fade 280ms (было 200ms). Spring DampingRatio 0.85 (было 0.75). Slide /3 для parallax-эффекта.

---

# Часть Б — Четвёртый таб «Профиль» в bottom-bar

## Б1. Структура bottom-bar

Сейчас 3 таба: Главная / Решать / Журнал.

Стало 4: **Главная / Решать / Журнал / Профиль**.

```kotlin
// EgeApp.kt — BottomTabBar items

val tabs = listOf(
    Tab(icon = "🏠", label = "Главная",  route = HomeRoute),
    Tab(icon = "📚", label = "Решать",   route = CatalogRoute),
    Tab(icon = "📊", label = "Журнал",   route = JournalRoute),
    Tab(icon = "👤", label = "Профиль",  route = ProfileRoute),  // NEW
)
```

## Б2. Высота bottom-bar

При 4 табах padding между ними меньше. Может потребоваться:
- Уменьшить горизонтальный padding между табами.
- Иконки 24dp (вместо 28dp если было больше) — чтобы помещались.
- Label 11sp Medium.

## Б3. Иконка-аватарка в шапке Главного экрана

В шапке `HomeScreen` справа сверху — круглая иконка-аватарка 36dp с инициалом или эмодзи (например 👤 если имя не задано, или **«D»** для Daniel).

Тап → `navController.navigate(ProfileRoute)`. Двойной доступ к профилю: через таб + через аватарку.

---

# Часть В — Экран «Профиль»

## В1. Состав

```
┌─────────────────────────────────┐
│  Профиль                        │  ← LargeTitleBar
│                                 │
│  ┌────────────────────────┐    │
│  │     👤  (аватар 80dp)   │    │  ← Avatar circle
│  │                         │    │
│  │     Daniel              │    │  ← Имя
│  │     До ЕГЭ-2027         │    │
│  │     осталось 213 дней   │    │
│  └────────────────────────┘    │
│                                 │
│  Личные данные                  │
│  ┌────────────────────────┐    │
│  │ Имя              Daniel ›│    │
│  │ Дата рождения  18.07.07 ›│    │
│  │ Целевой балл       80   ›│    │
│  │ Дата ЕГЭ      04.06.27 ›│    │
│  └────────────────────────┘    │
│                                 │
│  Подготовка                     │
│  ┌────────────────────────┐    │
│  │ ⚙️ Настройки           ›│    │
│  │ 📥 Импорт прогресса    ›│    │
│  │ 📤 Экспорт прогресса   ›│    │
│  └────────────────────────┘    │
│                                 │
│  О приложении                   │
│  ┌────────────────────────┐    │
│  │ Версия            1.0   │    │
│  │ GitHub             ›   │    │
│  │ Анонимная статистика OFF│    │
│  └────────────────────────┘    │
│                                 │
└─────────────────────────────────┘
```

## В2. UserProfileStore

```kotlin
// data/UserProfileStore.kt

class UserProfileStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("user_profile")
    
    private val NAME_KEY = stringPreferencesKey("name")
    private val BIRTH_DATE_KEY = stringPreferencesKey("birth_date")
    private val TARGET_SCORE_KEY = intPreferencesKey("target_score")
    private val EXAM_DATE_KEY = stringPreferencesKey("exam_date")
    
    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            name = prefs[NAME_KEY] ?: "",
            birthDate = prefs[BIRTH_DATE_KEY]?.let(LocalDate::parse),
            targetScore = prefs[TARGET_SCORE_KEY] ?: 80,
            examDate = prefs[EXAM_DATE_KEY]?.let(LocalDate::parse) ?: LocalDate.of(2027, 6, 4)
        )
    }
    
    suspend fun updateName(name: String) { ... }
    suspend fun updateBirthDate(date: LocalDate) { ... }
    suspend fun updateTargetScore(score: Int) { ... }
    suspend fun updateExamDate(date: LocalDate) { ... }
}

data class UserProfile(
    val name: String,
    val birthDate: LocalDate?,
    val targetScore: Int,
    val examDate: LocalDate
) {
    val daysUntilExam: Int
        get() = ChronoUnit.DAYS.between(LocalDate.now(), examDate).toInt()
}
```

## В3. Тапы по строкам

Каждая строка `AppleListRow` с trailing-arrow ведёт на **простой bottom sheet** или **dialog** для редактирования:

- Имя: TextField → сохранение в UserProfileStore.
- Дата рождения: DatePicker.
- Целевой балл: NumberPicker 50-100.
- Дата ЕГЭ: DatePicker (по умолчанию 04.06.2027).

---

# Часть Г — Экран «Настройки»

## Г1. Структура

Тап «⚙️ Настройки» в Профиле → отдельный экран:

```
┌─────────────────────────────────┐
│ ← Настройки                     │
│                                 │
│  Внешний вид                    │
│  ┌────────────────────────┐    │
│  │ 🎨 Тема       Авто  › │    │
│  │ 📊 Радар   Список   › │    │
│  └────────────────────────┘    │
│                                 │
│  Уведомления                    │
│  ┌────────────────────────┐    │
│  │ 🔔 Пробники         ON │    │  ← Switch
│  │ 🔥 Streak           ON │    │
│  │ 📚 Напоминания      ON │    │
│  └────────────────────────┘    │
│                                 │
│  Данные                         │
│  ┌────────────────────────┐    │
│  │ 📤 Экспорт прогресса  ›│    │
│  │ 📥 Импорт прогресса   ›│    │
│  │ 🗑️ Сброс прогресса    ›│    │  ← красный
│  └────────────────────────┘    │
│                                 │
└─────────────────────────────────┘
```

## Г2. AppSettingsStore

```kotlin
// data/AppSettingsStore.kt

enum class ThemeMode { AUTO, DARK, LIGHT }
enum class RadarStyle { LIST, DONUT, HEATMAP, RADAR_CHART }

class AppSettingsStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("app_settings")
    
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.valueOf(prefs[stringPreferencesKey("theme_mode")] ?: "AUTO"),
            radarStyle = RadarStyle.valueOf(prefs[stringPreferencesKey("radar_style")] ?: "LIST"),
            notifyMockExams = prefs[booleanPreferencesKey("notify_mock_exams")] ?: true,
            notifyStreak = prefs[booleanPreferencesKey("notify_streak")] ?: true,
            notifyReminders = prefs[booleanPreferencesKey("notify_reminders")] ?: true
        )
    }
    
    suspend fun setThemeMode(mode: ThemeMode) { ... }
    suspend fun setRadarStyle(style: RadarStyle) { ... }
    // ...
}

data class AppSettings(
    val themeMode: ThemeMode,
    val radarStyle: RadarStyle,
    val notifyMockExams: Boolean,
    val notifyStreak: Boolean,
    val notifyReminders: Boolean
)
```

## Г3. Тема через ручной toggle

В `Theme.kt`:

```kotlin
@Composable
fun EgeTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = appleTypography(), content = content)
}
```

В `MainActivity`:

```kotlin
setContent {
    val settings by viewModel.settings.collectAsState(initial = AppSettings.default())
    EgeTheme(themeMode = settings.themeMode) {
        EgeApp()
    }
}
```

При смене темы в настройках → весь UI перекрашивается **мгновенно** (всё через `collectAsState`).

## Г4. Тема — bottom sheet выбора

Тап «🎨 Тема» → bottom sheet:

```
┌────────────────┐
│ Тема           │
│                │
│ ● Авто       ✓ │  ← по системе
│ ○ Тёмная       │
│ ○ Светлая      │
│                │
└────────────────┘
```

Радио-кнопки. Тап на любую → сохранение в DataStore + закрытие sheet + перекраска UI.

## Г5. Радар — выбор варианта

Тап «📊 Радар» → bottom sheet с 4 вариантами:

```
┌──────────────────────┐
│ Внешний вид радара   │
│                      │
│ ● Список ✓           │  ← preview-картинка справа
│ ○ Круговая           │
│ ○ Тепловая карта     │
│ ○ Лепестковая        │
│                      │
└──────────────────────┘
```

Сохранение в DataStore. На главном экране (Stage P3-B) будет показан выбранный вариант.

---

# Часть Д — Бэкап / Экспорт / Импорт

## Д1. Что бэкапим

Полный JSON со **всеми** пользовательскими данными:

```json
{
  "version": "1.0",
  "exported_at": "2026-05-21T15:30:00Z",
  "device": "Samsung Galaxy",
  "app_version": "1.0",
  "profile": {
    "name": "Daniel",
    "birth_date": "2007-07-18",
    "target_score": 80,
    "exam_date": "2027-06-04"
  },
  "settings": {
    "theme_mode": "AUTO",
    "radar_style": "LIST",
    "notify_mock_exams": true,
    "notify_streak": true,
    "notify_reminders": true
  },
  "trainer_progress": {
    "accent_nouns": {"position": 47, "total": 65, "order": "alphabetical"},
    "blank_9": {"position": 123, "total": 847, "order": "random", "indices": [...]},
    ...
  },
  "favorites": ["problem_id_1", "problem_id_2", ...],
  "trainer_errors": {
    "accent_nouns": ["слово1", "слово2", ...],
    "blank_9": [...],
    ...
  },
  "user_attempts": [],  // в Stage P3-C появится
  "stats": {}  // в Stage P3-C
}
```

## Д2. BackupRepository

```kotlin
// data/BackupRepository.kt

class BackupRepository(
    private val context: Context,
    private val userProfileStore: UserProfileStore,
    private val settingsStore: AppSettingsStore,
    private val trainerProgressStore: TrainerProgressStore,
    private val favoritesStore: FavoritesStore,
    private val accentErrorsStore: AccentErrorsStore,
    private val wordBlankErrorsStore: WordBlankErrorsStore
) {
    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val backup = BackupSnapshot(
            version = "1.0",
            exportedAt = Instant.now().toString(),
            profile = userProfileStore.profile.first(),
            settings = settingsStore.settings.first(),
            trainerProgress = collectAllTrainerProgress(),
            favorites = favoritesStore.getAllFavorites().first().toList(),
            trainerErrors = collectAllErrors()
        )
        Json.encodeToString(backup)
    }
    
    suspend fun importBackup(json: String, confirmReplace: Boolean = false): ImportResult {
        if (!confirmReplace) return ImportResult.NeedsConfirmation
        try {
            val backup = Json.decodeFromString<BackupSnapshot>(json)
            // Восстановить всё из backup
            userProfileStore.restore(backup.profile)
            settingsStore.restore(backup.settings)
            // ...
            return ImportResult.Success(backup.exportedAt)
        } catch (e: Exception) {
            return ImportResult.Error(e.message ?: "Unknown error")
        }
    }
}
```

## Д3. Экспорт через системный share-sheet

В Настройках тап «📤 Экспорт прогресса»:

1. Запускается `BackupRepository.exportBackup()` (1-2 секунды).
2. Создаётся временный файл в `context.cacheDir` с именем `ege100_backup_2026-05-21.json`.
3. Через `Intent.ACTION_SEND` с MIME type `application/json` — открывается системный share-sheet:
   - Google Drive
   - Telegram (Saved Messages — идеально для тебя!)
   - Email
   - Сохранить в Файлы
   - Yandex Disk
   - И т.д.

Код:

```kotlin
fun shareBackup(context: Context, json: String) {
    val fileName = "ege100_backup_${LocalDate.now()}.json"
    val file = File(context.cacheDir, fileName).apply {
        writeText(json)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Резервная копия EGE100")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Сохранить резервную копию"))
}
```

Не забудь подключить `FileProvider` в `AndroidManifest.xml` и создать `res/xml/file_paths.xml`:

```xml
<paths>
    <cache-path name="cache" path="." />
</paths>
```

## Д4. Импорт через системный picker

Тап «📥 Импорт прогресса»:

1. Открывается `ActivityResultContracts.GetContent()` с MIME type `application/json`.
2. Пользователь выбирает файл (из Google Drive, Telegram, локально).
3. Файл читается через `context.contentResolver.openInputStream(uri)`.
4. JSON парсится.
5. **Bottom sheet подтверждения:** «Заменить текущий прогресс на данные из 2026-05-21? Текущий прогресс будет потерян.»
6. Тап «Заменить» → `BackupRepository.importBackup(json, confirmReplace = true)`.
7. Toast: «Прогресс восстановлен ✓».
8. **Перезапуск Activity** чтобы UI подхватил новые данные.

## Д5. Auto Backup от Android

В `AndroidManifest.xml`:

```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    ...>
```

`res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="sharedpref" path="."/>
    <include domain="database" path="."/>
    <exclude domain="database" path="corpus.db"/>  <!-- БД задач не бэкапим, она огромная -->
</full-backup-content>
```

`res/xml/data_extraction_rules.xml` (Android 12+):

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="."/>
        <include domain="database" path="."/>
        <exclude domain="database" path="corpus.db"/>
    </cloud-backup>
    <device-transfer>
        <include domain="sharedpref" path="."/>
        <include domain="database" path="."/>
        <exclude domain="database" path="corpus.db"/>
    </device-transfer>
</data-extraction-rules>
```

Это значит:
- При резервном копировании Android (Google) сохраняет DataStore (прогресс, профиль, настройки, избранное) автоматически в Google аккаунте.
- При переустановке приложения или смене телефона — данные подтянутся автоматически (если у пользователя включена синхронизация).
- БД задач `corpus.db` не бэкапим (она 192MB, к тому же в каждом APK).

## Д6. Сброс прогресса (с подтверждением)

Тап «🗑️ Сброс прогресса» → bottom sheet:

```
⚠️ Сбросить весь прогресс?

Будет удалено:
• Прогресс всех тренажёров
• Избранные задачи
• История ошибок

Профиль и настройки сохранятся.

[Да, сбросить]   ← DangerButton
[Отмена]
```

При подтверждении → очистить все Store кроме `UserProfileStore` и `AppSettingsStore`.

---

# Smoke-тесты

| # | Что проверить |
|---|---|
| 1 | Прогресс-бар в тренажёре «Существительные» виден в светлой теме (заполненная и пустая часть). |
| 2 | Прогресс-бар в темной теме тоже виден. |
| 3 | Шапка тренажёра «Приставки»: стрелка ← опущена (тап-зона удобная). |
| 4 | Переключатель ⇆ А-Я в шапке тоже не прижат к верху. |
| 5 | Свайпы между словами плавные (subjectively «как iOS»). |
| 6 | В bottom-bar 4 таба: Главная / Решать / Журнал / Профиль. |
| 7 | В шапке Главного экрана — аватарка справа сверху. Тап → Профиль. |
| 8 | Профиль: видны имя (если задано), целевой балл, дата ЕГЭ, дни до экзамена. |
| 9 | Тап на «Имя» → можно ввести, сохранить. |
| 10 | Тап на «Целевой балл» → NumberPicker 50-100. |
| 11 | Профиль → Настройки → экран Настройки. |
| 12 | Тема → bottom sheet «Авто / Тёмная / Светлая» → выбор «Светлая» → UI стал светлым **мгновенно**. |
| 13 | Радар → bottom sheet с 4 вариантами + preview. |
| 14 | Уведомления — 3 Switch (заглушки, реальные push в Stage P3-D). |
| 15 | Экспорт прогресса → открывается системный share-sheet → можно сохранить в Telegram Saved Messages. |
| 16 | Telegram → файл `ege100_backup_2026-05-21.json` пришёл, видимое содержимое. |
| 17 | Импорт прогресса → выбор файла → подтверждение → восстановление. |
| 18 | Сброс прогресса → подтверждение → всё обнулилось (тренажёры стартуют с 0, избранное пустое). |
| 19 | Профиль и настройки после сброса остались. |

---

# Зависимости

```kotlin
// app/build.gradle.kts
implementation("androidx.compose.material3:material3:1.3.1")  // обновление если ниже
implementation("androidx.activity:activity-compose:1.10.0")
```

Также проверь что есть `androidx.compose.material:material-icons-extended` для иконок.

---

# После итерации

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского «работает».
- В отчёте:
  - Скриншоты Профиля, Настроек, Экспорта, Импорта.
  - Размер APK (должен остаться ~228 MB).
  - 19 smoke-тестов — пройдены логически.

---

# Если что-то не получается

- FileProvider не работает → проверь манифест и file_paths.xml.
- Share-sheet не открывается → проверь Intent.ACTION_SEND + EXTRA_STREAM.
- ImportBackup ломается на старых форматах → добавь versioned parsing.
- Auto Backup от Android — это пассивная фича, тестировать не обязательно (работает сама).

---

# Last update

Stage P3-A — первая итерация Phase 3. После теста — commit + tag `phase-3-stage-a-done`.

Дальше: **Stage P3-B** (Главный экран с цитатами + предиктор + радар + быстрый тренажёр).
