"""Stage 3: статическая HTML-страница со списком первых задач из corpus.db.

10 задач mathb + 10 задач rus с условием, решением, ответом, правилом
(для русского). Формулы/иллюстрации подключены через relative path `assets/`.

Запуск (после build_db.py):
    python parser/export_view.py

Кладёт parser/view_corpus.html — открывается в браузере двойным кликом.
"""
from __future__ import annotations

import html
import json
import re
import sqlite3
import sys
from pathlib import Path

PARSER_ROOT = Path(__file__).resolve().parent
DB_PATH = PARSER_ROOT / "corpus.db"
OUT_PATH = PARSER_ROOT / "view_corpus.html"

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


# В HTML-теле задач src=" указывает на относительные пути формул/иллюстраций.
# Чтобы view_corpus.html (в parser/) видел эти ресурсы — добавляем префикс
# `assets/` к таким путям. НЕ трогаем абсолютные URL и UI-иконки sdamgia.
_RE_LOCAL_SRC = re.compile(
    r'src="(?!https?://)(?!data:)(?!//)(?!/img/)([^"]+)"'
)

# Декларация display:none внутри атрибута style. На sdamgia решения и правила
# отдаются с display:none — раскрываются их собственным JS, которого у нас нет.
# Чистим декларацию целиком; пустой style — удаляем.
_RE_STYLE_ATTR = re.compile(r"""style\s*=\s*(['"])(.*?)\1""", re.IGNORECASE | re.DOTALL)
_RE_DISPLAY_NONE_DECL = re.compile(r"display\s*:\s*none\s*;?", re.IGNORECASE)


def rewrite_src(html_text: str) -> str:
    if not html_text:
        return ""
    return _RE_LOCAL_SRC.sub(r'src="assets/\1"', html_text)


def strip_display_none(html_text: str) -> str:
    """Удалить декларацию display:none из всех style-атрибутов.

    Sdamgia использует display:none + кастомный JS-toggle. Когда мы кладём
    такой HTML в <details>/<div> — содержимое остаётся скрытым из-за
    inline display:none. Здесь чистим самую декларацию, остальные стили
    (padding, color, clear, etc.) сохраняем. Пустой style удаляем целиком.
    """
    if not html_text:
        return ""

    def replace_style(m: "re.Match[str]") -> str:
        quote = m.group(1)
        value = m.group(2)
        new_value = _RE_DISPLAY_NONE_DECL.sub("", value).strip()
        new_value = re.sub(r";\s*;", ";", new_value).strip(";").strip()
        if not new_value:
            return ""  # удалить атрибут целиком
        return f"style={quote}{new_value}{quote}"

    return _RE_STYLE_ATTR.sub(replace_style, html_text)


def sanitize(html_text: str) -> str:
    """Полная очистка для безопасной вставки в view_corpus.html."""
    return strip_display_none(rewrite_src(html_text))


PAGE_CSS = """
* { box-sizing: border-box; }
body { font-family: -apple-system, system-ui, "Segoe UI", Roboto, sans-serif;
       margin: 0; padding: 24px; max-width: 980px; margin: 0 auto;
       color: #1a1a1a; background: #fafafa; line-height: 1.45; }
h1 { font-size: 22px; border-bottom: 1px solid #ddd; padding-bottom: 10px; }
h2.subj { margin-top: 36px; font-size: 19px; color: #444; border-bottom: 2px solid #888; padding-bottom: 6px; }
.prob { background: white; border: 1px solid #e0e0e0; border-radius: 6px;
        padding: 18px 20px; margin: 14px 0; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
.prob-head { display: flex; justify-content: space-between; align-items: baseline;
             border-bottom: 1px dashed #ccc; padding-bottom: 8px; margin-bottom: 12px; }
.prob-id { font-family: monospace; color: #666; font-size: 13px; }
.prob-type { font-weight: 600; color: #006; }
.prob-subtype { color: #888; font-size: 13px; }
.stmt { padding: 6px 0; }
.stmt img.tex { vertical-align: middle; max-height: 22px; }
.stmt img:not(.tex) { max-width: 360px; height: auto; display: block; margin: 8px 0; border: 1px solid #ddd; }
.answer { margin-top: 10px; padding: 8px 12px; background: #effaf3; border-left: 4px solid #2ecc71;
          border-radius: 0 4px 4px 0; font-family: monospace; }
.meta { color: #888; font-size: 12px; margin-top: 8px; }
details { margin-top: 10px; }
details summary { cursor: pointer; color: #066; font-size: 13px; padding: 6px 0; }
details .body { padding: 10px 16px; background: #f4f7fa; border-left: 3px solid #888;
                border-radius: 0 4px 4px 0; margin-top: 6px; }
details .body img.tex { max-height: 22px; vertical-align: middle; }
details .body img:not(.tex) { max-width: 360px; height: auto; display: block; margin: 8px 0; }
.warn { background: #fff8e1; border-left: 4px solid #ff9800; padding: 8px 12px;
        font-size: 13px; margin-top: 8px; border-radius: 0 4px 4px 0; }
"""


