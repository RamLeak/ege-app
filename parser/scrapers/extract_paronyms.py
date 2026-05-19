"""
Извлекает паронимы из задач №5 русского (corpus.db).

Структура №5 (sdamgia):
- HTML содержит инструкцию + 4-5 пронумерованных предложений
- В каждом предложении ОДНО слово CAPS (выделение)
- В `answer` хранится правильный пароним для слова, где допущена ошибка
- Нужно найти CAPS-слово, паронимическое с `answer`, и вытащить предложение

Алгоритм: для каждого CAPS-слова считаем общий префикс с `answer`,
выбираем максимум (минимум 3 символа). Дополнительно — словарь известных пар
для случаев типа надеть/одеть, где префикс короткий.

Out: parser/data/paronyms.json
"""

import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = ROOT / "corpus.db"
OUT_PATH = ROOT / "data" / "paronyms.json"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# Известные паронимические пары (для случаев когда префикс короткий).
# Lower-cased lemmas; orientation: any direction works.
KNOWN_PARONYMS = {
    "одеть": "надеть", "надеть": "одеть",
    "одел": "надел", "надел": "одел",
    "одевать": "надевать", "надевать": "одевать",
    "оплатить": "заплатить", "заплатить": "оплатить",
    "представить": "предоставить", "предоставить": "представить",
    "адресат": "адресант", "адресант": "адресат",
    "командировочный": "командированный", "командированный": "командировочный",
    "эффективный": "эффектный", "эффектный": "эффективный",
    "целый": "цельный", "цельный": "целый",
    "коренной": "корневой", "корневой": "коренной",
    "земельный": "земляной", "земляной": "земельный",
    "лесистый": "лесной", "лесной": "лесистый",
    "понятный": "понятливый", "понятливый": "понятный",
    "обидный": "обидчивый", "обидчивый": "обидный",
    "бережливый": "бережный", "бережный": "бережливый",
    "глинистый": "глиняный", "глиняный": "глинистый",
    "болотистый": "болотный", "болотный": "болотистый",
    "дипломатический": "дипломатичный", "дипломатичный": "дипломатический",
    "практический": "практичный", "практичный": "практический",
    "соседский": "соседний", "соседний": "соседский",
    "годовой": "годичный", "годичный": "годовой",
    "артистический": "артистичный", "артистичный": "артистический",
    "сравнимый": "сравнительный", "сравнительный": "сравнимый",
    "усвоить": "освоить", "освоить": "усвоить",
}


def strip_html(html: str) -> str:
    # Удаляем теги, нормализуем пробелы и неразрывные пробелы.
    text = re.sub(r"<[^>]+>", " ", html)
    text = text.replace("\xa0", " ").replace("&nbsp;", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def extract_caps_words(text: str) -> list[str]:
    # Слова из 3+ заглавных букв (только кириллица).
    return re.findall(r"\b[А-ЯЁ]{3,}(?:[-А-ЯЁ]*)\b", text)


def common_prefix_len(a: str, b: str) -> int:
    n = min(len(a), len(b))
    i = 0
    while i < n and a[i] == b[i]:
        i += 1
    return i


def normalize(word: str) -> str:
    return word.lower().replace("ё", "е").strip()


def best_match(answer: str, candidates: list[str]) -> tuple[str | None, int]:
    """Найти кандидат, наиболее паронимически близкий к answer.

    Возвращает (winning_caps_word, score). Score — длина общего префикса.
    """
    ans_norm = normalize(answer)
    best_word = None
    best_score = 0
    for c in candidates:
        c_norm = normalize(c)
        # Известные пары
        if KNOWN_PARONYMS.get(c_norm) == ans_norm or KNOWN_PARONYMS.get(ans_norm) == c_norm:
            return c, 99
        score = common_prefix_len(c_norm, ans_norm)
        if score >= 3 and score > best_score:
            best_score = score
            best_word = c
    return best_word, best_score


def split_sentences_from_html(html: str) -> list[str]:
    """Делим по <p>...</p> блокам. У sdamgia два формата №5:

    1) Старый: каждый <p> начинается с '1) ', '2) ', ...
    2) Новый: каждый <p> — отдельное предложение без номера

    В обоих случаях разбивка по <p> даёт нужные элементы.
    """
    # Все содержимое <p>...</p>
    blocks = re.findall(r"<p[^>]*>(.*?)</p>", html, flags=re.DOTALL)
    out = []
    for b in blocks:
        text = re.sub(r"<[^>]+>", " ", b).replace("\xa0", " ").replace("&nbsp;", " ")
        text = re.sub(r"\s+", " ", text).strip()
        # Срезать ведущий "1) "
        text = re.sub(r"^\d\)\s*", "", text)
        if text:
            out.append(text)
    return out


def main():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    rows = cur.execute("""
        SELECT p.id, p.statement_html, p.answer
        FROM problems p
        JOIN subjects s ON p.subject_id=s.id
        JOIN problem_types pt ON p.type_id=pt.id
        WHERE s.slug='rus' AND pt.number=5
    """).fetchall()
    conn.close()

    paronyms = []
    skipped_no_match = 0
    skipped_no_answer = 0
    for pid, html, answer in rows:
        if not answer or len(answer) < 3:
            skipped_no_answer += 1
            continue
        text = strip_html(html)
        caps = extract_caps_words(text)
        # Отфильтровать служебные слова инструкции
        caps = [c for c in caps if c not in {"НЕВЕРНО", "США", "СССР"}]
        if not caps:
            skipped_no_match += 1
            continue
        wrong, score = best_match(answer, caps)
        if not wrong:
            skipped_no_match += 1
            continue

        # Извлечь предложение с этим словом (используем HTML, не stripped text)
        sentences = split_sentences_from_html(html)
        # Отфильтровать инструкцию (длинная и содержит "приведённых ниже")
        sentences = [s for s in sentences if "приведённых ниже" not in s and "Исправьте лексическую" not in s and len(s) < 400]
        target_sentence = None
        for s in sentences:
            if wrong in s:
                target_sentence = s
                break
        if not target_sentence:
            skipped_no_match += 1
            continue

        # Очистка предложения от хвоста инструкции, если случайно прилип
        target_sentence = target_sentence.strip(" .")
        if len(target_sentence) > 350 or len(target_sentence) < 20:
            continue

        paronyms.append({
            "problem_id": pid,
            "sentence": target_sentence,
            "wrong_word": wrong,
            "correct_word": answer.strip(),
        })

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(paronyms, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Extracted {len(paronyms)} paronym examples")
    print(f"  Skipped no_match: {skipped_no_match}")
    print(f"  Skipped no_answer: {skipped_no_answer}")


if __name__ == "__main__":
    main()
