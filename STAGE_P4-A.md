# STAGE_P4-A.md — Переработка пробника + AI с несколькими провайдерами

> **Первая итерация Phase 4.** Превращает приложение из «решалки» в **полноценный AI-помощник**.
>
> Время: 8-10 часов.
>
> Состав:
> - **Часть А** — Переработка пробника: BottomSheet выбора предмета + полные пробники (math 19 / rus 27).
> - **Часть Б** — Безопасное хранение API ключей через EncryptedSharedPreferences (Android Keystore).
> - **Часть В** — Архитектура AiProvider с 4 реализациями (Anthropic, GigaChat, YandexGPT, OpenAI).
> - **Часть Г** — Настройки AI в Профиле (выбор провайдера, модели, лимит запросов в день).
> - **Часть Д** — Кнопка «Спросить ИИ» в задачах с bottom sheet ответа.
> - **Часть Е** — Лимиты и кеш ответов (защита от случайных трат и дубликатов).

---

## Что работает (НЕ ломать)

- Phase 1 + Phase 2 + Phase 3.
- Календарь пробников из P3-FINAL (только composeMix() нужно переделать).
- 12 тренажёров, журнал ошибок, статистика, бэкап v1.3.
- 46 правил в rules.json.
- Размер APK ~229.5 MB.

---

# ЧАСТЬ А — Переработка пробника (~1.5 часа)

## А1. Что меняем

**Сейчас:** Тап «Начать пробник» → `composeMix()` → 8 math + 8 rus смешанно → 16 задач.

**Стало:** Тап «Начать пробник» → BottomSheet выбора предмета → один из двух полных пробников:
- **Математика: 19 задач** (по одной случайной из каждого типа №1-19).
- **Русский: 27 задач** (по одной случайной из каждого типа №1-27).

Реальные ЕГЭ — отдельные дни на каждый предмет, по полному количеству заданий. Это **педагогически правильно**.

## А2. SubjectChooserBottomSheet

```kotlin
@Composable
fun SubjectChooserBottomSheet(
    onMathChosen: () -> Unit,
    onRusChosen: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp)
        ) {
            Text(
                "Выбери предмет",
                style = MaterialTheme.typography.headlineMedium,
                color = Label,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "Пробник содержит по одной задаче из каждого типа",
                style = MaterialTheme.typography.bodyMedium,
                color = LabelSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Math card
            SubjectChoiceCard(
                emoji = "📐",
                title = "Математика профильная",
                subtitle = "19 заданий · ~3 часа",
                hint = "Из каждого типа № 1-19",
                onClick = onMathChosen
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Russian card
            SubjectChoiceCard(
                emoji = "📝",
                title = "Русский язык",
                subtitle = "27 заданий · ~3.5 часа",
                hint = "Из каждого типа № 1-27",
                onClick = onRusChosen
            )
        }
    }
}

@Composable
fun SubjectChoiceCard(emoji: String, title: String, subtitle: String, hint: String, onClick: () -> Unit) {
    AppleCard(onClick = onClick) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Label)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = LabelTertiary)
            }
            Text("›", style = MaterialTheme.typography.displayMedium, color = LabelTertiary)
        }
    }
}
```

## А3. composeMock — две функции

Заменить `composeMix()` на две специализированные:

```kotlin
class MockExamComposer(private val problemDao: ProblemDao) {
    
    /**
     * Math пробник: 19 задач, по одной случайной из каждого типа 1-19.
     * Если в каком-то типе нет задач — пропускаем (получаем <19).
     */
    suspend fun composeMath(): List<Long> {
        val problemIds = mutableListOf<Long>()
        for (typeNumber in 1..19) {
            val candidates = problemDao.getProblemIdsByTypeNumber("mathb", typeNumber)
            if (candidates.isNotEmpty()) {
                problemIds.add(candidates.random())
            }
        }
        return problemIds  // обычно 19, иногда меньше
    }
    
    /**
     * Russian пробник: 27 задач, по одной случайной из каждого типа 1-27.
     * Тип 27 — сочинение, его обычно НЕ включают в локальный пробник 
     * (требует проверки человеком). Опция: исключать или включать.
     */
    suspend fun composeRus(includeEssay: Boolean = false): List<Long> {
        val problemIds = mutableListOf<Long>()
        val lastType = if (includeEssay) 27 else 26
        for (typeNumber in 1..lastType) {
            val candidates = problemDao.getProblemIdsByTypeNumber("rus", typeNumber)
            if (candidates.isNotEmpty()) {
                problemIds.add(candidates.random())
            }
        }
        return problemIds
    }
}
```

