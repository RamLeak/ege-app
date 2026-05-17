"""Обходчик профильной математики (sdamgia.ru).

Стратегия:
1. Скачать /prob_catalog один раз, распарсить иерархию (типы + подвиды).
2. Для каждого подвида основного типа (is_supplementary=False) обойти страницы:
   /test?filter=all&category_id=N&page=1 → читаем data-total → page=2..total.
3. Для каждой задачи: нормализовать HTML, скачать формулы/иллюстрации, записать
   в math.jsonl. После каждой страницы — flush state.json.
4. State.json содержит completed_subtypes — повторный запуск пропускает их.

Запуск:
    python parser/scrapers/math_profile.py            # обычный прогон
    python parser/scrapers/math_profile.py --limit 50 # прогон первых 50 задач (smoke)
    python parser/scrapers/math_profile.py --only-cat 14  # один подвид
"""
from __future__ import annotations

import argparse
import sys
import time
import hashlib
from dataclasses import asdict
from datetime import datetime
from pathlib import Path
from typing import Optional

# Когда запускают как «python parser/scrapers/math_profile.py» — sys.path[0]
# становится parser/scrapers, и `from pipeline import ...` не находит модуль.
# Прокидываем parser/ в sys.path заранее.
PARSER_ROOT = Path(__file__).resolve().parent.parent
if str(PARSER_ROOT) not in sys.path:
    sys.path.insert(0, str(PARSER_ROOT))

from pipeline import fetch as fetch_mod
from pipeline import parse as parse_mod
from pipeline import normalize as norm_mod
from pipeline import store as store_mod

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

HOST = "https://math-ege.sdamgia.ru"
SUBJECT = "math_profile"
JSONL_PATH = PARSER_ROOT / "math.jsonl"
STATE_PATH = PARSER_ROOT / "state.json"


def _serialize_problem(
    raw: parse_mod.RawProblem,
    type_number: int,
    type_title: str,
    is_supplementary: bool,
    subtype_id: int,
    subtype_title: str,
    statement_html_norm: str,
    solution_html_norm: Optional[str],
    formula_paths: list[str],
    scraped_at: str,
) -> dict:
    raw_hash = hashlib.sha256(
        (raw.statement_html + (raw.solution_html or "")).encode("utf-8")
    ).hexdigest()[:16]
    return {
        "sdamgia_id": raw.sdamgia_id,
        "subject_slug": "mathb",   # math профиль; в БД маппится на subjects.slug
        "type_number": type_number,
        "type_title": type_title,
        "is_supplementary": is_supplementary,
        "subtype_category_id": subtype_id,
        "subtype_title": subtype_title,
        "type_marker_from_page": raw.type_marker,
        "statement_html": statement_html_norm,
        "solution_html": solution_html_norm,
        "answer_text": raw.answer_text,
        "answer_format": norm_mod.detect_answer_format(raw.answer_text),
        "kes_code": raw.kes_code,
        "kes_title": raw.kes_title,
        "subtype_path": raw.subtype_path,
        "formula_paths": formula_paths,
        "analog_ids": raw.analog_ids,
        "scraped_at": scraped_at,
        "raw_hash": raw_hash,
    }


