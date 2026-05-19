"""
Batch save trainer_explanations в SQLite. Идемпотентно (INSERT OR REPLACE).

Usage:
  cat batch_with_explanations.json | python parser/scrapers/save_explanations_batch.py
  python parser/scrapers/save_explanations_batch.py < batch.json
"""

import json
import sqlite3
import sys
import time
from pathlib import Path

# Windows: stdin часто cp1251 — пересобираем в UTF-8 чтобы кириллица не ломалась.
if hasattr(sys.stdin, "reconfigure"):
    sys.stdin.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

DB_PATH = Path(__file__).resolve().parent.parent / "corpus.db"


def ensure_table(conn):
    cur = conn.cursor()
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


def main():
    raw = sys.stdin.read()
    # Терпимо к ```json fenced output (хоть subagent не должен так делать)
    raw = raw.strip()
    if raw.startswith("```"):
        # отрезать первую и последнюю строки
        lines = raw.split("\n")
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        raw = "\n".join(lines).strip()

    try:
        items = json.loads(raw)
    except Exception as e:
        print(f"ERROR: invalid JSON: {e}", file=sys.stderr)
        print(f"First 200 chars: {raw[:200]!r}", file=sys.stderr)
        sys.exit(2)

    if not isinstance(items, list):
        print(f"ERROR: expected JSON array, got {type(items).__name__}", file=sys.stderr)
        sys.exit(2)

    conn = sqlite3.connect(DB_PATH)
    ensure_table(conn)
    cur = conn.cursor()

    now = int(time.time())
    inserted = 0
    skipped = 0
    for item in items:
        try:
            word = item.get("word", "").strip()
            kind = item.get("kind", "").strip()
            subtype = item.get("subtype", "").strip()
            if not word or not kind:
                skipped += 1
                continue
            if item.get("explanation", "").strip() == "skip":
                skipped += 1
                continue
            cur.execute("""
                INSERT OR REPLACE INTO trainer_explanations
                (word, kind, subtype, explanation, rule, examples, mnemonic, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                word, kind, subtype,
                item.get("explanation", ""),
                item.get("rule", ""),
                item.get("examples", ""),
                item.get("mnemonic", ""),
                now,
            ))
            inserted += 1
        except Exception as e:
            print(f"Skipped {item.get('word', '?')}: {e}", file=sys.stderr)
            skipped += 1

    conn.commit()
    cur.execute("SELECT COUNT(*) FROM trainer_explanations")
    total = cur.fetchone()[0]
    conn.close()

    print(f"Batch saved: {inserted} inserted, {skipped} skipped. Total in DB: {total}")


if __name__ == "__main__":
    main()
