"""
Собирает все слова из тренажёров в один JSON-список для pre-gen объяснений.

Источники (реальная структура, не как в spec):
- parser/accent_words.json — структура {categories: [{id, title, words: [{word, stressed_index}]}]}
- parser/word_blanks.json — {types: {"9"-"12": {title, words: [{masked, answer, full, rule_hint}]}}}

Дополнительно (если есть):
- parser/data/paronyms.json — {sentence, wrong_word, correct_word}
- parser/data/pleonasms.json — {sentence, extra_word}
- parser/data/grammar_errors.json — {sentence, error_word}
- parser/data/short_multiplication_formulas.json, derivatives.json, и т.д. — math.

Out: JSON-массив в stdout: [{word, kind, subtype, hint?}, ...].
Уже существующие записи (проверка по trainer_explanations) фильтруются.

Usage:
  python parser/scrapers/prepare_explanations_corpus.py > /tmp/all_words.json
  python parser/scrapers/prepare_explanations_corpus.py --kind accent > /tmp/accent.json
"""

import argparse
import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent       # parser/
PROJECT_ROOT = ROOT.parent                          # ege-app/
DB_PATH = ROOT / "corpus.db"
DATA_DIR = ROOT / "data"                            # parser/data/ (для новых json)
# accent_words.json и word_blanks.json исторически лежат в корне (accent) и в parser/ (blanks).


def ensure_table(conn):
    cur = conn.cursor()
    # Convention #12 — table-level PRIMARY KEY чтобы Room считал id NOT NULL.
    cur.execute("""
        CREATE TABLE IF NOT EXISTS trainer_explanations (
            id INTEGER NOT NULL,
            word TEXT NOT NULL,
            kind TEXT NOT NULL,
            subtype TEXT NOT NULL,
            explanation TEXT,
            rule TEXT,
            examples TEXT,
            mnemonic TEXT,
            generated_at INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(id),
            UNIQUE(word, kind, subtype)
        )
    """)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_explanations_lookup ON trainer_explanations(word, kind)")
    conn.commit()


def already_in_db(conn):
    cur = conn.cursor()
    return {(r[0], r[1], r[2]) for r in cur.execute("SELECT word, kind, subtype FROM trainer_explanations")}


def collect_accent(already):
    path = PROJECT_ROOT / "accent_words.json"
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for cat in data.get("categories", []):
        subtype = cat.get("id", "unknown")
        for w in cat.get("words", []):
            word = w.get("word", "").strip()
            if not word:
                continue
            stressed = w.get("stressed_index")
            if (word, "accent", subtype) in already:
                continue
            out.append({
                "word": word,
                "kind": "accent",
                "subtype": subtype,
                "hint": f"ударный слог по индексу: {stressed}" if stressed is not None else "",
            })
    return out


def collect_word_blank(already):
    path = ROOT / "word_blanks.json"
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for tnum, tdata in data.get("types", {}).items():
        subtype = f"t{tnum}"
        for w in tdata.get("words", []):
            full = w.get("full", "").strip()
            if not full:
                continue
            if (full, "word_blank", subtype) in already:
                continue
            out.append({
                "word": full,
                "kind": "word_blank",
                "subtype": subtype,
                "hint": f"правильная буква: {w.get('answer', '')}; правило: {w.get('rule_hint', '')}",
            })
    return out


def collect_paronyms(already):
    path = DATA_DIR / "paronyms.json"
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for item in data:
        word = f"{item.get('wrong_word','')}->{item.get('correct_word','')}".strip()
        if not word or word == "->":
            continue
        if (word, "paronym", "rus5") in already:
            continue
        out.append({
            "word": word,
            "kind": "paronym",
            "subtype": "rus5",
            "hint": f"в предложении: {item.get('sentence','')[:200]}",
        })
    return out


def collect_pleonasms(already):
    path = DATA_DIR / "pleonasms.json"
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for item in data:
        word = item.get("extra_word", "").strip()
        if not word:
            continue
        if (word, "pleonasm", "rus6") in already:
            continue
        out.append({
            "word": word,
            "kind": "pleonasm",
            "subtype": "rus6",
            "hint": f"в предложении: {item.get('sentence','')[:200]}",
        })
    return out


def collect_grammar_errors(already):
    path = DATA_DIR / "grammar_errors.json"
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for item in data:
        word = item.get("error_word") or "грам-ошибка"
        word = word.strip()
        sentence = item.get("sentence", "")
        if not sentence:
            continue
        key_word = f"{word}::{sentence[:50]}"
        if (key_word, "grammar", "rus7") in already:
            continue
        out.append({
            "word": key_word,
            "kind": "grammar",
            "subtype": "rus7",
            "hint": f"в предложении: {sentence[:200]}",
        })
    return out


def collect_math(already):
    out = []
    for fname, subtype, key in [
        ("trig_values.json", "trig", "angle_deg"),
        ("short_multiplication_formulas.json", "short_mult", "formula"),
        ("log_power_properties.json", "log_power", "left"),
        ("derivatives.json", "derivatives", "function"),
        ("geometric_formulas.json", "geometry", "name"),
    ]:
        path = DATA_DIR / fname
        if not path.exists():
            continue
        items = json.loads(path.read_text(encoding="utf-8"))
        for item in items:
            label = str(item.get(key, "")).strip()
            if not label:
                continue
            if (label, "math", subtype) in already:
                continue
            out.append({
                "word": label,
                "kind": "math",
                "subtype": subtype,
                "hint": json.dumps(item, ensure_ascii=False),
            })
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=["accent", "word_blank", "paronym", "pleonasm", "grammar", "math", "all"], default="all")
    parser.add_argument("--limit", type=int, default=0, help="максимум записей в выводе (0 = без лимита)")
    parser.add_argument("--offset", type=int, default=0)
    args = parser.parse_args()

    conn = sqlite3.connect(DB_PATH)
    ensure_table(conn)
    already = already_in_db(conn)
    conn.close()

    collectors = {
        "accent": collect_accent,
        "word_blank": collect_word_blank,
        "paronym": collect_paronyms,
        "pleonasm": collect_pleonasms,
        "grammar": collect_grammar_errors,
        "math": collect_math,
    }

    if args.kind == "all":
        corpus = []
        for k, fn in collectors.items():
            corpus.extend(fn(already))
    else:
        corpus = collectors[args.kind](already)

    if args.offset:
        corpus = corpus[args.offset:]
    if args.limit:
        corpus = corpus[:args.limit]

    sys.stdout.buffer.write(json.dumps(corpus, ensure_ascii=False, indent=2).encode("utf-8"))
    print(f"\n# Total: {len(corpus)} items (kind={args.kind}, already in DB: {len(already)})", file=sys.stderr)


if __name__ == "__main__":
    main()
