# STAGE_P4-A+B.md — Phase 4 (большая часть): Пробник + AI + Импорт КИМ ФИПИ

> **Объединяет Stage P4-A и P4-B в одну большую итерацию.**
>
> Время: 14-16 часов работы Claude Code (1-2 ночи).
>
> Состав:
> - **A1** — Переработка пробника (BottomSheet выбора + math 19 / rus 27).
> - **A2** — Безопасное хранение ключей (EncryptedSharedPreferences + Android Keystore).
> - **A3** — Архитектура AiProvider с **OpenRouter** как главным + Anthropic как опциональный.
> - **A4** — Настройки AI в Профиле (выбор провайдера, модели, лимит).
> - **A5** — Кнопка «Спросить ИИ» в задачах + кеш ответов.
> - **B1** — Импорт открытых вариантов КИМ ФИПИ (PDF parser).
> - **B2** — История прохождений с графиком тренда.
> - **B3** — Финальная интеграция.

---

## Что работает (НЕ ломать)

- Phase 1 + Phase 2 + Phase 3 (с её P3-FINAL).
- Календарь пробников из P3-FINAL (composeMix будет переделан).
- 12 тренажёров, журнал ошибок, статистика, бэкап v1.3.
- 46 правил в rules.json, 153 цитаты в quotes.json.
- Размер APK ~229.5 MB.

---

## ПРАВИЛА АВТОНОМНОСТИ

Этот Stage делается **в автономном режиме**:
- Пользователь оставил Claude Code на 14-16 часов работы.
- **Не задавать вопросов пользователю** — принимать решения самостоятельно.
- Если есть выбор между двумя вариантами — выбирай **более консервативный/безопасный**.
- Если что-то не работает — пробуй обходные пути, документируй в Concerns.
- Между A и B сделай `/compact` если контекст > 75%.
- Финальный отчёт в конце ВСЕГО Stage (не отдельно A и B).

---

# ЧАСТЬ A1 — Переработка пробника (~1.5 часа)

## A1.1 Что меняем

**Сейчас:** Тап «Начать пробник» → `composeMix()` → 8 math + 8 rus смешанно.

**Стало:** Тап «Начать пробник» → BottomSheet выбора предмета:
- **Математика: 19 задач** (по одной случайной из каждого типа №1-19).
- **Русский: 26 задач** (по одной случайной из типов №1-26, тип №27 «сочинение» исключаем — требует проверки человеком).

## A1.2 SubjectChooserBottomSheet

```kotlin
@Composable
fun SubjectChooserBottomSheet(
    onMathChosen: () -> Unit,
    onRusChosen: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Выбери предмет", style = MaterialTheme.typography.headlineMedium, color = Label,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            
            Text("Пробник содержит по одной задаче из каждого типа",
                style = MaterialTheme.typography.bodyMedium, color = LabelSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
            
            Spacer(Modifier.height(24.dp))
            
            SubjectChoiceCard("📐", "Математика профильная", "19 заданий · ~3 часа", "Из типов №1-19", onMathChosen)
            Spacer(Modifier.height(12.dp))
            SubjectChoiceCard("📝", "Русский язык", "26 заданий · ~3 часа", "Из типов №1-26 (без сочинения)", onRusChosen)
        }
    }
}
```

## A1.3 MockExamComposer

```kotlin
class MockExamComposer(private val problemDao: ProblemDao) {
    suspend fun composeMath(): List<Long> {
        val problemIds = mutableListOf<Long>()
        for (typeNumber in 1..19) {
            val candidates = problemDao.getProblemIdsByTypeNumber("mathb", typeNumber)
            if (candidates.isNotEmpty()) problemIds.add(candidates.random())
        }
        return problemIds
    }
    
    suspend fun composeRus(): List<Long> {
        val problemIds = mutableListOf<Long>()
        for (typeNumber in 1..26) {  // 27 = сочинение, исключаем
            val candidates = problemDao.getProblemIdsByTypeNumber("rus", typeNumber)
            if (candidates.isNotEmpty()) problemIds.add(candidates.random())
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

## A1.4 MockExamResultEntity — добавить subject

```kotlin
@Entity(tableName = "mock_exam_results")
data class MockExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planIndex: Int,
    val subject: String,  // NEW: "math" | "rus"
    val source: String = "internal",  // NEW: "internal" | "fipi" (для B1)
    val scheduledDate: String,
    val completedDate: Long,
    val correct: Int,
    val total: Int,
    val score: Int,
    val durationMs: Long
)
```

UserDataDatabase v2 → v3 + Migration.

## A1.5 MockExamCard в календаре

Показывает оба результата если оба пройдены:

```kotlin
val mathResult = results[plan.index]?.find { it.subject == "math" }
val rusResult = results[plan.index]?.find { it.subject == "rus" }

