# STAGE_3_POLISH_3.md — Финальная полировка Stage 3

> После Stage 3 Polish 2 чертежи появились и большинство правок работают. Осталось 4 финальные правки, после которых Stage 3 закроется ОКОНЧАТЕЛЬНО.
>
> Время: 1-2 часа. Это **короткая итерация**.

---

## Что работает (НЕ ломать)

- ✅ Чертежи появились (Skрин: задача №1 «Вписанные окружности» с треугольником + окружностью).
- ✅ Формулы в условиях и решениях белые на тёмном фоне (ColorMatrix инверсия для `_formulas/`).
- ✅ Slogan-режим в тренажёре №4 (тап на слог → раскрытие → тап на гласную → verdict).
- ✅ Свайпы влево/вправо в тренажёре.
- ✅ Тренажёр в правильном месте (внутри типа №4 русского).
- ✅ 8 опций в категориях тренажёра (6 + Все слова + Все по алфавиту).
- ✅ Стрелка ← с тап-зоной 48dp.
- ✅ Apple-стиль базовый (Inter шрифт, тени, скругления).
- ✅ Размер APK 218 MB.

---

## Правка #1 (КРИТИЧНАЯ) — Иллюстрации тоже инвертировать (luminance)

### Проблема

На скриншоте 1 видно: чертёж треугольника **чёрный на чёрном фоне**, линии еле различимы. При этом оранжевая окружность видна.

В Polish 2 решено было **НЕ инвертировать иллюстрации** чтобы не испортить цветные элементы. Это было осторожное решение, но для чертежей геометрии (где линии чёрные) — неправильное.

### Решение — luminance inversion

Использовать **инверсию яркости** (luminance) вместо полной инверсии RGB:
- Чёрный → белый.
- Белый → чёрный.
- Серый → инвертированный серый.
- **Цветные элементы (оранжевый, красный, синий) сохраняют свой цвет.**

Это работает так: ColorMatrix преобразует яркость, не меняя оттенок.

```kotlin
// HtmlRenderer.kt или SvgLoader.kt

fun applyLuminanceInversion(bitmap: Bitmap): Bitmap {
    // Luminance inversion: инвертирует яркость, сохраняет цвет
    val luminanceInvertMatrix = ColorMatrix(floatArrayOf(
        // R канал
        0.213f - 1.213f, 0.715f - 0.715f, 0.072f - 0.072f, 0f, 255f,
        // G канал
        0.213f - 0.213f, 0.715f - 1.715f, 0.072f - 0.072f, 0f, 255f,
        // B канал
        0.213f - 0.213f, 0.715f - 0.715f, 0.072f - 1.072f, 0f, 255f,
        // Alpha канал (без изменений)
        0f, 0f, 0f, 1f, 0f
    ))
    
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(luminanceInvertMatrix)
    }
    
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

// Альтернативный (проще) подход: использовать готовую формулу
// для инверсии luminance в HSL пространстве:
fun applyLuminanceInversionSimple(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(width * height)
    result.getPixels(pixels, 0, width, 0, 0, width, height)
    
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val alpha = (pixel shr 24) and 0xFF
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        // Если пиксель достаточно "серый" (R≈G≈B), инвертируем
        // Если пиксель цветной — оставляем
        val maxDiff = maxOf(
            Math.abs(r - g),
            Math.abs(g - b),
            Math.abs(r - b)
        )
        
        val newPixel = if (maxDiff < 30) {
            // Серый/чёрный/белый - инвертируем
            val invR = 255 - r
            val invG = 255 - g
            val invB = 255 - b
            (alpha shl 24) or (invR shl 16) or (invG shl 8) or invB
        } else {
            // Цветной - оставляем как есть
            pixel
        }
        pixels[i] = newPixel
    }
    
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}
```

**Выбери реализацию которая работает лучше** на твоих тестовых задачах. Если первый подход через ColorMatrix даёт желаемый результат для большинства чертежей — используй его (быстрее). Если нет — простой попиксельный подход надёжнее.

### Применение

В `HtmlRenderer.kt`:

```kotlin
val shouldInvertLuminance = isDarkTheme && (
    assetPath.contains("_formulas/") ||  // формулы — инвертировать (как было)
    assetPath.matches(Regex("\\d+/img_\\d+\\..*"))  // иллюстрации — ТОЖЕ инвертировать
)
```

То есть теперь **все SVG в темной теме** проходят через luminance inversion. Цветные элементы (оранжевая окружность, цветные графики) сохраняются.

### Smoke-тест

1. Math №1 «Вписанные окружности» — треугольник должен быть **белыми линиями**, окружность остаётся оранжевой.
2. Math №3 Стереометрия — куб/пирамида должны быть с белыми линиями.
3. Math №18 Параметр — если есть цветной график (синяя линия), линия остаётся синей, оси/сетка белые.
4. Формулы — продолжают работать как раньше.

---

## Правка #2 — Упрощение тренажёра №4 (тап на слог = выбор)

### Проблема

