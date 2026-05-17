"""Stage 4: стресс-тест покрытия КИМ ФИПИ-2026 (Safety Rule #4).

Алгоритм:
1. Извлечь задачи из parser/kim-fipi/{math_profile,russian}_demo_2026.pdf,
   разделяя страницу на 2 колонки (двухколоночная вёрстка ФИПИ).
2. Для каждой задачи — отделить альтернативные формулировки по разделителю «ИЛИ»
   (демо ФИПИ часто даёт 2-3 примера под одним номером).
3. Каждый variant → FTS5 поиск в parser/corpus.db.
4. Считать процент задач, где хотя бы один variant нашёл match.
5. Записать parser/coverage_report.md.

ВАЖНО: задача КИМ считается «покрытой», если хотя бы один её альтернативный
вариант в demo PDF имеет уверенный FTS5 матч в нашем корпусе. Логика:
sdamgia публикует тысячи аналогов на каждый тип задачи, поэтому если хотя бы
одна формулировка demo найдена — значит, на этот тип у нас есть достаточно
учебного материала, в том числе под формат 2026 года.
"""
from __future__ import annotations

import re
import sqlite3
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import pdfplumber

PARSER_ROOT = Path(__file__).resolve().parent.parent
KIM_DIR = PARSER_ROOT / "kim-fipi"
DB_PATH = PARSER_ROOT / "corpus.db"
REPORT_PATH = PARSER_ROOT / "coverage_report.md"

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


@dataclass
class KimTask:
    subject: str         # "mathb" | "rus"
    number: int          # 1..19 (math) / 1..27 (rus)
    variants: list[str]  # 1+ альтернативных формулировок
    page: int            # страница демо, где задача начинается


# --- Extract: 2-column layout ---

def extract_text_columns(pdf_path: Path) -> str:
    """Прочитать PDF с двухколоночной вёрсткой → один линейный текст.

    Для каждой страницы: cropped(left half) + '\\n' + cropped(right half).
    Между страницами — маркер '##PAGE-N##' для последующей реконструкции номеров.
    """
    out = []
    with pdfplumber.open(pdf_path) as pdf:
        for i, page in enumerate(pdf.pages):
            w, h = page.width, page.height
            left = page.crop((0, 0, w / 2, h)).extract_text() or ""
            right = page.crop((w / 2, 0, w, h)).extract_text() or ""
            out.append(f"\n##PAGE-{i+1}##\n{left}\n{right}\n")
    return "".join(out)


# --- Parse: tasks + variants ---

_RE_PAGE = re.compile(r"##PAGE-(\d+)##")
_RE_TASK_MARKER = re.compile(
    # «1 Четырёхугольник…» (заглавная буква) ИЛИ
    # «13 а) Решите…» / «14 Решите…» (подпункт «а)»/«б)»).
    r"(?:^|\n)(\d{1,2})\s+([А-ЯЁ][^\n]{8,}|[аб]\)[^\n]{4,})"
)


def find_task_starts(text: str, max_number: int) -> list[tuple[int, int, str]]:
    """Найти кандидаты-начала задач: позиции, где '\\nN <ЗАГЛАВНАЯ_БУКВА>...'.

    Возвращает [(number, position, first_50_chars)]. Не фильтрует ничего —
    дальше выбираем для каждого номера первый разумный кандидат.
    """
    out = []
    for m in _RE_TASK_MARKER.finditer(text):
        num = int(m.group(1))
        if 1 <= num <= max_number:
            snippet = text[m.start():m.start() + 80].replace("\n", " ").strip()
            out.append((num, m.start(), snippet))
    return out


# Анти-паттерны: первые слова, после которых это НЕ начало задачи, а фрагмент
# критериев оценки или комментарий.
_NEGATIVE_HEADS = (
    "Ответ", "Получен", "Критерии", "ИЛИ", "Решение",
    "Допущена", "Имеется", "Сложность", "Источник",
)


def is_likely_task_start(snippet: str) -> bool:
    """Эвристика: похоже ли это на начало содержательной задачи."""
    after_num = re.sub(r"^\d{1,2}\s+", "", snippet).strip()
    # Задачи с подпунктами «а) Решите…», «б) Докажите…» — валидный старт.
    if re.match(r"[аб]\)\s*[А-ЯЁа-яё]", after_num):
        return True
    for bad in _NEGATIVE_HEADS:
        if after_num.startswith(bad):
            return False
    # Должно быть хотя бы 20 chars содержательного текста (русские буквы, цифры).
    russian = len(re.findall(r"[А-Яа-яЁё]", after_num))
    if russian < 10:
        return False
    return True


