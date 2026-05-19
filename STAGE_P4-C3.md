# STAGE_P4-C3.md — Иконка приложения + iOS-style плавность

> Финальная косметика Phase 4. После — Phase 4 закрывается полностью, и идём в Phase 5 (SRS).
>
> Состав:
> - **Часть А** — Замена иконки приложения (mipmap + adaptive icon).
> - **Часть Б** — iOS-style плавный fling scroll везде в приложении.
> - **Часть В** — Edge swipe back (свайп от левого края → назад).
> - **Часть Г** — Свайп между задачами (влево/вправо → след/пред).

---

## Что работает (НЕ ломать)

- Phase 1 + Phase 2 + Phase 3 + Phase 4 Stage A+B+C+C2.
- Текущий NavHost с spring transitions (Convention #51).
- AI-кнопки в задачах и тренажёрах.
- LetterChoiceRow в №9-12.
- AnswerChecker, LatexCleaner, размер формул.
- Размер APK 230.8 MB (рост ожидается +0.5-1 MB из-за иконки в 5 размерах).

---

# ЧАСТЬ А — Замена иконки приложения

## А1. Исходник

Пользователь положил файл `app_icon_source.png` в корень проекта (`C:\Projects\ege-app\app_icon_source.png`).

Это **широкоформатная картинка ~3000x1500px** с центральной композицией: золотое число «100» с лучами на тёмно-синем фоне с созвездиями и формулами.

## А2. Проблема исходника для иконки

Иконки Android должны быть **квадратными** и **читаемыми на 48×48px**. Текущая картинка:
- **Горизонтальная** (2:1 пропорции).
- **Перегружена деталями** (формулы, созвездия).
- В малом размере «100» будет видно, но окружение превратится в шум.

## А3. Решение — adaptive icon с кропом

Создать **adaptive icon** (Android 8.0+ требование):
- **Background layer** — квадратный кроп центральной области с созвездиями и тёмным фоном.
- **Foreground layer** — только «100» с лучами на прозрачном фоне (центральная область картинки).

Foreground будет анимироваться/масштабироваться системой при свайпе или нажатии — это нативный Android UX.

## А4. Шаги для Claude Code

### А4.1 Создать рабочую папку

```bash
mkdir -p android/app/src/main/res/mipmap-anydpi-v26
mkdir -p android/app/src/main/res/drawable
```

### А4.2 Сделать background и foreground PNG через ImageMagick (или Python PIL)

Если ImageMagick доступен в системе:

```bash
# Background — квадратный кроп центра с тёмным фоном и созвездиями
magick app_icon_source.png -gravity center -crop 1500x1500+0+0 -resize 1024x1024 \
       android/app/src/main/res/drawable/ic_launcher_background.png

# Foreground — извлечь центральную область с "100" и лучами,
# но затемнить/удалить созвездия чтобы остался только символ.
# Прозрачный фон для adaptive icon.
magick app_icon_source.png -gravity center -crop 800x800+0+0 -resize 1024x1024 \
       -channel A -threshold 50% -alpha set \
       android/app/src/main/res/drawable/ic_launcher_foreground.png
```

Если ImageMagick недоступен — использовать Python с PIL/Pillow:

```python
# parser/scripts/generate_icons.py
from PIL import Image, ImageOps

src = Image.open("app_icon_source.png")
w, h = src.size

# Квадратный кроп по центру
side = min(w, h)
left = (w - side) // 2
top = (h - side) // 2
square = src.crop((left, top, left + side, top + side))

# Background — полный кроп с фоном
background = square.resize((1024, 1024), Image.LANCZOS)
background.save("android/app/src/main/res/drawable/ic_launcher_background.png")

# Foreground — только центральная область
center = side // 2
foreground = square.crop(
    (side//2 - center//2, side//2 - center//2,
     side//2 + center//2, side//2 + center//2)
).resize((1024, 1024), Image.LANCZOS)
foreground.save("android/app/src/main/res/drawable/ic_launcher_foreground.png")
```

### А4.3 Adaptive icon XML

`android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

Аналогично `ic_launcher_round.xml` (тот же контент).

### А4.4 Сгенерировать 5 размеров mipmap

Для устройств без поддержки adaptive icon (Android < 8.0) — сгенерировать классические PNG в mipmap-*:

```python
# generate_icons.py продолжение
sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

# Композитная иконка (foreground поверх background)
composite = Image.alpha_composite(background.convert("RGBA"), foreground.convert("RGBA"))

for density, size in sizes.items():
    icon = composite.resize((size, size), Image.LANCZOS)
    out_dir = f"android/app/src/main/res/mipmap-{density}"
    os.makedirs(out_dir, exist_ok=True)
    icon.save(f"{out_dir}/ic_launcher.png")
    icon.save(f"{out_dir}/ic_launcher_round.png")
```

### А4.5 Удалить старые иконки

Удалить все `mipmap-*/ic_launcher.webp` (или старые PNG), оставив только новые.

### А4.6 AndroidManifest.xml

Проверить что прописано:

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ...>
```

Скорее всего уже есть, не трогать.

### А4.7 Проверка

После пересборки APK иконка должна:
- Появиться на главном экране Android вместо старой.
- Быть **читаемой** — «100» видно даже в маленьком размере.
- Корректно работать на adaptive icon устройствах (мягкая анимация при тапе).

## А5. Если результат не идеален

Это **итеративный процесс**. После теста на Samsung возможны правки:
- «Слишком тёмно» → увеличить контраст foreground.
- «100 слишком маленькое» → больше zoom центра.
- «Формулы не видны» → backround оставить как было (полный кроп).

Не страшно перегенерить, это 2 минуты работы.

---

# ЧАСТЬ Б — iOS-style плавный fling scroll

## Б1. Что делает iOS со скроллом

На iPhone скролл имеет:
- **Высокий impulse** на старте свайпа.
- **Экспоненциальное замедление** (decay).
- **Длительную инерцию** — список долго едет после отпускания пальца.

В Compose по умолчанию `LazyColumn` использует Android-стиль:
- **Резкий impulse**.
- **Линейное замедление**.
- **Быстрая остановка**.

Это **другой feel** — пользователю, привыкшему к iOS, кажется «дёрганым».

## Б2. Решение — кастомный flingBehavior

В Compose есть `rememberSplineBasedDecay()` который генерирует **iOS-like decay**.

`ui/common/IosFlingBehavior.kt`:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberIosFlingBehavior(): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(decay) {
        IosFlingBehavior(decay)
    }
}

