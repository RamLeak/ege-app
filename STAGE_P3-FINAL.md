# STAGE_P3-FINAL.md — Финальная итерация Phase 3

> **Объединяет Stage P3-D и P3-E в одну большую ночную итерацию.** После неё Phase 3 закроется ПОЛНОСТЬЮ.
>
> Время: 8-10 часов работы (идеально для ночной сессии).
>
> Состав:
> - **Часть A** — Календарь пробников (16 контрольных точек).
> - **Часть Б** — Push-уведомления (WorkManager).
> - **Часть В** — Страховки #5 и #6 из premortem.
> - **Часть Г** — Бэкап v1.3 расширение.
> - **Часть Д** — Fix «Освоено типов» (по всем задачам типа, не по 1 попытке).
> - **Часть Е** — Финальная полировка Phase 3 (мелкие визуальные правки).

---

## Что работает (НЕ ломать)

- Всё Phase 2 + P3-A + P3-B + P3-C + P3-C2.
- 12 тренажёров, каталог 10272 задач, 46 правил, 153 цитаты.
- Главный экран с предиктором + радаром + быстрым тренажёром.
- Журнал с ошибками + статистикой + CSV.
- Бэкап v1.2 backward-compat.
- Размер APK ~229.6 MB.

---

# ЧАСТЬ A — Календарь пробников (~2 часа)

## A1. MockExamSchedule

`data/MockExamSchedule.kt`:

```kotlin
class MockExamSchedule(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("mock_exam_schedule")
    
    private val INSTALL_DATE_KEY = stringPreferencesKey("install_date")
    
    suspend fun ensureInstallDate(): LocalDate {
        val today = LocalDate.now()
        val saved = context.dataStore.data.map { it[INSTALL_DATE_KEY]?.let(LocalDate::parse) }.first()
        if (saved == null) {
            context.dataStore.edit { prefs -> prefs[INSTALL_DATE_KEY] = today.toString() }
            return today
        }
        return saved
    }
    
    suspend fun getInstallDate(): LocalDate = ensureInstallDate()
    
    /**
     * Возвращает 16 контрольных точек пробников:
     * - Первый = max(install + 28 дней, today + 14 дней)
     * - Каждый следующий = +21 день
     * - Последний должен быть <= examDate - 7 дней
     */
    suspend fun getSchedule(examDate: LocalDate): List<MockExamPlan> {
        val installDate = ensureInstallDate()
        val today = LocalDate.now()
        
        val firstFromInstall = installDate.plusDays(28)
        val firstFromToday = today.plusDays(14)
        val firstDate = if (firstFromInstall.isAfter(firstFromToday)) firstFromInstall else firstFromToday
        
        val lastAllowed = examDate.minusDays(7)
        
        val plans = mutableListOf<MockExamPlan>()
        var current = firstDate
        var index = 1
        while (current <= lastAllowed && plans.size < 16) {
            plans.add(MockExamPlan(
                index = index,
                date = current,
                status = computeStatus(current, today)
            ))
            current = current.plusDays(21)
            index++
        }
        return plans
    }
    
    suspend fun getNextMockExam(examDate: LocalDate): MockExamPlan? {
        val today = LocalDate.now()
        return getSchedule(examDate).firstOrNull { it.date >= today }
    }
    
    suspend fun getDaysUntilNext(examDate: LocalDate): Int? {
        val next = getNextMockExam(examDate) ?: return null
        return ChronoUnit.DAYS.between(LocalDate.now(), next.date).toInt()
    }
    
    private fun computeStatus(planDate: LocalDate, today: LocalDate): MockExamStatus = when {
        planDate.isEqual(today) -> MockExamStatus.TODAY
        planDate.isBefore(today) -> MockExamStatus.PAST
        else -> MockExamStatus.UPCOMING
    }
}

data class MockExamPlan(
    val index: Int,
    val date: LocalDate,
    val status: MockExamStatus
)

enum class MockExamStatus { UPCOMING, TODAY, PAST }
```

## A2. Таблица результатов

В UserDataDatabase добавить новую таблицу:

```kotlin
@Entity(tableName = "mock_exam_results")
data class MockExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_index") val planIndex: Int,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String,  // ISO date
    @ColumnInfo(name = "completed_date") val completedDate: Long,  // millis
    @ColumnInfo(name = "math_correct") val mathCorrect: Int,
    @ColumnInfo(name = "math_total") val mathTotal: Int,
    @ColumnInfo(name = "rus_correct") val rusCorrect: Int,
    @ColumnInfo(name = "rus_total") val rusTotal: Int,
    @ColumnInfo(name = "math_score") val mathScore: Int,  // прогноз ФИПИ балла
    @ColumnInfo(name = "rus_score") val rusScore: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long
)

@Dao
interface MockExamResultDao {
    @Insert
    suspend fun insert(result: MockExamResultEntity): Long
    
    @Query("SELECT * FROM mock_exam_results ORDER BY completed_date DESC")
    fun getAll(): Flow<List<MockExamResultEntity>>
    
    @Query("SELECT * FROM mock_exam_results WHERE plan_index = :idx LIMIT 1")
    suspend fun getByPlanIndex(idx: Int): MockExamResultEntity?
    
    @Query("DELETE FROM mock_exam_results")
    suspend fun deleteAll()
    
    @Insert
    suspend fun insertAll(items: List<MockExamResultEntity>)
}
```

UserDataDatabase version 1 → 2 + Migration.

## A3. MockExamCalendarScreen

```kotlin
@Composable
fun MockExamCalendarScreen(navController: NavController, viewModel: MockExamCalendarViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            LargeTitleBar(
                title = "Календарь пробников",
                subtitle = "${state.plans.size} контрольных точек до ЕГЭ",
                onBack = { navController.popBackStack() }
            )
        }
        
        item {
            // Сводка
            AppleCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Пройдено", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.completedCount} из ${state.plans.size}",
                        style = MaterialTheme.typography.displayLarge,
                        color = Label,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    AppleProgressBar(progress = state.completedCount.toFloat() / state.plans.size)
                }
            }
        }
        
        items(state.plans) { plan ->
            MockExamCard(
                plan = plan,
                result = state.results[plan.index],
                onClick = { navController.navigate(MockExamDetailRoute(plan.index)) }
            )
        }
    }
}

@Composable
fun MockExamCard(plan: MockExamPlan, result: MockExamResultEntity?, onClick: () -> Unit) {
    AppleCard(onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Эмодзи статус
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(statusBgColor(plan.status, result != null), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(statusEmoji(plan.status, result != null), fontSize = 24.sp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Пробник №${plan.index}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Label
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatDate(plan.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LabelSecondary
                )
                if (result != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "M:${result.mathScore} · Р:${result.rusScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SystemGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), plan.date).toInt()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            days == 0 -> "Сегодня"
                            days > 0 -> "Через ${pluralize(days, "день", "дня", "дней")}"
                            else -> "${pluralize(-days, "день", "дня", "дней")} назад"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (days in 0..7) SystemOrange else LabelTertiary
                    )
                }
            }
            
            Text("›", style = MaterialTheme.typography.headlineMedium, color = LabelTertiary)
        }
    }
}

fun statusEmoji(status: MockExamStatus, completed: Boolean): String = when {
    completed -> "✓"
    status == MockExamStatus.TODAY -> "📅"
    status == MockExamStatus.PAST -> "⏱"
    else -> "🎯"
}
```

## A4. MockExamDetailScreen и прохождение