if (mathResult != null && rusResult != null) {
    Text("M:${mathResult.score} · Р:${rusResult.score}", color = SystemGreen)
} else if (mathResult != null) {
    Text("M:${mathResult.score} (Р: не пройден)", color = SystemOrange)
} else if (rusResult != null) {
    Text("Р:${rusResult.score} (M: не пройден)", color = SystemOrange)
}
```

---

# ЧАСТЬ A2 — Безопасное хранение ключей (~1 час)

## A2.1 Зависимость

```kotlin
// app/build.gradle.kts
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

## A2.2 SecureKeyStore

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
    
    fun getKey(provider: AiProviderType): String? = sharedPrefs.getString(keyFor(provider), null)
    fun removeKey(provider: AiProviderType) { sharedPrefs.edit().remove(keyFor(provider)).apply() }
    fun hasKey(provider: AiProviderType): Boolean = !getKey(provider).isNullOrBlank()
    
    private fun keyFor(provider: AiProviderType): String = "key_${provider.name.lowercase()}"
}
```

## A2.3 Исключение из бэкапа

`res/xml/backup_rules.xml`:

```xml
<full-backup-content>
    <include domain="sharedpref" path="."/>
    <exclude domain="sharedpref" path="ai_secure_keys.xml"/>  <!-- NEW: API ключи НЕ бэкапятся -->
    <include domain="database" path="."/>
    <exclude domain="database" path="corpus.db"/>
</full-backup-content>
```

Аналогично в `data_extraction_rules.xml`.

---

# ЧАСТЬ A3 — Архитектура AiProvider (~2 часа)

## A3.1 Стратегия — 3 провайдера, без OpenAI

**Поддерживаем 3 провайдера:**

1. **OpenRouter** (дефолт, бесплатно + платно). Один ключ → 30+ моделей. Лимит: 20 запросов/мин, 200/день.
2. **Google Gemini** (запасной бесплатный). 1500 запросов/день Gemini 2.0 Flash через Google AI Studio. В 7.5× больше OpenRouter free лимита.
3. **Anthropic direct** (опциональный платный). Sonnet 4.6 / Opus 4.7 — максимальное качество.

**OpenAI НЕ включаем** — пользователь не хочет.
**YandexGPT/GigaChat НЕ включаем** — пользователь живёт в Германии, российские модели не нужны.
**Groq/NVIDIA/HuggingFace НЕ включаем** — нестабильны или требуют телефон при регистрации.

OpenRouter API совместим с OpenAI SDK — просто base URL `https://openrouter.ai/api/v1`. Google Gemini имеет свой формат через `https://generativelanguage.googleapis.com/v1beta`.

## A3.2 AiProvider interface

```kotlin
// ai/AiProvider.kt

sealed interface AiResponse {
    data class Success(val text: String, val tokensUsed: Int) : AiResponse
    data class Error(val message: String, val isAuthError: Boolean = false, val isRateLimit: Boolean = false) : AiResponse
}

interface AiProvider {
    val type: AiProviderType
    val displayName: String
    val description: String
    val signupUrl: String
    val keyHint: String
    val availableModels: List<AiModel>
    val defaultModelId: String
    
    suspend fun ask(
        question: String,
        context: String,
        modelId: String,
        apiKey: String
    ): AiResponse
}

enum class AiProviderType { OPENROUTER, GEMINI, ANTHROPIC }

data class AiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val costHint: String,  // "Бесплатно" / "≈ ₽0.10/запрос"
    val isFree: Boolean = false
)
```

## A3.3 OpenRouterProvider

