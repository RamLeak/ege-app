"""Постфактум-фикс: переименовать SVG-файлы из .png в .svg + обновить corpus.db.

КОНТЕКСТ. На Stage 1-2 normalize.py угадывал расширение иллюстрации по URL:
`https://math-ege.sdamgia.ru/get_file?id=NNN` — расширения в пути нет, fallback
был `.png`. Но sdamgia для большинства чертежей отдаёт SVG (text/xml).
В результате 3128 из 3510 файлов в parser/assets/{sid}/ — SVG под именем .png.
Браузер их не рендерит.

Скрипт:
1. Перебирает parser/assets/{sid}/*.png, читает magic bytes.
2. Если содержимое — SVG (начинается с `<?xml` ... `<svg`) → переименовывает в .svg.
3. Накапливает mapping {old_relative_path → new_relative_path}.
4. Обновляет corpus.db: problems.images_json (JSON-substring), problems.statement_html,
   solutions.solution_html — заменяет старые ссылки на новые.

Идемпотентен — повторный запуск ничего не делает (файлы уже .svg).
НЕ трогает парсер, НЕ пересобирает БД с нуля, только точечные UPDATE.

Запуск:
    python parser/scrapers/fix_illustration_extensions.py
"""
from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

PARSER_ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = PARSER_ROOT / "assets"
DB_PATH = PARSER_ROOT / "corpus.db"

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


def detect_real_type(data: bytes) -> str:
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if data.startswith(b"\xff\xd8\xff"):
        return "jpg"
    if data[:6] in (b"GIF87a", b"GIF89a"):
        return "gif"
    head = data[:300].decode("utf-8", errors="ignore").lstrip()
    if head.startswith("<?xml") and "svg" in head[:500].lower():
        return "svg"
    if head.startswith("<svg"):
        return "svg"
    return "unknown"


def scan_and_rename() -> dict[str, str]:
    """Возвращает mapping старого относительного пути на новый.

    Пример: {"27238/img_1.png": "27238/img_1.svg"}.
    """
    renames: dict[str, str] = {}
    for d in ASSETS_DIR.iterdir():
        if not d.is_dir() or d.name == "_formulas":
            continue
        for f in d.iterdir():
            if not f.is_file():
                continue
            ext_name = f.suffix.lower().lstrip(".")
            with f.open("rb") as fh:
                head = fh.read(512)
            real = detect_real_type(head)
            if real == "unknown" or real == ext_name:
                continue
            # Mismatch — переименовываем.
            new_name = f.stem + "." + real
            new_path = f.with_name(new_name)
            if new_path.exists():
                # На всякий случай — если уже есть svg-файл с тем же именем, пропускаем.
                continue
            f.rename(new_path)
            old_rel = f"{d.name}/{f.name}"
            new_rel = f"{d.name}/{new_name}"
            renames[old_rel] = new_rel
    return renames


def apply_db_updates(renames: dict[str, str]) -> dict[str, int]:
    """Обновляет corpus.db: images_json, statement_html, solution_html.

    Replace через простую substring — пути уникальные ({sid}/img_N.{ext}),
    коллизий с другими строками не будет.
    """
    if not DB_PATH.exists():
        print("!! corpus.db не найден — пропускаем DB updates")
        return {}

    counts = {"images_json": 0, "statement_html": 0, "solution_html": 0}
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row

    # 1. images_json — у каждой задачи отдельная JSON-строка.
    for row in conn.execute(
        "SELECT id, images_json FROM problems "
        "WHERE images_json IS NOT NULL AND images_json != '[]'"
    ).fetchall():
        ij = row["images_json"]
        new_ij = ij
        changed = False
        for old, new in renames.items():
            # Заменяем '"27238/img_1.png"' → '"27238/img_1.svg"' (с кавычками
            # чтобы не задеть фрагменты внутри других строк).
            needle = f'"{old}"'
            replacement = f'"{new}"'
            if needle in new_ij:
                new_ij = new_ij.replace(needle, replacement)
                changed = True
        if changed:
            conn.execute("UPDATE problems SET images_json = ? WHERE id = ?",
                         (new_ij, row["id"]))
            counts["images_json"] += 1

    # 2. statement_html — везде, где src="...{sid}/img_N.png".
    for row in conn.execute("SELECT id, statement_html FROM problems").fetchall():
        s = row["statement_html"]
        changed = False
        for old, new in renames.items():
            # В HTML src=" — без кавычек вокруг пути; используем дословное вхождение пути.
            if old in s:
                s = s.replace(old, new)
                changed = True
        if changed:
            conn.execute("UPDATE problems SET statement_html = ? WHERE id = ?",
                         (s, row["id"]))
            counts["statement_html"] += 1

    # 3. solution_html.
    for row in conn.execute("SELECT problem_id, solution_html FROM solutions").fetchall():
        s = row["solution_html"]
        changed = False
        for old, new in renames.items():
            if old in s:
                s = s.replace(old, new)
                changed = True
        if changed:
            conn.execute(
                "UPDATE solutions SET solution_html = ? WHERE problem_id = ?",
                (s, row["problem_id"]),
            )
            counts["solution_html"] += 1

    conn.commit()
    conn.close()
    return counts


def main() -> int:
    print("[1] переименовываем файлы...")
    renames = scan_and_rename()
    print(f"    переименовано: {len(renames)}")
    if not renames:
        print("nothing to do — exit")
        return 0
    # Покажем 3 примера.
    for old, new in list(renames.items())[:3]:
        print(f"    {old}  →  {new}")

    print()
    print("[2] обновляем corpus.db...")
    counts = apply_db_updates(renames)
    print(f"    problems.images_json UPDATE: {counts.get('images_json', 0)}")
    print(f"    problems.statement_html UPDATE: {counts.get('statement_html', 0)}")
    print(f"    solutions.solution_html UPDATE: {counts.get('solution_html', 0)}")
    print()
    print("OK. Не забудь перегенерировать view_corpus.html (python parser/export_view.py).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