Текущая логика: тап на слог → раскрытие → видны гласные внутри → тап на гласную → подсветка → второй тап → verdict.

Это **3-4 действия** на каждое слово.

Поскольку в нашем алгоритме syllabify **в каждом слоге одна гласная**, выбор гласной внутри слога — избыточен. Достаточно выбрать слог.

### Новая логика

```kotlin
sealed class SyllableTapState {
    object None : SyllableTapState()
    data class FirstTap(val syllableIndex: Int) : SyllableTapState()
    data class Verdict(
        val selectedSyllable: Int,
        val correctSyllable: Int,
        val isRight: Boolean
    ) : SyllableTapState()
}

fun onSyllableTap(syllableIndex: Int, syllables: List<Syllable>, word: AccentWord, state: SyllableTapState): SyllableTapState {
    val syllable = syllables[syllableIndex]
    
    return when (state) {
        is SyllableTapState.None -> SyllableTapState.FirstTap(syllableIndex)
        is SyllableTapState.FirstTap -> {
            if (state.syllableIndex == syllableIndex) {
                // 2-й тап на тот же слог = выбор
                val correctSyllableIndex = findSyllableContaining(syllables, word.stressed_index)
                SyllableTapState.Verdict(
                    selectedSyllable = syllableIndex,
                    correctSyllable = correctSyllableIndex,
                    isRight = syllableIndex == correctSyllableIndex
                )
            } else {
                // Тап на другой слог = новая подсветка
                SyllableTapState.FirstTap(syllableIndex)
            }
        }
        is SyllableTapState.Verdict -> state
    }
}

fun findSyllableContaining(syllables: List<Syllable>, letterIndex: Int): Int {
    return syllables.indexOfFirst { syl ->
        letterIndex in syl.startIndexInWord..syl.endIndexInWord
    }
}
```

### UI после правки

```
аэропорты → [а] [э] [ро] [пор] [ты]

Шаг 1: видны все слоги-кнопки.
Шаг 2: тап на [пор] → слог подсвечивается синим (FirstTap).
Шаг 3: тап на [пор] ещё раз → verdict.
   - Правильно: слог зеленеет, ✓ Верно, через 1 сек авто-переход.
   - Неверно: слог краснеет, правильный слог зеленеет, ✕ Неверно.

ВАЖНО: акут ´ над правильной буквой внутри правильного слога 
       должен сохраниться при verdict.
```

### Что удалить

- Код раскрытия слога (`expandedSyllable` state).
- Внутренние гласные-кнопки (40dp каждая).
- LetterTapState (заменяется на SyllableTapState).

### Что оставить

- Алгоритм syllabify (без изменений).
- Стиль слога-кнопки (60dp height, BgElevated2, corner 14dp, 32sp Bold).
- Свайпы между словами.
- Авто-переход через 1 сек.
- Запись ошибок в DataStore.

### Spec edge case — слог только из согласных

В словнике ФИПИ почти не бывает слогов без гласной (после syllabify). Но если попадётся (например, последний «закрытый» слог типа `ть` в инфинитиве — нет, там нет согласных-только слогов). На всякий случай: **если слог не содержит гласной, он НЕ тапаемый** (visual style: opacity 0.5).

---

## Правка #3 — Spring-slide анимации между экранами

### Проблема

При тапе ← или системного «назад» переход **мгновенный**. На iOS — slide-анимация: новый экран приезжает справа, текущий уезжает влево с лёгким fade.

### Решение

Использовать Compose Navigation 2.8+ animations API.

```kotlin
// EgeApp.kt — NavHost с animations

NavHost(
    navController = navController,
    startDestination = CatalogRoute,
    enterTransition = {
        slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialOffsetX = { fullWidth -> fullWidth }
        ) + fadeIn(animationSpec = tween(300))
    },
    exitTransition = {
        slideOutHorizontally(
            animationSpec = spring(...),
            targetOffsetX = { fullWidth -> -fullWidth / 3 }  // уезжает не полностью
        ) + fadeOut(animationSpec = tween(300))
    },
    popEnterTransition = {
        slideInHorizontally(
            animationSpec = spring(...),
            initialOffsetX = { fullWidth -> -fullWidth / 3 }  // приезжает слева частично
        ) + fadeIn(animationSpec = tween(300))
    },
    popExitTransition = {
        slideOutHorizontally(
            animationSpec = spring(...),
            targetOffsetX = { fullWidth -> fullWidth }  // уезжает полностью вправо
        ) + fadeOut(animationSpec = tween(300))
    }
) {
    composable<CatalogRoute> { ... }
    composable<TypesRoute> { ... }
    composable<SubtypesRoute> { ... }
    composable<ProblemListRoute> { ... }
    composable<ProblemDetailRoute> { ... }
    composable<AccentCategoriesRoute> { ... }
    composable<AccentTrainerRoute> { ... }
    composable<HomeStubRoute> { ... }
    composable<JournalStubRoute> { ... }
}
```

### Поведение