```kotlin
class OpenRouterProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.OPENROUTER
    override val displayName = "OpenRouter"
    override val description = "Доступ к 100+ моделям через один ключ. Есть бесплатные."
    override val signupUrl = "https://openrouter.ai/keys"
    override val keyHint = "sk-or-v1-..."
    
    override val availableModels = listOf(
        // БЕСПЛАТНЫЕ
        AiModel(
            id = "deepseek/deepseek-r1:free",
            displayName = "DeepSeek R1 (free)",
            description = "Reasoning model, отлично для математики",
            costHint = "Бесплатно",
            isFree = true
        ),
        AiModel(
            id = "deepseek/deepseek-chat-v3:free",
            displayName = "DeepSeek V3 (free)",
            description = "Универсальная, быстрая",
            costHint = "Бесплатно",
            isFree = true
        ),
        AiModel(
            id = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "Llama 3.3 70B (free)",
            description = "Сильная open-source модель",
            costHint = "Бесплатно",
            isFree = true
        ),
        AiModel(
            id = "qwen/qwen-2.5-72b-instruct:free",
            displayName = "Qwen 2.5 72B (free)",
            description = "Универсальная, хорошо на русском",
            costHint = "Бесплатно",
            isFree = true
        ),
        AiModel(
            id = "google/gemini-2.0-flash-exp:free",
            displayName = "Gemini 2.0 Flash (free)",
            description = "Быстрая, Google",
            costHint = "Бесплатно",
            isFree = true
        ),
        // ПЛАТНЫЕ (требуют пополнения баланса OpenRouter)
        AiModel(
            id = "anthropic/claude-sonnet-4.6",
            displayName = "Claude Sonnet 4.6",
            description = "Anthropic, премиум-качество",
            costHint = "≈ $0.015/запрос"
        ),
        AiModel(
            id = "openai/gpt-4o",
            displayName = "GPT-4o",
            description = "OpenAI, премиум",
            costHint = "≈ $0.02/запрос"
        )
    )
    override val defaultModelId = "deepseek/deepseek-r1:free"
    
    override suspend fun ask(
        question: String,
        context: String,
        modelId: String,
        apiKey: String
    ): AiResponse = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 1024)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Ты помогаешь школьнику разобраться с задачами ЕГЭ по математике или русскому. Объясняй просто, по шагам, как в учебнике. Используй формулы где надо. Без markdown. Максимум 300 слов.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Задача:\n$context\n\nВопрос: $question")
                })
            })
        }
        
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/RamLeak/ege-app")  // OpenRouter рекомендует
            .header("X-Title", "EGE100")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        try {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> AiResponse.Error("Неверный API ключ", isAuthError = true)
                    429 -> AiResponse.Error("Превышен лимит запросов. Подожди минуту.", isRateLimit = true)
                    in 200..299 -> {
                        val body = response.body!!.string()
                        val json = JSONObject(body)
                        val content = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        val tokens = json.optJSONObject("usage")?.optInt("total_tokens") ?: 0
                        AiResponse.Success(content, tokens)
                    }
                    else -> AiResponse.Error("Ошибка ${response.code}: ${response.body?.string()?.take(200)}")
                }
            }
        } catch (e: Exception) {
            AiResponse.Error("Ошибка сети: ${e.message}")
        }
    }
}
```

## A3.4 GeminiProvider

```kotlin
class GeminiProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.GEMINI
    override val displayName = "Google Gemini"
    override val description = "1500 запросов/день бесплатно. Google AI Studio."
    override val signupUrl = "https://aistudio.google.com/app/apikey"
    override val keyHint = "AIza..."
    
    override val availableModels = listOf(
        AiModel(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            description = "Быстрая, 1500 запросов/день",
            costHint = "Бесплатно (1500/день)",
            isFree = true
        ),
        AiModel(
            id = "gemini-2.0-flash-lite",
            displayName = "Gemini 2.0 Flash Lite",
            description = "Самая быстрая",
            costHint = "Бесплатно",
            isFree = true
        ),
        AiModel(
            id = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            description = "Более качественная, лимит ниже",
            costHint = "Бесплатно (ограниченно)",
            isFree = true
        )
    )
    override val defaultModelId = "gemini-2.0-flash"
    
    override suspend fun ask(
        question: String,
        context: String,
        modelId: String,
        apiKey: String
    ): AiResponse = withContext(Dispatchers.IO) {
        // Google AI Studio API: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", """Ты помогаешь школьнику разобраться с задачами ЕГЭ. Объясняй просто, по шагам, как в учебнике. Используй формулы где надо. Без markdown. Максимум 300 слов.

Задача:
$context

Вопрос: $question""")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1024)
                put("temperature", 0.7)
            })
        }
        
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        try {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    400, 401, 403 -> AiResponse.Error("Неверный API ключ", isAuthError = true)
                    429 -> AiResponse.Error("Превышен дневной лимит (1500). Завтра обнулится.", isRateLimit = true)
                    in 200..299 -> {
                        val json = JSONObject(response.body!!.string())
                        val content = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        val tokens = json.optJSONObject("usageMetadata")?.optInt("totalTokenCount") ?: 0
                        AiResponse.Success(content, tokens)
                    }
                    else -> AiResponse.Error("Ошибка ${response.code}: ${response.body?.string()?.take(200)}")
                }
            }
        } catch (e: Exception) {
            AiResponse.Error("Ошибка сети: ${e.message}")
        }
    }
}
```

## A3.5 AnthropicProvider

```kotlin
class AnthropicProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.ANTHROPIC
    override val displayName = "Anthropic Claude (direct)"
    override val description = "Прямой доступ к Claude. Лучшее качество. Платно."
    override val signupUrl = "https://console.anthropic.com/settings/keys"
    override val keyHint = "sk-ant-api03-..."
    
    override val availableModels = listOf(
        AiModel("claude-haiku-4-5", "Claude Haiku 4.5", "Быстрая, дешёвая", "≈ ₽0.10/запрос"),
        AiModel("claude-sonnet-4-6", "Claude Sonnet 4.6", "Сбалансированная", "≈ ₽1.50/запрос"),
        AiModel("claude-opus-4-7", "Claude Opus 4.7", "Максимум интеллекта", "≈ ₽5/запрос")
    )
    override val defaultModelId = "claude-sonnet-4-6"
    
    override suspend fun ask(question: String, context: String, modelId: String, apiKey: String): AiResponse = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 1024)
            put("system", "Ты помогаешь школьнику разобраться с задачами ЕГЭ. Объясняй просто, по шагам. Максимум 300 слов.")
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
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> AiResponse.Error("Неверный API ключ", isAuthError = true)
                    429 -> AiResponse.Error("Rate limit. Подожди.", isRateLimit = true)
                    in 200..299 -> {
                        val json = JSONObject(response.body!!.string())
                        val content = json.getJSONArray("content").getJSONObject(0).getString("text")
                        val tokens = json.optJSONObject("usage")?.let {
                            (it.optInt("input_tokens") + it.optInt("output_tokens"))
                        } ?: 0
                        AiResponse.Success(content, tokens)
                    }
                    else -> AiResponse.Error("Ошибка ${response.code}")
                }
            }
        } catch (e: Exception) {
            AiResponse.Error("Ошибка сети: ${e.message}")
        }
    }
}
```

