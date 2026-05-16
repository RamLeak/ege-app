"""
fipi_recon.py — разведка структуры открытого банка ФИПИ.
Запуск:  python fipi_recon.py
Результат: папка recon/ с тремя HTML-файлами.
"""
from pathlib import Path
import httpx
import time
import warnings

# Глушим предупреждение об отключённой SSL-проверке (см. verify=False ниже)
warnings.filterwarnings("ignore")

OUT = Path("recon")
OUT.mkdir(exist_ok=True)

# proj ID для математики профильной — публичный, из ссылок самого ФИПИ
MATH_PROFILE_PROJ = "AC437B34557F88EA4115D2F374B0A07B"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml",
    "Accept-Language": "ru-RU,ru;q=0.9",
}

URLS = {
    "01_bank_root.html":
        "https://ege.fipi.ru/bank/",
    "02_math_catalog.html":
        f"https://ege.fipi.ru/bank/index.php?proj={MATH_PROFILE_PROJ}",
    "03_math_one_task.html":
        f"https://ege.fipi.ru/bank/index.php?proj={MATH_PROFILE_PROJ}&qid=110B03",
}

# verify=False — у ФИПИ сломана цепочка SSL-сертификатов.
# Безопасно для GET публичного HTML, без передачи каких-либо данных.
with httpx.Client(headers=HEADERS, timeout=30, follow_redirects=True, verify=False) as client:
    for filename, url in URLS.items():
        print(f"→ {url}")
        try:
            r = client.get(url)
            # write_bytes — сохраняем сырые байты в исходной кодировке (cp1251),
            # чтобы браузер сам прочитал <meta charset> и отрендерил правильно.
            (OUT / filename).write_bytes(r.content)
            print(f"  HTTP {r.status_code}  |  {len(r.content)} байт  →  {filename}")
        except Exception as e:
            print(f"  ОШИБКА: {e}")
        time.sleep(2)

print(f"\nГотово. Файлы лежат в: {OUT.absolute()}")