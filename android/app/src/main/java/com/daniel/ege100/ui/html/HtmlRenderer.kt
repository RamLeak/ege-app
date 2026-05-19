package com.daniel.ege100.ui.html

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import com.caverock.androidsvg.SVG
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.Separator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.math.min

/**
 * Stage 3 polish: рендер sdamgia HTML с iOS-полировкой.
 *
 * Часть А STAGE_3_POLISH:
 *   А1 — принудительный цвет Label на каждый TextSegment; sanitize чистит
 *        inline `color:` чтобы sdamgia не подсовывал свой серый.
 *   А2 — новый классификатор inline/block по длине alt, наличию `=`, числу
 *        дробей и стрелкам перехода.
 *   А3 — inline height = baseFontSize × 1.4, ширина адаптивная по реальному
 *        viewBox SVG (читается из первых 512 байт).
 *   А4 — block maxHeight = min(screenHeight × 0.35, 360dp), corner 14dp,
 *        лёгкий border 1dp Separator.
 */

private const val TAG = "HtmlRenderer"

private val DISPLAY_NONE_REGEX = Regex("display\\s*:\\s*none\\s*;?", RegexOption.IGNORE_CASE)
private val INLINE_COLOR_REGEX = Regex("color\\s*:[^;\"]*[;\"]?", RegexOption.IGNORE_CASE)
private val COMMENT_REGEX = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))

private fun sanitizeHtml(html: String): String =
    html
        .replace(COMMENT_REGEX, "")
        .replace(DISPLAY_NONE_REGEX, "")
        .replace(INLINE_COLOR_REGEX, "")

// ----------------------- классификатор формул -----------------------

private fun isLargeFormula(alt: String): Boolean {
    if (alt.length > 25) return true
    if (alt.contains("=") && alt.length > 12) return true
    if (alt.count { it == '/' } > 1) return true
    if (alt.contains("→") || alt.contains("⇔") || alt.contains("⇒")) return true
    return false
}

// ----------------------- модель сегментов -----------------------

private sealed class Seg {
    data class Text(val text: String, val style: SpanStyle = SpanStyle()) : Seg()
    data class Inline(val src: String, val alt: String, val placeholderId: String) : Seg()
    data class Block(val src: String, val alt: String) : Seg()
    data object Break : Seg()
}

private data class ParsedBlock(
    val inlineFlow: List<Seg>,
    val blockImages: List<Seg.Block>,
)

// ----------------------- Jsoup-парсинг -----------------------