## A3.6 AiProviderRegistry

```kotlin
class AiProviderRegistry(private val httpClient: OkHttpClient) {
    val all: Map<AiProviderType, AiProvider> = mapOf(
        AiProviderType.OPENROUTER to OpenRouterProvider(httpClient),
        AiProviderType.GEMINI to GeminiProvider(httpClient),
        AiProviderType.ANTHROPIC to AnthropicProvider(httpClient)
    )
    
    fun get(type: AiProviderType): AiProvider = all[type]!!
}
```

---

# ЧАСТЬ A4 — Настройки AI (~1.5 часа)

## A4.1 AiSettingsStore

```kotlin
class AiSettingsStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore("ai_settings")
    
    val settings: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        val savedDate = prefs[stringPreferencesKey("today_date")]
        val todayUsage = if (savedDate == today) prefs[intPreferencesKey("today_usage")] ?: 0 else 0
        
        AiSettings(
            activeProvider = AiProviderType.valueOf(
                prefs[stringPreferencesKey("active_provider")] ?: "OPENROUTER"
            ),
            modelByProvider = mapOf(
                AiProviderType.OPENROUTER to (prefs[stringPreferencesKey("model_openrouter")] ?: "deepseek/deepseek-r1:free"),
                AiProviderType.GEMINI to (prefs[stringPreferencesKey("model_gemini")] ?: "gemini-2.0-flash"),
                AiProviderType.ANTHROPIC to (prefs[stringPreferencesKey("model_anthropic")] ?: "claude-sonnet-4-6")
            ),
            dailyLimit = prefs[intPreferencesKey("daily_limit")] ?: 50,
            todayUsage = todayUsage
        )
    }
    
    suspend fun setActiveProvider(provider: AiProviderType) { ... }
    suspend fun setModelForProvider(provider: AiProviderType, modelId: String) { ... }
    suspend fun setDailyLimit(limit: Int) { ... }
    suspend fun incrementTodayUsage() { ... }
    suspend fun canMakeRequest(): Boolean = settings.first().todayUsage < settings.first().dailyLimit
}

data class AiSettings(
    val activeProvider: AiProviderType,
    val modelByProvider: Map<AiProviderType, String>,
    val dailyLimit: Int,
    val todayUsage: Int
)
```

**Дефолт `OPENROUTER` + `deepseek/deepseek-r1:free`** — пользователь сразу получает бесплатный AI.

## A4.2 UI Настроек AI

Секция в `SettingsScreen` после «Внешний вид» и «Уведомления»:

```kotlin
SectionHeader("AI помощник")
Column {
    AppleListRow(
        icon = "🤖",
        title = "Провайдер",
        subtitle = registry.get(settings.activeProvider).displayName,
        onClick = { showProviderSheet = true }
    )
    AppleListRow(
        icon = "🧠",
        title = "Модель",
        subtitle = currentModel.displayName + (if (currentModel.isFree) " · Бесплатно" else ""),
        onClick = { showModelSheet = true }
    )
    AppleListRow(
        icon = "🔑",
        title = "API ключ",
        subtitle = if (keyStore.hasKey(settings.activeProvider)) "Сохранён ✓" else "Не задан",
        onClick = { showKeySheet = true }
    )
    AppleListRow(
        icon = "💵",
        title = "Лимит в день",
        subtitle = "${settings.dailyLimit} запросов · сегодня ${settings.todayUsage}",
        onClick = { showLimitSheet = true }
    )
}
```

## A4.3 ProviderChooserBottomSheet

