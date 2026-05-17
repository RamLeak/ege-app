"""HTTP fetch с idempotent-кешом, rate-limit и retry.

- Каждый успешный GET кешируется в parser/cache/raw/<key>.html. Повторный
  fetch с тем же ключом — без сети, прямо из кеша.
- Между сетевыми запросами выдерживается delay 1.5-2с + джиттер 0.5-1с.
- При HTTP 429/503/5xx — экспоненциальный бэк-офф (1, 2, 4, 8 сек), 4 попытки.
- HTTP 403 — fail fast, это сигнал «sdamgia забанила», обработку выше.
- Все запросы логируются в parser/logs/scraper.log.
"""
from __future__ import annotations

import hashlib
import random
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional

import httpx

PARSER_ROOT = Path(__file__).resolve().parent.parent
CACHE_DIR = PARSER_ROOT / "cache" / "raw"
LOG_DIR = PARSER_ROOT / "logs"
CACHE_DIR.mkdir(parents=True, exist_ok=True)
LOG_DIR.mkdir(parents=True, exist_ok=True)
LOG_PATH = LOG_DIR / "scraper.log"

DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
}

RATE_LIMIT_BASE_SEC = 3.0
RATE_LIMIT_JITTER_SEC = 2.0
RETRY_DELAYS_SEC = (1, 2, 4, 8)


class FetchError(Exception):
    pass


class BannedError(FetchError):
    """HTTP 403 — sdamgia забанила. Скрипт должен остановиться."""


@dataclass
class FetchResult:
    url: str
    final_url: str
    status: int
    text: str
    content: bytes
    from_cache: bool


def _log(label: str, url: str, status: int, size: int, final_url: str = "", note: str = "") -> None:
    ts = datetime.now().astimezone().isoformat(timespec="seconds")
    line = f"{ts}\t{label}\t{status}\t{size}\t{url}\t{final_url}\t{note}\n"
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(line)


def cache_key(url: str, label: Optional[str] = None) -> str:
    """Стабильный ключ кеша. Если label задан — он, иначе sha1 от URL."""
    if label:
        # Заменим символы, опасные для имён файлов на Windows.
        safe = label.replace("?", "_").replace("&", "_").replace("=", "-").replace("/", "_")
        return safe
    h = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    return h


def cache_path(key: str, suffix: str = ".html") -> Path:
    return CACHE_DIR / f"{key}{suffix}"


def polite_sleep() -> None:
    delay = RATE_LIMIT_BASE_SEC + random.uniform(0.0, RATE_LIMIT_JITTER_SEC)
    time.sleep(delay)


def fetch(
    client: httpx.Client,
    url: str,
    label: Optional[str] = None,
    referer: Optional[str] = None,
    suffix: str = ".html",
    force: bool = False,
) -> FetchResult:
    """Достать URL: сначала кеш, иначе сеть с retry/rate-limit.

    Raises:
        BannedError: HTTP 403.
        FetchError: исчерпан retry-бюджет.
    """
    key = cache_key(url, label)
    path = cache_path(key, suffix)
    if not force and path.exists() and path.stat().st_size > 0:
        data = path.read_bytes()
        return FetchResult(
            url=url, final_url=url, status=200,
            text=data.decode("utf-8", errors="replace"),
            content=data, from_cache=True,
        )

    headers = dict(DEFAULT_HEADERS)
    if referer:
        headers["Referer"] = referer

    last_status = -1
    for attempt, backoff in enumerate([0] + list(RETRY_DELAYS_SEC), start=1):
        if backoff:
            time.sleep(backoff)
        polite_sleep()
        try:
            r = client.get(url, headers=headers, follow_redirects=True, timeout=30)
        except httpx.HTTPError as e:
            _log(label or "?", url, -1, 0, "", note=f"httpx:{e!s}")
            last_status = -1
            continue

        last_status = r.status_code
        if r.status_code == 403:
            _log(label or "?", url, 403, len(r.content), str(r.url), note="banned")
            raise BannedError(f"HTTP 403 on {url}")
        if r.status_code in (429, 500, 502, 503, 504):
            _log(label or "?", url, r.status_code, len(r.content), str(r.url),
                 note=f"retry-attempt-{attempt}")
            continue
        if 200 <= r.status_code < 400:
            path.write_bytes(r.content)
            _log(label or "?", url, r.status_code, len(r.content), str(r.url))
            return FetchResult(
                url=url, final_url=str(r.url), status=r.status_code,
                text=r.text, content=r.content, from_cache=False,
            )
        # 4xx (кроме 403) — не имеет смысла повторять.
        _log(label or "?", url, r.status_code, len(r.content), str(r.url), note="4xx-noretry")
        raise FetchError(f"HTTP {r.status_code} on {url}")

    raise FetchError(f"HTTP {last_status} on {url} after retries")


def make_client() -> httpx.Client:
    return httpx.Client(headers=DEFAULT_HEADERS, timeout=30, http2=False)