def render_problem(row: dict) -> str:
    """Один блок задачи. row — dict из cursor."""
    sid = row["sdamgia_id"]
    subject = row["subject_slug"]
    type_label = f"№{row['type_number']} «{row['type_title']}»"
    subtype_label = row["subtype_title"] or ""
    answer = row["answer"] or ""
    answer_html = ""
    if answer:
        answer_html = f'<div class="answer">Ответ: {html.escape(answer)}</div>'

    stmt = sanitize(row["statement_html"] or "")
    sol = sanitize(row["solution_html"] or "")
    rule_html = sanitize(row["rule_content_html"] or "")

    extras = []
    if row.get("source"):
        extras.append(f"источник: {html.escape(row['source'])}")
    if row.get("difficulty"):
        extras.append(f"сложность: {html.escape(row['difficulty'])}")
    n_imgs = len(json.loads(row["images_json"])) if row.get("images_json") else 0
    if n_imgs:
        extras.append(f"картинок/формул: {n_imgs}")
    meta_html = f'<div class="meta">{" · ".join(extras)}</div>' if extras else ""

    sol_block = ""
    if sol:
        sol_block = (
            '<details><summary>Показать авторское решение</summary>'
            f'<div class="body">{sol}</div></details>'
        )

    rule_block = ""
    if rule_html and row.get("rule_title"):
        rule_block = (
            f'<details><summary>Правило: {html.escape(row["rule_title"])}</summary>'
            f'<div class="body">{rule_html}</div></details>'
        )

    warn = ""
    if subject == "rus" and row["type_number"] in (1, 2, 3):
        warn = (
            '<div class="warn">⚠ На sdamgia у задач №1-3 не выводится исходный текст, '
            'к которому они отсылают. Это известное ограничение корпуса.</div>'
        )

    return f"""
<div class="prob">
  <div class="prob-head">
    <div>
      <span class="prob-type">{html.escape(type_label)}</span>
      <span class="prob-subtype">— {html.escape(subtype_label)}</span>
    </div>
    <div class="prob-id">sdamgia_id: {html.escape(sid)} | {html.escape(subject)}</div>
  </div>
  <div class="stmt">{stmt}</div>
  {answer_html}
  {warn}
  {meta_html}
  {sol_block}
  {rule_block}
</div>
"""


def fetch_sample(conn: sqlite3.Connection, subject_slug: str, limit: int = 10) -> list[dict]:
    """Первые N задач предмета с join'ом solution и (для русского) правила."""
    conn.row_factory = sqlite3.Row
    rows = conn.execute(
        """
        SELECT
            p.id AS pid,
            p.sdamgia_id,
            p.statement_html,
            p.answer,
            p.images_json,
            p.source,
            p.difficulty,
            s.slug AS subject_slug,
            t.number AS type_number,
            t.title AS type_title,
            st.title AS subtype_title,
            sol.solution_html AS solution_html,
            r.title AS rule_title,
            r.content_html AS rule_content_html
        FROM problems p
        JOIN subjects s ON p.subject_id = s.id
        JOIN problem_types t ON p.type_id = t.id
        LEFT JOIN problem_subtypes st ON p.subtype_id = st.id
        LEFT JOIN solutions sol ON sol.problem_id = p.id
        LEFT JOIN problem_rules pr ON pr.problem_id = p.id
        LEFT JOIN rules r ON r.id = pr.rule_id
        WHERE s.slug = ?
        ORDER BY p.id
        LIMIT ?
        """,
        (subject_slug, limit),
    ).fetchall()
    return [dict(r) for r in rows]


def main() -> int:
    if not DB_PATH.exists():
        print(f"!! {DB_PATH} не найден — запусти build_db.py")
        return 1
    conn = sqlite3.connect(DB_PATH)

    math_sample = fetch_sample(conn, "mathb", 10)
    rus_sample = fetch_sample(conn, "rus", 10)
    n_problems = conn.execute("SELECT COUNT(*) FROM problems").fetchone()[0]
    n_rules = conn.execute("SELECT COUNT(*) FROM rules").fetchone()[0]
    n_attach = conn.execute("SELECT COUNT(*) FROM problem_rules").fetchone()[0]
    conn.close()

    body = []
    body.append("<h2 class='subj'>Математика профильная (первые 10 из {})</h2>".format(
        len([1 for r in math_sample]) and 4863))
    for r in math_sample:
        body.append(render_problem(r))
    body.append("<h2 class='subj'>Русский язык (первые 10 из {})</h2>".format(5409))
    for r in rus_sample:
        body.append(render_problem(r))

    html_out = f"""<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>view_corpus — sample задач (Stage 3)</title>
<style>{PAGE_CSS}</style>
</head>
<body>
<h1>view_corpus.html — выборка задач из corpus.db</h1>
<p>Всего в БД: <b>{n_problems}</b> задач (4863 math + 5409 rus), <b>{n_rules}</b> уникальных правил, <b>{n_attach}</b> привязок.</p>
{''.join(body)}
</body>
</html>
"""
    OUT_PATH.write_text(html_out, encoding="utf-8")
    print(f"OK: {OUT_PATH} ({OUT_PATH.stat().st_size/1024:.1f} KB)")
    print(f"математика: {len(math_sample)} задач, русский: {len(rus_sample)} задач.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