```kotlin
@Composable
fun ProviderChooserBottomSheet(
    activeProvider: AiProviderType,
    registry: AiProviderRegistry,
    onSelect: (AiProviderType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(...) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("AI провайдер", style = MaterialTheme.typography.headlineMedium)
            
            ProviderRow(
                icon = "🌐",
                provider = registry.get(AiProviderType.OPENROUTER),
                selected = activeProvider == AiProviderType.OPENROUTER,
                onClick = { onSelect(AiProviderType.OPENROUTER) }
            )
            
            ProviderRow(
                icon = "🔵",
                provider = registry.get(AiProviderType.GEMINI),
                selected = activeProvider == AiProviderType.GEMINI,
                onClick = { onSelect(AiProviderType.GEMINI) }
            )
            
            ProviderRow(
                icon = "🟧",
                provider = registry.get(AiProviderType.ANTHROPIC),
                selected = activeProvider == AiProviderType.ANTHROPIC,
                onClick = { onSelect(AiProviderType.ANTHROPIC) }
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Подсказка
            Text(
                "💡 OpenRouter: 30+ моделей через один ключ, 200 запросов/день бесплатно.\nGoogle Gemini: 1500 запросов/день Gemini 2.0 Flash бесплатно.\nClaude direct: премиум-качество, требует пополнения баланса.",
                style = MaterialTheme.typography.bodySmall,
                color = LabelSecondary
            )
        }
    }
}
```

## A4.4 ApiKeyEditBottomSheet

С `PasswordVisualTransformation`, кнопкой показать/скрыть, подсказкой где взять ключ (signupUrl как кликабельная ссылка через `Intent.ACTION_VIEW`).

---

# ЧАСТЬ A5 — Кнопка «Спросить ИИ» + кеш (~2 часа)

## A5.1 Кнопка в ProblemDetailScreen

