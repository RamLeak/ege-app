"""Пост-процессор: извлечь правила/источник/сложность из cached /test HTML русского.

НЕ делает сетевых запросов. Перебирает parser/cache/raw/rus_test_cat*_p*.html,
для каждой задачи извлекает её блок div.align-left и его компоненты.

Структура align-left у задачи русского:
  <div class="align-left">
    <div><span>Источник: ОБЗ ФИПИ</span></div>
    <div><span>Актуальность: Текущий учебный год</span></div>
    <div><span>Сложность: обычная</span></div>
    <div>
      <span style="font-weight:bold">Правило: 1 ЕГЭ. Логико-смысловые...</span>
      <img class="nodraw" ... collapse/expand >
      <div style="display:none">
        <div class="pbody">  ← САМО ПРАВИЛО ТУТ (большой HTML)
          ...
        </div>
      </div>
    </div>
    <div><span>Номер ОБЗ ФИ-ПИ: ...</span></div>
    <div>Название_подвида. Группа</div>
  </div>

Дедупликация правил: sha256 от (нормализованный rule_title + нормализованный
первые 500 символов plain-text содержимого). Это устойчиво к разнице в разметке
и достаточно длинно, чтобы реально разные правила не схлопнулись.

Выход (два файла):
  parser/russian_rules.jsonl — одна строка на УНИКАЛЬНОЕ правило:
    {
      "rule_hash": "abc123...",     # sha256[:16]
      "rule_title": "1 ЕГЭ. Логико-смысловые...",
      "content_html": "<div class=\"pbody\">...</div>",
      "problem_ids": ["50643", "50646", ...],
      "sources": {"ОБЗ ФИПИ": 50, "РЕШУ ЕГЭ": 30, ...},
      "difficulties": {"обычная": 75, ...}
    }

  parser/russian_problem_meta.jsonl — одна строка на ЗАДАЧУ (для join с
  russian.jsonl в build_db.py Stage 3):
    {
      "sdamgia_id": "50643",
      "source": "ОБЗ ФИПИ",          # пустая строка если отсутствует
      "difficulty": "обычная",       # пустая строка если отсутствует
      "rule_hash": "fdb560dae31785b1"  # пустая строка если у задачи нет правила
    }

Запуск:
  python parser/scrapers/extract_rules.py
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Optional

PARSER_ROOT = Path(__file__).resolve().parent.parent
if str(PARSER_ROOT) not in sys.path:
    sys.path.insert(0, str(PARSER_ROOT))

from selectolax.parser import HTMLParser, Node

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

CACHE_DIR = PARSER_ROOT / "cache" / "raw"
OUT_RULES = PARSER_ROOT / "russian_rules.jsonl"
OUT_META = PARSER_ROOT / "russian_problem_meta.jsonl"


_SHY = "­"  # U+00AD soft hyphen
_NBSP_WORD_JOINER = "⁠"


def _norm_text(s: str) -> str:
    """Нормализация для сравнения: убрать &shy;, лишние пробелы, в нижний регистр."""
    s = s.replace(_SHY, "").replace(_NBSP_WORD_JOINER, "")
    s = re.sub(r"\s+", " ", s).strip()
    return s.lower()


def _strip_field_prefix(text: str, prefix: str) -> str:
    """Извлечь значение из строки вида 'Prefix: value'."""
    text = text.replace(_SHY, "").strip()
    if text.startswith(prefix):
        return text[len(prefix):].lstrip(":").strip()
    return ""


def _inner_html(node: Optional[Node]) -> str:
    if not node:
        return ""
    html = node.html or ""
    # Уберём внешний тег самого node, оставим внутренний контент?
    # На самом деле node.html включает сам узел. Это OK — мы пишем content_html
    # как полный <div class="pbody">...</div>.
    return html


def extract_meta_from_align_left(align_left: Node) -> dict:
    """Из одного div.align-left извлечь источник, сложность, правило-title и
    rule_content_html (HTML вложенного <div class="pbody"> с самим текстом правила).

    Возвращает: {source, difficulty, rule_title, rule_content_html}.
    Любое поле может быть пустой строкой.
    """
    out = {"source": "", "difficulty": "", "rule_title": "", "rule_content_html": ""}

    # Прямые дети — каждый <div> с одним <span>.
    for div in align_left.iter():
        if div.tag != "div":
            continue
        # text() возвращает текст всего поддерева — для верхних метаданных это
        # ровно «Префикс: значение».
        full_text = (div.text() or "").replace(_SHY, "").strip()
        if not full_text:
            continue

        if not out["source"]:
            val = _strip_field_prefix(full_text, "Источник")
            if val:
                out["source"] = val
                continue
        if not out["difficulty"]:
            val = _strip_field_prefix(full_text, "Сложность")
            if val:
                out["difficulty"] = val
                continue
        if not out["rule_title"]:
            # «Правило: title». Title — это содержимое bold-span.
            bold = div.css_first("span[style*='font-weight:bold']")
            if bold:
                bold_text = _strip_field_prefix(
                    (bold.text() or "").replace(_SHY, "").strip(), "Правило"
                )
                if bold_text:
                    out["rule_title"] = bold_text
                    # Содержательная часть правила — внутри вложенного
                    # <div style="display:none"> рядом с bold-span.
                    # Берём первый внутренний div с .pbody.
                    pbody = div.css_first("div.pbody")
                    if pbody:
                        out["rule_content_html"] = _inner_html(pbody)
                    continue

    return out


_RE_PROBLEM_ID = re.compile(r"^problem_(\d+)$")


def extract_from_file(html: str) -> list[dict]:
    """Из HTML одной /test страницы выдать список dict'ов по задачам.

    Каждый dict: {sdamgia_id, source, difficulty, rule_title, rule_content_html}.
    """
    tree = HTMLParser(html)
    list_node = tree.css_first("div.prob_list")
    if not list_node:
        return []
    out = []
    for cont in list_node.css("div.problem_container[id^='problem_']"):
        m = _RE_PROBLEM_ID.match(cont.attributes.get("id") or "")
        if not m:
            continue
        sdamgia_id = m.group(1)
        maindiv = cont.css_first("div.prob_maindiv")
        if not maindiv:
            continue
        # У одной задачи может быть несколько div.align-left (вложенные).
        # Брать ПЕРВЫЙ — это тот, который относится к самой задаче.
        align_left = maindiv.css_first("div.align-left")
        if not align_left:
            out.append({
                "sdamgia_id": sdamgia_id, "source": "", "difficulty": "",
                "rule_title": "", "rule_content_html": "",
            })
            continue
        meta = extract_meta_from_align_left(align_left)
        meta["sdamgia_id"] = sdamgia_id
        out.append(meta)
    return out


def rule_hash(rule_title: str, content_html: str) -> str:
    """Стабильный хеш для дедупликации одного правила.

    Берём нормализованный title + первые 500 chars нормализованного plain-text
    содержимого. Это достаточно длинно и устойчиво к разнице в разметке.
    """
    content_text = re.sub(r"<[^>]+>", " ", content_html)
    content_text = re.sub(r"&[a-z]+;", " ", content_text)
    title_norm = _norm_text(rule_title)
    content_norm = _norm_text(content_text)[:500]
    payload = f"{title_norm}||{content_norm}"
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]


def main() -> int:
    files = sorted(CACHE_DIR.glob("rus_test_cat*_p*.html"))
    print(f"[scan] cache files: {len(files)}")
    if not files:
        print("!! Нет файлов rus_test_cat*_p*.html в cache/raw/. Запускать ПОСЛЕ парсера русского.")
        return 1

    # rule_hash -> {title, content_html, problem_ids: list, sources: Counter, difficulties: Counter}
    rules: dict[str, dict] = {}
    # per-problem метаданные для последующего join'а в build_db.py
    problem_meta: dict[str, dict] = {}
    # Для статистики
    problems_with_rule = 0
    problems_without_rule = 0
    problems_total = 0

    for i, fpath in enumerate(files, 1):
        if i % 50 == 0:
            print(f"  [progress] {i}/{len(files)}")
        try:
            html = fpath.read_text(encoding="utf-8", errors="replace")
        except Exception as e:
            print(f"!! {fpath.name}: {e}")
            continue

        page_items = extract_from_file(html)
        for item in page_items:
            sid = item["sdamgia_id"]
            # Дедуп по sdamgia_id: одна задача может встречаться в нескольких
            # подвидах (cat=*). Берём ПЕРВОЕ вхождение с непустым правилом,
            # либо первое вхождение вообще если правил нет нигде.
            existing = problem_meta.get(sid)
            if existing and existing["rule_hash"]:
                # Уже сохранили задачу с правилом — пропускаем.
                continue

            problems_total = len(problem_meta)
            has_rule = bool(item["rule_title"] and item["rule_content_html"])

            h = ""
            if has_rule:
                h = rule_hash(item["rule_title"], item["rule_content_html"])
                if h not in rules:
                    rules[h] = {
                        "rule_hash": h,
                        "rule_title": item["rule_title"].replace(_SHY, "").strip(),
                        "content_html": item["rule_content_html"],
                        "problem_ids": [],
                        "sources": Counter(),
                        "difficulties": Counter(),
                    }
                # Добавляем sid в problem_ids только если ещё не там (дедуп списка).
                if sid not in rules[h]["problem_ids"]:
                    rules[h]["problem_ids"].append(sid)
                if item["source"]:
                    rules[h]["sources"][item["source"]] += 1
                if item["difficulty"]:
                    rules[h]["difficulties"][item["difficulty"]] += 1

            problem_meta[sid] = {
                "sdamgia_id": sid,
                "source": item["source"] or "",
                "difficulty": item["difficulty"] or "",
                "rule_hash": h,
            }

    # Пересчёт финальной статистики (т.к. problem_meta мог переписываться)
    problems_total = len(problem_meta)
    problems_with_rule = sum(1 for m in problem_meta.values() if m["rule_hash"])
    problems_without_rule = problems_total - problems_with_rule

    # Запись: правила
    with OUT_RULES.open("w", encoding="utf-8") as f:
        for h, rec in rules.items():
            row = {
                "rule_hash": rec["rule_hash"],
                "rule_title": rec["rule_title"],
                "content_html": rec["content_html"],
                "problem_ids": rec["problem_ids"],
                "sources": dict(rec["sources"]),
                "difficulties": dict(rec["difficulties"]),
            }
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    # Запись: per-problem метаданные
    with OUT_META.open("w", encoding="utf-8") as f:
        for sid in sorted(problem_meta.keys(), key=lambda x: int(x) if x.isdigit() else 0):
            f.write(json.dumps(problem_meta[sid], ensure_ascii=False) + "\n")

    # Статистика
    print()
    print("=== РЕЗУЛЬТАТЫ ===")
    print(f"unique rules:                 {len(rules)}")
    print(f"problems с правилом:          {problems_with_rule}")
    print(f"problems без правила:         {problems_without_rule}")
    print(f"problems total (уникальных):  {problems_total}")
    if rules:
        sizes = sorted((len(r["problem_ids"]) for r in rules.values()), reverse=True)
        print(f"задач на правило: max={sizes[0]}, median={sizes[len(sizes)//2]}, min={sizes[-1]}")
        # Топ-5 самых популярных правил
        top = sorted(rules.values(), key=lambda r: -len(r["problem_ids"]))[:5]
        print("\\nТоп-5 правил по числу задач:")
        for r in top:
            print(f"  {len(r['problem_ids']):>4} задач — «{r['rule_title'][:80]}»")
    print(f"\\noutput rules: {OUT_RULES}  ({OUT_RULES.stat().st_size/1024:.1f} KB)")
    print(f"output meta:  {OUT_META}  ({OUT_META.stat().st_size/1024:.1f} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
