"""
sdamgia_recon.py — Stage 0. Минимально достаточная разведка sdamgia.ru.

Бюджет: <=10 GET-запросов. Между запросами 2.0-3.0с + джиттер 0.5-1.0с.
Стартуем с https://sdamgia.ru/ и оттуда находим реальные URL для
профильной математики и русского — структуру не угадываем.

Сырой HTML складывается в parser/recon-sdamgia/<step>.html.
Метаданные каждого запроса — в parser/recon-sdamgia/requests.log.
"""
from __future__ import annotations

import random
import sys
import time
from datetime import datetime
from pathlib import Path
from urllib.parse import urljoin, urlparse

import httpx
from selectolax.parser import HTMLParser

# Windows-консоль по умолчанию cp1251 — режим UTF-8 для печати кириллицы и стрелок.
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

OUT = Path(__file__).resolve().parent.parent / "recon-sdamgia"
OUT.mkdir(parents=True, exist_ok=True)
LOG = OUT / "requests.log"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
}

REQUEST_BUDGET = 10
_requests_made = 0


def first_problem_id(html: str) -> str | None:
    """Из HTML страницы /test?... вытащить первый problem id.

    На sdamgia ID задачи может оказаться в трёх формах:
      1) <a href="/problem?id=NNNNN"> — стандартный URL.
      2) <div class="prob_maindiv" data-prob-id="NNNNN"> — встроенный в список.
      3) <div id="problem_NNNNN"> — id-якорь блока задачи.
    Пробуем по очереди, возвращаем первое попавшееся числовое значение.
    """
    import re

    m = re.search(r'/problem\?id=(\d+)', html)
    if m:
        return m.group(1)
    m = re.search(r'data-prob-id=["\'](\d+)["\']', html)
    if m:
        return m.group(1)
    m = re.search(r'id=["\']problem_(\d+)["\']', html)
    if m:
        return m.group(1)
    return None


def polite_sleep() -> None:
    delay = 2.0 + random.uniform(0.5, 1.0)
    time.sleep(delay)


def log_request(label: str, url: str, status: int, size: int, final_url: str) -> None:
    line = (
        f"{datetime.utcnow().isoformat(timespec='seconds')}Z\t"
        f"{label}\t{status}\t{size}\t{url}\t{final_url}\n"
    )
    with LOG.open("a", encoding="utf-8") as f:
        f.write(line)


class CachedResp:
    """Минимальный stand-in под httpx.Response для повторных прогонов из кеша."""

    def __init__(self, url: str, text: str, content: bytes) -> None:
        self.url = url
        self.text = text
        self.content = content
        self.status_code = 200


def fetch(
    client: httpx.Client,
    url: str,
    label: str,
    referer: str | None = None,
) -> httpx.Response | CachedResp | None:
    global _requests_made
    cached = OUT / f"{label}.html"
    if cached.exists() and cached.stat().st_size > 0:
        data = cached.read_bytes()
        print(f"\n· [cache] {label}: {url}  ({len(data)} bytes из {cached.name})")
        return CachedResp(url, data.decode("utf-8", errors="replace"), data)

    if _requests_made >= REQUEST_BUDGET:
        print(f"!! Бюджет {REQUEST_BUDGET} запросов исчерпан, пропускаем {label}")
        return None

    polite_sleep()
    headers = dict(HEADERS)
    if referer:
        headers["Referer"] = referer

    print(f"\n→ [{_requests_made + 1}/{REQUEST_BUDGET}] {label}: {url}")
    try:
        r = client.get(url, headers=headers, follow_redirects=True, timeout=30)
    except httpx.HTTPError as e:
        print(f"  ОШИБКА: {e}")
        log_request(label, url, -1, 0, "")
        _requests_made += 1
        return None

    _requests_made += 1
    out_path = OUT / f"{label}.html"
    out_path.write_bytes(r.content)
    log_request(label, url, r.status_code, len(r.content), str(r.url))
    print(f"  HTTP {r.status_code}  {len(r.content)} bytes  →  {out_path.name}")
    if str(r.url) != url:
        print(f"  redirect → {r.url}")
    return r


