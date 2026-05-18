"""Stage 3 polish 2 fix: привести src в statement_html / solution_html / images_json
к локальным path соответствующим parser/assets/.

Контекст: после Stage 2 schema rebuild (Convention #12 — table-level PK) corpus.db
перегенерировалась из JSONL, где `<img src=...>` хранится в исходной форме как
sdamgia её отдаёт. Старый fix_illustration_extensions.py трогал только имена
файлов на диске (.png → .svg), но БД пересобиралась поверх — UPDATE'ы пропали.

После Stage 3 fix мы подключили parser/assets/ в Android assets через sourceSets,
поэтому Android может найти `_formulas/XX/HASH.svg` и `<sid>/img_N.svg` если
HTML на них ссылается. Но в БД оказалось:
  - 688 ссылок `<sid>/img_N.png` в statement_html (на диске .svg)
  - 2822 ссылки `<sid>/img_N.png` в solution_html
  - 28K ссылок `/img/exclamation.png` и др. UI-иконки sdamgia — не нужны
  - 18 ссылок `/get_file?id=N` — попытаемся сматчить через data-original-src
  - 483 ссылки `https://...sdamgia.ru/formula/svg/XX/HASH.svg` — есть локально

Что делаем (идемпотентно):
  1. Для каждого <img>:
     a. Если src матчит "<digits>/img_N.<ext>" и файл с этим расширением
        не существует на диске — попробовать .svg/.png/.jpg/.jpeg/.gif, заменить.
     b. Если src — абсолютный URL формулы (https://...sdamgia.ru/formula/svg/XX/HASH.svg) —
        заменить на относительный _formulas/XX/HASH.svg.
     c. Если src — UI-иконка sdamgia (/img/...) — удалить весь <img> тег.
     d. Если src — /get_file?id=N или https://...get_file?id=N — попытаться
        взять путь из соседнего images_json задачи; если не получается — удалить.
  2. UPDATE problems.statement_html, problems.images_json, solutions.solution_html
     in-place. Atomic transaction.

Запуск:
    python parser/scrapers/fix_html_image_refs.py
"""
from __future__ import annotations

import json
import os
import re
import sqlite3
import sys
from pathlib import Path
from typing import Optional

PARSER_ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = PARSER_ROOT / "assets"
DB_PATH = PARSER_ROOT / "corpus.db"

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

# Negative lookbehind: не матчим data-original-src=
SRC_RE = re.compile(r'(?<![-\w])src="([^"]+)"', re.IGNORECASE)
# Полный тег <img ...> (одна строка, до >).
IMG_TAG_RE = re.compile(r"<img\b[^>]*>", re.IGNORECASE)
# Формула на абсолютном URL: https://(any).sdamgia.ru/formula/svg/XX/HASH.svg
FORMULA_ABS_RE = re.compile(
    r"^https?://[\w.-]*sdamgia\.ru/formula/svg/([0-9a-f]{2})/([0-9a-f]{32})\.svg$",
    re.IGNORECASE,
)
# UI-иконки sdamgia
UI_ICON_RE = re.compile(r"^/img/[^/]+$", re.IGNORECASE)
# get_file?id=N (рел/абс)
GET_FILE_RE = re.compile(r"^(?:https?://[\w.-]*sdamgia\.ru)?/get_file\?id=(\d+)$", re.IGNORECASE)
# <sid>/img_N.<ext>
SID_PATH_RE = re.compile(r"^(\d+)/(img_\d+)\.(\w+)$", re.IGNORECASE)

EXT_CANDIDATES = (".svg", ".png", ".jpg", ".jpeg", ".gif")


def find_disk_ext(rel_path_without_ext: str) -> Optional[str]:
    """rel = '27238/img_1' — ищем '27238/img_1.{svg,png,jpg,...}' на диске.

    Возвращает расширение с точкой, или None если ничего не найдено.
    """
    for ext in EXT_CANDIDATES:
        if (ASSETS_DIR / (rel_path_without_ext + ext)).is_file():
            return ext
    return None


def resolve_get_file(images_json_paths: list[str], src_get_file: str) -> Optional[str]:
    """Эвристика: get_file?id=N — это первая (или единственная) иллюстрация задачи.

    Если в images_json есть хотя бы один путь не из _formulas/ — берём его.
    Это работает потому что get_file в БД встречается всего 18 раз и почти всегда
    как единственная иллюстрация задачи.
    """
    illustrations = [p for p in images_json_paths if not p.startswith("_formulas/")]
    if len(illustrations) == 1:
        return illustrations[0]
    return None


