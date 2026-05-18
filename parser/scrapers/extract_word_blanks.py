"""Stage 4: извлечь слова с пропусками для тренажёров №9-12 русского.

Структура входа в corpus.db:
    statement_html: «1) заг..релый, непром..каемый, к..мендант 2) оп..раться, ...»
    solution_html:  «Приведём верное написание: 1) <b>загорелый</b> — ..., <b>непромокаемый</b> — ...»

Алгоритм:
    1. Из statement_html выбираем все слова с `..` (≥2 точки).
    2. Из solution_html выбираем все <b>...</b> блоки — это полные правильные слова.
    3. Для каждого masked слова `р..сти` строим regex `^р\\w+сти$` и ищем
       среди bold-слов. Из match.group(1) получаем пропущенную букву.
    4. Дедупликация по `(masked, answer)` — одно и то же слово может
       встречаться в десятках задач.

Output: parser/word_blanks.json со словарём по типам 9/10/11/12.

Запуск:
    python parser/scrapers/extract_word_blanks.py
"""
from __future__ import annotations

import json
import re
import sqlite3
import sys
from pathlib import Path
from typing import Optional

PARSER_ROOT = Path(__file__).resolve().parent.parent
DB_PATH = PARSER_ROOT / "corpus.db"
OUT_PATH = PARSER_ROOT / "word_blanks.json"

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

# Слово с пропуском: одна или две группы букв вокруг "..".
# Поддерживаем русские буквы (включая ё), мягкий/твёрдый знак, дефис.
# Точно НЕ хотим ловить html-сущности (например &nbsp;), поэтому ограничиваем
# буквы кириллицей.
WORD_LETTERS = r"[а-яА-ЯёЁ\-]"
WORD_WITH_BLANK_RE = re.compile(
    rf"({WORD_LETTERS}*\.\.+{WORD_LETTERS}*)",
    re.UNICODE,
)
# Bold-блоки в solution_html — внутри обычные слова, без вложенной разметки
# (на практике <b>загорелый</b> — простое слово).
BOLD_RE = re.compile(r"<b\b[^>]*>([^<]+)</b>", re.IGNORECASE)

# Заголовки типов и подсказки.
TYPE_TITLES = {
    9: "Корни",
    10: "Приставки",
    11: "Суффиксы",
    12: "Окончания и причастия",
}
TYPE_FULL_TITLES = {
    9: "Правописание гласных в корне",
    10: "Правописание приставок",
    11: "Правописание суффиксов (кроме -Н-/-НН-)",
    12: "Правописание окончаний и суффиксов причастий",
}
TYPE_RULE_HINTS = {
    9: "Проверь корень: словарное слово, проверочное или чередующаяся гласная.",
    10: "Приставка ПРЕ-/ПРИ- зависит от смысла; на С/З — от первой согласной корня.",
    11: "Суффиксы прилагательных, существительных и глаголов имеют свои правила.",
    12: "Окончание зависит от спряжения; суффиксы причастий — от спряжения исходного глагола.",
}


def strip_tags(html: str) -> str:
    """Убрать HTML-теги, оставить text. Сжать whitespace."""
    text = re.sub(r"<[^>]+>", " ", html)
    text = text.replace("&nbsp;", " ").replace("&amp;", "&")
    return re.sub(r"\s+", " ", text).strip()


def find_blanks(text: str) -> list[str]:
    """Найти все слова с пропусками. Нормализуем к lowercase."""
    out: list[str] = []
    for m in WORD_WITH_BLANK_RE.finditer(text):
        w = m.group(1)
        # Отсекаем огрызки типа просто ".." без букв.
        if "." not in w:
            continue
        before, after = w.split("..", 1)[0], w.split("..", -1)[-1]
        # WORD_LETTERS позволил бы матчить пустые с обеих сторон. Хотим
        # минимум 1 букву хотя бы с одной стороны.
        if not (before or after):
            continue
        # Слова с дефисом-сами по себе («чёрно-..белый») редки; пока пропускаем.
        out.append(w.lower())
    return out


def find_bold_words(html: str) -> list[str]:
    """Извлечь полные правильные слова из solution_html.

    Sdamgia использует ДВА формата:
      A) `<b>загорелый</b>` — слово целиком в bold.
      B) `<span class="root">р<b>а</b>ст</span>ительность` — bold только
         на ответе-букве, остальное вокруг.

    Стратегия:
      1. Удаляем все теги кроме <b>…</b>, оставляя текст.
      2. Заменяем <b>X</b> на маркер `|X|`.
      3. Если в результате есть `|word|` где word — длинное (>2) слово —
         это формат A, добавляем word.
      4. Если есть `letter|short|letter…` — это формат B, конкатенируем
         буквы слева/справа маркера до пробела/пунктуации.

    Возвращает уникальные слова в lowercase.
    """
    # 1. Удалить все теги кроме <b>...</b>.
    cleaned = re.sub(r"</?(?!b\b)[a-zA-Z][^>]*>", "", html)
    cleaned = cleaned.replace("&nbsp;", " ").replace("&amp;", "&")
    # 2. <b>X</b> → |X|
    cleaned = re.sub(r"<b\b[^>]*>([^<]+)</b>", r"|\1|", cleaned, flags=re.IGNORECASE)

    words: set[str] = set()
    # 3. Найти все вхождения маркера + окружающие буквы.
    # Группы: (буквы_до)(|содержимое|)(буквы_после).
    marker_re = re.compile(
        rf"({WORD_LETTERS}*)\|([^|]+)\|({WORD_LETTERS}*)",
        re.UNICODE,
    )
    for m in marker_re.finditer(cleaned):
        before = m.group(1).lower()
        inside = m.group(2).strip().lower()
        after = m.group(3).lower()
        # Если маркер содержит уже целое слово (формат A) — добавляем его
        # отдельно ТОЛЬКО если оно похоже на слово (≥3 буквы кириллицы).
        for token in inside.split():
            t = token.strip(",.;:()«»\"' ").lower()
            if re.fullmatch(rf"{WORD_LETTERS}+", t) and len(t) >= 3:
                words.add(t)
        # Формат B: слово = before + inside (без пробелов) + after.
        inside_no_space = inside.replace(" ", "")
        if (before or after) and re.fullmatch(rf"{WORD_LETTERS}+", inside_no_space):
            full = (before + inside_no_space + after)
            if re.fullmatch(rf"{WORD_LETTERS}+", full) and len(full) >= 3:
                words.add(full)
    return sorted(words)