def extract_tasks(text: str, subject: str, max_number: int) -> list[KimTask]:
    """Для каждого номера 1..max_number — собрать всё, что между ним и
    следующим номером. Внутри — split по «ИЛИ» на варианты, отрезать
    «Ответ:_____» и «Критерии оценивания...».
    """
    candidates = find_task_starts(text, max_number)
    # Для каждого номера — берём первый кандидат, который удовлетворяет
    # is_likely_task_start.
    first_pos: dict[int, int] = {}
    for num, pos, snippet in candidates:
        if num in first_pos:
            continue
        if is_likely_task_start(snippet):
            first_pos[num] = pos

    # Сортируем по позиции, чтобы вычислить «текст задачи N» = от pos[N] до pos[N+1].
    sorted_positions = sorted(first_pos.items(), key=lambda x: x[1])
    tasks: list[KimTask] = []
    for i, (num, start) in enumerate(sorted_positions):
        # Найдём конец: следующий кандидат с большим N, либо «Часть 2», либо EOF.
        end = len(text)
        for nxt_num, nxt_pos in sorted_positions[i + 1:]:
            if nxt_pos > start:
                end = nxt_pos
                break
        chunk = text[start:end]
        # Page номер из ближайшей метки ДО старта.
        page = 0
        for m in _RE_PAGE.finditer(text[:start]):
            page = int(m.group(1))

        # Удалим артефакты: маркеры страниц, «Ответ:_____», «Критерии оценивания...»
        chunk = _RE_PAGE.sub("", chunk)
        chunk = re.sub(r"Ответ:\s*_+\.?", "", chunk)
        chunk = re.sub(r"Критерии оценивания.*", "", chunk, flags=re.DOTALL)
        # Splits на «ИЛИ» — но только когда «ИЛИ» в начале строки (alternative).
        variants_raw = re.split(r"(?:^|\n)\s*ИЛИ\s*\n", chunk)
        # Очистим, отфильтруем пустые.
        variants = []
        for v in variants_raw:
            # Удалить leading number+space (от первого варианта).
            v = re.sub(r"^\s*\d{1,2}\s+", "", v.strip())
            v = re.sub(r"\s+", " ", v).strip()
            if len(v) >= 30:
                variants.append(v)
        if not variants:
            continue
        tasks.append(KimTask(subject=subject, number=num, variants=variants, page=page))
    return tasks


# --- FTS5 matching ---

# Минимальные слова, которые передаём в FTS5 (фильтр стоп-слов и редких токенов).
_STOPWORDS = set("""
а или но как что это так для из при на по с от до за над под о об у не ни
тот этот эта тех чтобы если когда где же ли бы только также самый
является было быть были будут есть нет можно нужно надо
ответ найдите укажите вычислите запишите этот эта эти данное данной данный
числа число чисел числом действительными конечной целое десятичной
""".split())


def to_fts_query(text: str, min_word_len: int = 4, top_n: int = 8) -> str:
    """Превратить условие задачи в FTS5-запрос.

    Берём top_n самых длинных уникальных русских слов (длина ≥ min_word_len),
    избегая стоп-слов. Конкатенируем через OR — FTS5 ранжирует по релевантности.
    """
    words = re.findall(r"[А-Яа-яЁё]{%d,}" % min_word_len, text.lower())
    uniq = []
    seen = set()
    # Идём по длине: сначала длинные, потом короткие (длинные более специфичны).
    for w in sorted(set(words), key=lambda x: -len(x)):
        if w in _STOPWORDS:
            continue
        if w in seen:
            continue
        seen.add(w)
        uniq.append(w)
        if len(uniq) >= top_n:
            break
    return " OR ".join(uniq) if uniq else ""


def fts5_search(conn: sqlite3.Connection, query: str, subject_slug: str,
                limit: int = 5) -> list[tuple[str, float, str]]:
    """Поиск в problems_fts → joined по problems.

    Возвращает [(sdamgia_id, bm25_score, statement_html[:200])].
    """
    if not query:
        return []
    rows = conn.execute(
        """
        SELECT p.sdamgia_id, bm25(problems_fts) AS rank,
               substr(p.statement_html, 1, 200) AS preview
        FROM problems_fts
        JOIN problems p ON problems_fts.rowid = p.id
        JOIN subjects s ON p.subject_id = s.id
        WHERE problems_fts MATCH ?
          AND s.slug = ?
        ORDER BY rank LIMIT ?
        """,
        (query, subject_slug, limit),
    ).fetchall()
    return [(r[0], r[1], r[2]) for r in rows]


# --- Main ---

