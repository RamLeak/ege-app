# -*- coding: utf-8 -*-
"""
export_math_for_claude.py
=========================
Выгрузка ВСЕХ математических задач (subject_id=1, slug 'mathb') из parser/corpus.db
в формат для прикрепления к проекту Claude.ai (генерация заданий с решениями,
ответами и картинками).

Выход (в <repo>/exports/):
  - math_for_claude_full.jsonl       все 4863 задачи
  - math_for_claude_no_images.jsonl  подмножество без реальных картинок
  - images/img_<sdamgia_id>_<seq>.png реальные картинки (SVG растеризованы в PNG)
  - export_summary.txt               отчёт

Особенности данных corpus.db (выявлено при разведке):
  * MathML в БД НЕТ — все формулы это <img class="tex" alt="...">, alt уже содержит
    читаемый русский текст формулы. Поэтому формулы заменяются их alt-текстом инлайн.
  * Реальные картинки (геом. чертежи, графики) — это <img> БЕЗ класса "tex",
    src вида "<sdamgia_id>/img_N.svg|png". Физически лежат в parser/assets/<sdamgia_id>/.
    ~99.6% реальных картинок — SVG (растеризуются в PNG через resvg-py, шрифты системные).
  * display:none в HTML решений игнорируется: BeautifulSoup.get_text() не учитывает CSS
    (Convention #11 выполняется автоматически).

Повторно запускаемый. По умолчанию делает чистую выгрузку (стирает старые exports/).
    python parser/scrapers/export_math_for_claude.py            # полная свежая выгрузка
    python parser/scrapers/export_math_for_claude.py --skip-existing-images  # не перерендеривать
    python parser/scrapers/export_math_for_claude.py --keep     # не стирать старое
    python parser/scrapers/export_math_for_claude.py --zoom 2.5 # масштаб растеризации SVG

Корень репо определяется по __file__ (скрипт в parser/scrapers/), либо через
переменную окружения EGE_REPO_ROOT (для запуска копии скрипта из другого места).
"""
import argparse
import json
import os
import re
import shutil
import sqlite3
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

# --- корень репозитория ---
_ENV_ROOT = os.environ.get("EGE_REPO_ROOT")
REPO_ROOT = Path(_ENV_ROOT).resolve() if _ENV_ROOT else Path(__file__).resolve().parents[2]
DB_PATH = REPO_ROOT / "parser" / "corpus.db"
ASSETS_DIR = REPO_ROOT / "parser" / "assets"
OUT_DIR = REPO_ROOT / "exports"
IMAGES_DIR = OUT_DIR / "images"
FULL_PATH = OUT_DIR / "math_for_claude_full.jsonl"
NOIMG_PATH = OUT_DIR / "math_for_claude_no_images.jsonl"
SUMMARY_PATH = OUT_DIR / "export_summary.txt"

MATH_SUBJECT_ID = 1
RASTER_EXTS = {"png", "jpg", "jpeg", "gif", "bmp", "webp"}  # растровые — через PIL

# --- зависимости ---
try:
    from bs4 import BeautifulSoup, Comment
except ImportError:
    sys.exit("Нужен beautifulsoup4: python -m pip install beautifulsoup4 lxml")
try:
    from tqdm import tqdm
except ImportError:
    sys.exit("Нужен tqdm: python -m pip install tqdm")
try:
    import resvg_py
    _HAS_RESVG = True
except Exception:
    _HAS_RESVG = False
try:
    from PIL import Image
    _HAS_PIL = True
except Exception:
    _HAS_PIL = False

# выбор парсера bs4
try:
    import lxml  # noqa: F401
    _BS_PARSER = "lxml"
except ImportError:
    _BS_PARSER = "html.parser"

BLOCK_TAGS = {"p", "div", "br", "li", "tr", "table", "ul", "ol",
              "h1", "h2", "h3", "h4", "blockquote", "section"}
_WS_RE = re.compile(r"[ \t  -   　]+")
_NL_RE = re.compile(r"\n{3,}")
_NL_TRIM_RE = re.compile(r"[ \t]*\n[ \t]*")


def normalize_text(text: str) -> str:
    """Схлопывает пробелы, чистит переносы. Абзацы как \\n."""
    text = _WS_RE.sub(" ", text)
    text = _NL_TRIM_RE.sub("\n", text)
    text = _NL_RE.sub("\n\n", text)
    lines = [ln.strip() for ln in text.split("\n")]
    text = "\n".join(lines)
    text = _NL_RE.sub("\n\n", text)
    return text.strip()