В `ProblemDao`:

```kotlin
@Query("""
    SELECT p.id FROM problems p 
    JOIN problem_types pt ON p.type_id = pt.id 
    JOIN subjects s ON pt.subject_id = s.id 
    WHERE s.slug = :subjectSlug AND pt.number = :typeNumber
""")
suspend fun getProblemIdsByTypeNumber(subjectSlug: String, typeNumber: Int): List<Long>
```

## А4. MockExamDetailScreen обновлён

В `MockExamUpcomingView` заменить кнопку «Начать пробник» на тап → bottom sheet выбора предмета:

```kotlin
var showSubjectChooser by remember { mutableStateOf(false) }

PrimaryButton(
    "Начать пробник",
    onClick = { showSubjectChooser = true },
    modifier = Modifier.fillMaxWidth()
)

if (showSubjectChooser) {
    SubjectChooserBottomSheet(
        onMathChosen = {
            showSubjectChooser = false
            navController.navigate(MockExamRunnerRoute(planIndex, subject = "math"))
        },
        onRusChosen = {
            showSubjectChooser = false
            navController.navigate(MockExamRunnerRoute(planIndex, subject = "rus"))
        },
        onDismiss = { showSubjectChooser = false }
    )
}
```

## А5. MockExamRunnerRoute теперь с subject

```kotlin
@Serializable
data class MockExamRunnerRoute(
    val planIndex: Int,
    val subject: String  // "math" | "rus"
)
```

ViewModel в `init` вызывает `composer.composeMath()` или `composer.composeRus()` в зависимости от subject.

## А6. MockExamResultEntity — добавить поле subject

```kotlin
@Entity(tableName = "mock_exam_results")
data class MockExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_index") val planIndex: Int,
    @ColumnInfo(name = "subject") val subject: String,  // NEW: "math" | "rus"
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String,
    @ColumnInfo(name = "completed_date") val completedDate: Long,
    @ColumnInfo(name = "correct") val correct: Int,           // упрощено: одно число
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "score") val score: Int,                // прогноз ФИПИ балла
    @ColumnInfo(name = "duration_ms") val durationMs: Long
)
```

UserDataDatabase v2 → v3 + Migration.

Один пользователь может пройти **оба пробника одного planIndex** (математику и русский в один планируемый день). Запросы возвращают **список** результатов на planIndex.

## А7. UI календаря обновляется

`MockExamCard` теперь может показать **2 результата** (math + rus) если оба пройдены, или один, или ни одного:

```kotlin
val mathResult = results[plan.index]?.find { it.subject == "math" }
val rusResult = results[plan.index]?.find { it.subject == "rus" }

// Показывать сводку
if (mathResult != null && rusResult != null) {
    Text("M:${mathResult.score} · Р:${rusResult.score}", color = SystemGreen)
} else if (mathResult != null) {
    Text("M:${mathResult.score} (Р: не пройден)", color = SystemOrange)
} else if (rusResult != null) {
    Text("Р:${rusResult.score} (M: не пройден)", color = SystemOrange)
} else {
    // Предстоящий — как раньше
}
```

---

# ЧАСТЬ Б — Безопасное хранение API ключей (~1 час)

## Б1. Зависимость