def fix_one_html(html: str, images_json_paths: list[str]) -> tuple[str, dict[str, int]]:
    """Прогоняет один HTML-блок, возвращает (новый_html, stat-counter).

    stat-counter ключи:
      ext_fixed, formula_abs_fixed, ui_icon_removed,
      get_file_fixed, get_file_removed, unchanged
    """
    stat = {
        "ext_fixed": 0,
        "formula_abs_fixed": 0,
        "ui_icon_removed": 0,
        "get_file_fixed": 0,
        "get_file_removed": 0,
        "unchanged": 0,
    }

    def replace_img(m: "re.Match[str]") -> str:
        tag = m.group(0)
        src_m = SRC_RE.search(tag)
        if not src_m:
            stat["unchanged"] += 1
            return tag
        src = src_m.group(1)

        # 1. UI-иконка sdamgia → удалить весь тег.
        if UI_ICON_RE.match(src):
            stat["ui_icon_removed"] += 1
            return ""

        # 2. Абсолютный URL формулы → перевести на _formulas/.
        fm = FORMULA_ABS_RE.match(src)
        if fm:
            new_src = f"_formulas/{fm.group(1).lower()}/{fm.group(2).lower()}.svg"
            stat["formula_abs_fixed"] += 1
            return tag.replace(f'src="{src}"', f'src="{new_src}"', 1)

        # 3. get_file?id=N → попытка найти локально.
        if GET_FILE_RE.match(src):
            resolved = resolve_get_file(images_json_paths, src)
            if resolved:
                stat["get_file_fixed"] += 1
                return tag.replace(f'src="{src}"', f'src="{resolved}"', 1)
            else:
                stat["get_file_removed"] += 1
                return ""

        # 4. <sid>/img_N.<ext> с неправильным расширением.
        sm = SID_PATH_RE.match(src)
        if sm:
            sid, name, ext = sm.group(1), sm.group(2), sm.group(3)
            full_now = ASSETS_DIR / sid / f"{name}.{ext.lower()}"
            if full_now.is_file():
                stat["unchanged"] += 1
                return tag
            real_ext = find_disk_ext(f"{sid}/{name}")
            if real_ext:
                new_src = f"{sid}/{name}{real_ext}"
                stat["ext_fixed"] += 1
                return tag.replace(f'src="{src}"', f'src="{new_src}"', 1)
            else:
                # Файл вообще пропал — оставим как есть (логи покажут).
                stat["unchanged"] += 1
                return tag

        stat["unchanged"] += 1
        return tag

    new_html = IMG_TAG_RE.sub(replace_img, html)
    return new_html, stat


def fix_one_images_json(images_json: str) -> tuple[str, dict[str, int]]:
    """Аналогично для images_json (только ext_fix)."""
    stat = {"ext_fixed": 0, "unchanged": 0}
    try:
        paths = json.loads(images_json)
    except Exception:
        return images_json, stat
    if not isinstance(paths, list):
        return images_json, stat
    changed = False
    new_paths: list[str] = []
    for p in paths:
        sm = SID_PATH_RE.match(p)
        if sm:
            sid, name, ext = sm.group(1), sm.group(2), sm.group(3)
            full_now = ASSETS_DIR / sid / f"{name}.{ext.lower()}"
            if not full_now.is_file():
                real_ext = find_disk_ext(f"{sid}/{name}")
                if real_ext:
                    new_p = f"{sid}/{name}{real_ext}"
                    stat["ext_fixed"] += 1
                    new_paths.append(new_p)
                    changed = True
                    continue
        new_paths.append(p)
        stat["unchanged"] += 1
    if changed:
        return json.dumps(new_paths, ensure_ascii=False), stat
    return images_json, stat


def main() -> int:
    if not DB_PATH.exists():
        print(f"!! БД не найдена: {DB_PATH}")
        return 1

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    aggregate = {
        "statement": {},
        "solution": {},
        "images_json": {},
    }

    # 1. statement_html + images_json
    print("[1] обрабатываем problems.statement_html + images_json ...")
    rows = conn.execute(
        "SELECT id, sdamgia_id, statement_html, images_json FROM problems"
    ).fetchall()
    n_html_updated = 0
    n_ij_updated = 0
    for r in rows:
        pid = r["id"]
        old_html = r["statement_html"]
        old_ij = r["images_json"] or "[]"
        ij_paths: list[str] = []
        try:
            ij_paths = json.loads(old_ij) if old_ij else []
        except Exception:
            pass

        new_html, stat_html = fix_one_html(old_html, ij_paths)
        for k, v in stat_html.items():
            aggregate["statement"][k] = aggregate["statement"].get(k, 0) + v
        new_ij, stat_ij = fix_one_images_json(old_ij)
        for k, v in stat_ij.items():
            aggregate["images_json"][k] = aggregate["images_json"].get(k, 0) + v

        if new_html != old_html:
            conn.execute(
                "UPDATE problems SET statement_html = ? WHERE id = ?",
                (new_html, pid),
            )
            n_html_updated += 1
        if new_ij != old_ij:
            conn.execute(
                "UPDATE problems SET images_json = ? WHERE id = ?",
                (new_ij, pid),
            )
            n_ij_updated += 1

    print(f"  statement_html обновлено в {n_html_updated} задачах")
    print(f"  images_json обновлено в {n_ij_updated} задачах")
    print(f"  stats statement: {aggregate['statement']}")
    print(f"  stats images_json: {aggregate['images_json']}")

    # 2. solution_html
    print()
    print("[2] обрабатываем solutions.solution_html ...")
    rows = conn.execute(
        "SELECT s.problem_id, s.solution_html, p.images_json "
        "FROM solutions s JOIN problems p ON p.id = s.problem_id"
    ).fetchall()
    n_sol_updated = 0
    for r in rows:
        pid = r["problem_id"]
        old_html = r["solution_html"]
        ij_paths: list[str] = []
        try:
            ij_paths = json.loads(r["images_json"] or "[]")
        except Exception:
            pass
        new_html, stat = fix_one_html(old_html, ij_paths)
        for k, v in stat.items():
            aggregate["solution"][k] = aggregate["solution"].get(k, 0) + v
        if new_html != old_html:
            conn.execute(
                "UPDATE solutions SET solution_html = ? WHERE problem_id = ?",
                (new_html, pid),
            )
            n_sol_updated += 1

    print(f"  solution_html обновлено в {n_sol_updated} строках")
    print(f"  stats solution: {aggregate['solution']}")

    conn.commit()
    conn.close()
    print()
    print("OK. Не забудь скопировать parser/corpus.db в android/app/src/main/assets/.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