def find_subject_links(html: str, base_url: str) -> dict[str, str]:
    """На главной sdamgia.ru ищет ссылки на ЕГЭ-профиль математики и ЕГЭ русского.

    На странице это пункты `<li><a href="https://math-ege.sdamgia.ru/">ЕГЭ профиль</a>`
    в разделе «Математика» и `<li><a href="https://rus-ege.sdamgia.ru/">ЕГЭ</a>` в
    разделе «Русский язык» (видимая часть страницы; в `<!--header_nav-->` есть и
    прямые ссылки, но они в комментариях).
    """
    tree = HTMLParser(html)
    found: dict[str, str] = {}
    for a in tree.css("a"):
        href = a.attributes.get("href") or ""
        text = (a.text() or "").strip().lower()
        if not href:
            continue
        full = urljoin(base_url, href)
        host = urlparse(full).netloc

        # ЕГЭ профильная математика — поддомен math-ege.sdamgia.ru
        if "math_profile" not in found and host == "math-ege.sdamgia.ru":
            found["math_profile"] = full
        # ЕГЭ русский — поддомен rus-ege.sdamgia.ru
        if "russian" not in found and host == "rus-ege.sdamgia.ru":
            found["russian"] = full
        # подстраховка — если найдём по тексту, тоже годится
        if "math_profile" not in found and (
            "профильн" in text and "математ" in text
        ):
            found["math_profile"] = full
        if "russian" not in found and ("русский" in text and "язык" in text):
            found["russian"] = full
    return found


def list_anchor_candidates(html: str, base_url: str, needle: str, limit: int = 25) -> list[tuple[str, str]]:
    """Возвращает (text, abs_url) для ссылок, чей текст или href содержит needle."""
    tree = HTMLParser(html)
    out: list[tuple[str, str]] = []
    needle = needle.lower()
    for a in tree.css("a"):
        text = (a.text() or "").strip()
        href = a.attributes.get("href") or ""
        if not href:
            continue
        if needle in text.lower() or needle in href.lower():
            out.append((text, urljoin(base_url, href)))
            if len(out) >= limit:
                break
    return out