def run(limit: Optional[int] = None, only_cat: Optional[int] = None,
        include_supplementary: bool = False) -> int:
    writer = store_mod.JsonlWriter(JSONL_PATH)
    state = store_mod.StateCheckpoint(STATE_PATH, subject=SUBJECT)

    with fetch_mod.make_client() as client:
        # 1. Каталог.
        cat_res = fetch_mod.fetch(client, f"{HOST}/prob_catalog", label="math_prob_catalog")
        types = parse_mod.parse_catalog(cat_res.text)
        main_types = [t for t in types if not t.is_supplementary]
        supp_types = [t for t in types if t.is_supplementary]
        print(f"[catalog] main types: {len(main_types)}, supplementary: {len(supp_types)}")
        print(f"[catalog] заявлено задач: main={sum(t.total_count for t in main_types)}, "
              f"supp={sum(t.total_count for t in supp_types)}")

        # 2. Соберём список подвидов для обхода.
        targets: list[tuple[parse_mod.TypeBlock, parse_mod.SubtypeRow]] = []
        for t in types:
            if not include_supplementary and t.is_supplementary:
                continue
            if t.number is None:
                continue
            for sub in t.subtypes:
                if sub.count <= 0:
                    continue
                if only_cat is not None and sub.category_id != only_cat:
                    continue
                targets.append((t, sub))
        print(f"[plan] подвидов к обходу: {len(targets)}, "
              f"заявлено задач: {sum(s.count for _, s in targets)}")

        # 3. Обход.
        scraped_count = 0
        scraped_in_session = 0
        t_start = time.monotonic()
        for type_block, sub in targets:
            if state.is_subtype_done(sub.category_id):
                print(f"  [skip] cat={sub.category_id} «{sub.title}» — уже завершён")
                continue
            state.update(current_subtype={
                "category_id": sub.category_id, "title": sub.title,
                "page": 1, "total_pages": None,
            })
            state.flush()

            page = 1
            total_pages: Optional[int] = None
            sub_problems = 0
            while True:
                url = f"{HOST}/test?filter=all&category_id={sub.category_id}&page={page}"
                label = f"test_cat{sub.category_id}_p{page}"
                referer = f"{HOST}/prob_catalog" if page == 1 else \
                    f"{HOST}/test?filter=all&category_id={sub.category_id}&page={page-1}"
                try:
                    res = fetch_mod.fetch(client, url, label=label, referer=referer)
                except fetch_mod.BannedError:
                    print("!! HTTP 403 — sdamgia забанила. Останавливаюсь.")
                    state.add_error(category_id=sub.category_id, page=page, msg="banned-403")
                    state.flush()
                    return 1
                except fetch_mod.FetchError as e:
                    print(f"!! {e}; skip page")
                    state.add_error(category_id=sub.category_id, page=page, msg=str(e))
                    break

                data_total, data_page, raw_problems = parse_mod.parse_test_list(res.text)
                if total_pages is None:
                    total_pages = data_total
                    state.update(current_subtype={
                        "category_id": sub.category_id, "title": sub.title,
                        "page": page, "total_pages": total_pages,
                    })

                page_problems = 0
                for raw in raw_problems:
                    if writer.has(raw.sdamgia_id):
                        continue
                    # Нормализуем условие и решение.
                    page_referer = url
                    img_counter = [0]
                    stmt_norm, formula_paths = norm_mod.normalize_html_block(
                        raw.statement_html, client, HOST, page_referer,
                        raw.sdamgia_id, img_counter,
                    )
                    sol_norm: Optional[str] = None
                    sol_formulas: list[str] = []
                    if raw.solution_html:
                        sol_norm, sol_formulas = norm_mod.normalize_html_block(
                            raw.solution_html, client, HOST, page_referer,
                            raw.sdamgia_id, img_counter,
                        )
                    all_formulas = list(dict.fromkeys(formula_paths + sol_formulas))

                    record = _serialize_problem(
                        raw, type_number=type_block.number,
                        type_title=type_block.title,
                        is_supplementary=type_block.is_supplementary,
                        subtype_id=sub.category_id, subtype_title=sub.title,
                        statement_html_norm=stmt_norm,
                        solution_html_norm=sol_norm,
                        formula_paths=all_formulas,
                        scraped_at=datetime.now().astimezone().isoformat(timespec="seconds"),
                    )
                    if writer.write(record):
                        scraped_count += 1
                        scraped_in_session += 1
                        sub_problems += 1
                        page_problems += 1
                        state.inc_processed(1)
                        if scraped_count % 50 == 0:
                            state.flush()
                        if limit is not None and scraped_in_session >= limit:
                            state.flush()
                            elapsed = time.monotonic() - t_start
                            print(f"[done by limit] {scraped_in_session} задач за {elapsed:.1f}с")
                            return 0

                print(f"  cat={sub.category_id} «{sub.title[:50]}» page={page}/{total_pages} "
                      f"+{page_problems} (total_in_sub={sub_problems})")

                if total_pages is None or page >= total_pages:
                    break
                page += 1

            state.mark_subtype_done(sub.category_id)
            print(f"[done subtype] cat={sub.category_id} «{sub.title[:60]}» "
                  f"got={sub_problems}/{sub.count}")

    elapsed = time.monotonic() - t_start
    print(f"\n=== Готово. Сессия: {scraped_in_session} задач за {elapsed:.1f}с "
          f"({elapsed/60:.1f} мин). Всего в JSONL: {writer.count} ===")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=None,
                    help="прервать после N новых задач в сессии (для smoke)")
    ap.add_argument("--only-cat", type=int, default=None,
                    help="обработать только один подвид по category_id")
    ap.add_argument("--include-supplementary", action="store_true",
                    help="включить «Задания Д1..Д19» (по умолчанию — только №1..№19)")
    args = ap.parse_args()
    return run(limit=args.limit, only_cat=args.only_cat,
               include_supplementary=args.include_supplementary)


if __name__ == "__main__":
    sys.exit(main())
