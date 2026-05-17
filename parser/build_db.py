"""Stage 3: собрать corpus.db из артефактов парсера.

Источники:
- parser/math.jsonl              4863 задачи математики
- parser/russian.jsonl            5409 задач русского
- parser/russian_rules.jsonl      36 уникальных правил
- parser/russian_problem_meta.jsonl  per-problem source/difficulty/rule_hash
- parser/assets/_formulas/        SVG-формулы (общий пул)
- parser/assets/{sdamgia_id}/     иллюстрации (per-problem)

Схема — точно по CLAUDE.md секция «Схема БД». При запуске пересоздаёт БД
полностью (DROP + CREATE). На выходе:
- parser/corpus.db  — SQLite база со всеми задачами, решениями, правилами и FTS5-индексом.
"""
from __future__ import annotations

import json
import sqlite3
import sys
import time
from pathlib import Path
from typing import Optional

PARSER_ROOT = Path(__file__).resolve().parent
DB_PATH = PARSER_ROOT / "corpus.db"
MATH_JSONL = PARSER_ROOT / "math.jsonl"
RUSSIAN_JSONL = PARSER_ROOT / "russian.jsonl"
RULES_JSONL = PARSER_ROOT / "russian_rules.jsonl"
META_JSONL = PARSER_ROOT / "russian_problem_meta.jsonl"
ASSETS_DIR = PARSER_ROOT / "assets"

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

SCHEMA_SQL = """
CREATE TABLE subjects (
    id INTEGER PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    sdamgia_subdomain TEXT NOT NULL
);

CREATE TABLE problem_types (
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    number INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    is_supplementary INTEGER NOT NULL DEFAULT 0,
    UNIQUE(subject_id, number, title, is_supplementary)
);

CREATE TABLE problem_subtypes (
    id INTEGER PRIMARY KEY,
    type_id INTEGER NOT NULL REFERENCES problem_types(id),
    kes_code TEXT,
    title TEXT NOT NULL,
    sdamgia_category_id INTEGER UNIQUE,   -- параметр /test?category_id=N; UNIQUE через оба предмета (sdamgia использует глобальный пул).
    UNIQUE(type_id, title)
);
CREATE INDEX idx_subtypes_sdamgia ON problem_subtypes(sdamgia_category_id);

CREATE TABLE problems (
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    sdamgia_id TEXT NOT NULL,
    prototype_id TEXT,
    type_id INTEGER NOT NULL REFERENCES problem_types(id),
    subtype_id INTEGER REFERENCES problem_subtypes(id),
    statement_html TEXT NOT NULL,
    answer TEXT,
    answer_format TEXT,
    images_json TEXT,
    source TEXT,
    difficulty TEXT,
    scraped_at TEXT NOT NULL,
    raw_hash TEXT NOT NULL,
    UNIQUE(subject_id, sdamgia_id)   -- 9 коллизий между math и rus (низкие ID 902-915 и др., историческое наследие sdamgia)
);
CREATE INDEX idx_problems_subject ON problems(subject_id);
CREATE INDEX idx_problems_type ON problems(type_id);
CREATE INDEX idx_problems_subtype ON problems(subtype_id);

CREATE TABLE solutions (
    problem_id INTEGER PRIMARY KEY REFERENCES problems(id),
    solution_html TEXT NOT NULL,
    explanation_text TEXT
);

CREATE TABLE rules (
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    title TEXT NOT NULL,
    content_html TEXT NOT NULL,
    source TEXT,
    rule_hash TEXT NOT NULL UNIQUE
);

CREATE TABLE problem_rules (
    problem_id INTEGER NOT NULL REFERENCES problems(id),
    rule_id INTEGER NOT NULL REFERENCES rules(id),
    PRIMARY KEY (problem_id, rule_id)
);
CREATE INDEX idx_problem_rules_rule ON problem_rules(rule_id);

CREATE TABLE user_progress (
    problem_id INTEGER PRIMARY KEY REFERENCES problems(id),
    status TEXT NOT NULL DEFAULT 'not_started',
    user_answer TEXT,
    attempts INTEGER DEFAULT 0,
    last_attempt_at TEXT,
    flagged INTEGER DEFAULT 0,
    used_ai INTEGER DEFAULT 0
);

CREATE TABLE ai_conversations (
    id INTEGER PRIMARY KEY,
    problem_id INTEGER NOT NULL REFERENCES problems(id),
    user_question TEXT NOT NULL,
    ai_response TEXT NOT NULL,
    prompt_hash TEXT NOT NULL UNIQUE,
    model TEXT NOT NULL,
    tokens_in INTEGER,
    tokens_out INTEGER,
    cost_usd REAL,
    created_at TEXT NOT NULL
);

CREATE TABLE error_atoms (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    related_subtype_ids TEXT,
    next_review_at TEXT,
    review_interval_days INTEGER DEFAULT 1,
    times_failed INTEGER DEFAULT 0
);

CREATE TABLE mock_exams (
    id INTEGER PRIMARY KEY,
    subject_id INTEGER NOT NULL REFERENCES subjects(id),
    source TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    raw_score INTEGER,
    scaled_score INTEGER,
    problem_ids_json TEXT NOT NULL,
    answers_json TEXT
);

CREATE TABLE daily_streak (
    date TEXT PRIMARY KEY,
    problems_solved INTEGER NOT NULL,
    streak_value INTEGER NOT NULL
);

CREATE VIRTUAL TABLE problems_fts USING fts5(
    statement_html, content=problems, content_rowid=id
);
"""