- **Forward navigation** (тап на карточку, идём вглубь):
  - Новый экран приезжает справа (full width).
  - Старый экран уезжает влево на 1/3 ширины (parallax-эффект).
  - Spring smooth, не bouncy.

- **Back navigation** (стрелка ← или системный back):
  - Текущий экран уезжает вправо (full width).
  - Предыдущий экран приезжает слева с 1/3 (parallax обратно).

- **Tab switching** в bottom bar:
  - **БЕЗ slide** (это не stack-навигация). Использовать `fadeIn + fadeOut` через tween 200ms.

### Совместимость

Compose Navigation 2.8.5 (уже подключён) поддерживает enterTransition/exitTransition по умолчанию. Если в твоём проекте используется более старая версия — обнови до 2.8.5.

---

## Правка #4 — Дополнительная полировка

### Закругления

Сейчас:
- Карточки 20dp.
- Кнопки 14dp.
- TextField 12dp.

Стало:
- Карточки **24dp** (чуть больше — мягче).
- Кнопки **16dp**.
- TextField **14dp**.
- Bottom sheets **28dp** (только верхние углы).

### Тени

Сейчас elevation 12dp с alpha 0.5. Сделать **более выразительные** для карточек условия и решения:

```kotlin
Modifier.shadow(
    elevation = 16.dp,
    shape = RoundedCornerShape(24.dp),
    spotColor = Color.Black.copy(alpha = 0.6f),
    ambientColor = Color.Black.copy(alpha = 0.2f)
)
```

### Spring везде

Глобально для всех scale-анимаций:

```kotlin
val standardSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = 0.01f
)
```

Применить ко:
- AppleButton press (scale 0.97 → 1.0).
- AppleCard press (scale 0.985 → 1.0).
- BottomTab активация (scale 1.0 → 1.1 → 1.0).
- Появление плашки результата (scale 0.85 → 1.0).
- Slog-кнопка в тренажёре при FirstTap (scale 1.0 → 1.04 → 1.0).
- Slog-кнопка при Verdict (scale 1.0 → 1.06 → 1.0).
- Иконка ✓/✕ в плашке (scale 0.5 → 1.2 → 1.0 bounce).

### Bottom-bar

- Высота 84dp (было 80dp).
- Активный таб: иконка + текст SystemBlue, **scale 1.1** при тапе с bounce.
- Иконки крупнее: 28dp (было 24dp).
- Под иконкой текст 11sp Medium.

### Поле ввода ответа в задачах

- При фокусе — лёгкая обводка SystemBlue 1.5dp + scale 1.01 spring.

### Размер шрифта в условии

Сейчас условие 17sp. Поднять до **18sp** с line-height 26sp — заметно читабельнее.

### Padding в карточках

- Между секцией «условие» и «ответ»: было 16dp, стало **24dp**.
- Внутри карточек: было 20dp, стало **22dp**.

---

## Smoke-тесты после итерации

| # | Что проверить |
|---|---|
| 1 | **Math №1 «Вписанные окружности»**: чертёж треугольника **белыми линиями**, окружность оранжевая. |
| 2 | **Math №3 Стереометрия**: куб/пирамида **белые линии**. |
| 3 | **Math №18 Параметр** (если есть цветные графики): цветные линии графика **сохраняют цвет**, оси/сетка белые. |
| 4 | Тренажёр существительных: первое слово «аэропорты» как `[а][э][ро][пор][ты]`. |
| 5 | Тап на слог `[пор]` → подсветка слога синим. **БЕЗ раскрытия гласной отдельно**. |
| 6 | Тап на `[пор]` ещё раз → verdict. Правильно → ✓, через 1 сек следующее слово. |
| 7 | Длинное слово «вероисповедание» → 8 слогов, тап → подсветка → второй тап → verdict. |
| 8 | Открываем задачу из каталога → новый экран **плавно приезжает справа**. |
| 9 | Тап ← в шапке → **плавный slide обратно**, предыдущий экран приезжает слева. |
| 10 | Между табами в bottom-bar → fade-переход (не slide). |
| 11 | Карточки выглядят **мягче** (24dp скругления). |
| 12 | Тапы на кнопки чувствуются плавнее, scale-spring везде. |

---

## Зависимости

Никаких новых не нужно.

---

## После итерации

- `gradlew assembleDebug`.
- **НЕ коммитить пока** — жду пользовательского «работает».
- В отчёте:
  - Какой подход к luminance inversion выбрал (ColorMatrix или попиксельный) и почему.
  - Что упрощено в SyllableTapState.
  - Чек-лист 12 пунктов выше.
  - Путь к APK, размер.

---

## Если что-то не получается

- Luminance inversion даёт странный результат на каких-то чертежах → пришли скриншот, скорректируем порог `maxDiff` или подход.
- Compose Navigation animations не работают → проверь версию `navigation-compose` (нужна 2.8+).
- Spring анимации тормозят → понизь stiffness.

---

## Last update

После Stage 3 Polish 2 — чертежи появились, основной функционал работает. Финальная полировка с luminance inversion для чертежей, упрощением тренажёра, slide-анимациями.
