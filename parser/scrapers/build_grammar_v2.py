"""
Phase 4 Stage P4-D4 — пересборка grammar_errors.json на multi-choice формат.

Старый формат (P4-D): {sentence (5 фраз через ·), error_word, correct_form}.
Новый (P4-D4):       {id, error_type, wrong_sentence, options: [{text, is_correct}]*4}.

Шаги:
1. Извлечь 200-250 задач №7 из corpus.db.
2. Из каждой выбрать ОДНУ фразу с CAPS-словом + нормализовать CAPS в обычный регистр.
3. Определить error_type через регулярки (Convention #79).
4. Сохранить как draft (options=null) в grammar_errors_draft.json.
5. Дистракторы по 4 варианта на задачу генерируются inline через Opus 4.7
   в основном контексте Claude Code (не через Python). После заполнения
   options — финальный JSON копируется в grammar_errors.json.

Запуск:
  python parser/scrapers/build_grammar_v2.py             # draft в parser/data/grammar_errors_draft.json
  # после inline-генерации options — пользователь правит draft → grammar_errors.json
"""

import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = ROOT / "corpus.db"
DRAFT_PATH = ROOT / "data" / "grammar_errors_draft.json"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


# Convention #79 — error_type детектируется heuristic-регулярками. Если ни одна не сработала,
# используется generic «Нарушение в построении предложения».
ERROR_TYPE_PATTERNS = [
    (re.compile(r"\b(лежащ|работающ|говорящ|решающ|плывущ|сидящ|стоящ|идущ|висящ|читающ)\w*", re.I),
     "Нарушение в построении предложения с причастным оборотом"),
    (re.compile(r"\b(делая|решая|проходя|встретив|увидев|читая|войдя|сделав|подумав|закончив)\b", re.I),
     "Нарушение в употреблении деепричастного оборота"),
    (re.compile(r"\b(благодаря|согласно|вопреки|наперекор)\b", re.I),
     "Неправильное употребление падежной формы существительного с предлогом"),
    (re.compile(r"\b(как\W+так и|не только\W+но и|если\W+то)\b", re.I),
     "Нарушение в построении предложения с двойными союзами"),
    (re.compile(r"\b(кто\W+\w+ли|все, кто\W+\w+|каждый, кто\W+\w+)\b", re.I),
     "Нарушение связи между подлежащим и сказуемым"),
    (re.compile(r"\b(говорит, что|сказал, что|думает, что|пишет, что)\b", re.I),
     "Нарушение в построении предложения с косвенной речью"),
    (re.compile(r"\b(который|которая|которое|которые)\b", re.I),
     "Нарушение в построении сложноподчинённого предложения"),
    (re.compile(r"\bпо (приезд|прилёт|окончани|завершени|возвращени|приход)", re.I),
     "Неправильное употребление падежной формы существительного с предлогом"),
    (re.compile(r"\b(один из|каждый из|любой из|никто из)\b", re.I),
     "Нарушение связи между подлежащим и сказуемым"),
]


def detect_error_type(sentence: str) -> str:
    for pattern, error_type in ERROR_TYPE_PATTERNS:
        if pattern.search(sentence):
            return error_type
    return "Нарушение в построении предложения"


def strip_tags(html: str) -> str:
    text = re.sub(r"<[^>]+>", " ", html)
    text = text.replace("\xa0", " ").replace("&nbsp;", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def extract_phrases_from_html(html: str) -> list[str]:
    blocks = re.findall(r"<p[^>]*>(.+?)</p>", html, flags=re.DOTALL)
    out = []
    for b in blocks:
        text = strip_tags(b)
        text = re.sub(r"^\d\)\s*", "", text)
        if text and "В одном из выделенных ниже" not in text and "Примечание" not in text:
            out.append(text)
    return out


def lowercase_caps_word(text: str) -> str:
    """Нормализуем CAPS-маркер: первая буква остаётся заглавной если в начале предложения,
    остальные строчные. Это нужно чтобы предложение читалось естественно."""
    def repl(m: re.Match) -> str:
        word = m.group(0)
        return word[0] + word[1:].lower()
    return re.sub(r"\b[А-ЯЁ]{3,}\b", repl, text)


def main():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    rows = cur.execute("""
        SELECT p.id, p.statement_html
        FROM problems p
        JOIN subjects s ON p.subject_id=s.id
        JOIN problem_types pt ON p.type_id=pt.id
        WHERE s.slug='rus' AND pt.number=7
        ORDER BY p.id
        LIMIT 250
    """).fetchall()
    conn.close()

    drafts = []
    skipped = 0
    seen_sentences: set[str] = set()

    for idx, (pid, html) in enumerate(rows):
        phrases = extract_phrases_from_html(html)
        # Берём первую фразу с CAPS-словом и достаточной длиной
        target = None
        for p in phrases:
            if re.search(r"\b[А-ЯЁ]{3,}\b", p) and 25 < len(p) < 180:
                target = p
                break
        if not target:
            skipped += 1
            continue

        # Нормализуем CAPS, обрезаем лишние пробелы, добавляем точку если нет
        sentence = lowercase_caps_word(target).strip()
        if not sentence.endswith((".", "!", "?")):
            sentence = sentence.rstrip(",;") + "."
        sentence = re.sub(r"\s+", " ", sentence).strip()

        if sentence in seen_sentences:
            continue
        seen_sentences.add(sentence)

        error_type = detect_error_type(sentence)

        drafts.append({
            "id": f"grammar_{pid}",
            "problem_id": pid,
            "error_type": error_type,
            "wrong_sentence": sentence,
            "options": None,  # заполняется через Opus 4.7 inline
        })

    DRAFT_PATH.parent.mkdir(parents=True, exist_ok=True)
    DRAFT_PATH.write_text(json.dumps(drafts, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Drafts saved: {len(drafts)} (skipped {skipped} без CAPS-фразы)")
    print(f"  File: {DRAFT_PATH}")

    # Сводка по типам ошибок
    by_type: dict[str, int] = {}
    for d in drafts:
        by_type[d["error_type"]] = by_type.get(d["error_type"], 0) + 1
    print()
    print("Распределение по error_type:")
    for t, n in sorted(by_type.items(), key=lambda x: -x[1]):
        print(f"  {n:3d}  {t}")


if __name__ == "__main__":
    main()