private fun parse(html: String): List<ParsedBlock> {
    val cleaned = sanitizeHtml(html)
    val doc = Jsoup.parse(cleaned)
    val body = doc.body()

    val segs = mutableListOf<Seg>()
    var inlineCounter = 0
    val stack = ArrayDeque<SpanStyle>().apply { addLast(SpanStyle()) }

    lateinit var walk: (Node) -> Unit

    fun enterStyle(newStyle: SpanStyle, el: Element) {
        stack.addLast(newStyle)
        try {
            el.childNodes().forEach { walk(it) }
        } finally {
            stack.removeLast()
        }
    }

    walk = { node ->
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotEmpty()) segs += Seg.Text(text, stack.last())
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                when (tag) {
                    "img" -> {
                        val src = node.attr("src").trim()
                        val alt = node.attr("alt")
                        if (src.isNotEmpty()) {
                            // Новый классификатор: даже class="tex" может оказаться большой
                            // многокомпонентной формулой — кладём её отдельным блоком, чтобы
                            // не ломала строку.
                            val isFormula = node.hasClass("tex")
                            val kind = if (!isFormula) "block"
                                      else if (isLargeFormula(alt)) "block" else "inline"
                            if (kind == "inline") {
                                segs += Seg.Inline(src, alt, "img_${inlineCounter++}")
                            } else {
                                segs += Seg.Block(src, alt)
                            }
                        }
                    }
                    "br" -> segs += Seg.Break
                    "p", "div" -> {
                        node.childNodes().forEach { walk(it) }
                        segs += Seg.Break
                    }
                    "i", "em" -> enterStyle(stack.last().copy(fontStyle = FontStyle.Italic), node)
                    "b", "strong" -> enterStyle(stack.last().copy(fontWeight = FontWeight.Bold), node)
                    "sub" -> enterStyle(
                        stack.last().copy(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = 12.sp,
                        ),
                        node,
                    )
                    "sup" -> enterStyle(
                        stack.last().copy(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 12.sp,
                        ),
                        node,
                    )
                    else -> node.childNodes().forEach { walk(it) }
                }
            }
            else -> { /* DataNode, Comment и прочее — игнор */ }
        }
    }

    body.childNodes().forEach { walk(it) }

    val result = mutableListOf<ParsedBlock>()
    var currentInline = mutableListOf<Seg>()
    var currentBlocks = mutableListOf<Seg.Block>()

    fun flush() {
        if (currentInline.isNotEmpty() || currentBlocks.isNotEmpty()) {
            while (currentInline.isNotEmpty() && currentInline.first().let { it is Seg.Text && it.text.isBlank() }) {
                currentInline.removeAt(0)
            }
            while (currentInline.isNotEmpty() && currentInline.last().let { it is Seg.Text && it.text.isBlank() }) {
                currentInline.removeAt(currentInline.size - 1)
            }
            if (currentInline.isNotEmpty() || currentBlocks.isNotEmpty()) {
                result += ParsedBlock(currentInline.toList(), currentBlocks.toList())
            }
        }
        currentInline = mutableListOf()
        currentBlocks = mutableListOf()
    }

    for (seg in segs) {
        when (seg) {
            is Seg.Break -> flush()
            is Seg.Block -> currentBlocks += seg
            else -> currentInline += seg
        }
    }
    flush()
    return result
}

// ----------------------- SVG / PNG декодинг -----------------------

private data class RenderedImage(
    val bitmap: Bitmap,
    val widthPx: Float,
    val heightPx: Float,
    /** Natural aspect ratio (width/height) — для inline placeholder sizing. */
    val aspect: Float,
)

/**
 * Stage 3 polish 3 (#1): luminance-инверсия с сохранением цвета.
 *
 * Зачем: sdamgia рисует формулы и геометрические чертежи чёрным по белому.
 * На чёрной теме они невидимы. Раньше я делал полную RGB-инверсию через
 * ColorMatrix — это работало для формул (черно-белые), но ломало цветные
 * элементы иллюстраций: оранжевая окружность становилась голубой и т.д.
 *
 * Сейчас попиксельный подход:
 *   1. Если пиксель «серый» (max channel diff < 30) — RGB-инверсия (чёрное → белое).
 *   2. Если пиксель «цветной» — оставляем как есть (оранжевый остаётся оранжевым).
 *
 * Производительность: 100×30 формула ≈ 3000 пикселей (микросекунды), большой
 * чертёж до 360dp ≈ 1080×720 ≈ 780K пикселей × 1 операция ≈ ~10мс на Snapdragon.
 * Запускается на Dispatchers.IO, не блокирует UI.
 *
 * Применяется ко всем SVG и растровым иллюстрациям, поскольку формулы и
 * чертежи sdamgia имеют одну и ту же зашитую тёмную палитру.
 */
private const val GRAY_THRESHOLD = 30

private fun applyLuminanceInversion(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) {
        val px = pixels[i]
        val a = (px ushr 24) and 0xFF
        if (a == 0) {
            pixels[i] = px
            continue
        }
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        val maxDiff = maxOf(
            kotlin.math.abs(r - g),
            kotlin.math.abs(g - b),
            kotlin.math.abs(r - b),
        )
        if (maxDiff < GRAY_THRESHOLD) {
            val ir = 255 - r
            val ig = 255 - g
            val ib = 255 - b
            pixels[i] = (a shl 24) or (ir shl 16) or (ig shl 8) or ib
        }
    }
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    src.recycle()
    return out
}