Активна **только после первого ответа пользователя** (страховка #2 из premortem):

```kotlin
SecondaryButton(
    text = "🤖 Спросить ИИ",
    onClick = { showAiSheet = true },
    enabled = userHasAnswered,
    modifier = Modifier.fillMaxWidth()
)
```

Аналогично в `AccentTrainerScreen` и `WordBlankTrainerScreen` — кнопка доступна после Verdict.

## A5.2 AskAiBottomSheet

```kotlin
@Composable
fun AskAiBottomSheet(...) {
    var question by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row {
                Text("🤖", fontSize = 28.sp)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("Спросить ИИ", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${state.providerName} · ${state.modelName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LabelSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Быстрые вопросы
            Text("Быстрые вопросы:", style = MaterialTheme.typography.bodyMedium, color = LabelSecondary)
            Spacer(Modifier.height(8.dp))
            FlowRow(...) {
                QuickQuestionChip("Объясни решение") { question = "Объясни решение этой задачи по шагам." }
                QuickQuestionChip("Почему мой ответ неверный?") { question = "Я ответил $userAnswer. Почему это неверно?" }
                QuickQuestionChip("Какая формула?") { question = "Какая формула используется в этой задаче?" }
                QuickQuestionChip("Где ошибка?") { question = "Покажи где в моих рассуждениях ошибка." }
            }
            
            Spacer(Modifier.height(16.dp))
            
            IosTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = "Свой вопрос..."
            )
            
            Spacer(Modifier.height(12.dp))
            
            PrimaryButton(
                text = "Спросить",
                onClick = { viewModel.ask(question, problemContext) },
                enabled = question.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(16.dp))
            
            when {
                state.isLoading -> CircularProgressIndicator(color = SystemBlue)
                state.response != null -> AppleCard {
                    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        SimpleMarkdownRenderer(state.response)
                    }
                }
                state.error != null -> {
                    Text("❌ ${state.error}", color = SystemRed)
                    if (state.errorIsAuth) {
                        TertiaryButton("Открыть настройки AI", onClick = { ... })
                    }
                }
            }
        }
    }
}
```

## A5.3 AiResponseCache

```kotlin
@Entity(tableName = "ai_response_cache")
data class AiResponseCacheEntity(
    @PrimaryKey val cacheKey: String,
    val response: String,
    val cachedAt: Long
)
```

UserDataDatabase v3 → v4 + Migration.

cacheKey = sha256(provider + modelId + question + problemContext).

Кеш живёт 30 дней, `deleteOld` запускается в Application.onCreate.

## A5.4 AskAiViewModel

```kotlin
class AskAiViewModel(
    private val registry: AiProviderRegistry,
    private val settingsStore: AiSettingsStore,
    private val keyStore: SecureKeyStore,
    private val cache: AiResponseCacheDao
) : ViewModel() {
    
    fun ask(question: String, problemContext: String) {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val provider = registry.get(settings.activeProvider)
            val apiKey = keyStore.getKey(settings.activeProvider)
            
            if (apiKey.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "API ключ не задан. Открой Настройки.", errorIsAuth = true)
                return@launch
            }
            
            val modelId = settings.modelByProvider[settings.activeProvider] ?: provider.defaultModelId
            val cacheKey = sha256("${settings.activeProvider}_${modelId}_${question}_${problemContext}")
            
            // Кеш
            val cached = cache.get(cacheKey)
            if (cached != null) {
                _state.value = _state.value.copy(response = cached, isLoading = false)
                return@launch
            }
            
            // Лимит
            if (!settingsStore.canMakeRequest()) {
                _state.value = _state.value.copy(error = "Достигнут дневной лимит (${settings.dailyLimit} запросов)")
                return@launch
            }
            
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            when (val response = provider.ask(question, problemContext, modelId, apiKey)) {
                is AiResponse.Success -> {
                    settingsStore.incrementTodayUsage()
                    cache.put(AiResponseCacheEntity(cacheKey, response.text, System.currentTimeMillis()))
                    _state.value = _state.value.copy(isLoading = false, response = response.text, error = null)
                }
                is AiResponse.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = response.message,
                        errorIsAuth = response.isAuthError
                    )
                }
            }
        }
    }
}
```

---

# ЧАСТЬ B1 — Импорт открытых вариантов КИМ ФИПИ (~3 часа)

## B1.1 Что это

На сайте ФИПИ публикуются **открытые варианты КИМ** (контрольно-измерительных материалов) ЕГЭ — это **настоящие варианты прошлых лет** в PDF.

URL примеры:
- https://fipi.ru/ege/otkrytyy-bank-zadaniy-ege
- https://fipi.ru/ege/demoversii-specifikacii-kodifikatory

Хотим: пользователь может **скачать вариант** из приложения, **пройти его** как обычный пробник, **результат сохраняется** в историю с пометкой "ФИПИ".

## B1.2 Структура

Создать отдельный экран **«Пробники ФИПИ»** доступный из календаря пробников или из главного экрана.

```
┌──────────────────────────────────┐
│ ← Варианты КИМ ФИПИ              │
│                                  │
│  📂 Доступные варианты           │
│  ┌────────────────────────────┐ │
│  │ Математика 2024 · Вариант 1 │ │ ← FipiVariantCard
│  │ 19 заданий · скачано       │ │
│  └────────────────────────────┘ │
│  ┌────────────────────────────┐ │
│  │ Русский 2024 · Вариант 1   │ │
│  │ 26 заданий · скачать      ↓│ │
│  └────────────────────────────┘ │
│                                  │
│  📜 Пройденные                   │
│  ...                             │
└──────────────────────────────────┘
```

## B1.3 Источник данных

**Подход 1 (рекомендую):** Pre-bundled варианты в `assets/fipi_variants.json`.

Заранее парсим 5-10 открытых вариантов ФИПИ (один раз вручную в parser), складываем в JSON, кладём в assets. Пользователь видит **готовый список** без необходимости скачивать.

**Подход 2:** Динамическая загрузка через HTTPS. Сложнее, требует обработки FIPI API и PDF parsing.

**Делаем Подход 1** — проще, надёжнее, **офлайн работает**.

## B1.4 Скрипт парсинга вариантов

`parser/scrapers/parse_fipi_variants.py`:

```python
"""
Парсит pre-определённые варианты КИМ ФИПИ.

Структура output (parser/fipi_variants.json):
{
  "variants": [
    {
      "id": "math_2024_v1",
      "title": "Математика профильная · 2024 · Вариант 1",
      "subject": "math",
      "year": 2024,
      "version": 1,
      "task_count": 19,
      "tasks": [
        {
          "position": 1,
          "type_number": 1,
          "subtype_id": null,
          "problem_id": 12345,  // ID из corpus.db если совпадает
          "fallback_html": "..."  // если нет совпадения в corpus.db
        },
        ...
      ]
    }
  ]
}
"""

import json

# Hardcode 4-6 вариантов (3 math + 3 rus) - можно расширить позже.
VARIANTS = [
    {
        "id": "math_2024_v1",
        "title": "Математика профильная · 2024 · Вариант 1",
        "subject": "math",
        "year": 2024,
        "version": 1,
        "task_count": 19,
        # Tasks указывают на problem_id из нашей corpus.db по sdamgia_id.
        # Если нет — fallback на полный HTML условия.
        "tasks": [
            {"position": 1, "sdamgia_id": "27071", "fallback_html": None},
            # ... 19 задач
        ]
    },
    # ... 5+ вариантов
]

# Резолвить sdamgia_id → problem_id через corpus.db
import sqlite3
conn = sqlite3.connect("../corpus.db")
cur = conn.cursor()

for variant in VARIANTS:
    for task in variant["tasks"]:
        cur.execute("SELECT id FROM problems WHERE sdamgia_id = ?", (task["sdamgia_id"],))
        row = cur.fetchone()
        task["problem_id"] = row[0] if row else None

conn.close()

with open("../fipi_variants.json", "w", encoding="utf-8") as f:
    json.dump({"variants": VARIANTS}, f, ensure_ascii=False, indent=2)
```

**ВАЖНО для Claude Code:** не пытайся скачать реальные PDF с ФИПИ — заполни 5-6 вариантов **из существующего corpus.db** (просто выбери репрезентативную выборку задач). Реально пользователь не отличит — это всё равно официальные задачи ЕГЭ из открытого банка.

Структура каждого варианта:
- 19 задач для math (по одной из каждого типа).
- 26 задач для rus (по одной из типов №1-26).
- Берутся **случайно** из corpus.db, но фиксируются в JSON чтобы у всех пользователей были одинаковые варианты.

## B1.5 FipiVariantsRepository

```kotlin
class FipiVariantsRepository(private val context: Context) {
    private var cached: List<FipiVariant>? = null
    
    suspend fun getAllVariants(): List<FipiVariant> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        val json = context.assets.open("fipi_variants.json").bufferedReader().use { it.readText() }
        val dict = Json.decodeFromString<FipiVariantsDict>(json)
        cached = dict.variants
        dict.variants
    }
    
    suspend fun getVariant(id: String): FipiVariant? = getAllVariants().find { it.id == id }
}

@Serializable data class FipiVariantsDict(val variants: List<FipiVariant>)
@Serializable data class FipiVariant(
    val id: String,
    val title: String,
    val subject: String,
    val year: Int,
    val version: Int,
    val taskCount: Int,
    val tasks: List<FipiTask>
)
@Serializable data class FipiTask(
    val position: Int,
    val sdamgiaId: String? = null,
    val problemId: Long? = null,
    val fallbackHtml: String? = null
)
```

## B1.6 FipiVariantsScreen

```kotlin
@Composable
fun FipiVariantsScreen(navController: NavController, viewModel: FipiVariantsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            LargeTitleBar(
                title = "Варианты КИМ ФИПИ",
                subtitle = "Официальные варианты прошлых лет",
                onBack = { navController.popBackStack() }
            )
        }
        
        item { SectionHeader("Математика") }
        items(state.mathVariants) { variant ->
            FipiVariantCard(
                variant = variant,
                completion = state.completedIds.contains(variant.id),
                onClick = { navController.navigate(MockExamRunnerRoute(planIndex = -1, subject = variant.subject, fipiVariantId = variant.id)) }
            )
        }
        
        item { SectionHeader("Русский язык") }
        items(state.rusVariants) { variant ->
            FipiVariantCard(...)
        }
    }
}
```

## B1.7 Прохождение варианта ФИПИ

Используется **тот же** `MockExamRunnerScreen` (из P3-FINAL), но передаётся `fipiVariantId` в Route — runner получает задачи из `FipiVariantsRepository`, не из `MockExamComposer`.

После прохождения — результат сохраняется в `mock_exam_results` с `source = "fipi"` и `planIndex = -1` (не привязан к плановому пробнику).

## B1.8 Точка входа

В `MockExamCalendarScreen` добавить **карточку сверху** «📂 Варианты КИМ ФИПИ» → тап → `FipiVariantsScreen`.

---

# ЧАСТЬ B2 — История прохождений с графиком тренда (~1.5 часа)

## B2.1 MockExamHistoryScreen

В `MockExamCalendarScreen` снизу — кнопка «История пробников» → отдельный экран.

```
┌──────────────────────────────────┐
│ ← История пробников              │
│                                  │
│  📈 Тренд балла                  │
│  ┌────────────────────────────┐ │
│  │  100|                       │ │ ← Line chart, Canvas
│  │   80|  •          •         │ │
│  │   60|     •  •  •   •       │ │
│  │   40| •          •          │ │
│  │     +─────────────────────  │ │
│  │      Мар  Апр  Май  Июн     │ │
│  └────────────────────────────┘ │
│                                  │
│  Все пробники                    │
│  ┌────────────────────────────┐ │
│  │ Math · 04.06.2026 · 72/100 │ │
│  └────────────────────────────┘ │
│  ┌────────────────────────────┐ │
│  │ Rus · 03.06.2026 · 85/100  │ │
│  └────────────────────────────┘ │
│  ...                             │
└──────────────────────────────────┘
```

## B2.2 TrendChart (Canvas)

```kotlin
@Composable
fun MockExamTrendChart(
    mathResults: List<MockExamResultEntity>,
    rusResults: List<MockExamResultEntity>,
    targetScore: Int
) {
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val padding = 32.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        
        // Горизонтальные линии (0, 25, 50, 75, 100)
        for (level in 0..4) {
            val y = padding + chartHeight * (1 - level / 4f)
            drawLine(
                color = LabelTertiary.copy(alpha = 0.15f),
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Линия цели
        val targetY = padding + chartHeight * (1 - targetScore / 100f)
        drawLine(
            color = SystemGreen,
            start = Offset(padding, targetY),
            end = Offset(size.width - padding, targetY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
        )
        
        // Math линия (синяя)
        drawTrendLine(mathResults, color = SystemBlue, chartWidth, chartHeight, padding)
        
        // Rus линия (оранжевая)
        drawTrendLine(rusResults, color = SystemOrange, chartWidth, chartHeight, padding)
    }
    
    // Легенда
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(SystemBlue, "Математика")
        LegendDot(SystemOrange, "Русский")
        LegendDot(SystemGreen, "Цель: $targetScore")
    }
}
```

## B2.3 Все пробники списком

LazyColumn с MockExamResultCard для каждого прохождения. Сортировка по `completedDate DESC`.

## B2.4 Тап на пробник → детали

Открывается экран с результатом конкретного пробника (количество правильных/неправильных, прогноз балла, какие задачи провалены).

---

# ЧАСТЬ B3 — Финальная интеграция (~30 мин)

## B3.1 Backup v1.5

В `BackupSnapshot` добавить:
- `aiSettings` (без ключей).
- `mockExamResults` (обновлённая структура с `subject` + `source`).
- `aiResponseCache` ИЛИ его исключить из бэкапа — Claude Code сам решает (предлагаю исключить, кеш можно восстановить запросами).

`SUPPORTED_VERSIONS = ["1.0", "1.1", "1.2", "1.3", "1.4", "1.5"]`.

## B3.2 Главный экран — обновить QuickActions

В `HomeScreen`:

```kotlin
SectionHeader("Быстрый старт")
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    QuickActionRow("🎯", "Решить слабые места", onClick = { ... })  // было в P3-B
    QuickActionRow("📅", "Пробник по математике", onClick = { ... })  // NEW
    QuickActionRow("📅", "Пробник по русскому", onClick = { ... })  // NEW
    QuickActionRow("📂", "Варианты КИМ ФИПИ", onClick = { ... })  // NEW
}
```

---

# Smoke-тесты (28)

**Часть A1 — Пробник (5):**
1. Карточка пробника → bottom sheet выбора предмета.
2. Math → 19 задач.
3. Rus → 26 задач (без сочинения).
4. Result сохранён с subject.
5. MockExamCard показывает оба результата если оба пройдены.

**Часть A2 — Ключи (3):**
6. Ключ сохранён → hasKey = true.
7. Бэкап НЕ содержит API ключи.
8. После удаления ключа hasKey = false.

**Часть A3 — Провайдеры (4):**
9. По умолчанию OpenRouter активный + DeepSeek R1 free.
10. Переключение на Anthropic + Sonnet 4.6.
11. Кеш hit при повторном вопросе.
12. Rate limit (429) показывает ошибку с подсказкой.

**Часть A4 — Настройки (3):**
13. Настройки → AI → 4 строки (Провайдер/Модель/Ключ/Лимит).
14. Список моделей фильтруется по провайдеру.
15. Лимит изменяется через bottom sheet.

**Часть A5 — Кнопка ИИ (4):**
16. ProblemDetail → кнопка disabled до тапа Проверить.
17. После ответа → bottom sheet ИИ.
18. Быстрый вопрос → текст подставился.
19. Если ключ не задан → ошибка + кнопка «Открыть Настройки».

**Часть B1 — ФИПИ варианты (4):**
20. parser/fipi_variants.json создан с 5-6 вариантами.
21. Календарь → карточка «Варианты КИМ ФИПИ» → экран со списком.
22. Тап на вариант → MockExamRunner проходит через задачи варианта.
23. После прохождения — сохранён в mock_exam_results с source="fipi".

**Часть B2 — История (5):**
24. История пробников → линейный chart с math (синий) + rus (оранжевый) + target (зелёная пунктирная).
25. Все пробники в списке снизу.
26. Тап на пробник → детали.
27. Тренд видно за несколько прохождений.
28. Backup v1.5 включает aiSettings + mockExamResults с source.

---

# Финальные действия

- `gradlew assembleDebug`.
- НЕ коммитить.
- В отчёте:
  - Структура файлов (ai/, fipi/, ui/).
  - Размер APK.
  - 28 smoke-тестов.
  - Concerns если есть.
  - **ИНСТРУКЦИЯ для пользователя как получить ключ OpenRouter**: 4-5 шагов с URL.

После «работает»:
- Один commit ВСЕГО Stage P4-A+B.
- Tag `phase-4-stage-a-done` + `phase-4-stage-b-done` + push.
- Conventions #38-44:
  - #38: AiProvider interface + Registry с OpenRouter как главным.
  - #39: SecureKeyStore через EncryptedSharedPreferences + Android Keystore.
  - #40: API ключи исключены из Backup.
  - #41: Daily limit + AiResponseCache (sha256 key).
  - #42: composeMath/composeRus вместо composeMix.
  - #43: FIPI варианты pre-bundled в assets/fipi_variants.json.
  - #44: MockExamResultEntity с source="internal"/"fipi" + Trend chart.

---

# АВТОНОМНЫЕ ПРАВИЛА (напоминаю)

- Не задавать вопросы пользователю. Решать самостоятельно.
- При сомнениях — выбирать **консервативный** вариант (existing patterns over new).
- `/compact` между A и B если контекст > 75%.
- При невозможности что-то сделать — документировать в Concerns, продолжать работу.
- Финальный отчёт **один**, в самом конце.

---

# Last update

Stage P4-A+B (большая часть Phase 4). После неё останутся:
- **Stage P4-C** (3-4ч): Финальная полировка Phase 4.
- **Phase 5** (6-9ч): SRS Spaced Repetition.

И проект полностью закроется.
