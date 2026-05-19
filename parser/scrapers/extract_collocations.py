"""
Phase 4 Stage P4-D5 (Convention #83) — извлечение словосочетаний из задач №7
русского для нового тренажёра WordCollocationTrainer.

Структура задач №7 в corpus.db:
  <p class="left_margin">В одном из выделенных ниже слов допущена...</p>
  <p>&nbsp;</p>
  <p> ЛЯГТЕ на пол</p>
  <p> ИХ работа</p>
  <p> горячие СУПЫ</p>
  <p> ШЕСТИСТАМИ учениками</p>
  <p> ИНЖЕНЕРЫ</p>

`answer` = «шестьюстами» (правильное исправление; может содержать `|` для
альтернативных правильных форм по Convention #48).

Алгоритм:
  1. Парсим все <p> в statement_html.
  2. Отбираем 5 параграфов с CAPS-словом (≥2 заглавные кириллицы подряд) —
     это и есть словосочетания.
  3. Для каждой CAPS-формы вычисляем сходство с answer (общий префикс ≥2 +
     общие буквы). Лучшая — wrong_index.
  4. correct_answers = answer.split('|') — нормализованные lowercase варианты.

Output: parser/data/word_collocations.json + копия в
android/app/src/main/assets/word_collocations.json.

Минимум 30 валидных задач (Spec А3). На практике выходит ~200+.
"""
from __future__ import annotations

import json
import re
import shutil
import sqlite3
import sys
from pathlib import Path

# UTF-8 reconfigure для Windows cp1251 stdin/stdout (Convention #43).
sys.stdout.reconfigure(encoding="utf-8")

# Пути.
ROOT = Path(__file__).resolve().parent.parent  # parser/
DB_PATH = ROOT / "corpus.db"
OUT_PATH = ROOT / "data" / "word_collocations.json"
ANDROID_COPY = ROOT.parent / "android" / "app" / "src" / "main" / "assets" / "word_collocations.json"

# Regex для CAPS-слова. Кириллица + Ё. Минимум 2 буквы (иначе ловит ложные "А").
CAPS_RE = re.compile(r"\b([А-ЯЁ]{2,})\b")

# Минимальная длина задачи (число строк-словосочетаний). На sdamgia всегда 5.
MIN_PHRASES = 5
MAX_PHRASES = 5


def strip_html_tags(html: str) -> str:
    """Грубое удаление HTML-тегов и nbsp. Только для одного <p>."""
    text = re.sub(r"<[^>]+>", "", html)
    text = text.replace("&nbsp;", " ").replace("&amp;", "&")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def extract_phrases(html: str) -> list[str]:
    """
    Извлекает словосочетания из statement_html.

    Эвристика: все <p>...</p>, очищаем теги, фильтруем по наличию CAPS-слова.
    Инструкция «В одном из выделенных...» имеет CAPS-слов 0, поэтому отсекается.
    """
    # Все <p>-блоки.
    p_blocks = re.findall(r"<p[^>]*>(.*?)</p>", html, flags=re.DOTALL)
    if not p_blocks:
        # fallback: разбить по <br> и <div>
        parts = re.split(r"<(?:br|/?div)[^>]*>", html, flags=re.IGNORECASE)
        p_blocks = parts

    phrases = []
    for block in p_blocks:
        text = strip_html_tags(block)
        if not text or text == "&nbsp;":
            continue
        # Должно быть CAPS-слово и общая длина словосочетания ≤80 символов.
        if not CAPS_RE.search(text):
            continue
        if len(text) > 80:
            continue
        # Отсекаем «Примечание.» и подобные.
        if text.lower().startswith("примечание"):
            continue
        phrases.append(text)

    return phrases


def normalize_answer(s: str) -> str:
    """trim + lowercase + ё→е (для устойчивого сравнения с CAPS-словом)."""
    return s.strip().lower().replace("ё", "е")


def similarity(a: str, b: str) -> int:
    """
    Грубое сходство двух слов в lowercase: длина общего префикса.
    Возвращает int, чем больше — тем похожее.
    """
    a = normalize_answer(a)
    b = normalize_answer(b)
    if not a or not b:
        return 0
    n = min(len(a), len(b))
    common = 0
    for i in range(n):
        if a[i] == b[i]:
            common += 1
        else:
            break
    return common


def find_wrong_index(phrases: list[str], answer_variants: list[str]) -> tuple[int, str]:
    """
    Возвращает (wrong_index, wrong_word_in_phrase).

    Алгоритм: для каждого словосочетания берём CAPS-слово; вычисляем максимальное
    сходство с любым из вариантов answer. Лучшее (max common-prefix) — наш wrong.

    Если все CAPS-слова имеют sim < 2 — fallback на первое.
    """
    best_idx = 0
    best_word = ""
    best_sim = -1

    for i, phrase in enumerate(phrases):
        caps_matches = CAPS_RE.findall(phrase)
        if not caps_matches:
            continue
        for caps in caps_matches:
            for variant in answer_variants:
                sim = similarity(caps, variant)
                if sim > best_sim:
                    best_sim = sim
                    best_idx = i
                    best_word = caps

    if best_sim < 2:
        # Не нашли явного совпадения. Это плохой кандидат — но всё равно
        # возьмём первое CAPS-слово как fallback.
        for i, phrase in enumerate(phrases):
            m = CAPS_RE.search(phrase)
            if m:
                return (i, m.group(1))
        return (0, "")

    return (best_idx, best_word)


def main() -> int:
    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()
    cur.execute(
        """
        SELECT p.id, p.statement_html, p.answer
        FROM problems p
        JOIN problem_types pt ON p.type_id = pt.id
        JOIN subjects s ON p.subject_id = s.id
        WHERE s.slug = 'rus' AND pt.number = 7 AND pt.is_supplementary = 0
        ORDER BY p.id
        LIMIT 500
        """
    )

    results = []
    skipped_no_answer = 0
    skipped_phrases = 0
    skipped_no_caps = 0

    for problem_id, html, answer in cur.fetchall():
        if not answer or not answer.strip():
            skipped_no_answer += 1
            continue

        phrases = extract_phrases(html)
        if len(phrases) < MIN_PHRASES:
            skipped_phrases += 1
            continue
        phrases = phrases[:MAX_PHRASES]

        # Поддерживаем `|` (Convention #48 — alternatives).
        answer_variants = [normalize_answer(v) for v in answer.split("|") if v.strip()]
        if not answer_variants:
            skipped_no_answer += 1
            continue

        wrong_index, wrong_word = find_wrong_index(phrases, answer_variants)
        if not wrong_word:
            skipped_no_caps += 1
            continue

        results.append({
            "id": f"collocation_{problem_id}",
            "problem_id": problem_id,
            "items": phrases,
            "wrong_index": wrong_index,
            "wrong_word_in_phrase": wrong_word,
            "correct_answers": answer_variants,
        })

    conn.close()

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(
        json.dumps(results, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    # Копия в android assets.
    ANDROID_COPY.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(OUT_PATH, ANDROID_COPY)

    print(f"extract_collocations: total={len(results)}, "
          f"skipped_no_answer={skipped_no_answer}, "
          f"skipped_phrases={skipped_phrases}, "
          f"skipped_no_caps={skipped_no_caps}")
    print(f"out: {OUT_PATH}")
    print(f"android copy: {ANDROID_COPY}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