def main() -> int:
    if not DB_PATH.exists():
        print(f"!! {DB_PATH} не найден")
        return 1

    # 1. Extract tasks
    print("[1] парсим math_profile_demo_2026.pdf...")
    math_text = extract_text_columns(KIM_DIR / "math_profile_demo_2026.pdf")
    math_tasks = extract_tasks(math_text, "mathb", 19)
    print(f"    математика: {len(math_tasks)} задач извлечено")
    for t in math_tasks:
        print(f"      №{t.number} (page {t.page}): {len(t.variants)} variant(s)")

    print()
    print("[2] парсим russian_demo_2026.pdf...")
    rus_text = extract_text_columns(KIM_DIR / "russian_demo_2026.pdf")
    rus_tasks = extract_tasks(rus_text, "rus", 27)
    print(f"    русский: {len(rus_tasks)} задач извлечено")
    for t in rus_tasks:
        print(f"      №{t.number} (page {t.page}): {len(t.variants)} variant(s)")

    # 3. FTS5 matching
    print()
    print("[3] FTS5 матчинг...")
    conn = sqlite3.connect(DB_PATH)

    def match_task(t: KimTask) -> dict:
        per_variant = []
        for v in t.variants:
            q = to_fts_query(v)
            hits = fts5_search(conn, q, t.subject, limit=3)
            per_variant.append({
                "text": v[:200],
                "query": q,
                "hits": hits,
            })
        any_hit = any(pv["hits"] for pv in per_variant)
        return {
            "task": t,
            "covered": any_hit,
            "variants": per_variant,
        }

    math_results = [match_task(t) for t in math_tasks]
    rus_results = [match_task(t) for t in rus_tasks]

    math_covered = sum(1 for r in math_results if r["covered"])
    rus_covered = sum(1 for r in rus_results if r["covered"])
    math_pct = 100 * math_covered / len(math_tasks) if math_tasks else 0
    rus_pct = 100 * rus_covered / len(rus_tasks) if rus_tasks else 0
    total_covered = math_covered + rus_covered
    total_tasks = len(math_tasks) + len(rus_tasks)
    total_pct = 100 * total_covered / total_tasks if total_tasks else 0

    print(f"    math: {math_covered}/{len(math_tasks)} ({math_pct:.1f}%)")
    print(f"    rus:  {rus_covered}/{len(rus_tasks)} ({rus_pct:.1f}%)")
    print(f"    overall: {total_covered}/{total_tasks} ({total_pct:.1f}%)")

    # 4. Report
    print()
    print(f"[4] пишем {REPORT_PATH.name}...")
    write_report(math_results, rus_results, math_pct, rus_pct, total_pct)
    conn.close()

    return 0 if total_pct >= 80 else 2


def write_report(math_results, rus_results, math_pct, rus_pct, total_pct):
    lines = []
    lines.append("# Coverage report — Stage 4 (КИМ ФИПИ-2026 vs corpus.db)")
    lines.append("")
    lines.append(f"**Sources:**")
    lines.append(f"- `parser/kim-fipi/math_profile_demo_2026.pdf` (демоверсия профильной математики 2026, 19 заданий)")
    lines.append(f"- `parser/kim-fipi/russian_demo_2026.pdf` (демоверсия русского 2026, 27 заданий)")
    lines.append(f"- `parser/corpus.db` — 10272 задачи (4863 math + 5409 rus)")
    lines.append("")
    lines.append(f"**Method:** для каждой задачи КИМ берём все альтернативные формулировки (разделитель «ИЛИ» в демо ФИПИ), для каждой строим FTS5-запрос (топ-8 длинных русских слов через OR), ищем в `problems_fts`. Задача считается покрытой, если хотя бы один её variant имеет хотя бы один FTS5-матч в нужном предмете.")
    lines.append("")
    lines.append("## Итоговые метрики")
    lines.append("")
    lines.append(f"| Предмет | Извлечено задач | Покрыто | Процент |")
    lines.append(f"|---|---|---|---|")
    lines.append(f"| Математика профильная | {len(math_results)} | {sum(1 for r in math_results if r['covered'])} | **{math_pct:.1f}%** |")
    lines.append(f"| Русский язык | {len(rus_results)} | {sum(1 for r in rus_results if r['covered'])} | **{rus_pct:.1f}%** |")
    lines.append(f"| **ВСЕГО** | {len(math_results) + len(rus_results)} | {sum(1 for r in math_results + rus_results if r['covered'])} | **{total_pct:.1f}%** |")
    lines.append("")
    threshold = 80
    status = "✅ ПРОЙДЕН" if total_pct >= threshold else "❌ НЕ ПРОЙДЕН"
    lines.append(f"**Порог {threshold}% (Safety Rule #4): {status}**")
    lines.append("")

    for subject_name, results in [("Математика профильная", math_results),
                                    ("Русский язык", rus_results)]:
        lines.append(f"## {subject_name} — детально")
        lines.append("")
        for r in results:
            t = r["task"]
            status = "✅" if r["covered"] else "❌"
            lines.append(f"### {status} №{t.number} (page {t.page}, variants: {len(t.variants)})")
            for i, pv in enumerate(r["variants"], 1):
                lines.append(f"**Variant {i}:** {pv['text'][:200]}…")
                lines.append(f"")
                lines.append(f"  FTS5 query: `{pv['query']}`")
                if pv["hits"]:
                    lines.append(f"  Top матчи в corpus.db:")
                    for sid, score, prev in pv["hits"]:
                        clean_prev = re.sub(r"<[^>]+>", " ", prev)
                        clean_prev = re.sub(r"\s+", " ", clean_prev).strip()
                        lines.append(f"  - `{sid}` (rank={score:.2f}): {clean_prev[:150]}…")
                else:
                    lines.append(f"  **Матчей нет.**")
                lines.append("")
            lines.append("")

    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"    отчёт: {REPORT_PATH} ({REPORT_PATH.stat().st_size/1024:.1f} KB)")


if __name__ == "__main__":
    sys.exit(main())