private class IosFlingBehavior(
    private val flingDecay: DecayAnimationSpec<Float>
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // Снижаем минимальный порог — даже маленькие свайпы дают плавный fling
        if (kotlin.math.abs(initialVelocity) <= 1f) return initialVelocity
        
        var velocityLeft = initialVelocity
        var lastValue = 0f
        
        // Усиливаем impulse в 1.3x для iOS-feel
        val boostedVelocity = initialVelocity * 1.3f
        
        AnimationState(
            initialValue = 0f,
            initialVelocity = boostedVelocity
        ).animateDecay(flingDecay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = this.velocity
            
            // Останавливаем если упёрлись
            if (kotlin.math.abs(delta - consumed) > 0.5f) {
                this.cancelAnimation()
            }
        }
        
        return velocityLeft
    }
}
```

## Б3. Применение

Заменить в **каждом `LazyColumn` приложения**:

```kotlin
LazyColumn(
    flingBehavior = rememberIosFlingBehavior(),  // ← NEW
    ...
)
```

Места где есть `LazyColumn`:
- `HomeScreen` — главный экран.
- `ProblemListScreen` — список задач каталога.
- `JournalScreen` — журнал ошибок + статистика.
- `MockExamCalendarScreen` — календарь пробников.
- `MockExamHistoryScreen` — история пробников.
- `FipiVariantsScreen` — варианты ФИПИ.
- `SettingsScreen` — настройки.
- `ProfileScreen` — профиль.
- Любые другие вертикальные списки.

## Б4. Также для `Column` с verticalScroll

Если есть `Column(modifier = Modifier.verticalScroll(rememberScrollState()))` — это **обычный Column со скроллом**, не LazyColumn. Для него:

```kotlin
Column(
    modifier = Modifier.verticalScroll(
        state = rememberScrollState(),
        flingBehavior = rememberIosFlingBehavior()
    )
)
```

Не уверен есть ли поддержка `flingBehavior` в `verticalScroll` — если API не позволяет, оставить как есть, это меньше критично.

---

# ЧАСТЬ В — Edge swipe back

## В1. Что это

iOS-фича: свайп **от левого края экрана вправо** → возврат на предыдущий экран. Не работает свайп в середине экрана — только от самого края (это важно, иначе ломает контент).

Android по умолчанию **не имеет** этой функции в Compose. Нужно реализовать.

## В2. Реализация через `pointerInput` и `Predictive Back`

Android 14+ имеет **Predictive Back Gesture** — нативная поддержка swipe back. Но она требует включения в манифесте:

`AndroidManifest.xml`:

```xml
<application
    android:enableOnBackInvokedCallback="true"
    ...>
