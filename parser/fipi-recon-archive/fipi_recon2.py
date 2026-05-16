"""
fipi_recon2.py — вторая разведка, тащим напрямую questions.php
Запуск: python fipi_recon2.py
"""
from pathlib import Path
import httpx
import time
import warnings

warnings.filterwarnings("ignore")

OUT = Path("recon2")
OUT.mkdir(exist_ok=True)

MATH = "AC437B34557F88EA4115D2F374B0A07B"  # Математика профильная
INF  = "B9ACA5BBB2E19E434CD6BEC25284C67F"  # Информатика
RUS  = "AF0ED3F2557F8FFC4C06F80B6803FD26"  # Русский язык

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml",
    "Accept-Language": "ru-RU,ru;q=0.9",
    "Referer": "https://ege.fipi.ru/bank/index.php",
}

BASE = "https://ege.fipi.ru/bank/questions.php"

URLS = {
    # Первая страница математики, 10 задач — основной тестовый случай
    "math_page0.html":     f"{BASE}?proj={MATH}&page=0&pagesize=10",
    # 100 задач сразу — проверим, что pagesize работает с большим числом
    "math_page0_100.html": f"{BASE}?proj={MATH}&page=0&pagesize=100",
    # Вторая страница — убеждаемся, что пагинация работает
    "math_page1.html":     f"{BASE}?proj={MATH}&page=1&pagesize=10",
    # То же для информатики и русского — посмотрим, отличается ли формат
    "inf_page0.html":      f"{BASE}?proj={INF}&page=0&pagesize=10",
    "rus_page0.html":      f"{BASE}?proj={RUS}&page=0&pagesize=10",
}

with httpx.Client(headers=HEADERS, timeout=30, follow_redirects=True, verify=False) as client:
    # Сначала зайдём на главную, чтобы получить cookies — на старых PHP-сайтах это часто важно
    print("→ Прогрев сессии: открываем /bank/")
    client.get("https://ege.fipi.ru/bank/")
    time.sleep(1)

    for filename, url in URLS.items():
        print(f"→ {url}")
        try:
            r = client.get(url)
            (OUT / filename).write_bytes(r.content)
            print(f"  HTTP {r.status_code}  |  {len(r.content)} байт  →  {filename}")
        except Exception as e:
            print(f"  ОШИБКА: {e}")
        time.sleep(2)

print(f"\nГотово. Файлы лежат в: {OUT.absolute()}")