class ImageMaterializer:
    """Растеризует/копирует реальные картинки и ведёт учёт."""

    def __init__(self, zoom: float, skip_existing: bool):
        # resvg_py.svg_to_bytes требует целочисленный zoom
        self.zoom = max(1, int(round(zoom)))
        self.skip_existing = skip_existing
        self.missing = []          # (sdamgia_id, src) — файла нет на диске
        self.render_failures = []  # (sdamgia_id, src, error)
        self.copied_png = 0
        self.rasterized = 0
        self.reused = 0

    def materialize(self, sdamgia_id: str, src: str, seq: int):
        """Возвращает (filename, relpath) либо (None, None) если файл отсутствует."""
        abs_src = (ASSETS_DIR / src).resolve()
        ext = src.rsplit(".", 1)[-1].lower() if "." in src else ""
        if not abs_src.is_file():
            # HTML-ссылка может не совпадать с реальным расширением файла
            # (Convention #10: расширение по magic bytes). Пробуем альтернативы.
            stem = src.rsplit(".", 1)[0] if "." in src else src
            found = None
            for alt in ("svg", "png", "gif", "jpg", "jpeg", "webp", "bmp"):
                cand = (ASSETS_DIR / f"{stem}.{alt}").resolve()
                if cand.is_file():
                    found, ext = cand, alt
                    break
            if found is None:
                self.missing.append((sdamgia_id, src))
                return None, None
            abs_src = found

        if ext == "svg":
            target = IMAGES_DIR / f"img_{sdamgia_id}_{seq}.png"
            if target.exists() and self.skip_existing:
                self.reused += 1
                return target.name, f"images/{target.name}"
            if _HAS_RESVG:
                try:
                    svg = abs_src.read_text(encoding="utf-8", errors="replace")
                    # dpi=96 нужен для SVG с физическими единицами (mm/in из CorelDRAW);
                    # на px/безразмерные SVG не влияет (проверено: байт-в-байт идентично).
                    data = resvg_py.svg_to_bytes(svg_string=svg, zoom=self.zoom, dpi=96)
                    if not isinstance(data, (bytes, bytearray)):
                        data = bytes(data)
                    target.write_bytes(data)
                    self.rasterized += 1
                    return target.name, f"images/{target.name}"
                except Exception as e:
                    self.render_failures.append((sdamgia_id, src, f"{type(e).__name__}: {e}"))
            # фолбэк: кладём исходный SVG как есть, чтобы не потерять данные
            fb = IMAGES_DIR / f"img_{sdamgia_id}_{seq}.svg"
            shutil.copyfile(abs_src, fb)
            return fb.name, f"images/{fb.name}"

        if ext in RASTER_EXTS:
            target = IMAGES_DIR / f"img_{sdamgia_id}_{seq}.png"
            if target.exists() and self.skip_existing:
                self.reused += 1
                return target.name, f"images/{target.name}"
            try:
                if ext == "png":
                    shutil.copyfile(abs_src, target)
                elif _HAS_PIL:
                    with Image.open(abs_src) as im:
                        im.convert("RGBA").save(target, "PNG")
                else:
                    fb = IMAGES_DIR / f"img_{sdamgia_id}_{seq}.{ext}"
                    shutil.copyfile(abs_src, fb)
                    return fb.name, f"images/{fb.name}"
                self.copied_png += 1
                return target.name, f"images/{target.name}"
            except Exception as e:
                self.render_failures.append((sdamgia_id, src, f"{type(e).__name__}: {e}"))
                fb = IMAGES_DIR / f"img_{sdamgia_id}_{seq}.{ext}"
                shutil.copyfile(abs_src, fb)
                return fb.name, f"images/{fb.name}"

        # неизвестное расширение — копируем как есть
        fb = IMAGES_DIR / f"img_{sdamgia_id}_{seq}.{ext or 'bin'}"
        shutil.copyfile(abs_src, fb)
        return fb.name, f"images/{fb.name}"


def is_tex(img) -> bool:
    cls = img.get("class") or []
    if isinstance(cls, str):
        cls = cls.split()
    return any("tex" in c for c in cls)