```

После этого:
- На Android 14+ → системная gesture (та что уже есть в Samsung — свайп от любого края).
- На Android < 14 → нужно реализовать вручную.

## В3. Кастомная реализация для всех Android версий

`ui/common/EdgeSwipeBack.kt`:

```kotlin
@Composable
fun EdgeSwipeBack(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val edgeWidth = with(density) { 24.dp.toPx() }  // зона свайпа — 24dp слева
    val swipeThreshold = with(density) { 100.dp.toPx() }  // для срабатывания нужно протянуть 100dp
    
    var dragDistance by remember { mutableStateOf(0f) }
    var startedFromEdge by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startedFromEdge = offset.x <= edgeWidth
                        dragDistance = 0f
                    },
                    onDragEnd = {
                        if (startedFromEdge && dragDistance > swipeThreshold) {
                            onBack()
                        }
                        dragDistance = 0f
                        startedFromEdge = false
                    },
                    onDragCancel = {
                        dragDistance = 0f
                        startedFromEdge = false
                    },
                    onHorizontalDrag = { _, drag ->
                        if (startedFromEdge && drag > 0) {
                            dragDistance += drag
                        }
                    }
                )
            }
    ) {
        content()
        
        // Визуальный индикатор свайпа (опционально) — затемнение или стрелка
        if (startedFromEdge && dragDistance > 0) {
            val progress = (dragDistance / swipeThreshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((dragDistance / density.density).dp.coerceAtMost(80.dp))
                    .background(LabelTertiary.copy(alpha = 0.1f * progress))
                    .align(Alignment.CenterStart)
            )
        }
    }
}
```

## В4. Применение

Обернуть **каждый детальный экран** в `EdgeSwipeBack`:

```kotlin
@Composable
fun ProblemDetailScreen(...) {
    EdgeSwipeBack(onBack = { navController.popBackStack() }) {
        // ... текущая реализация
    }
}
```

Места применения:
- `ProblemDetailScreen`.
- `ProblemListScreen`.
- `StatsScreen`.
- `ErrorsListScreen`.
- `MockExamCalendarScreen`.
- `MockExamDetailScreen`.
- `MockExamHistoryScreen`.
- `FipiVariantsScreen`.
- `SettingsScreen`.
- `RuleDetailScreen` (если есть).
- Все экраны с шапкой и кнопкой ←.

**НЕ применять** на главных табах (Главная/Решать/Журнал/Профиль) — там некуда возвращаться, и свайп от края должен открывать боковое меню или ничего не делать.

---

# ЧАСТЬ Г — Свайп между задачами

## Г1. Что хочет пользователь

В `ProblemDetailScreen`:
- Свайп **влево** → следующая задача.
- Свайп **вправо** → предыдущая задача.

Это **критическая UX правка** — без неё нужно тянуться до низа за кнопкой «Далее →».

## Г2. Внимание — конфликт с EdgeSwipeBack

Edge swipe back (Часть В) тоже использует свайп вправо. Решение:
- **От самого края (0-24dp)** → возврат назад через `EdgeSwipeBack`.
- **От середины экрана (24dp+)** → переход между задачами.

Эти зоны **не пересекаются**, можно реализовать оба одновременно.

## Г3. Реализация

В `ProblemDetailScreen`:

```kotlin
val density = LocalDensity.current
val edgeWidth = with(density) { 24.dp.toPx() }
val taskSwipeThreshold = with(density) { 80.dp.toPx() }

var taskSwipeDistance by remember { mutableStateOf(0f) }
var taskSwipeStarted by remember { mutableStateOf(false) }