def main() -> int:
    # Лог — append-only, чтобы при повторном запуске видеть всю историю.
    with httpx.Client(headers=HEADERS, timeout=30) as client:
        # 1. Главная sdamgia.ru
        r_main = fetch(client, "https://sdamgia.ru/", "01_main")
        if r_main is None or r_main.status_code >= 400:
            print("!! Не получили главную, дальше идти бессмысленно")
            return 1

        base_main = str(r_main.url)
        subjects = find_subject_links(r_main.text, base_main)
        print(f"\n  Найденные ссылки на предметы: {subjects}")

        if "math_profile" not in subjects or "russian" not in subjects:
            print("  Нужно посмотреть в HTML вручную — селектор не сработал.")
            # все ссылки с текстом «математ» или «русск» — для последующего анализа
            cand_math = list_anchor_candidates(r_main.text, base_main, "математ")
            cand_rus = list_anchor_candidates(r_main.text, base_main, "русск")
            print("  Кандидаты «математ»:")
            for t, u in cand_math:
                print(f"    - {t!r}  →  {u}")
            print("  Кандидаты «русск»:")
            for t, u in cand_rus:
                print(f"    - {t!r}  →  {u}")

        math_url = subjects.get("math_profile")
        russian_url = subjects.get("russian")

        # 2. Главная математики профильной — нужна, чтобы зафиксировать факт, что
        # это React SPA (пустой <div class="Root">), а контент в HTML только у
        # старых PHP-эндпоинтов.
        r_math_home = None
        if math_url:
            r_math_home = fetch(client, math_url, "02_math_home", referer=base_main)

        # 3. robots.txt — официальный публичный документ, который должен показать
        # пути sitemap и закрытые/открытые разделы.
        r_math_robots = fetch(
            client, "https://math-ege.sdamgia.ru/robots.txt", "03_math_robots",
            referer=str(r_math_home.url) if r_math_home else None,
        )
        if r_math_robots and r_math_robots.status_code < 400:
            print("\n  math robots.txt:")
            for line in r_math_robots.text.splitlines()[:30]:
                if line.strip():
                    print(f"    {line}")

        # 4. Тематический каталог. У sdamgia исторически путь /prob_catalog
        # (в архивном fipi-recon-archive есть упоминания, в самом sdamgia этот
        # путь стабилен много лет). Проверяем эмпирически — это и есть смысл разведки.
        r_math_catalog = fetch(
            client, "https://math-ege.sdamgia.ru/prob_catalog",
            "04_math_prob_catalog",
            referer=str(r_math_home.url) if r_math_home else None,
        )

        # 5. Одна задача — ищем ссылку вида /problem?id=NNN на странице каталога.
        math_problem_url: str | None = None
        if r_math_catalog and r_math_catalog.status_code < 400:
            for keyword in ("problem?id=", "/problem", "?id="):
                cands = list_anchor_candidates(r_math_catalog.text, str(r_math_catalog.url), keyword)
                if cands:
                    print(f"\n  Math catalog: кандидаты задач по '{keyword}':")
                    for t, u in cands[:5]:
                        print(f"    - {t!r}  →  {u}")
                    math_problem_url = cands[0][1]
                    break

        if math_problem_url:
            fetch(client, math_problem_url, "05_math_problem",
                  referer=str(r_math_catalog.url) if r_math_catalog else None)

        # 6. Главная русского
        r_rus_home = None
        if russian_url:
            r_rus_home = fetch(client, russian_url, "06_russian_home", referer=base_main)

        # 7. Каталог русского — тот же путь /prob_catalog.
        r_rus_catalog = fetch(
            client, "https://rus-ege.sdamgia.ru/prob_catalog",
            "07_rus_prob_catalog",
            referer=str(r_rus_home.url) if r_rus_home else None,
        )

        # 8. Одна задача русского
        rus_problem_url: str | None = None
        if r_rus_catalog and r_rus_catalog.status_code < 400:
            for keyword in ("problem?id=", "/problem", "?id="):
                cands = list_anchor_candidates(r_rus_catalog.text, str(r_rus_catalog.url), keyword)
                if cands:
                    print(f"\n  Russian catalog: кандидаты задач по '{keyword}':")
                    for t, u in cands[:5]:
                        print(f"    - {t!r}  →  {u}")
                    rus_problem_url = cands[0][1]
                    break

        if rus_problem_url:
            fetch(client, rus_problem_url, "08_russian_problem",
                  referer=str(r_rus_catalog.url) if r_rus_catalog else None)

        # ===== Stage 0, продолжение: 4 целевых запроса =====
        # Цель: добрать селекторы для списка задач и страницы одной задачи
        # по обоим предметам. ID задач берём из самих ответов.

        # 9. Список задач математики по подвиду №14 (мелкая выборка: 9 задач,
        #    «Линейные, квадратные, кубические уравнения», тип №6).
        r_math_test = fetch(
            client,
            "https://math-ege.sdamgia.ru/test?filter=all&category_id=14",
            "09_math_test_cat14",
            referer="https://math-ege.sdamgia.ru/prob_catalog",
        )

        # 10. Одна задача математики — id берём из ответа шага 9.
        math_pid: str | None = None
        if r_math_test and r_math_test.status_code < 400:
            math_pid = first_problem_id(r_math_test.text)
            print(f"\n  math first problem id from /test cat=14: {math_pid}")
        if math_pid:
            fetch(
                client,
                f"https://math-ege.sdamgia.ru/problem?id={math_pid}",
                "10_math_problem",
                referer="https://math-ege.sdamgia.ru/test?filter=all&category_id=14",
            )
        else:
            print("!! Не удалось извлечь id задачи математики — /problem?id= пропускаем")

        # 11. Список задач русского по подвиду 365 «Задания ФИПИ» к №1 (4 задачи,
        #    самая мелкая адекватная выборка из 07_rus_prob_catalog).
        r_rus_test = fetch(
            client,
            "https://rus-ege.sdamgia.ru/test?filter=all&category_id=365",
            "11_russian_test_cat365",
            referer="https://rus-ege.sdamgia.ru/prob_catalog",
        )

        # 12. Одна задача русского.
        rus_pid: str | None = None
        if r_rus_test and r_rus_test.status_code < 400:
            rus_pid = first_problem_id(r_rus_test.text)
            print(f"\n  russian first problem id from /test cat=365: {rus_pid}")
        if rus_pid:
            fetch(
                client,
                f"https://rus-ege.sdamgia.ru/problem?id={rus_pid}",
                "12_russian_problem",
                referer="https://rus-ege.sdamgia.ru/test?filter=all&category_id=365",
            )
        else:
            print("!! Не удалось извлечь id задачи русского — /problem?id= пропускаем")

    print(f"\n=== Готово. Сделано {_requests_made} запросов. ===")
    print(f"HTML: {OUT.absolute()}")
    print(f"Лог:  {LOG.absolute()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