def clean_html(html, sdamgia_id, mat, img_order, src_map):
    """
    Преобразует HTML условия/решения в plain text:
      - <img class="tex">  -> alt-текст инлайн (читаемая формула)
      - реальный <img>     -> маркер [картинка: img_<id>_<seq>.png] + материализация файла
    img_order / src_map общие для условия и решения одной задачи (сквозная нумерация).
    """
    if not html:
        return ""
    soup = BeautifulSoup(html, _BS_PARSER)

    # удалить HTML-комментарии (<!--np-->, <!--rule_info--> и т.п.)
    for c in soup.find_all(string=lambda t: isinstance(t, Comment)):
        c.extract()

    # заменить картинки
    for img in soup.find_all("img"):
        if is_tex(img):
            alt = (img.get("alt") or "").strip()
            img.replace_with(" " + alt + " " if alt else " ")
            continue
        src = img.get("src") or ""
        if not src:
            img.replace_with(" ")
            continue
        if src in src_map:
            filename = src_map[src]
        else:
            seq = len(src_map) + 1
            filename, relpath = mat.materialize(sdamgia_id, src, seq)
            if filename is None:
                filename = f"img_{sdamgia_id}_{seq}.png"
                relpath = None
            src_map[src] = filename
            img_order.append((src, relpath))
        img.replace_with(f" [картинка: {filename}] ")

    # переносы по блочным элементам
    for tag in soup.find_all(BLOCK_TAGS):
        tag.append("\n")

    return normalize_text(soup.get_text())


def human_mb(num_bytes):
    return num_bytes / (1024 * 1024)


