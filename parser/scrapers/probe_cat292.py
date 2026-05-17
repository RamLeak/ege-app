"""Одноразовая разведка пагинации на cat=292 (Кредиты, заявлено 180 задач).

Закрывает open_question #1 из selectors.yaml: подтвердить, что &page=K отдаёт
оставшиеся задачи на крупном подвиде и понять реальный лимит на странице.

Делает 2 запроса с rate-limit 2-3с между ними. Сохраняет HTML в parser/cache/raw/
(а не в recon-sdamgia/), чтобы не плодить отдельные папки.
"""
from __future__ import annotations

import random
import re
import sys
import time
from datetime import datetime
from pathlib import Path

import httpx

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

CACHE = Path(__file__).resolve().parent.parent / "cache" / "raw"
CACHE.mkdir(parents=True, exist_ok=True)
LOG_DIR = Path(__file__).resolve().parent.parent / "logs"
LOG_DIR.mkdir(parents=True, exist_ok=True)
LOG = LOG_DIR / "scraper.log"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
}


def log(label: str, url: str, status: int, size: int) -> None:
    ts = datetime.now().astimezone().isoformat(timespec="seconds")
    with LOG.open("a", encoding="utf-8") as f:
        f.write(f"{ts}\t{label}\t{status}\t{size}\t{url}\n")


def fetch_and_save(client: httpx.Client, url: str, label: str, referer: str | None = None) -> str | None:
    cache_path = CACHE / f"probe_{label}.html"
    if cache_path.exists() and cache_path.stat().st_size > 0:
        print(f"[cache] {label}: {url}")
        return cache_path.read_text(encoding="utf-8", errors="replace")

    headers = dict(HEADERS)
    if referer:
        headers["Referer"] = referer
    print(f"\n→ {label}: {url}")
    time.sleep(2.0 + random.uniform(0.5, 1.0))
    r = client.get(url, headers=headers, follow_redirects=True, timeout=30)
    print(f"  HTTP {r.status_code}  {len(r.content)} bytes")
    log(label, url, r.status_code, len(r.content))
    if r.status_code >= 400:
        return None
    cache_path.write_bytes(r.content)
    return r.text


def analyze(html: str, label: str) -> dict:
    # data-total + data-page
    m_total = re.search(r'class="prob_list"[^>]*data-total="(\d+)"', html)
    m_page = re.search(r'class="prob_list"[^>]*data-page="(\d+)"', html)
    # Уникальные ID задач, отображённых верхним уровнем (через problem_container).
    container_ids = re.findall(r'class="problem_container"\s+id="problem_(\d+)"', html)
    # На случай вариаций атрибутов:
    if not container_ids:
        container_ids = re.findall(r'id="problem_(\d+)"', html)
    # prob_nums → первый href в каждом
    prob_nums_ids = re.findall(r'<span\s+class="prob_nums">\s*[^<]*<a\s+href="/problem\?id=(\d+)"', html)
    # Заголовок типа (для cat=292 это «Кредиты», тип 16)
    m_type = re.search(r'Тип\s+(\S+?)\s*&nbsp;№', html)
    info = {
        "label": label,
        "data_total": int(m_total.group(1)) if m_total else None,
        "data_page": int(m_page.group(1)) if m_page else None,
        "problem_container_count": len(container_ids),
        "unique_problem_ids_in_containers": len(set(container_ids)),
        "prob_nums_ids_count": len(prob_nums_ids),
        "first_3_container_ids": container_ids[:3],
        "last_3_container_ids": container_ids[-3:],
        "first_type_marker": m_type.group(1) if m_type else None,
    }
    return info


def main() -> int:
    base = "https://math-ege.sdamgia.ru/test?filter=all&category_id=292"
    with httpx.Client(headers=HEADERS, timeout=30) as client:
        p1_html = fetch_and_save(client, f"{base}&page=1", "cat292_p1",
                                 referer="https://math-ege.sdamgia.ru/prob_catalog")
        p2_html = fetch_and_save(client, f"{base}&page=2", "cat292_p2",
                                 referer=f"{base}&page=1")

    if p1_html:
        info1 = analyze(p1_html, "cat292_p1")
        print("\n", info1)
    if p2_html:
        info2 = analyze(p2_html, "cat292_p2")
        print("\n", info2)

    # Сверка: ID-ы из p1 и p2 должны НЕ пересекаться.
    if p1_html and p2_html:
        ids1 = set(re.findall(r'class="problem_container"\s+id="problem_(\d+)"', p1_html))
        ids2 = set(re.findall(r'class="problem_container"\s+id="problem_(\d+)"', p2_html))
        overlap = ids1 & ids2
        print(f"\n  Overlap p1 ∩ p2: {len(overlap)} ID (ожидается 0)")
        if overlap:
            print(f"  Пересекаются: {sorted(overlap)[:5]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