```kotlin
// app/build.gradle.kts
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

## Б2. SecureKeyStore

`data/SecureKeyStore.kt`:

```kotlin
class SecureKeyStore(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "ai_secure_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveKey(provider: AiProviderType, key: String) {
        sharedPrefs.edit().putString(keyFor(provider), key).apply()
    }
    
    fun getKey(provider: AiProviderType): String? {
        return sharedPrefs.getString(keyFor(provider), null)
    }
    
    fun removeKey(provider: AiProviderType) {
        sharedPrefs.edit().remove(keyFor(provider)).apply()
    }
    
    fun hasKey(provider: AiProviderType): Boolean {
        return !getKey(provider).isNullOrBlank()
    }
    
    private fun keyFor(provider: AiProviderType): String = "key_${provider.name.lowercase()}"
}
```

**Как работает шифрование:**
1. `MasterKey` — генерируется один раз через **Android Keystore** (хардвер-чип в Samsung).
2. Доступ к мастер-ключу — только из этого приложения (sandbox isolation).
3. API-ключи шифруются AES256-GCM перед сохранением в SharedPreferences.
4. Даже **rooted device** не сможет извлечь ключи без знания мастер-ключа.
5. В Auto Backup от Android (бэкап в Google Drive) — мастер-ключ **не бэкапится** (это feature Android), поэтому при восстановлении на новом телефоне ключи нужно ввести заново.

**Это правильно** — твои ключи останутся только на твоём Samsung. Никто другой их не извлечёт.

## Б3. Исключение из бэкапа

В `BackupRepository.exportBackup` **НЕ включать** API-ключи. Это намеренно: бэкап летит в Telegram/Google Drive в открытом виде, а ключи там быть не должны.

Когда пользователь восстанавливается из бэкапа на новом устройстве — он **сам введёт ключи заново** через Настройки.

В `res/xml/backup_rules.xml`:

```xml
<full-backup-content>
    <include domain="sharedpref" path="."/>
    <exclude domain="sharedpref" path="ai_secure_keys.xml"/>  <!-- NEW -->
    <include domain="database" path="."/>
    <exclude domain="database" path="corpus.db"/>
</full-backup-content>
```

---

# ЧАСТЬ В — Архитектура AiProvider (~2 часа)

## В1. AiProvider interface

```kotlin
// ai/AiProvider.kt

sealed interface AiResponse {
    data class Success(val text: String, val tokensUsed: Int) : AiResponse
    data class Error(val message: String, val isAuthError: Boolean = false) : AiResponse
}

interface AiProvider {
    val type: AiProviderType
    val availableModels: List<AiModel>
    val defaultModel: AiModel
    val signupUrl: String
    val keyHint: String  // подсказка для UI типа "sk-ant-..."
    
    suspend fun ask(
        question: String,
        context: String,
        model: AiModel,
        apiKey: String
    ): AiResponse
}

enum class AiProviderType { ANTHROPIC, OPENAI, GIGACHAT, YANDEX_GPT }

