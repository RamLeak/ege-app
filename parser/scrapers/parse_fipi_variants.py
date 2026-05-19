"""
Phase 4 Stage B1 — генератор pre-bundled вариантов КИМ ФИПИ.

Не парсит реальные PDF с fipi.ru (стабильность нулевая, разметка меняется).
Вместо этого берёт **репрезентативную выборку** из corpus.db по одной
случайной задаче из каждого типа предмета — у всех пользователей одинаковые
варианты (random.seed фиксирован per variant).

Output:
    parser/fipi_variants.json
    android/app/src/main/assets/fipi_variants.json

3 варианта math (по 19 задач) + 3 варианта rus (по 26 задач).
Каждый вариант с фиксированным seed → одинаковые задачи у всех.

Запуск:
    python parser/scrapers/parse_fipi_variants.py
"""

from __future__ import annotations

import json
import random
import sqlite3
from pathlib import Path

VARIANTS_SPEC = [
    # (id, title, subject, year, version, seed, type_range)
    ("math_2024_v1", "Математика профильная · 2024 · Вариант 1", "math", 2024, 1, 20240001, range(1, 20)),
    ("math_2024_v2", "Математика профильная · 2024 · Вариант 2", "math", 2024, 2, 20240002, range(1, 20)),
    ("math_2023_v1", "Математика профильная · 2023 · Вариант 1", "math", 2023, 1, 20230001, range(1, 20)),
    ("rus_2024_v1", "Русский язык · 2024 · Вариант 1", "rus", 2024, 1, 20240101, range(1, 27)),
    ("rus_2024_v2", "Русский язык · 2024 · Вариант 2", "rus", 2024, 2, 20240102, range(1, 27)),
    ("rus_2023_v1", "Русский язык · 2023 · Вариант 1", "rus", 2023, 1, 20230101, range(1, 27)),
]

SUBJECT_TO_SLUG = {"math": "mathb", "rus": "rus"}


def main() -> None:
    here = Path(__file__).resolve().parent
    parser_dir = here.parent
    project_root = parser_dir.parent
    corpus_db = parser_dir / "corpus.db"
    if not corpus_db.exists():
        raise SystemExit(f"corpus.db not found at {corpus_db}")

    conn = sqlite3.connect(str(corpus_db))
    cur = conn.cursor()

    variants: list[dict] = []
    for vid, title, subject, year, version, seed, type_range in VARIANTS_SPEC:
        slug = SUBJECT_TO_SLUG[subject]
        rng = random.Random(seed)
        tasks: list[dict] = []
        position = 1
        for type_number in type_range:
            cur.execute(
                """
                SELECT p.id FROM problems p
                JOIN problem_types pt ON p.type_id = pt.id
                JOIN subjects s ON pt.subject_id = s.id
                WHERE s.slug = ? AND pt.number = ? AND pt.is_supplementary = 0
                """,
                (slug, type_number),
            )
            candidates = [row[0] for row in cur.fetchall()]
            if not candidates:
                # Тип отсутствует в corpus — пропускаем (вариант будет короче).
                continue
            picked = rng.choice(candidates)
            tasks.append(
                {
                    "position": position,
                    "typeNumber": type_number,
                    "problemId": picked,
                }
            )
            position += 1

        variants.append(
            {
                "id": vid,
                "title": title,
                "subject": subject,
                "year": year,
                "version": version,
                "taskCount": len(tasks),
                "tasks": tasks,
            }
        )

    conn.close()

    output = {"version": "1.0", "variants": variants}

    out_parser = parser_dir / "fipi_variants.json"
    out_assets = project_root / "android" / "app" / "src" / "main" / "assets" / "fipi_variants.json"

    out_parser.write_text(
        json.dumps(output, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    out_assets.parent.mkdir(parents=True, exist_ok=True)
    out_assets.write_text(
        json.dumps(output, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Wrote {len(variants)} variants:")
    for v in variants:
        # Title содержит non-ASCII, на cp1251-консоли Windows может упасть.
        # Печатаем только id+count чтобы не было UnicodeEncodeError.
        print(f"  {v['id']}: tasks = {v['taskCount']}")
    print(f"  parser/fipi_variants.json: {out_parser}")
    print(f"  android assets:            {out_assets}")


if __name__ == "__main__":
    main()
