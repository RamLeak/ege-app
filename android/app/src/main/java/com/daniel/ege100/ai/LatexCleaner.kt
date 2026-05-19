package com.daniel.ege100.ai

/**
 * Phase 4 Stage P4-C part Б (Convention #49) — fallback-чистка LaTeX
 * из ответов AI.
 *
 * Двойная защита от LaTeX в ответе:
 *   (1) Системный промпт EGE100_SYSTEM_PROMPT жёстко запрещает LaTeX.
 *   (2) Этот helper подчищает то что модель всё-таки прислала.
 *
 * Применяется один раз — в AskAiViewModel.ask() ПЕРЕД сохранением в
 * кеш. Это значит:
 *  - В кеше уже очищенный текст (повторный запрос → мгновенный clean ответ).
 *  - SimpleMarkdownRenderer получает то что может отрисовать.
 *
 * Покрытие:
 *  - `\(...\)` `\[...\]` обёртки → просто содержимое.
 *  - `\frac{a}{b}` → `a/b`. Если a или b — ещё один \frac, ловим в один
 *    проход через два regex'а (внешний → внутренний).
 *  - `\sqrt{x}` → `√x`. `\cdot` → `·`. `x^{2}` / `_{i}` → `x^2` / `_i`.
 *  - Греческие \alpha..\omega → α..ω. Сравнения \leq..\to → ≤..→.
 *  - Остатки `\anything` удаляются. Фигурные скобки удаляются.
 *
 * НЕ покрывает:
 *  - `\begin{align}…\end{align}` blocks (редко) — пройдут через generic
 *    `\anything` cleanup, фигурные пропадут.
 *  - LaTeX-комментарии `%` (редко в AI-ответах).
 */
object LatexCleaner {

    private val INLINE_PAREN = Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)
    private val DISPLAY_BRACKET = Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)
    private val DOLLAR_INLINE = Regex("""\$([^$]+?)\$""")
    private val FRAC = Regex("""\\frac\s*\{([^{}]+)\}\s*\{([^{}]+)\}""")
    private val SQRT = Regex("""\\sqrt\s*\{([^{}]+)\}""")
    private val SUPER_BRACE = Regex("""\^\s*\{([^{}]+)\}""")
    private val SUB_BRACE = Regex("""_\s*\{([^{}]+)\}""")
    private val BACKSLASH_CMD = Regex("""\\[a-zA-Z]+""")

    private val GREEK = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\vartheta" to "θ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
        "\\pi" to "π", "\\rho" to "ρ", "\\sigma" to "σ", "\\tau" to "τ",
        "\\phi" to "φ", "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Alpha" to "Α", "\\Beta" to "Β", "\\Gamma" to "Γ", "\\Delta" to "Δ",
        "\\Theta" to "Θ", "\\Lambda" to "Λ", "\\Xi" to "Ξ", "\\Pi" to "Π",
        "\\Sigma" to "Σ", "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω",
    )

    private val COMPARE = mapOf(
        "\\leq" to "≤", "\\le" to "≤",
        "\\geq" to "≥", "\\ge" to "≥",
        "\\neq" to "≠", "\\ne" to "≠",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\to" to "→", "\\rightarrow" to "→",
        "\\leftarrow" to "←",
        "\\Leftrightarrow" to "⇔", "\\Rightarrow" to "⇒",
        "\\infty" to "∞",
        "\\cdot" to "·",
        "\\times" to "×",
        "\\div" to "÷",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\ldots" to "…",
        "\\cdots" to "…",
        "\\dots" to "…",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\forall" to "∀",
        "\\exists" to "∃",
    )

    fun clean(input: String): String {
        if (input.isEmpty()) return input
        var s = input

        // 1. Обёртки \( \) \[ \] $...$ → просто содержимое.
        s = s.replace(INLINE_PAREN) { it.groupValues[1] }
        s = s.replace(DISPLAY_BRACKET) { "\n${it.groupValues[1]}\n" }
        // $-обёртки: только парные, не одиночные $ как символ валюты.
        s = s.replace(DOLLAR_INLINE) { it.groupValues[1] }

        // 2. \frac{}{} → a/b. Делаем 3 прохода для вложенных дробей.
        repeat(3) {
            val next = s.replace(FRAC) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
            if (next == s) return@repeat
            s = next
        }
        // После \frac снимаем лишние скобки вокруг одиночных токенов:
        // "(4)/(7)" → "4/7", но "(x+1)/(x-2)" остаются как есть.
        s = s.replace(Regex("""\(([^()/+\-*·\s]+)\)/\(([^()/+\-*·\s]+)\)""")) {
            "${it.groupValues[1]}/${it.groupValues[2]}"
        }

        // 3. \sqrt{x} → √x.
        s = s.replace(SQRT) { "√${it.groupValues[1]}" }

        // 4. x^{2} → x^2, _{i} → _i.
        s = s.replace(SUPER_BRACE) { "^${it.groupValues[1]}" }
        s = s.replace(SUB_BRACE) { "_${it.groupValues[1]}" }

        // 5. Юникод-словари.
        GREEK.forEach { (latex, char) -> s = s.replace(latex, char) }
        COMPARE.forEach { (latex, char) -> s = s.replace(latex, char) }

        // 6. Все остальные `\command` — пытаемся снять команду оставив имя.
        // Но безопаснее просто удалить — большинство оставшегося это
        // декорация (`\quad`, `\,`, `\left`, `\right`, `\text{…}` уже без `{`).
        s = s.replace(BACKSLASH_CMD, "")

        // 7. Фигурные скобки — теперь декорация, удаляем.
        s = s.replace("{", "").replace("}", "")

        // 8. Чистим тройные newlines и лидирующие пробелы у строк.
        s = s.replace(Regex("\n{3,}"), "\n\n")

        return s.trim()
    }
}