data class AiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val costHint: String  // "≈ $0.001/запрос" — для UI
)
```

## В2. AnthropicProvider

```kotlin
class AnthropicProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.ANTHROPIC
    override val availableModels = listOf(
        AiModel(
            id = "claude-haiku-4-5",
            displayName = "Claude Haiku 4.5",
            description = "Быстрая, экономичная",
            costHint = "≈ ₽0.10/запрос"
        ),
        AiModel(
            id = "claude-sonnet-4-6",
            displayName = "Claude Sonnet 4.6",
            description = "Умнее, лучше для сложного",
            costHint = "≈ ₽1.50/запрос"
        ),
        AiModel(
            id = "claude-opus-4-7",
            displayName = "Claude Opus 4.7",
            description = "Максимум интеллекта",
            costHint = "≈ ₽5/запрос"
        )
    )
    override val defaultModel = availableModels[1]  // Sonnet 4.6 как просил пользователь
    override val signupUrl = "https://console.anthropic.com/settings/keys"
    override val keyHint = "sk-ant-api03-..."
    
    override suspend fun ask(
        question: String,
        context: String,
        model: AiModel,
        apiKey: String
    ): AiResponse = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("model", model.id)
            put("max_tokens", 1024)
            put("system", "Ты помогаешь школьнику разобраться с задачами ЕГЭ. Объясняй просто, по шагам, в стиле учебника. Используй формулы где надо, но без markdown. Не более 300 слов.")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Задача:\n$context\n\nВопрос: $question")
                })
            })
        }
        
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        try {
            val response = httpClient.newCall(request).execute()
            if (response.code == 401 || response.code == 403) {
                return@withContext AiResponse.Error("Неверный API ключ", isAuthError = true)
            }
            if (!response.isSuccessful) {
                return@withContext AiResponse.Error("Ошибка: ${response.code}")
            }
            
            val json = JSONObject(response.body!!.string())
            val content = json.getJSONArray("content").getJSONObject(0).getString("text")
            val tokens = json.getJSONObject("usage").let {
                it.getInt("input_tokens") + it.getInt("output_tokens")
            }
            AiResponse.Success(content, tokens)
        } catch (e: Exception) {
            AiResponse.Error("Ошибка сети: ${e.message}")
        }
    }
}
```

## В3. GigaChatProvider

```kotlin
class GigaChatProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.GIGACHAT
    override val availableModels = listOf(
        AiModel(
            id = "GigaChat",
            displayName = "GigaChat (бесплатно)",
            description = "Базовая модель Сбера",
            costHint = "Бесплатно (с лимитами)"
        ),
        AiModel(
            id = "GigaChat-Pro",
            displayName = "GigaChat Pro",
            description = "Платная, мощнее",
            costHint = "По тарифу"
        )
    )
    override val defaultModel = availableModels[0]
    override val signupUrl = "https://developers.sber.ru/portal/products/gigachat"
    override val keyHint = "Auth Key (base64)..."
    
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0
    
    private suspend fun getAccessToken(authKey: String): String? {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return accessToken
        }
        // OAuth2 flow: POST с Authorization: Basic для получения access_token
        // Документация: https://developers.sber.ru/docs/ru/gigachat/api/authorization
        val request = Request.Builder()
            .url("https://ngw.devices.sberbank.ru:9443/api/v2/oauth")
            .header("Authorization", "Basic $authKey")
            .header("RqUID", UUID.randomUUID().toString())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post("scope=GIGACHAT_API_PERS".toRequestBody())
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val json = JSONObject(response.body!!.string())
        accessToken = json.getString("access_token")
        tokenExpiry = json.getLong("expires_at")
        return accessToken
    }
    
    override suspend fun ask(question: String, context: String, model: AiModel, apiKey: String): AiResponse {
        val token = getAccessToken(apiKey) ?: return AiResponse.Error("Не получен access token", isAuthError = true)
        
        // POST к /chat/completions с моделью model.id
        // Возврат content из choices[0].message.content
        // ... (структура похожа на OpenAI)
    }
}
```

## В4. OpenAiProvider

```kotlin
class OpenAiProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.OPENAI
    override val availableModels = listOf(
        AiModel("gpt-4o-mini", "GPT-4o mini", "Быстрая, дешёвая", "≈ ₽0.05/запрос"),
        AiModel("gpt-4o", "GPT-4o", "Стандартная модель OpenAI", "≈ ₽1.50/запрос")
    )
    override val defaultModel = availableModels[0]
    override val signupUrl = "https://platform.openai.com/api-keys"
    override val keyHint = "sk-..."
    
    override suspend fun ask(...): AiResponse {
        // POST к https://api.openai.com/v1/chat/completions
        // С Authorization: Bearer $apiKey
        // Структура messages с system + user
    }
}
```

## В5. YandexGptProvider

```kotlin
class YandexGptProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.YANDEX_GPT
    override val availableModels = listOf(
        AiModel("yandexgpt-lite", "YandexGPT Lite", "Быстрая, базовая", "≈ ₽0.20/запрос"),
        AiModel("yandexgpt", "YandexGPT 4", "Стандартная", "≈ ₽1.00/запрос"),
        AiModel("yandexgpt-pro", "YandexGPT 4 Pro", "Мощная", "≈ ₽5.00/запрос")
    )
    override val defaultModel = availableModels[1]
    override val signupUrl = "https://yandex.cloud/ru/docs/foundation-models/quickstart/console"
    override val keyHint = "API Key (folder ID:key)..."
    
    override suspend fun ask(...): AiResponse {
        // POST к https://llm.api.cloud.yandex.net/foundationModels/v1/completion
        // Auth: Api-Key $apiKey
        // modelUri: gpt://$folderId/$modelId
    }
}
```

## В6. AiProviderRegistry

```kotlin
class AiProviderRegistry(
    private val httpClient: OkHttpClient
) {
    val all: Map<AiProviderType, AiProvider> = mapOf(
        AiProviderType.ANTHROPIC to AnthropicProvider(httpClient),
        AiProviderType.OPENAI to OpenAiProvider(httpClient),
        AiProviderType.GIGACHAT to GigaChatProvider(httpClient),
        AiProviderType.YANDEX_GPT to YandexGptProvider(httpClient)
    )
    
    fun get(type: AiProviderType): AiProvider = all[type]!!
}
```

---

# ЧАСТЬ Г — Настройки AI (~1.5 часа)

## Г1. AiSettingsStore

`data/AiSettingsStore.kt`:

```kotlin
class AiSettingsStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("ai_settings")
    
    private val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_provider")
    private val MODEL_KEY_PREFIX = "model_"
    private val DAILY_LIMIT_KEY = intPreferencesKey("daily_limit")
    private val TODAY_USAGE_KEY = intPreferencesKey("today_usage")
    private val TODAY_DATE_KEY = stringPreferencesKey("today_date")
    
    val settings: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        val savedDate = prefs[TODAY_DATE_KEY]
        val todayUsage = if (savedDate == today) prefs[TODAY_USAGE_KEY] ?: 0 else 0
        
        AiSettings(
            activeProvider = AiProviderType.valueOf(prefs[ACTIVE_PROVIDER_KEY] ?: "GIGACHAT"),
            selectedModelByProvider = AiProviderType.entries.associateWith { type ->
                prefs[stringPreferencesKey(MODEL_KEY_PREFIX + type.name)]
            },
            dailyLimit = prefs[DAILY_LIMIT_KEY] ?: 50,
            todayUsage = todayUsage
        )
    }
    
    suspend fun setActiveProvider(provider: AiProviderType) { ... }
    suspend fun setModelForProvider(provider: AiProviderType, modelId: String) { ... }
    suspend fun setDailyLimit(limit: Int) { ... }
    
    suspend fun incrementTodayUsage() {
        val today = LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            val savedDate = prefs[TODAY_DATE_KEY]
            val current = if (savedDate == today) prefs[TODAY_USAGE_KEY] ?: 0 else 0
            prefs[TODAY_USAGE_KEY] = current + 1
            prefs[TODAY_DATE_KEY] = today
        }
    }
    
    suspend fun canMakeRequest(): Boolean {
        val s = settings.first()
        return s.todayUsage < s.dailyLimit
    }
}