private suspend fun loadImageFromAsset(
    context: Context,
    assetPath: String,
    targetHeightPx: Int,
    invertColors: Boolean,
): RenderedImage? = withContext(Dispatchers.IO) {
    val cleaned = assetPath.trim().removePrefix("./").removePrefix("/")
    val isSvg = cleaned.endsWith(".svg", ignoreCase = true)
    try {
        if (isSvg) {
            context.assets.open(cleaned).use { stream ->
                val svg = SVG.getFromInputStream(stream)
                val docW = if (svg.documentWidth > 0f) svg.documentWidth else 100f
                val docH = if (svg.documentHeight > 0f) svg.documentHeight else 24f
                val scale = targetHeightPx / docH
                val widthPx = (docW * scale).coerceAtLeast(1f)
                val bmpW = widthPx.toInt().coerceAtLeast(1)
                val bmpH = targetHeightPx.coerceAtLeast(1)
                var bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.scale(scale, scale)
                svg.renderToCanvas(canvas)
                if (invertColors) bmp = applyLuminanceInversion(bmp)
                RenderedImage(bmp, widthPx, targetHeightPx.toFloat(), docW / docH)
            }
        } else {
            context.assets.open(cleaned).use { stream ->
                var bmp = BitmapFactory.decodeStream(stream)
                    ?: error("BitmapFactory вернул null для $cleaned")
                val srcW = bmp.width.toFloat().coerceAtLeast(1f)
                val srcH = bmp.height.toFloat().coerceAtLeast(1f)
                val scale = targetHeightPx / srcH
                val widthPx = srcW * scale
                if (invertColors) bmp = applyLuminanceInversion(bmp)
                RenderedImage(bmp, widthPx, targetHeightPx.toFloat(), srcW / srcH)
            }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "loadImageFromAsset failed: $cleaned", e)
        null
    }
}

/**
 * Естественный размер SVG в SVG-юнитах (~pt для sdamgia формул).
 * Для нашего рендера 1pt ≈ 1dp визуально (формула высоты 30pt должна
 * примерно соответствовать 30dp на экране).
 */
private data class SvgSize(val width: Float, val height: Float) {
    val aspect: Float get() = if (height > 0f) width / height else 1f
}

private val VIEWBOX_REGEX = Regex("viewBox\\s*=\\s*['\"]\\s*[\\-\\d.]+\\s+[\\-\\d.]+\\s+([\\d.]+)\\s+([\\d.]+)")
private val WIDTH_HEIGHT_REGEX = Regex("width\\s*=\\s*['\"]([\\d.]+)[^'\"]*['\"][^>]*height\\s*=\\s*['\"]([\\d.]+)")

/**
 * Читает первые ~1KB SVG, извлекает естественный размер из viewBox или
 * width/height. Используется в InlineFlow (правильная ширина placeholder)
 * и в BlockSvg (cap-высота для маленьких формул).
 *
 * Возвращает null если SVG нет или размер невалиден — caller использует
 * fallback по alt-эвристикам.
 */
