"""
Фильтрует JSON-массив на stdin: оставляет только те (word, kind, subtype)
которых ещё нет в trainer_explanations.

Usage:
  cat batch.json | python parser/scrapers/filter_unprocessed.py > batch_filtered.json
"""

import json
import sqlite3
import sys
from pathlib import Path

if hasattr(sys.stdin, "reconfigure"):
    sys.stdin.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

DB_PATH = Path(__file__).resolve().parent.parent / "corpus.db"


def main():
    items = json.load(sys.stdin)
    if not isinstance(items, list):
        print("ERROR: expected JSON array", file=sys.stderr)
        sys.exit(2)

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    existing = {(r[0], r[1], r[2]) for r in cur.execute("SELECT word, kind, subtype FROM trainer_explanations")}
    conn.close()

    filtered = [it for it in items if (it.get("word"), it.get("kind"), it.get("subtype")) not in existing]

    sys.stdout.buffer.write(json.dumps(filtered, ensure_ascii=False).encode("utf-8"))
    print(f"\n# Filtered: {len(items)} → {len(filtered)} (skipped {len(items) - len(filtered)} existing)", file=sys.stderr)


if __name__ == "__main__":
    main()