def find_bold_inserts(html: str) -> list[tuple[str, str]]:
    """Для формата B вернуть пары (полное_слово, вставленные_буквы).

    Это даёт прямой ответ для masked без необходимости угадывать `\\w{1,3}`.
    """
    cleaned = re.sub(r"</?(?!b\b)[a-zA-Z][^>]*>", "", html)
    cleaned = cleaned.replace("&nbsp;", " ").replace("&amp;", "&")
    cleaned = re.sub(r"<b\b[^>]*>([^<]+)</b>", r"|\1|", cleaned, flags=re.IGNORECASE)

    pairs: list[tuple[str, str]] = []
    marker_re = re.compile(
        rf"({WORD_LETTERS}*)\|([^|]+)\|({WORD_LETTERS}*)",
        re.UNICODE,
    )
    for m in marker_re.finditer(cleaned):
        before = m.group(1).lower()
        inside = m.group(2).strip().lower()
        after = m.group(3).lower()
        inside_no_space = inside.replace(" ", "")
        if not (before or after):
            continue
        if not re.fullmatch(rf"{WORD_LETTERS}+", inside_no_space):
            continue
        full = before + inside_no_space + after
        if re.fullmatch(rf"{WORD_LETTERS}+", full):
            pairs.append((full, inside_no_space))
    return pairs


def restore(masked: str, candidates: list[str]) -> Optional[tuple[str, str]]:
    """`р..сти` + ['растение','роса','расти'] → ('а', 'расти').

    Возвращает (вставленная_часть, полное_слово). Вставленная часть может быть
    1-3 букв (`пр..кр..тить`-style — мы пока запрещаем многократные пропуски,
    но один `..` может покрывать «ое» или «ьи»).
    """
    if ".." not in masked:
        return None
    # Заменяем все группы `..` на одну захватывающую группу.
    # Для простоты — один проход: разрешаем ровно одно `..`.
    count_blanks = masked.count("..")
    if count_blanks != 1:
        return None
    parts = masked.split("..", 1)
    pattern = re.compile(
        "^" + re.escape(parts[0]) + r"(\w{1,3})" + re.escape(parts[1]) + r"$",
        re.UNICODE,
    )
    for cand in candidates:
        m = pattern.match(cand)
        if m:
            return m.group(1), cand
    return None


def main() -> int:
    if not DB_PATH.exists():
        print(f"!! {DB_PATH} не найден — запусти build_db.py", file=sys.stderr)
        return 1

    conn = sqlite3.connect(DB_PATH)
    out: dict[str, dict] = {}
    overall_total = 0
    overall_resolved = 0

    for type_number in (9, 10, 11, 12):
        rows = conn.execute(
            """
            SELECT p.id, p.statement_html, sol.solution_html
            FROM problems p
            JOIN problem_types t ON t.id = p.type_id
            LEFT JOIN solutions sol ON sol.problem_id = p.id
            WHERE t.subject_id = 2 AND t.number = ?
            """,
            (type_number,),
        ).fetchall()

        type_total = 0
        type_resolved = 0
        seen: dict[tuple[str, str], dict] = {}

        for problem_id, statement_html, solution_html in rows:
            statement_text = strip_tags(statement_html or "")
            blanks = find_blanks(statement_text)
            type_total += len(blanks)
            if not solution_html or not blanks:
                continue

            candidates = find_bold_words(solution_html)
            if not candidates:
                continue

            for masked in blanks:
                key_seen_already = any(k[0] == masked for k in seen.keys())
                if key_seen_already:
                    type_resolved += 1
                    continue
                m = restore(masked, candidates)
                if m is None:
                    continue
                ans, full = m
                key = (masked, ans)
                if key in seen:
                    type_resolved += 1
                    continue
                seen[key] = {
                    "masked": masked,
                    "answer": ans,
                    "full": full,
                    "rule_hint": TYPE_RULE_HINTS[type_number],
                }
                type_resolved += 1

        out[str(type_number)] = {
            "title": TYPE_TITLES[type_number],
            "full_title": TYPE_FULL_TITLES[type_number],
            "words": sorted(seen.values(), key=lambda x: x["full"]),
        }
        rate = 100.0 * type_resolved / type_total if type_total else 0
        print(
            f"  №{type_number} {TYPE_TITLES[type_number]}: "
            f"всего пропусков={type_total}, распознано={type_resolved} "
            f"({rate:.1f}%), уникальных слов={len(seen)}",
        )
        overall_total += type_total
        overall_resolved += type_resolved

    conn.close()

    overall_rate = 100.0 * overall_resolved / overall_total if overall_total else 0
    print()
    print(f"ИТОГО: распознано {overall_resolved}/{overall_total} ({overall_rate:.1f}%)")

    payload = {
        "version": "ege-2026",
        "source": "Извлечено из corpus.db (sdamgia) скриптом extract_word_blanks.py",
        "types": out,
    }
    OUT_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    size_kb = OUT_PATH.stat().st_size / 1024
    print(f"OK: {OUT_PATH} ({size_kb:.1f} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