def iter_jsonl(path: Path):
    if not path.exists():
        return
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            yield json.loads(line)


def scan_illustrations(sdamgia_id: str) -> list[str]:
    """Список путей к иллюстрациям задачи относительно assets/."""
    d = ASSETS_DIR / sdamgia_id
    if not d.is_dir():
        return []
    return sorted(f"{sdamgia_id}/{p.name}" for p in d.iterdir() if p.is_file())


def main() -> int:
    if DB_PATH.exists():
        DB_PATH.unlink()
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.executescript(SCHEMA_SQL)

    t_start = time.monotonic()

    # ---- 1. Subjects ----
    subjects = [
        ("mathb", "Математика профильная", "math-ege.sdamgia.ru"),
        ("rus", "Русский язык", "rus-ege.sdamgia.ru"),
    ]
    conn.executemany(
        "INSERT INTO subjects (slug, title, sdamgia_subdomain) VALUES (?, ?, ?)",
        subjects,
    )
    subjects_map: dict[str, int] = {
        row["slug"]: row["id"] for row in conn.execute("SELECT id, slug FROM subjects").fetchall()
        for row in [{"id": row[0], "slug": row[1]}]
    }
    # Лучше через прямой fetch:
    subjects_map = {slug: sid for sid, slug in conn.execute("SELECT id, slug FROM subjects")}
    print(f"[subjects] {len(subjects_map)}: {subjects_map}")

    # ---- 2. Pass 1: пробежаться по обоим JSONL, собрать unique типы и подвиды ----
    # Структура types_seen: {(subject_slug, type_number, is_supp): {"title": str}}
    # Структура subtypes_seen: {sdamgia_category_id: {"subject_slug", "type_number",
    #                                                  "is_supp", "title"}}
    types_seen: dict[tuple, str] = {}
    subtypes_seen: dict[int, dict] = {}

    def scan_jsonl_for_taxonomy(path: Path):
        for row in iter_jsonl(path):
            subj = row["subject_slug"]
            tn = row["type_number"]
            tt = row["type_title"]
            ts = bool(row["is_supplementary"])
            cat_id = row["subtype_category_id"]
            cat_title = row["subtype_title"]
            key = (subj, tn, tt, int(ts))
            if key not in types_seen:
                types_seen[key] = tt
            if cat_id not in subtypes_seen:
                subtypes_seen[cat_id] = {
                    "subject_slug": subj, "type_number": tn, "type_title": tt,
                    "is_supp": int(ts), "title": cat_title,
                }

    scan_jsonl_for_taxonomy(MATH_JSONL)
    scan_jsonl_for_taxonomy(RUSSIAN_JSONL)
    print(f"[taxonomy] уникальных типов: {len(types_seen)}, подвидов: {len(subtypes_seen)}")

    # ---- 3. INSERT problem_types ----
    types_map: dict[tuple, int] = {}  # (subject_slug, type_number, type_title, is_supp) → type_id
    for (subj, tn, tt, ts), _ in sorted(types_seen.items()):
        cur = conn.execute(
            "INSERT INTO problem_types (subject_id, number, title, is_supplementary) "
            "VALUES (?, ?, ?, ?)",
            (subjects_map[subj], tn, tt, ts),
        )
        types_map[(subj, tn, tt, ts)] = cur.lastrowid

    # ---- 4. INSERT problem_subtypes ----
    subtypes_map: dict[int, int] = {}  # sdamgia_category_id → subtype_id
    for cat_id, sub in sorted(subtypes_seen.items()):
        type_id = types_map[(sub["subject_slug"], sub["type_number"], sub["type_title"], sub["is_supp"])]
        cur = conn.execute(
            "INSERT INTO problem_subtypes (type_id, title, sdamgia_category_id) "
            "VALUES (?, ?, ?)",
            (type_id, sub["title"], cat_id),
        )
        subtypes_map[cat_id] = cur.lastrowid

    print(f"[insert] problem_types: {len(types_map)}, problem_subtypes: {len(subtypes_map)}")
    conn.commit()

    # ---- 5. INSERT problems + solutions ----
    # Ключ problems_map — (subject_slug, sdamgia_id), потому что 9 ID коллизий между math и rus.
    problems_map: dict[tuple[str, str], int] = {}
    n_problems = 0
    n_solutions = 0

    def import_problems(path: Path, subject_slug: str) -> tuple[int, int]:
        nonlocal n_problems, n_solutions
        local_p = 0
        local_s = 0
        subject_id = subjects_map[subject_slug]
        for row in iter_jsonl(path):
            sid = str(row["sdamgia_id"])
            type_id = types_map[(
                row["subject_slug"], row["type_number"], row["type_title"],
                int(bool(row["is_supplementary"])),
            )]
            subtype_id = subtypes_map.get(row["subtype_category_id"])

            # images_json = formulas (из JSONL) + illustrations (из assets/{sid}/)
            formula_paths = row.get("formula_paths") or []
            illustrations = scan_illustrations(sid)
            images = formula_paths + illustrations
            images_json = json.dumps(images, ensure_ascii=False) if images else None

            cur = conn.execute(
                "INSERT INTO problems (subject_id, sdamgia_id, type_id, subtype_id, statement_html, "
                "answer, answer_format, images_json, scraped_at, raw_hash) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (subject_id, sid, type_id, subtype_id, row["statement_html"],
                 row.get("answer_text"), row.get("answer_format"),
                 images_json, row["scraped_at"], row["raw_hash"]),
            )
            problem_id = cur.lastrowid
            problems_map[(subject_slug, sid)] = problem_id
            local_p += 1

            if row.get("solution_html"):
                conn.execute(
                    "INSERT INTO solutions (problem_id, solution_html) VALUES (?, ?)",
                    (problem_id, row["solution_html"]),
                )
                local_s += 1
            if local_p % 1000 == 0:
                conn.commit()
                print(f"  [{subject_slug}] импортировано {local_p} задач...")
        conn.commit()
        n_problems += local_p
        n_solutions += local_s
        return local_p, local_s

    math_n, math_s = import_problems(MATH_JSONL, "mathb")
    print(f"[insert] math problems: {math_n}, solutions: {math_s}")
    rus_n, rus_s = import_problems(RUSSIAN_JSONL, "rus")
    print(f"[insert] russian problems: {rus_n}, solutions: {rus_s}")
    print(f"[insert] всего problems: {n_problems}, solutions: {n_solutions}")

    # ---- 6. INSERT rules + problem_rules ----
    rules_map: dict[str, int] = {}  # rule_hash → rule_id
    n_rules = 0
    rus_subject_id = subjects_map["rus"]
    for r in iter_jsonl(RULES_JSONL):
        # Доминирующий источник правила (для UI отображения).
        sources = r.get("sources") or {}
        top_source = max(sources.items(), key=lambda x: x[1])[0] if sources else None
        cur = conn.execute(
            "INSERT INTO rules (subject_id, title, content_html, source, rule_hash) "
            "VALUES (?, ?, ?, ?, ?)",
            (rus_subject_id, r["rule_title"], r["content_html"], top_source, r["rule_hash"]),
        )
        rules_map[r["rule_hash"]] = cur.lastrowid
        n_rules += 1
    print(f"[insert] rules: {n_rules}")
    conn.commit()

    # ---- 7. Per-problem meta → UPDATE problems + INSERT problem_rules ----
    n_meta_applied = 0
    n_problem_rules = 0
    missing_problem = 0
    missing_rule = 0
    for m in iter_jsonl(META_JSONL):
        sid = m["sdamgia_id"]
        # russian_problem_meta — только для русского, ищем по (rus, sid).
        pid = problems_map.get(("rus", sid))
        if pid is None:
            missing_problem += 1
            continue
        # UPDATE source/difficulty (даже если пустые — пиши NULL, не пустую строку).
        source = m.get("source") or None
        difficulty = m.get("difficulty") or None
        if source or difficulty:
            conn.execute(
                "UPDATE problems SET source = ?, difficulty = ? WHERE id = ?",
                (source, difficulty, pid),
            )
            n_meta_applied += 1
        rh = m.get("rule_hash") or ""
        if rh:
            rid = rules_map.get(rh)
            if rid is None:
                missing_rule += 1
                continue
            conn.execute(
                "INSERT OR IGNORE INTO problem_rules (problem_id, rule_id) VALUES (?, ?)",
                (pid, rid),
            )
            n_problem_rules += 1
    print(f"[insert] problems с meta: {n_meta_applied}, problem_rules: {n_problem_rules}")
    if missing_problem or missing_rule:
        print(f"  ! missing_problem: {missing_problem}, missing_rule: {missing_rule}")
    conn.commit()

    # ---- 8. FTS5 индекс ----
    conn.execute(
        "INSERT INTO problems_fts (rowid, statement_html) "
        "SELECT id, statement_html FROM problems"
    )
    fts_count = conn.execute("SELECT COUNT(*) FROM problems_fts").fetchone()[0]
    print(f"[fts5] indexed: {fts_count}")
    conn.commit()

    # ---- 9. Integrity checks ----
    print()
    print("=== INTEGRITY CHECKS ===")
    # PRAGMA integrity_check
    ic = conn.execute("PRAGMA integrity_check").fetchone()[0]
    print(f"integrity_check: {ic}")
    # FK check
    fk_issues = conn.execute("PRAGMA foreign_key_check").fetchall()
    print(f"foreign_key_check: {len(fk_issues)} issues" + (f" — {fk_issues[:5]}" if fk_issues else ""))

    # Orphan: solutions без problems
    orphan_solutions = conn.execute(
        "SELECT COUNT(*) FROM solutions s LEFT JOIN problems p ON s.problem_id = p.id "
        "WHERE p.id IS NULL"
    ).fetchone()[0]
    print(f"orphan solutions: {orphan_solutions}")
    # Orphan: problem_rules без problems или rules
    orphan_pr = conn.execute(
        "SELECT COUNT(*) FROM problem_rules pr "
        "LEFT JOIN problems p ON pr.problem_id = p.id "
        "LEFT JOIN rules r ON pr.rule_id = r.id "
        "WHERE p.id IS NULL OR r.id IS NULL"
    ).fetchone()[0]
    print(f"orphan problem_rules: {orphan_pr}")

    # Vacuum для компактности.
    conn.commit()
    conn.execute("VACUUM")
    conn.close()

    # ---- 10. Финальный отчёт ----
    elapsed = time.monotonic() - t_start
    size_mb = DB_PATH.stat().st_size / 1024 / 1024
    print()
    print("=== ИТОГ ===")
    print(f"corpus.db: {size_mb:.2f} MB")
    print(f"wall time: {elapsed:.1f}с")

    # Снова открыть для отчёта по таблицам.
    conn = sqlite3.connect(DB_PATH)
    tables = [
        "subjects", "problem_types", "problem_subtypes",
        "problems", "solutions", "rules", "problem_rules",
        "user_progress", "ai_conversations", "error_atoms",
        "mock_exams", "daily_streak", "problems_fts",
    ]
    print()
    print(f"{'table':<22} rows")
    for t in tables:
        n = conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
        print(f"  {t:<20} {n}")
    # Distribution по предметам
    print()
    print("problems по предметам:")
    for slug, n in conn.execute(
        "SELECT s.slug, COUNT(p.id) FROM problems p "
        "JOIN problem_types t ON p.type_id = t.id "
        "JOIN subjects s ON t.subject_id = s.id "
        "GROUP BY s.slug ORDER BY s.slug"
    ):
        print(f"  {slug}: {n}")
    # Топ-5 правил по числу привязок
    print()
    print("Топ-5 правил по problem_rules:")
    for title, n in conn.execute(
        "SELECT r.title, COUNT(pr.problem_id) cnt FROM rules r "
        "LEFT JOIN problem_rules pr ON r.id = pr.rule_id "
        "GROUP BY r.id ORDER BY cnt DESC LIMIT 5"
    ):
        print(f"  {n:>4} — «{title[:70]}»")
    conn.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
