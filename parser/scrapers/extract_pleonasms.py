"""
Извлекает плеоназмы из задач №6 русского (corpus.db).

Структура: инструкция "исключите лишнее слово" + ровно одно предложение в <b>...</b>.
Фильтр — только "исключить", не "заменить".

answer = лишнее слово, которое нужно удалить.

Out: parser/data/pleonasms.json
"""

import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = ROOT / "corpus.db"
OUT_PATH = ROOT / "data" / "pleonasms.json"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def strip_tags(html: str) -> str:
    text = re.sub(r"<[^>]+>", " ", html)
    text = text.replace("\xa0", " ").replace("&nbsp;", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def extract_sentence_block(html: str) -> str | None:
    """Берём содержимое <b>...</b> в <p class="left_margin"> — там само предложение."""
    # Sdamgia: <p class="left_margin"><b>В крупной фирме ... менеджера.</b></p>
    matches = re.findall(r"<p[^>]*>\s*<b>(.+?)</b>\s*</p>", html, flags=re.DOTALL)
    if matches:
        # Берём самый длинный (это и есть предложение, не пустой <b>)
        candidates = [strip_tags(m) for m in matches]
        candidates = [c for c in candidates if c]
        if candidates:
            return max(candidates, key=len)
    # Fallback — последний <p> после инструкции
    paragraphs = re.findall(r"<p[^>]*>(.+?)</p>", html, flags=re.DOTALL)
    candidates = [strip_tags(p) for p in paragraphs]
    candidates = [c for c in candidates if c and "Отредактируйте" not in c and "Выпишите" not in c and len(c) > 20]
    if candidates:
        return candidates[-1]
    return None


def main():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    rows = cur.execute("""
        SELECT p.id, p.statement_html, p.answer
        FROM problems p
        JOIN subjects s ON p.subject_id=s.id
        JOIN problem_types pt ON p.type_id=pt.id
        WHERE s.slug='rus' AND pt.number=6
    """).fetchall()
    conn.close()

    pleonasms = []
    skipped_replace = 0
    skipped_no_sentence = 0
    skipped_no_match = 0
    skipped_multivariant = 0

    for pid, html, answer in rows:
        text_plain = strip_tags(html)

        # Фильтр: только "исключить"
        if "замен" in text_plain.lower():
            skipped_replace += 1
            continue
        if "исключ" not in text_plain.lower():
            skipped_replace += 1
            continue

        if not answer:
            skipped_no_match += 1
            continue
        answer = answer.strip()
        # Если несколько вариантов ответа через | — берём первый (это редкость в №6)
        if "|" in answer:
            skipped_multivariant += 1
            continue

        sentence = extract_sentence_block(html)
        if not sentence or len(sentence) < 20:
            skipped_no_sentence += 1
            continue

        # Проверка: предложение содержит лишнее слово (грубо — по основе)
        ans_norm = answer.lower().rstrip()
        if ans_norm not in sentence.lower() and ans_norm.rstrip("аеиоыуяюей") not in sentence.lower():
            # Иногда ответ в основе, а в предложении — со склонением
            # Проверим основу длиной >=4
            stem = ans_norm[:max(4, len(ans_norm) - 2)]
            if stem not in sentence.lower():
                skipped_no_match += 1
                continue

        pleonasms.append({
            "problem_id": pid,
            "sentence": sentence,
            "extra_word": answer,
        })

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(pleonasms, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Extracted {len(pleonasms)} pleonasm examples (only 'исключить')")
    print(f"  Skipped (заменить или не указано действие): {skipped_replace}")
    print(f"  Skipped (multivariant '|' answer): {skipped_multivariant}")
    print(f"  Skipped (no sentence): {skipped_no_sentence}")
    print(f"  Skipped (ans not in sentence): {skipped_no_match}")


if __name__ == "__main__":
    main()
