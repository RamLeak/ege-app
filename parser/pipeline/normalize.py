"""Нормализация HTML задачи и скачивание ассетов.

Задачи модуля:
- Скачать SVG-формулы (img.tex) в assets/_formulas/{2hex}/{hash}.svg и заменить
  абсолютный src на относительный путь. SVG дедуплицируются по контент-хешу,
  как и на самом sdamgia.
- Скачать иллюстрации (div.pbody img без class=tex) в assets/{sdamgia_id}/img_N.{ext}
  и заменить src на относительный путь.
- Убрать &shy; (U+00AD) из HTML.
- Не трогать остальной HTML — он попадёт в БД как есть и будет показан в
  Android через AsyncImage + HtmlText (Compose).

normalize_problem_html() — основная точка входа. Не делает сетевых запросов
сама: использует переданный httpx.Client (rate-limit обеспечен fetch.py
для одного типа контента, но формулы и картинки — статика, можем дёргать
без задержки, потому что они идут на формульный поддомен/get_file).
В коде ниже мы аккуратно ставим polite_sleep ТОЛЬКО на новые скачивания.
"""
from __future__ import annotations

import hashlib
import os
import random
import re
import time
from pathlib import Path
from typing import Optional
from urllib.parse import urljoin, urlparse

import httpx
from selectolax.parser import HTMLParser, Node

from . import fetch as fetch_mod

PARSER_ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = PARSER_ROOT / "assets"
FORMULAS_DIR = ASSETS_DIR / "_formulas"
ASSETS_DIR.mkdir(parents=True, exist_ok=True)
FORMULAS_DIR.mkdir(parents=True, exist_ok=True)


# Регекс хеш-картинки sdamgia: /formula/svg/{2hex}/{32hex}.svg
_RE_FORMULA = re.compile(
    r"/formula/svg/([0-9a-f]{2})/([0-9a-f]{32})\.svg",
    re.IGNORECASE,
)
# Регекс мягкого переноса.
_SHY = "­"


def _ext_from_url(url: str, default: str = ".bin") -> str:
    path = urlparse(url).path
    ext = os.path.splitext(path)[1].lower()
    if ext and len(ext) <= 5:
        return ext
    return default


def _polite_asset_sleep() -> None:
    # Формулы (img.tex) — статика на /formula/svg/{hash}.svg, контент-адресуемая,
    # ответ из CDN-edge кеша. Иллюстрации (get_file) — тоже статика. Риск бана
    # минимальный. Лёгкий джиттер 0.05-0.15с — чтобы не лупить пачку
    # параллельных коннектов с одного IP, но без серьёзной задержки.
    time.sleep(0.05 + random.uniform(0.0, 0.10))


def _download_to(client: httpx.Client, url: str, dest: Path, referer: Optional[str] = None) -> bool:
    """Скачать URL в dest. True если успех или уже на диске."""
    if dest.exists() and dest.stat().st_size > 0:
        return True
    headers = {}
    if referer:
        headers["Referer"] = referer
    _polite_asset_sleep()
    try:
        r = client.get(url, headers=headers, follow_redirects=True, timeout=30)
    except httpx.HTTPError:
        return False
    if r.status_code != 200 or not r.content:
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(r.content)
    return True


def _process_formula_img(
    node: Node,
    client: httpx.Client,
    base_url: str,
    problem_referer: str,
) -> None:
    src = node.attributes.get("src") or ""
    if not src:
        return
    abs_url = urljoin(base_url, src)
    m = _RE_FORMULA.search(abs_url)
    if not m:
        # Не наш формат — оставим как было; некоторые иконки тоже class=tex
        # (например /img/exclamation.png), их не скачиваем.
        return
    prefix, hash32 = m.group(1).lower(), m.group(2).lower()
    dest = FORMULAS_DIR / prefix / f"{hash32}.svg"
    ok = _download_to(client, abs_url, dest, referer=problem_referer)
    if ok:
        # Меняем src на относительный путь от parser/assets/
        rel = f"_formulas/{prefix}/{hash32}.svg"
        node.attrs["src"] = rel
        node.attrs["data-original-src"] = abs_url


def _process_illustration_img(
    node: Node,
    client: httpx.Client,
    base_url: str,
    problem_referer: str,
    sdamgia_id: str,
    counter: list[int],
) -> None:
    src = node.attributes.get("src") or ""
    if not src:
        return
    # Исключения по классам — это технические иконки, не иллюстрации.
    classes = (node.attributes.get("class") or "").split()
    if "tex" in classes or "nodraw" in classes or "briefcase" in classes:
        return
    abs_url = urljoin(base_url, src)
    # Не качаем /img/*.png (UI-иконки sdamgia) и data: URI.
    if abs_url.startswith("data:"):
        return
    parsed = urlparse(abs_url)
    if parsed.path.startswith("/img/"):
        return
    ext = _ext_from_url(abs_url, default=".png")
    counter[0] += 1
    dest = ASSETS_DIR / sdamgia_id / f"img_{counter[0]}{ext}"
    ok = _download_to(client, abs_url, dest, referer=problem_referer)
    if ok:
        rel = f"{sdamgia_id}/img_{counter[0]}{ext}"
        node.attrs["src"] = rel
        node.attrs["data-original-src"] = abs_url


def normalize_html_block(
    html: str,
    client: httpx.Client,
    base_url: str,
    problem_referer: str,
    sdamgia_id: str,
    img_counter: Optional[list[int]] = None,
) -> tuple[str, list[str]]:
    """Прогнать HTML-блок (условие/решение) через нормализацию.

    Returns (normalized_html, list_of_formula_paths).
    img_counter — список из одного int, разделяемый между condition и solution
    одной задачи, чтобы нумерация img_N была сквозная.
    """
    if not html:
        return "", []
    html = html.replace(_SHY, "")
    tree = HTMLParser(html)
    if img_counter is None:
        img_counter = [0]

    formula_paths: list[str] = []
    for img in tree.css("img"):
        classes = (img.attributes.get("class") or "").split()
        if "tex" in classes:
            _process_formula_img(img, client, base_url, problem_referer)
            src = img.attributes.get("src") or ""
            if src.startswith("_formulas/"):
                formula_paths.append(src)
        else:
            _process_illustration_img(img, client, base_url, problem_referer, sdamgia_id, img_counter)

    body = tree.body
    if body is not None and body.html:
        # Возвращаем содержимое <body>, без обрамления.
        inner = body.html
        inner = re.sub(r"^<body[^>]*>", "", inner)
        inner = re.sub(r"</body>\s*$", "", inner)
        return inner.strip(), formula_paths
    return html.strip(), formula_paths


def detect_answer_format(answer_text: Optional[str]) -> Optional[str]:
    """Грубо классифицировать формат ответа для problems.answer_format."""
    if not answer_text:
        return None
    t = answer_text.strip()
    if not t:
        return None
    if "|" in t:
        return "alternatives"
    if re.fullmatch(r"-?\d+(?:[.,]\d+)?", t):
        return "number"
    if re.fullmatch(r"[\d\s.,;-]+", t):
        return "multipart"
    return "string"


def cleanup_text(s: Optional[str]) -> Optional[str]:
    if s is None:
        return None
    return s.replace(_SHY, "").replace("\xa0", " ").strip() or None