```kotlin
@Composable
fun MockExamDetailScreen(planIndex: Int, ...) {
    val state by viewModel.state.collectAsState()
    
    if (state.result != null) {
        // Прошедший пробник — показать результаты
        MockExamResultView(state.result, state.plan)
    } else {
        // Предстоящий
        MockExamUpcomingView(
            plan = state.plan,
            onStart = { viewModel.startMockExam() }
        )
    }
}

@Composable
fun MockExamUpcomingView(plan: MockExamPlan, onStart: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Пробник №${plan.index}", style = MaterialTheme.typography.displayLarge, color = Label)
        Spacer(Modifier.height(8.dp))
        Text(formatDate(plan.date), style = MaterialTheme.typography.titleLarge, color = LabelSecondary)
        
        Spacer(Modifier.height(32.dp))
        
        // Информация
        AppleCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Что внутри пробника", style = MaterialTheme.typography.titleMedium, color = Label)
                Spacer(Modifier.height(12.dp))
                InfoRow("📐", "8 задач по математике")
                InfoRow("📝", "8 задач по русскому")
                InfoRow("⏱", "Время неограниченно (но рекомендуется ~30 мин)")
                InfoRow("📊", "Результат сохранится в журнал")
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        PrimaryButton(
            "Начать пробник",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

## A5. Прохождение пробника

`MockExamRunnerScreen` — переиспользует `ProblemDetailScreen` через **последовательность 16 задач**.

Логика composeMix:
- 8 math: по одной случайной из типов №1-19 (8 случайных типов).
- 8 rus: по одной случайной из типов №1-26.
- Перемешать.

После каждого ответа фиксируется в локальный state. Не пишется в attempt_log как обычная задача — пробник идёт **отдельной сущностью**.

После 16-й — экран результата + сохранение в `mock_exam_results`.

## A6. Подключение к главному экрану

Заменить `MockExamPreviewCard.onClick = {}` на `onClick = { navController.navigate(MockExamCalendarRoute) }`.

Также `daysUntilNextMock` теперь считается через `MockExamSchedule`, а не «через 28 дней от сегодня».

---

# ЧАСТЬ Б — Push-уведомления (~1.5 часа)

## Б1. WorkManager и Notification Permission

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

В `MainActivity` при первом запуске запросить permission (Android 13+):

```kotlin
val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    // OK or no-op
}

if (Build.VERSION.SDK_INT >= 33) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

## Б2. NotificationHelper

```kotlin
object NotificationHelper {
    private const val CHANNEL_ID = "ege100_main"
    private const val CHANNEL_NAME = "EGE100 Reminders"
    
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Напоминания и пробники"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    fun showNotification(context: Context, title: String, text: String, notificationId: Int) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)  // упрощённая моно-иконка
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
```

## Б3. WorkManager — три воркера

**Worker 1 — MockExamReminderWorker:** запускается ежедневно в 9:00 утра. Проверяет — есть ли пробник завтра, и если да — push «Завтра пробник №N — будь готов!».

**Worker 2 — StreakReminderWorker:** запускается ежедневно в 20:00. Проверяет — сколько решено сегодня. Если <10 — push «Сегодня решено N из 10. Streak в опасности!». Только если AppSettings.notifyStreak = true.

**Worker 3 — DailyReminderWorker:** запускается ежедневно в 9:00. Если последняя активность была >2 дня назад — push «Daniel, ждут задачи. Не пропускай дни!». Только если AppSettings.notifyReminders = true.

```kotlin
class StreakReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettingsStore(applicationContext).settings.first()
        if (!settings.notifyStreak) return Result.success()
        
        val streak = StreakStore(applicationContext).state.first()
        if (streak.todaySolvedCount < 10) {
            NotificationHelper.showNotification(
                context = applicationContext,
                title = "Streak в опасности!",
                text = "Сегодня решено ${streak.todaySolvedCount} из 10 задач. Доведи до конца!",
                notificationId = 1001
            )
        }
        return Result.success()
    }
}
```

## Б4. Регистрация workers при старте

В `Application.onCreate`:

```kotlin
class Ege100App : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleDailyWorkers()
    }
    
    private fun scheduleDailyWorkers() {
        val workManager = WorkManager.getInstance(this)
        
        // StreakReminder в 20:00 ежедневно
        workManager.enqueueUniquePeriodicWork(
            "streak_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StreakReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(computeInitialDelayUntil(20, 0), TimeUnit.MILLISECONDS)
                .build()
        )
        
        // DailyReminder в 9:00
        workManager.enqueueUniquePeriodicWork("daily_reminder", ...)
        
        // MockExamReminder в 9:00
        workManager.enqueueUniquePeriodicWork("mock_exam_reminder", ...)
    }
}
```

Не забудь зарегистрировать `Ege100App` в `AndroidManifest.xml`:

```xml
<application android:name=".Ege100App" ... >
```

## Б5. Тоггл в настройках работает

`AppSettings.notifyMockExams`, `notifyStreak`, `notifyReminders` уже есть из P3-A. Воркеры проверяют флаги в начале `doWork()` и просто возвращают `Result.success()` если выключено.

---

# ЧАСТЬ В — Страховки #5 и #6 (~1 час)

## В1. SafetyGuardsStore