EdgeSwipeBack(onBack = { navController.popBackStack() }) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        // Только если НЕ от левого края (там EdgeSwipeBack)
                        taskSwipeStarted = offset.x > edgeWidth
                        taskSwipeDistance = 0f
                    },
                    onDragEnd = {
                        if (taskSwipeStarted) {
                            when {
                                taskSwipeDistance < -taskSwipeThreshold -> {
                                    // Свайп влево → следующая
                                    if (state.hasNext) viewModel.goNext()
                                }
                                taskSwipeDistance > taskSwipeThreshold -> {
                                    // Свайп вправо → предыдущая
                                    if (state.hasPrev) viewModel.goPrev()
                                }
                            }
                        }
                        taskSwipeDistance = 0f
                        taskSwipeStarted = false
                    },
                    onDragCancel = {
                        taskSwipeDistance = 0f
                        taskSwipeStarted = false
                    },
                    onHorizontalDrag = { _, drag ->
                        if (taskSwipeStarted) {
                            taskSwipeDistance += drag
                        }
                    }
                )
            }
            .graphicsLayer {
                // Визуальный сдвиг при свайпе (как iOS)
                translationX = taskSwipeDistance * 0.5f  // полу-следование пальцу
            }
    ) {
        // ... текущая реализация ProblemDetailScreen
    }
}
```

## Г4. Анимация перехода

При свайпе должна быть **плавная анимация ухода** старой задачи и **появления** новой:

В `MockExamComposer`-стиле уже была `AnimatedContent` (slide-in + fade) для QuickTrainer. Применить аналогично:

```kotlin
AnimatedContent(
    targetState = state.currentProblemId,
    transitionSpec = {
        if (targetState > initialState) {
            // вперёд: новая входит справа, старая уходит влево
            slideInHorizontally { it } + fadeIn() togetherWith
            slideOutHorizontally { -it } + fadeOut()
        } else {
            // назад: новая входит слева, старая уходит вправо
            slideInHorizontally { -it } + fadeIn() togetherWith
            slideOutHorizontally { it } + fadeOut()
        }.using(SizeTransform(clip = false))
    }
) { problemId ->
    // ... контент задачи
}
```

## Г5. Визуальные подсказки

При первом запуске — небольшой **onboarding tooltip**:
- «👈 Свайп — следующая задача».
- «👉 Свайп — предыдущая задача».

Сохранить флаг `shown_swipe_hint` в AppSettings чтобы показать один раз.

---

# Smoke-тесты

## Часть А — Иконка

| # | Что |
|---|---|
| 1 | После установки APK иконка приложения на главном экране Android — новая (золотое 100). |
| 2 | Иконка читаема на маленьком размере (например в недавних приложениях). |
| 3 | На Samsung One UI 6+ adaptive icon корректно отображается. |
| 4 | Иконка в задачах списка приложений (App Drawer) — новая. |

## Часть Б — Fling scroll

| # | Что |
|---|---|
| 5 | Главный экран → свайп пальцем вверх → плавный скролл с длительной инерцией. |
| 6 | ProblemList → быстрый flick свайп → список долго едет. |
| 7 | JournalScreen → плавная остановка после fling. |
| 8 | Скролл не «прилипает» к жёсткой остановке. |

## Часть В — Edge swipe back

| # | Что |
|---|---|
| 9 | ProblemDetail → свайп от левого края вправо → возврат на ProblemList. |
| 10 | Свайп от центра экрана вправо → НЕ возврат (это территория Части Г). |
| 11 | На Android 14+ работает системный predictive back. |
| 12 | На главных табах swipe back не активен. |

## Часть Г — Свайп между задачами

| # | Что |
|---|---|
| 13 | ProblemDetail → свайп влево → следующая задача. |
| 14 | Свайп вправо (НЕ от края) → предыдущая задача. |
| 15 | На последней задаче свайп влево → no-op (без эффекта или toast «последняя»). |
| 16 | На первой задаче свайп вправо (не от края) → no-op. |
| 17 | Slide-in анимация при переходе. |
| 18 | При первом запуске показывается onboarding tooltip про свайп. |

---

# Финальные действия

- `gradlew assembleDebug`.
- НЕ коммитить.
- В отчёте:
  - Размер APK.
  - Структура изменённых файлов.
  - 18 smoke-тестов.
  - Если иконка не получилась идеально — варианты для итерации.
  - Concerns (если что-то не сработало).

После «работает»:
- Один commit Stage P4-C3.
- Tag phase-4-stage-c3-done.
- Tag phase-4-done (закрытие ВСЕЙ Phase 4!).
- Push в GitHub.
- Conventions #60-63:
  - #60: adaptive icon с background + foreground PNG, 5 mipmap размеров.
  - #61: IosFlingBehavior через splineBasedDecay для всех LazyColumn.
  - #62: EdgeSwipeBack паттерн через pointerInput с 24dp edge zone.
  - #63: HorizontalDrag паттерн для перехода между задачами + AnimatedContent slide.

---

# Last update

После закрытия Stage P4-C3 — Phase 4 закроется полностью. Останется только Phase 5 (SRS Spaced Repetition).