data class AiSettings(
    val activeProvider: AiProviderType,
    val selectedModelByProvider: Map<AiProviderType, String?>,
    val dailyLimit: Int,
    val todayUsage: Int
)
```

**По умолчанию GigaChat** — бесплатный, доступен сразу после регистрации без денег на балансе.

## Г2. AI секция в SettingsScreen

```kotlin
// Новая секция в SettingsScreen.kt
SectionHeader("AI помощник")
AppleListRow(
    icon = "🤖",
    title = "Провайдер",
    subtitle = providerDisplayName(settings.activeProvider),
    onClick = { showProviderSheet = true }
)
AppleListRow(
    icon = "🧠",
    title = "Модель",
    subtitle = currentModelDisplayName,
    onClick = { showModelSheet = true }
)
AppleListRow(
    icon = "🔑",
    title = "API ключ",
    subtitle = if (hasKey) "Сохранён" else "Не задан",
    onClick = { showKeySheet = true }
)
AppleListRow(
    icon = "💵",
    title = "Лимит в день",
    subtitle = "${settings.dailyLimit} запросов · сегодня ${settings.todayUsage}",
    onClick = { showLimitSheet = true }
)
```

## Г3. ProviderChooserBottomSheet

```kotlin
@Composable
fun ProviderChooserBottomSheet(
    activeProvider: AiProviderType,
    onSelect: (AiProviderType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(...) {
        Column(...) {
            Text("AI провайдер", style = MaterialTheme.typography.headlineMedium)
            
            ProviderRow(
                emoji = "🇷🇺",
                name = "GigaChat",
                description = "Сбер · Бесплатно с лимитами",
                selected = activeProvider == AiProviderType.GIGACHAT,
                onClick = { onSelect(AiProviderType.GIGACHAT) }
            )
            ProviderRow(
                emoji = "🇷🇺",
                name = "YandexGPT",
                description = "Яндекс · Платно по тарифу",
                selected = activeProvider == AiProviderType.YANDEX_GPT,
                onClick = { onSelect(AiProviderType.YANDEX_GPT) }
            )
            ProviderRow(
                emoji = "🇺🇸",
                name = "Anthropic Claude",
                description = "Лучшее качество · Платно ($)",
                selected = activeProvider == AiProviderType.ANTHROPIC,
                onClick = { onSelect(AiProviderType.ANTHROPIC) }
            )
            ProviderRow(
                emoji = "🇺🇸",
                name = "OpenAI GPT",
                description = "GPT-4o · Платно ($)",
                selected = activeProvider == AiProviderType.OPENAI,
                onClick = { onSelect(AiProviderType.OPENAI) }
            )
        }
    }
}
```

## Г4. ApiKeyEditBottomSheet

```kotlin
@Composable
fun ApiKeyEditBottomSheet(
    provider: AiProvider,
    currentKey: String?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey ?: "") }
    var isVisible by remember { mutableStateOf(false) }
    
    ModalBottomSheet(...) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("API ключ ${provider.type}", style = MaterialTheme.typography.headlineMedium)
            
            Spacer(Modifier.height(16.dp))
            
            // Хинт где взять
            AppleCard(backgroundColorOverride = SystemBlueTint) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Где взять ключ?", style = MaterialTheme.typography.bodyMedium, color = Label, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(provider.signupUrl, style = MaterialTheme.typography.bodySmall, color = SystemBlue)
                    Spacer(Modifier.height(8.dp))
                    Text("Зарегистрируйся → создай API ключ → вставь сюда.", style = MaterialTheme.typography.bodySmall, color = LabelSecondary)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Поле ввода с показать/скрыть
            IosTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = provider.keyHint,
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isVisible = !isVisible }) {
                        Icon(
                            if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isVisible) "Скрыть" else "Показать"
                        )
                    }
                }
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "🔒 Ключ хранится зашифрованно через Android Keystore. Не передаётся никуда кроме самого провайдера.",
                style = MaterialTheme.typography.bodySmall,
                color = LabelSecondary
            )
            
            Spacer(Modifier.height(24.dp))
            
            PrimaryButton(
                text = "Сохранить",
                onClick = {
                    if (keyInput.isNotBlank()) onSave(keyInput.trim())
                    onDismiss()
                },
                enabled = keyInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            
            if (currentKey != null) {
                Spacer(Modifier.height(8.dp))
                TertiaryButton(
                    text = "Удалить ключ",
                    onClick = { onDelete(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

---

# ЧАСТЬ Д — Кнопка «Спросить ИИ» в задачах (~1 час)

## Д1. Текущая кнопка (если есть) или новая

В `ProblemDetailScreen` рядом с кнопкой «📋 Правило» добавить (или активировать существующую) **«🤖 Спросить ИИ»**.

**Логика активации:**
- Disabled пока пользователь не дал свой ответ (страховка #2 из premortem: «AI как замена решения»).
- После ответа (правильного или неправильного) — кнопка enabled.

```kotlin
SecondaryButton(
    text = "🤖 Спросить ИИ",
    onClick = { showAiSheet = true },
    enabled = userHasAnswered,  // флаг из ViewModel — был ли тап "Проверить"
    modifier = Modifier.fillMaxWidth()
)
```

## Д2. AskAiBottomSheet

```kotlin
@Composable
fun AskAiBottomSheet(
    problemContext: String,
    userAnswer: String,
    correctAnswer: String,
    onAsk: (String) -> Unit,
    aiState: AiState,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 28.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Спросить ИИ", style = MaterialTheme.typography.titleLarge, color = Label)
                    Text("Provider: ${aiState.providerName} · ${aiState.todayUsage}/${aiState.dailyLimit}",
                         style = MaterialTheme.typography.bodySmall, color = LabelSecondary)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Быстрые вопросы (chips)
            Text("Быстрые вопросы:", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
            Spacer(Modifier.height(8.dp))
            
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickQuestionChip("Объясни решение") { question = "Объясни решение этой задачи по шагам." }
                QuickQuestionChip("Почему мой ответ неверный?") { question = "Я ответил $userAnswer. Почему это неверно?" }
                QuickQuestionChip("Какая формула?") { question = "Какая формула используется?" }
                QuickQuestionChip("Где я ошибся?") { question = "Покажи где в моих рассуждениях ошибка." }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Текстовое поле
            IosTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = "Свой вопрос..."
            )
            
            Spacer(Modifier.height(12.dp))
            
            PrimaryButton(
                text = "Спросить",
                onClick = { onAsk(question) },
                enabled = question.isNotBlank() && !aiState.isLoading && aiState.canMakeRequest,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Ответ или ошибка
            when {
                aiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SystemBlue)
                    }
                }
                aiState.lastResponse != null -> {
                    AppleCard {
                        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                            SimpleMarkdownRenderer(aiState.lastResponse)
                        }
                    }
                }
                aiState.lastError != null -> {
                    Text(
                        "❌ ${aiState.lastError}",
                        color = SystemRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (aiState.lastErrorIsAuth) {
                        Spacer(Modifier.height(8.dp))
                        TertiaryButton(
                            "Открыть настройки AI",
                            onClick = { /* navigate to settings */ },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
```

## Д3. AskAiViewModel

```kotlin
class AskAiViewModel(
    private val registry: AiProviderRegistry,
    private val settingsStore: AiSettingsStore,
    private val keyStore: SecureKeyStore,
    private val cache: AiResponseCache
) : ViewModel() {
    
    private val _state = MutableStateFlow(AskAiState())
    val state: StateFlow<AskAiState> = _state.asStateFlow()
    
    fun ask(question: String, problemContext: String) {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val provider = registry.get(settings.activeProvider)
            val apiKey = keyStore.getKey(settings.activeProvider)
            
            if (apiKey.isNullOrBlank()) {
                _state.value = _state.value.copy(lastError = "API ключ не задан. Открой Настройки.", lastErrorIsAuth = true)
                return@launch
            }
            
            // Кеш: тот же вопрос + контекст → возвращаем кешированный ответ
            val cacheKey = "${settings.activeProvider}_${question.hashCode()}_${problemContext.hashCode()}"
            val cached = cache.get(cacheKey)
            if (cached != null) {
                _state.value = _state.value.copy(lastResponse = cached)
                return@launch
            }
            
            // Лимит
            if (!settingsStore.canMakeRequest()) {
                _state.value = _state.value.copy(lastError = "Достигнут дневной лимит (${settings.dailyLimit} запросов)")
                return@launch
            }
            
            _state.value = _state.value.copy(isLoading = true, lastError = null)
            
            val modelId = settings.selectedModelByProvider[settings.activeProvider] ?: provider.defaultModel.id
            val model = provider.availableModels.find { it.id == modelId } ?: provider.defaultModel
            
            when (val response = provider.ask(question, problemContext, model, apiKey)) {
                is AiResponse.Success -> {
                    settingsStore.incrementTodayUsage()
                    cache.put(cacheKey, response.text)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        lastResponse = response.text,
                        lastError = null
                    )
                }
                is AiResponse.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        lastError = response.message,
                        lastErrorIsAuth = response.isAuthError
                    )
                }
            }
        }
    }
}
```

---

# ЧАСТЬ Е — Кеш ответов (~30 мин)

## Е1. AiResponseCache

```kotlin
// data/AiResponseCache.kt
@Entity(tableName = "ai_response_cache")
data class AiResponseCacheEntity(
    @PrimaryKey val cacheKey: String,
    val response: String,
    val cachedAt: Long
)

@Dao
interface AiResponseCacheDao {
    @Query("SELECT response FROM ai_response_cache WHERE cacheKey = :key")
    suspend fun get(key: String): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AiResponseCacheEntity)
    
    @Query("DELETE FROM ai_response_cache WHERE cachedAt < :before")
    suspend fun deleteOld(before: Long)
}
```

UserDataDatabase v3 → v4 + Migration.

Кеш живёт 30 дней (`deleteOld` запускается в Application.onCreate).

## Е2. Зачем кеш

- Ты задал одинаковый вопрос дважды по одной задаче → не платишь второй раз.
- Может пересматриваешь задачу через неделю → ответ ИИ всё ещё есть.
- Сокращает расходы в 3-5 раз на длинном горизонте.

---

# Backup расширение

В `BackupSnapshot v1.4` добавить:

```kotlin
val aiSettings: AiSettings? = null,         // НЕ API ключи!
val mockExamResults: List<MockExamResultEntity>? = null  // обновлённая структура с subject
```

API ключи в бэкап **не идут** (см. Часть Б).

`SUPPORTED_VERSIONS = ["1.0", "1.1", "1.2", "1.3", "1.4"]`

---

# Smoke-тесты

## Часть А — Пробник

| # | Что |
|---|---|
| 1 | Главный → пробник → тап «Начать» → bottom sheet выбора предмета. |
| 2 | Выбор «Математика» → 19 задач. |
| 3 | Выбор «Русский» → 27 задач. |
| 4 | После math пробника результат сохранён с subject="math". |
| 5 | Тот же plan можно пройти rus отдельно (планка показывает оба результата). |

## Часть Б+В+Г — AI настройки

| # | Что |
|---|---|
| 6 | Профиль → Настройки → AI секция видна. |
| 7 | Тап «Провайдер» → bottom sheet с 4 вариантами. |
| 8 | Выбор Anthropic → активный провайдер сменился. |
| 9 | Тап «API ключ» → bottom sheet ввода, поле скрыто звёздочками. |
| 10 | Тап на иконку «глаз» → ключ виден. |
| 11 | Сохранение → ключ сохранился (subtitle «Сохранён»). |
| 12 | Удаление ключа → subtitle «Не задан». |
| 13 | Тап «Модель» → список моделей текущего провайдера + costHint. |

## Часть Д — Спросить ИИ

| # | Что |
|---|---|
| 14 | Math №6 → кнопка «🤖 Спросить ИИ» disabled пока не ответил. |
| 15 | После «Проверить» → кнопка enabled. |
| 16 | Тап → bottom sheet ИИ. |
| 17 | Тап быстрый вопрос «Объясни решение» → вопрос подставился в поле. |
| 18 | Тап «Спросить» → загрузка → ответ в маркдауне. |
| 19 | Тот же вопрос второй раз → из кеша мгновенно, без incrementUsage. |
| 20 | Превысил daily limit → ошибка «Достигнут лимит». |
| 21 | Неверный ключ → ошибка «Неверный API ключ» + кнопка «Открыть настройки AI». |

## Часть E — Бэкап

| # | Что |
|---|---|
| 22 | Экспорт прогресса → файл НЕ содержит API ключи. |
| 23 | Импорт старого бэкапа v1.3 — новые поля aiSettings null, работает. |

---

# После итерации

- `gradlew assembleDebug`.
- НЕ коммитить.
- В отчёте:
  - Структура файлов (ai/, data/, ui/).
  - Размер APK.
  - 23 smoke-теста.
  - Что в Concerns.

После «работает»:
- Commit + tag `phase-4-stage-a-done` + push.
- Conventions #38-42:
  - #38: AiProvider interface + Registry pattern.
  - #39: SecureKeyStore через EncryptedSharedPreferences + Android Keystore.
  - #40: API ключи исключены из Backup (backup_rules.xml).
  - #41: Daily limit + кеш ответов.
  - #42: composeMath/composeRus вместо composeMix.

---

# Last update

Stage P4-A — полноценная переработка пробников по предметам + AI с 4 провайдерами + безопасное хранение ключей.

Дальше: **Stage P4-B** (импорт открытых вариантов КИМ ФИПИ как «официальных» пробников).