```kotlin
class SafetyGuardsStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("safety_guards")
    
    private val WEEKLY_GUARD_ACTIVE_KEY = booleanPreferencesKey("weekly_guard_active")
    private val WEEKLY_GUARD_DATE_KEY = stringPreferencesKey("weekly_guard_date")
    private val EIGHT_WEEK_CHECKPOINT_LAST_KEY = stringPreferencesKey("eight_week_last")
    private val EIGHT_WEEK_GUARD_ACTIVE_KEY = booleanPreferencesKey("eight_week_guard_active")
    
    val guardsState: Flow<GuardsState> = context.dataStore.data.map { prefs ->
        GuardsState(
            weeklyGuardActive = prefs[WEEKLY_GUARD_ACTIVE_KEY] ?: false,
            eightWeekGuardActive = prefs[EIGHT_WEEK_GUARD_ACTIVE_KEY] ?: false
        )
    }
    
    suspend fun setWeeklyGuard(active: Boolean) { ... }
    suspend fun setEightWeekGuard(active: Boolean) { ... }
}
```

## В2. SafetyGuardsChecker (вызывается в Application или на старте экрана)

```kotlin
class SafetyGuardsChecker(
    private val context: Context,
    private val attemptLogDao: AttemptLogDao,
    private val mockExamSchedule: MockExamSchedule,
    private val guardsStore: SafetyGuardsStore
) {
    /**
     * Страховка #5: 50 задач в неделю.
     * Если за прошлую календарную неделю (понедельник-воскресенье) решено <50 задач —
     * активируется guard на эту неделю.
     */
    suspend fun checkWeekly() {
        val today = LocalDate.now()
        if (today.dayOfWeek != DayOfWeek.MONDAY) return
        
        val lastWeekStart = today.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lastWeekEnd = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val count = attemptLogDao.getCountBetween(lastWeekStart, lastWeekEnd)
        
        if (count < 50) {
            guardsStore.setWeeklyGuard(true)
        } else {
            guardsStore.setWeeklyGuard(false)
        }
    }
    
    /**
     * Страховка #6: контрольная точка через 8 недель от install.
     * Если за период 8 недель решено <300 задач — активируется guard.
     */
    suspend fun checkEightWeek() {
        val installDate = mockExamSchedule.getInstallDate()
        val today = LocalDate.now()
        val weeksSinceInstall = ChronoUnit.WEEKS.between(installDate, today).toInt()
        
        if (weeksSinceInstall < 8) return
        
        // Проверяем каждые 8 недель
        val periodIndex = weeksSinceInstall / 8
        val periodStart = installDate.plusWeeks((periodIndex * 8L) - 8L)
        val periodEnd = installDate.plusWeeks((periodIndex * 8L))
        
        val startMs = periodStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMs = periodEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val count = attemptLogDao.getCountBetween(startMs, endMs)
        
        if (count < 300) {
            guardsStore.setEightWeekGuard(true)
        }
    }
}
```

Добавить в AttemptLogDao:

```kotlin
@Query("SELECT COUNT(*) FROM attempt_log WHERE timestamp >= :start AND timestamp < :end")
suspend fun getCountBetween(start: Long, end: Long): Int
```

## В3. Визуализация на главном экране

Если `guardsState.weeklyGuardActive = true` — на главном экране сверху (под шапкой) **большая красная карточка**:

```kotlin
@Composable
fun WeeklyGuardCard(weekTotal: Int, onDismiss: () -> Unit) {
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColorOverride = SystemRed.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row {
                Text("⚠️", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text("Страховка #5: 50 задач/неделю", style = MaterialTheme.typography.titleMedium, color = SystemRed)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "На прошлой неделе решено $weekTotal задач. Минимум — 50. Эта неделя в режиме использования: без новых функций приложения, только тренировки.",
                style = MaterialTheme.typography.bodyMedium,
                color = Label
            )
        }
    }
}
```

Если `eightWeekGuardActive` — модальное окно при открытии главного:

```kotlin
@Composable
fun EightWeekCheckpointDialog(periodTotal: Int, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { Text("Контрольная точка") },
        text = { 
            Text("За последние 8 недель ты решил $periodTotal задач. Минимум — 300. Месяц активного использования, без новых функций.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Понял") }
        }
    )
}
```

После confirm — `guardsStore.setEightWeekGuard(false)` (показано один раз).

---

# ЧАСТЬ Г — Бэкап v1.3 (~30 мин)

В `BackupSnapshot` добавить:

```kotlin
@Serializable
data class BackupSnapshot(
    val version: String = "1.3",
    val exportedAt: String,
    val profile: UserProfile,
    val settings: AppSettings,
    val trainerProgress: Map<String, TrainerProgress>,
    val favorites: List<String>,
    val accentErrors: Map<String, Set<String>>,
    val wordBlankErrors: Map<String, Set<String>>,
    val userStats: UserStatsSnapshot? = null,
    val streak: StreakState? = null,
    val errorLog: List<ErrorLogEntity>? = null,
    val attemptLog: List<AttemptLogEntity>? = null,
    val mockExamResults: List<MockExamResultEntity>? = null,  // P3-D NEW
    val installDate: String? = null,                          // P3-D NEW
    val guardsState: GuardsState? = null                      // P3-D NEW
)

val SUPPORTED_VERSIONS = listOf("1.0", "1.1", "1.2", "1.3")
```

`BackupRepository.exportBackup` — добавить сбор данных из новых store/dao.

`applyBackup` — добавить восстановление.

`resetProgress` — очищать mockExamResults и guardsState, но **сохранять install_date** (это техническая дата, не пользовательская).

---

# ЧАСТЬ Д — Fix критерия «Освоено типов» (~30 мин)

## Д1. Проблема

На скриншоте: «Освоено типов: 1 из 46» — но пользователь решил **1 задачу типа №4**, не «освоил тип». Сейчас критерий «освоено = есть хотя бы 1 попытка». Неправильно.

## Д2. Новый критерий

Тип считается **«освоенным»** если выполнены оба условия:

1. **Покрытие:** решено минимум 80% задач этого типа (`attempts >= total_problems_in_type * 0.8`).
2. **Точность:** accuracy >= 70%.

ИЛИ упрощённый вариант:
1. Решено минимум 15 уникальных задач этого типа.
2. Accuracy >= 70%.

**Используем упрощённый вариант** (он понятнее пользователю и не требует учёта какие именно задачи он решал).

## Д3. Расчёт

В `AchievementsState` (или где считается «typesCovered»):

```kotlin
suspend fun computeTypesCovered(): Int {
    val mathStats = statsStore.getTypeStats("math")
    val rusStats = statsStore.getTypeStats("rus")
    val all = mathStats + rusStats
    
    return all.count { stat ->
        stat.attempts >= 15 && stat.accuracy >= 0.70f
    }
}
```

Это даст реалистичную картину — типы где пользователь **действительно силён**.

## Д4. UI — поясняющая подсказка

В `AchievementsRow` строку «Освоено типов: N из 46» дополнить:

```kotlin
AchievementCard(
    emoji = "💪",
    title = "Освоено типов",
    value = "$typesCovered из 46",
    hint = "15+ решений + точность 70%+"  // мелким текстом под value
)
```

Это понятно объясняет критерий.

## Д5. Изменение в Statistics экране

Сейчас в `TypeAccuracyTable` каждый тип с хоть какой-то попыткой попадает в список. Оставить как есть — там видна **полная картина** (с маленькими попытками).

Но добавить **визуальную метку** для освоенных типов: ✓ или 🏆 рядом с типом если он осовоен.

---

# ЧАСТЬ Е — Финальная полировка (~1 час)

## Е1. Цвет статистики

На скриншоте слова «Освоено типов: 1 из 46» **цветом SystemBlue (синий)**. После fix — если 0 типов освоено, цвет должен быть `LabelSecondary` (нейтральный серый), не синий. Синий = «достижение», 0 — не достижение.

## Е2. Empty state на главном экране

Если у пользователя совсем нет данных (только что установил приложение) — главный экран сейчас может выглядеть пусто. Добавить welcoming-card:

```kotlin
if (state.totalAttempts == 0) {
    AppleCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("🎯", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text("Начни подготовку", style = MaterialTheme.typography.titleLarge, color = Label)
            Spacer(Modifier.height(4.dp))
            Text(
                "Реши первые задачи в разделе «Решать», чтобы появились прогноз балла и радар слабых мест.",
                style = MaterialTheme.typography.bodyMedium,
                color = LabelSecondary
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("К каталогу", onClick = { navigateToCatalog() }, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

## Е3. Roadmap-индикатор в Профиле

В Профиле добавить мини-секцию «Прогресс подготовки»:

```
Прогресс подготовки
┌──────────────────────┐
│ Решено всего: 487    │
│ Дней до ЕГЭ: 213    │
│ Пройдено пробников: 0 из 16 │
└──────────────────────┘
```

## Е4. Мелкие правки UX

- Все размеры скруглений в одной системе: 14dp/16dp кнопки, 20dp/24dp карточки.
- Padding-bottom всех LazyColumn увеличить до 100dp чтобы контент не упирался в bottom-bar.
- Haptic feedback на всех кнопках Primary/Danger.
- Spring scale 0.96 на тапах вместо 0.97 (чуть заметнее).
- Color SystemBlue в светлой теме чуть темнее (0xFF0066CC вместо 0xFF0A84FF) для лучшего контраста.

---

# Smoke-тесты ВСЕЙ итерации

## Часть A — Календарь

| # | Что |
|---|---|
| 1 | Главный → тап на MockExamPreviewCard → открывается календарь. |
| 2 | Календарь показывает N пробников (зависит от install date и examDate). |
| 3 | Тап на предстоящий пробник → MockExamUpcomingView. |
| 4 | Тап «Начать пробник» → последовательно 16 задач. |
| 5 | После 16-й — экран результата + сохранение в mock_exam_results. |
| 6 | Возврат в календарь — пробник теперь с эмодзи ✓ и баллами. |

## Часть Б — Push

| # | Что |
|---|---|
| 7 | После установки запрашивается permission на уведомления (Android 13+). |
| 8 | В 20:00 если решено <10 — приходит push (можно проверить вручную через WorkManager TestDriver). |
| 9 | Toggle notifyStreak = OFF → пуши не приходят. |
| 10 | За день до пробника приходит «Завтра пробник №N». |

## Часть В — Страховки

| # | Что |
|---|---|
| 11 | Если за прошлую неделю <50 задач → красная карточка #5 на главном. |
| 12 | После 8 недель если <300 → модальное окно #6 → confirm убирает его. |
| 13 | Если решено >50 в неделю / >300 за 8 недель — карточки не появляются. |

## Часть Г — Бэкап

| # | Что |
|---|---|
| 14 | Экспорт прогресса включает mock_exam_results + install_date + guards. |
| 15 | Импорт старого бэкапа v1.0-v1.2 работает (новые поля null). |
| 16 | Сброс прогресса очищает результаты пробников и guards, но сохраняет install_date. |

## Часть Д — Освоено типов

| # | Что |
|---|---|
| 17 | Статистика → «Освоено типов: 0 из 46» (если решена только 1 задача). |
| 18 | После 15 решённых задач в одном типе с точностью >70% → освоен (+1). |
| 19 | В Statistics видна подсказка «15+ решений + точность 70%+». |

## Часть Е — Полировка

| # | Что |
|---|---|
| 20 | Только что установил → welcome-card на главном. |
| 21 | Профиль → «Прогресс подготовки» виден. |
| 22 | Тактика на кнопках чувствуется (haptic). |

---

# Финальные действия

- `gradlew assembleDebug`.
- НЕ коммитить — жду пользовательского «работает».
- В отчёте:
  - Структура всех новых файлов (MockExam*, SafetyGuards*, NotificationHelper, Workers).
  - Размер APK.
  - 22 smoke-теста.
  - Concerns если есть.

После «работает»:
- Один commit ВСЕГО Stage P3-FINAL.
- Tag `phase-3-stage-d-done` (на этом коммите).
- Tag `phase-3-done` (закрытие Phase 3).
- Push.
- Conventions #33-37:
  - #33: MockExamSchedule pattern с install_date + 21-day intervals.
  - #34: WorkManager periodic workers + AppSettings flags гейт.
  - #35: SafetyGuards from premortem (50/week + 300/8weeks).
  - #36: BackupSnapshot v1.3 backward-compat.
  - #37: typesCovered = (attempts >= 15 && accuracy >= 0.70).

---

# Last update

Финальная итерация Phase 3. После неё:

```
Phase 1: ✅ Парсер
Phase 2: ✅ MVP
Phase 3: ✅ Главный экран + Прогресс + Бэкап + Календарь + Push + Страховки
Phase 4: ⏳ AI + Пробники из КИМ ФИПИ
Phase 5: ⏳ SRS
```

Phase 3 закрыта **полностью**. Дальше Phase 4 — AI-кнопка в задачах и импорт открытых вариантов КИМ ФИПИ.