private suspend fun readSvgSize(context: Context, assetPath: String): SvgSize? =
    withContext(Dispatchers.IO) {
        val cleaned = assetPath.trim().removePrefix("./").removePrefix("/")
        if (!cleaned.endsWith(".svg", ignoreCase = true)) return@withContext null
        try {
            context.assets.open(cleaned).use { stream ->
                val buf = ByteArray(1024)
                val n = stream.read(buf)
                if (n <= 0) return@withContext null
                val head = String(buf, 0, n)
                VIEWBOX_REGEX.find(head)?.let {
                    val w = it.groupValues[1].toFloatOrNull()
                    val h = it.groupValues[2].toFloatOrNull()
                    if (w != null && h != null && w > 0f && h > 0f) return@withContext SvgSize(w, h)
                }
                WIDTH_HEIGHT_REGEX.find(head)?.let {
                    val w = it.groupValues[1].toFloatOrNull()
                    val h = it.groupValues[2].toFloatOrNull()
                    if (w != null && h != null && w > 0f && h > 0f) return@withContext SvgSize(w, h)
                }
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

/** Содержится ли asset в библиотеке формул (всё кроме чертежей). */
private fun isFormulaPath(assetPath: String): Boolean =
    assetPath.startsWith("_formulas/") || assetPath.contains("/_formulas/")

// ----------------------- Compose-обёртки -----------------------

@Composable
private fun InlineSvg(
    assetPath: String,
    alt: String,
    heightSp: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val heightPx = with(density) { heightSp.sp.roundToPx() }
    val invert = isSystemInDarkTheme()  // Stage 5 part Ж — в светлой теме НЕ инвертируем
    var image by remember(assetPath, heightPx, invert) { mutableStateOf<RenderedImage?>(null) }

    LaunchedEffect(assetPath, heightPx, invert) {
        image = loadImageFromAsset(context, assetPath, heightPx, invertColors = invert)
    }

    val bmp = image
    if (bmp != null) {
        Image(
            bitmap = bmp.bitmap.asImageBitmap(),
            contentDescription = alt.ifBlank { "формула" },
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier = modifier)
    }
}

@Composable
private fun BlockFormula(assetPath: String, alt: String) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 1. Читаем естественный размер SVG.
    var natural by remember(assetPath) { mutableStateOf<SvgSize?>(null) }
    LaunchedEffect(assetPath) { natural = readSvgSize(context, assetPath) }

    // Phase 4 Stage P4-C part В (Convention #50) — block-формулы крупнее.
    // 2. Высота = clamp(naturalHeight × 1.4, min 48dp, max 120dp). Раньше
    //    «4/7 · x = 7 3/7» рендерилось высотой ~24dp — слишком мелко
    //    относительно текста. Теперь минимум 48dp, естественные формулы
    //    масштабируются в 1.4× ради читаемости.
    val formulaMinDp = 48
    val formulaMaxDp = 120
    val naturalHeightDp = natural?.height?.coerceAtLeast(1f) ?: 72f
    val effectiveHeightDp = (naturalHeightDp * 1.4f)
        .coerceAtLeast(formulaMinDp.toFloat())
        .coerceAtMost(formulaMaxDp.toFloat())
    val heightPx = with(density) { effectiveHeightDp.dp.roundToPx() }
    val invert = isSystemInDarkTheme()

    var image by remember(assetPath, heightPx, invert) { mutableStateOf<RenderedImage?>(null) }
    LaunchedEffect(assetPath, heightPx, invert) {
        image = loadImageFromAsset(context, assetPath, heightPx, invertColors = invert)
    }

    val bmp = image
    if (bmp != null) {
        // Wrap-content: формула рисуется в её естественной ширине, не
        // растягивается до полной ширины экрана. Если ширина больше экрана —
        // Compose ужмёт через ContentScale.Fit (редкость для нормальных формул).
        Image(
            bitmap = bmp.bitmap.asImageBitmap(),
            contentDescription = alt.ifBlank { "формула" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .heightIn(max = effectiveHeightDp.dp)
                .padding(vertical = 4.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .heightIn(min = 36.dp, max = effectiveHeightDp.dp)
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun BlockIllustration(
    assetPath: String,
    alt: String,
    maxHeight: Dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val heightPx = with(density) { maxHeight.roundToPx() }
    val invert = isSystemInDarkTheme()
    var image by remember(assetPath, heightPx, invert) { mutableStateOf<RenderedImage?>(null) }

    LaunchedEffect(assetPath, heightPx, invert) {
        image = loadImageFromAsset(context, assetPath, heightPx, invertColors = invert)
    }

    val bmp = image
    // Phase 4 Stage P4-C part В (Convention #50) — иллюстрации крупнее:
    // фиксируем minHeight = 120dp чтобы маленькие чертежи не выглядели
    // обрезком; maxHeight остаётся как было (35% экрана / 360dp).
    if (bmp != null) {
        Image(
            bitmap = bmp.bitmap.asImageBitmap(),
            contentDescription = alt.ifBlank { "иллюстрация" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = maxHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, Separator, RoundedCornerShape(14.dp))
                .padding(4.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = maxHeight)
                .padding(vertical = 4.dp),
        )
    }
}

// ----------------------- Главный публичный API -----------------------

@Composable
fun HtmlRenderer(
    html: String,
    modifier: Modifier = Modifier,
    baseFontSizeSp: Int = 17,
) {
    if (html.isBlank()) return

    val blocks = remember(html) { parse(html) }

    // Phase 4 Stage P4-C part В (Convention #50) — крупнее формулы.
    // Inline-формулы: scaleFactor 1.4 → 1.6 и минимум 28sp. На base=18sp
    // это даёт 28sp (вместо 25sp было), на base=17 — 28sp (вместо 23.8).
    // Math №6 «4/7 · x = 7 3/7» больше не выглядит крошечно рядом с заголовком.
    val inlineHeightSp = (baseFontSizeSp * 1.6f).toInt().coerceAtLeast(28)

    // Block-картинка maxHeight = min(35% экрана, 360dp).
    val configuration = LocalConfiguration.current
    val blockMaxHeight = min(configuration.screenHeightDp * 0.35f, 360f).dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                if (block.inlineFlow.isNotEmpty()) {
                    InlineFlow(
                        flow = block.inlineFlow,
                        baseFontSizeSp = baseFontSizeSp,
                        inlineHeightSp = inlineHeightSp,
                    )
                }
                block.blockImages.forEach { img ->
                    if (isFormulaPath(img.src)) {
                        BlockFormula(assetPath = img.src, alt = img.alt)
                    } else {
                        BlockIllustration(
                            assetPath = img.src,
                            alt = img.alt,
                            maxHeight = blockMaxHeight,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineFlow(
    flow: List<Seg>,
    baseFontSizeSp: Int,
    inlineHeightSp: Int,
) {
    val context = LocalContext.current
    val inlineContent = mutableMapOf<String, InlineTextContent>()

    // Pre-load aspect ratios для каждого inline image, чтобы placeholder
    // соответствовал реальной геометрии формулы.
    val aspects = remember(flow) { mutableMapOf<String, Float>() }
    val inlineSegs = flow.filterIsInstance<Seg.Inline>()
    LaunchedEffect(flow) {
        inlineSegs.forEach { seg ->
            if (!aspects.containsKey(seg.placeholderId)) {
                val sz = readSvgSize(context, seg.src)
                if (sz != null) aspects[seg.placeholderId] = sz.aspect
            }
        }
    }

    val ann = buildAnnotatedString {
        // ВАЖНО (А1): принудительный onBackground цвет на любой текст,
        // отменяет случайные стили sdamgia.
        flow.forEach { seg ->
            when (seg) {
                is Seg.Text -> {
                    val styled = seg.style.copy(color = Label)
                    withStyleOrNot(styled) { append(seg.text) }
                }
                is Seg.Inline -> {
                    appendInlineContent(seg.placeholderId, seg.alt.ifBlank { "формула" })
                    val aspect = aspects[seg.placeholderId] ?: 2.8f
                    val widthSp = (inlineHeightSp * aspect.coerceIn(0.7f, 6f)).coerceAtLeast(20f)
                    inlineContent[seg.placeholderId] = InlineTextContent(
                        placeholder = Placeholder(
                            width = widthSp.sp,
                            height = inlineHeightSp.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                        ),
                    ) {
                        InlineSvg(
                            assetPath = seg.src,
                            alt = seg.alt,
                            heightSp = inlineHeightSp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    if (ann.text.isNotBlank() || inlineContent.isNotEmpty()) {
        Text(
            text = ann,
            inlineContent = inlineContent,
            fontSize = baseFontSizeSp.sp,
            color = Label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private inline fun AnnotatedString.Builder.withStyleOrNot(
    style: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit,
) {
    if (style == SpanStyle()) {
        block()
    } else {
        val idx = pushStyle(style)
        try {
            block()
        } finally {
            pop(idx)
        }
    }
}