def dir_size_bytes(path: Path):
    total = 0
    for root, _dirs, files in os.walk(path):
        for f in files:
            try:
                total += os.path.getsize(os.path.join(root, f))
            except OSError:
                pass
    return total


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zoom", type=float, default=3.0, help="масштаб растеризации SVG (default 3.0)")
    ap.add_argument("--skip-existing-images", action="store_true",
                    help="не перерендеривать уже существующие PNG")
    ap.add_argument("--keep", action="store_true", help="не стирать старые exports/ перед выгрузкой")
    args = ap.parse_args()

    if not DB_PATH.is_file():
        sys.exit(f"corpus.db не найден: {DB_PATH}")
    if not ASSETS_DIR.is_dir():
        sys.exit(f"parser/assets не найден: {ASSETS_DIR}")
    if not _HAS_RESVG:
        print("[WARN] resvg_py недоступен — SVG будут скопированы как .svg (claude.ai не увидит их как картинку)")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if not args.keep and not args.skip_existing_images:
        for f in (FULL_PATH, NOIMG_PATH, SUMMARY_PATH):
            if f.exists():
                f.unlink()
        if IMAGES_DIR.exists():
            shutil.rmtree(IMAGES_DIR)
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)

    con = sqlite3.connect(str(DB_PATH))
    cur = con.cursor()
    cur.execute(
        """
        SELECT p.id, p.sdamgia_id, pt.number, pt.title, ps.title,
               p.statement_html, p.answer, p.difficulty, s.solution_html
        FROM problems p
        JOIN problem_types pt ON pt.id = p.type_id
        LEFT JOIN problem_subtypes ps ON ps.id = p.subtype_id
        LEFT JOIN solutions s ON s.problem_id = p.id
        WHERE p.subject_id = ?
        ORDER BY p.id
        """,
        (MATH_SUBJECT_ID,),
    )
    rows = cur.fetchall()
    con.close()

    mat = ImageMaterializer(zoom=args.zoom, skip_existing=args.skip_existing_images)

    by_number = {}  # number -> [total, with_img, without_img, title]
    total_with_img = 0
    total_real_images = 0

    f_full = FULL_PATH.open("w", encoding="utf-8")
    f_noimg = NOIMG_PATH.open("w", encoding="utf-8")

    for (pid, sdamgia_id, number, type_title, subtype_title,
         statement_html, answer, difficulty, solution_html) in tqdm(rows, desc="export math", unit="task"):
        sdamgia_id = str(sdamgia_id)
        img_order = []   # [(src, relpath)] в порядке появления
        src_map = {}     # src -> filename (дедуп внутри задачи)

        statement = clean_html(statement_html, sdamgia_id, mat, img_order, src_map)
        solution = clean_html(solution_html, sdamgia_id, mat, img_order, src_map)

        image_paths = [rel for (_src, rel) in img_order if rel]
        has_image = len(image_paths) > 0
        total_real_images += len(image_paths)

        obj = {
            "id": pid,
            "sdamgia_id": sdamgia_id,
            "task_number": number,
            "task_title": type_title,
            "subtype_title": subtype_title,
            "statement": statement,
            "answer": answer if answer not in (None, "") else None,
            "solution": solution,
            "has_image": has_image,
            "image_paths": image_paths,
            "difficulty": difficulty if difficulty not in (None, "") else None,
        }
        line = json.dumps(obj, ensure_ascii=False)
        f_full.write(line + "\n")
        if not has_image:
            f_noimg.write(line + "\n")
        else:
            total_with_img += 1

        rec = by_number.setdefault(number, [0, 0, 0, type_title])
        rec[0] += 1
        if has_image:
            rec[1] += 1
        else:
            rec[2] += 1

    f_full.close()
    f_noimg.close()

    total_tasks = len(rows)
    total_without_img = total_tasks - total_with_img
    img_files = sorted(p for p in IMAGES_DIR.iterdir() if p.is_file())
    full_mb = human_mb(FULL_PATH.stat().st_size)
    noimg_mb = human_mb(NOIMG_PATH.stat().st_size)
    images_mb = human_mb(dir_size_bytes(IMAGES_DIR))

    first_two = []
    with FULL_PATH.open("r", encoding="utf-8") as fr:
        for _ in range(2):
            ln = fr.readline()
            if ln:
                first_two.append(ln.rstrip("\n"))

    lines = []
    lines.append("=" * 70)
    lines.append("ОТЧЁТ О ВЫГРУЗКЕ: математика (subject_id=1, slug 'mathb') -> Claude.ai")
    lines.append("=" * 70)
    lines.append(f"corpus.db        : {DB_PATH}")
    lines.append(f"bs4 parser       : {_BS_PARSER}")
    lines.append(f"resvg доступен   : {_HAS_RESVG} | PIL: {_HAS_PIL} | zoom={args.zoom}")
    lines.append("")
    lines.append(f"Всего задач                : {total_tasks}  (ожидалось 4863)")
    lines.append(f"  с реальными картинками   : {total_with_img}")
    lines.append(f"  без картинок             : {total_without_img}")
    lines.append(f"Всего ссылок на картинки   : {total_real_images}")
    lines.append("")
    lines.append("Картинки (физические файлы в exports/images/):")
    lines.append(f"  файлов всего             : {len(img_files)}")
    lines.append(f"  SVG растеризовано в PNG  : {mat.rasterized}")
    lines.append(f"  PNG скопировано напрямую : {mat.copied_png}")
    lines.append(f"  переиспользовано (skip)  : {mat.reused}")
    lines.append(f"  не найдено на диске      : {len(mat.missing)}")
    lines.append(f"  ошибок растеризации      : {len(mat.render_failures)}")
    lines.append("")
    lines.append("Размеры:")
    lines.append(f"  math_for_claude_full.jsonl      : {full_mb:.2f} MB")
    lines.append(f"  math_for_claude_no_images.jsonl : {noimg_mb:.2f} MB")
    lines.append(f"  images/                         : {images_mb:.2f} MB")
    lines.append("")
    lines.append("Распределение по task_number (всего / с картинкой / без):")
    lines.append(f"  {'№':>3}  {'всего':>6} {'с img':>6} {'без':>6}  название")
    for num in sorted(by_number):
        tot, wi, wo, title = by_number[num]
        lines.append(f"  {num:>3}  {tot:>6} {wi:>6} {wo:>6}  {title}")
    lines.append("")
    if mat.missing:
        lines.append(f"ОТСУТСТВУЮТ на диске ({len(mat.missing)}):")
        for sid, src in mat.missing[:50]:
            lines.append(f"  sdamgia={sid}  src={src}")
        if len(mat.missing) > 50:
            lines.append(f"  ... ещё {len(mat.missing) - 50}")
        lines.append("")
    if mat.render_failures:
        lines.append(f"ОШИБКИ РАСТЕРИЗАЦИИ ({len(mat.render_failures)}) — сохранены как .svg фолбэк:")
        for sid, src, err in mat.render_failures[:50]:
            lines.append(f"  sdamgia={sid}  src={src}  {err}")
        if len(mat.render_failures) > 50:
            lines.append(f"  ... ещё {len(mat.render_failures) - 50}")
        lines.append("")
    lines.append("Первые 2 строки JSONL (верификация):")
    for i, ln in enumerate(first_two, 1):
        shown = ln if len(ln) <= 1500 else ln[:1500] + f"... [+{len(ln) - 1500} символов]"
        lines.append(f"  [{i}] {shown}")
    lines.append("")
    lines.append("=" * 70)

    report = "\n".join(lines)
    SUMMARY_PATH.write_text(report + "\n", encoding="utf-8")
    print("\n" + report)
    print(f"\nГотово. Выгрузка в: {OUT_DIR}")


if __name__ == "__main__":
    main()
