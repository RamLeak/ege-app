"""
Извлекает грамматические ошибки из задач №7 русского (corpus.db).

Структура: инструкция + 5 коротких фраз, в каждой одно слово CAPS. Одна из CAPS-форм
содержит грамматическую ошибку. `answer` — правильная форма (может быть пара через `|`).

Извлечённый формат:
- sentence: все 5 фраз через ' · '
- error_word: ошибочная CAPS-форма
- correct_form: первый вариант из answer

Out: parser/data/grammar_errors.json
"""

import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = ROOT / "corpus.db"
OUT_PATH = ROOT / "data" / "grammar_errors.json"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def strip_tags(html: str) -> str:
    text = re.sub(r"<[^>]+>", " ", html)
    text = text.replace("\xa0", " ").replace("&nbsp;", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def split_phrases_from_html(html: str) -> list[str]:
    blocks = re.findall(r"<p[^>]*>(.+?)</p>", html, flags=re.DOTALL)
    out = []
    for b in blocks:
        text = strip_tags(b)
        # Срезать ведущую нумерацию (редко)
        text = re.sub(r"^\d\)\s*", "", text)
        if text:
            out.append(text)
    return out


def normalize(s: str) -> str:
    return s.lower().replace("ё", "е").strip()


def common_prefix_len(a: str, b: str) -> int:
    n = min(len(a), len(b))
    i = 0
    while i < n and a[i] == b[i]:
        i += 1
    return i


def find_error_word(caps_words: list[str], correct: str) -> tuple[str | None, int]:
    """Найти CAPS-слово, ошибочную форму корректного."""
    correct_norm = normalize(correct)
    best_word = None
    best_score = 0
    for c in caps_words:
        c_norm = normalize(c)
        if c_norm == correct_norm:
            # Это и есть правильный — игнорируем
            continue
        sc = common_prefix_len(c_norm, correct_norm)
        # Должно быть совпадение хотя бы по 3 символам корня
        if sc >= 3 and sc > best_score:
            best_score = sc
            best_word = c
    return best_word, best_score


def main():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    rows = cur.execute("""
        SELECT p.id, p.statement_html, p.answer
        FROM problems p
        JOIN subjects s ON p.subject_id=s.id
        JOIN problem_types pt ON p.type_id=pt.id
        WHERE s.slug='rus' AND pt.number=7
    """).fetchall()
    conn.close()

    errors = []
    skipped_no_match = 0
    skipped_no_phrases = 0

    INSTR_MARKER = "В одном из выделенных ниже"

    for pid, html, answer in rows:
        if not answer:
            skipped_no_match += 1
            continue
        # Брать первый вариант из multipart
        correct = answer.split("|")[0].strip()
        if len(correct) < 3:
            skipped_no_match += 1
            continue

        phrases = split_phrases_from_html(html)
        # Отбросить инструкцию и примечания
        phrases = [p for p in phrases if INSTR_MARKER not in p and "Примечание" not in p and "Написание слова" not in p and len(p) >= 6]
        if not phrases:
            skipped_no_phrases += 1
            continue

        # Собрать все CAPS-слова
        all_caps = []
        per_phrase_caps: list[list[str]] = []
        for p in phrases:
            caps = re.findall(r"\b[А-ЯЁ]{3,}(?:[-А-ЯЁ]*)\b", p)
            per_phrase_caps.append(caps)
            all_caps.extend(caps)

        # Найти ошибочное слово
        wrong, score = find_error_word(all_caps, correct)
        if not wrong:
            skipped_no_match += 1
            continue

        # Найти фразу с этим словом (для будущего sentence)
        target_phrases = [p for p, caps in zip(phrases, per_phrase_caps) if wrong in caps]
        if not target_phrases:
            skipped_no_match += 1
            continue

        # sentence = все фразы через ' · '
        sentence = " · ".join(phrases)
        if len(sentence) > 350:
            sentence = target_phrases[0]  # fallback на одну фразу

        errors.append({
            "problem_id": pid,
            "sentence": sentence,
            "error_word": wrong,
            "correct_form": correct,
        })

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Extracted {len(errors)} grammar error examples")
    print(f"  Skipped no_match: {skipped_no_match}")
    print(f"  Skipped no_phrases: {skipped_no_phrases}")


if __name__ == "__main__":
    main()
