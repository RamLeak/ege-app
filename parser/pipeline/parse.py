"""Парсинг HTML с помощью selectolax.

Три уровня:
- parse_catalog(html) → список TypeBlock (типы и их подвиды).
- parse_test_list(html) → (data_total, data_page, [RawProblem]).
- parse_problem_block(node) → RawProblem (из одного div.problem_container).

«Raw» — это сырые данные до normalize.py (без скачивания картинок).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Optional

from selectolax.parser import HTMLParser, Node


# ---------- dataclasses ----------

@dataclass
class SubtypeRow:
    category_id: int          # data-id у div.cat_category — параметр для /test
    title: str                # «Тригонометрические уравнения, сводимые к квадратным»
    count: int                # сколько задач заявлено в каталоге
    url: str                  # /test?filter=all&category_id=N (относительный)


@dataclass
class TypeBlock:
    """Тип ЕГЭ (№1..№N) или «Дополнительное» (Д1..Д14)."""
    number: Optional[int]     # 1..N для основных, 1..14 для supplementary
    is_supplementary: bool    # True для «Задания Д1..Д14»
    title: str                # «Планиметрия», «Чтение графиков и диаграмм»
    total_count: int          # счётчик задач в типе
    subtypes: list[SubtypeRow] = field(default_factory=list)


@dataclass
class RawProblem:
    sdamgia_id: str           # «26662»
    type_marker: Optional[str]  # «6» или «Д1» — то, что в span.prob_nums до №
    statement_html: str       # внутренний HTML первого div[id^=body] внутри prob_maindiv
    solution_html: Optional[str]   # внутренний HTML div.solution
    answer_text: Optional[str]     # «13», «или|либо», None для сочинения
    kes_code: Optional[str]        # «2.1.2» (из href /search?...&search=2.1.2 ...)
    kes_title: Optional[str]       # «Рациональные уравнения»
    subtype_path: Optional[str]    # «Простейшие уравнения. Линейные...»
    analog_ids: list[str] = field(default_factory=list)


# ---------- helpers ----------

# Regex выбран осознанно: «Тип ...&nbsp;№» допускает любой токен (число, «Д1»,
# даже многословный), но обычно это число 1..27 или «Д»N.
_RE_TYPE_NUMBER = re.compile(r"Тип\s+(\S+?)\s*№", re.UNICODE)
_RE_PROBLEM_ID = re.compile(r"^problem_(\d+)$")
_RE_KES_SEARCH = re.compile(r"search=([\d.]+)\s+(.+?)$", re.UNICODE)


def _clean_text(s: Optional[str]) -> str:
    """Убрать &shy; (мягкий перенос U+00AD), &nbsp; и лишние пробелы."""
    if not s:
        return ""
    return (s
            .replace("­", "")     # мягкий перенос
            .replace(" ", " ")    # &nbsp;
            .strip())


def _first(node: Node, css: str) -> Optional[Node]:
    res = node.css_first(css)
    return res if res else None


def _attr(node: Node, name: str) -> Optional[str]:
    return node.attributes.get(name) if node else None


def _inner_html(node: Optional[Node]) -> str:
    if not node:
        return ""
    return node.html or ""


def _decode_html_attr(s: str) -> str:
    """Восстановить «обычные» символы в атрибуте href (например, search-параметр).
    Не делаем полный html unescape — только нужный минимум.
    """
    return (s
            .replace("&shy;", "")
            .replace("&amp;", "&")
            .replace("&quot;", '"')
            .replace("&nbsp;", " ")
            .strip())


# ---------- catalog ----------

def parse_catalog(html: str) -> list[TypeBlock]:
    """Распарсить /prob_catalog (одинаковая структура для math и rus).

    Возвращает плоский список TypeBlock в порядке появления на странице.
    «Задания Д1..Д14» (только у математики) попадают сюда с
    is_supplementary=True и собственной number (Д1→1, Д2→2, ...).
    Если у блока нет pcat_num — это «Дополнительное», номер парсим из заголовка.
    """
    tree = HTMLParser(html)
    root = tree.css_first("div.cat_main")
    if not root:
        return []

    types: list[TypeBlock] = []
    # supplementary-нумерация — отдельный счётчик: Д1 → 1, Д2 → 2, ...
    for cat_node in root.css("div.cat_main > div.cat_category"):
        title_block = _first(cat_node, "b.cat_name")
        if not title_block:
            continue

        # Полный текст заголовка типа без span.theory и без pcat_num.
        # Берём text() и удаляем мусор.
        pcat_num_node = _first(title_block, "span.pcat_num")
        pcat_text = _clean_text(pcat_num_node.text()) if pcat_num_node else ""
        full_title = _clean_text(title_block.text())
        # Ведущее «Т» от span.theory нужно срезать ТОЛЬКО если theory-span реально
        # присутствует в этом b.cat_name — иначе настоящие названия типа
        # «Текстовые задачи» / «Тригонометрические…» потеряют первую букву.
        has_theory_span = title_block.css_first("span.theory") is not None
        title_no_T = full_title[1:].strip() if has_theory_span and full_title.startswith("Т") else full_title

        # Определяем номер и supplementary-флаг.
        is_supplementary = False
        number: Optional[int] = None
        title_clean = title_no_T
        if pcat_text and pcat_text.isdigit():
            number = int(pcat_text)
            # «1. Планиметрия» → «Планиметрия». Срезаем всё до и включая «1.».
            title_clean = re.sub(r"^" + re.escape(pcat_text) + r"\s*\.?\s*", "", title_no_T).strip()
        else:
            # Supplementary: «Задания Д1. ...» / «Задания Д14. ...»
            m = re.search(r"Задания\s+Д(\d+)", title_no_T)
            if m:
                number = int(m.group(1))
                is_supplementary = True
                # «Задания Д1. Чтение графиков» → «Чтение графиков»
                # Иногда: «Задания Д8 C1. Уравнения» → «C1. Уравнения» (оставляем как есть)
                title_clean = re.sub(r"^Задания\s+Д\d+\s*[.]?\s*", "", title_no_T).strip()

        # Счётчик задач в типе — div.cat_count.cat_sum как прямой ребёнок cat_node.
        # selectolax (lexbor) не поддерживает :scope, поэтому обходим детей руками.
        total_count = 0
        for child in cat_node.iter():
            if child.tag != "div":
                continue
            classes = (child.attributes.get("class") or "").split()
            if "cat_count" in classes and "cat_sum" in classes:
                try:
                    total_count = int(_clean_text(child.text()))
                except ValueError:
                    total_count = 0
                break

        # Подвиды: cat_node > div.cat_children > div.cat_category[data-id].
        # Опять обходим без :scope.
        subtypes: list[SubtypeRow] = []
        children_div: Optional[Node] = None
        for child in cat_node.iter():
            if child.tag == "div" and "cat_children" in (child.attributes.get("class") or "").split():
                children_div = child
                break
        if not children_div:
            types.append(TypeBlock(
                number=number, is_supplementary=is_supplementary,
                title=title_clean, total_count=total_count, subtypes=[],
            ))
            continue
        for sub_node in children_div.iter():
            if sub_node.tag != "div":
                continue
            if "cat_category" not in (sub_node.attributes.get("class") or "").split():
                continue
            data_id = _attr(sub_node, "data-id")
            if not data_id or not data_id.isdigit():
                continue
            title_a = _first(sub_node, "a.cat_name")
            count_div = _first(sub_node, "div.cat_count")
            count = 0
            if count_div:
                try:
                    count = int(_clean_text(count_div.text()))
                except ValueError:
                    count = 0
            sub_title = _clean_text(title_a.text()) if title_a else ""
            href = _attr(title_a, "href") if title_a else ""
            subtypes.append(SubtypeRow(
                category_id=int(data_id),
                title=sub_title,
                count=count,
                url=href or f"/test?filter=all&category_id={data_id}",
            ))

        types.append(TypeBlock(
            number=number,
            is_supplementary=is_supplementary,
            title=title_clean,
            total_count=total_count,
            subtypes=subtypes,
        ))
    return types


# ---------- test list ----------

def parse_test_list(html: str) -> tuple[Optional[int], Optional[int], list[RawProblem]]:
    """Распарсить /test?filter=all&category_id=N&page=K.

    Returns: (data_total_pages, data_page, [RawProblem]).
    Если div.prob_list отсутствует — (None, None, []).
    """
    tree = HTMLParser(html)
    list_node = tree.css_first("div.prob_list")
    if not list_node:
        return None, None, []

    def _to_int(s: Optional[str]) -> Optional[int]:
        try:
            return int(s) if s is not None else None
        except ValueError:
            return None

    data_total = _to_int(_attr(list_node, "data-total"))
    data_page = _to_int(_attr(list_node, "data-page"))

    problems: list[RawProblem] = []
    for cont in list_node.css("div.problem_container[id^='problem_']"):
        prob = _parse_problem_container(cont)
        if prob:
            problems.append(prob)
    return data_total, data_page, problems


def _parse_problem_container(cont: Node) -> Optional[RawProblem]:
    """Распарсить один div.problem_container из /test."""
    container_id = _attr(cont, "id") or ""
    m = _RE_PROBLEM_ID.match(container_id)
    if not m:
        return None
    sdamgia_id = m.group(1)

    maindiv = cont.css_first("div.prob_maindiv")
    if not maindiv:
        return None

    # Тип задачи — из span.prob_nums.
    type_marker: Optional[str] = None
    prob_nums = maindiv.css_first("span.prob_nums")
    if prob_nums:
        mtype = _RE_TYPE_NUMBER.search(prob_nums.text() or "")
        if mtype:
            type_marker = mtype.group(1).strip()

    # Условие: первый div[id^=body] внутри prob_maindiv.
    body_node = None
    for d in maindiv.css("div[id]"):
        d_id = _attr(d, "id") or ""
        if d_id.startswith("body"):
            body_node = d
            break
    statement_html = _inner_html(body_node)

    # Решение: div.solution[id^=sol] внутри maindiv.
    sol_node = None
    for d in maindiv.css("div.solution[id]"):
        d_id = _attr(d, "id") or ""
        if d_id.startswith("sol"):
            sol_node = d
            break
    solution_html = _inner_html(sol_node) if sol_node else None

    # Ответ: div.answer span — предпочтительно. Иначе — из конца решения.
    answer_text: Optional[str] = None
    ans_node = maindiv.css_first("div.answer span")
    if ans_node:
        txt = _clean_text(ans_node.text())
        # «Ответ: 13» → «13».
        answer_text = re.sub(r"^Ответ\s*:?\s*", "", txt).strip() or None

    # КЭС — две части: ссылка /search?... и подпись с КЭС-путём.
    kes_code: Optional[str] = None
    kes_title: Optional[str] = None
    subtype_path: Optional[str] = None

    align_left = maindiv.css_first("div.align-left")
    if align_left:
        search_link = align_left.css_first("a[href*='/search']")
        if search_link:
            href = _decode_html_attr(_attr(search_link, "href") or "")
            # href пример: /search?keywords=1&cb=1&search=2.1.2 Рациональные уравнения
            m = re.search(r"search=([\d.]+)\s+(.+?)(?:&|$)", href)
            if m:
                kes_code = m.group(1)
                kes_title = _clean_text(m.group(2))
            else:
                # У русского search-ссылки нет — пробуем span с font-weight:bold
                bold = align_left.css_first("span[style*='font-weight:bold']")
                if bold:
                    kes_title = _clean_text(bold.text())

        # subtype_path — второй блок div внутри align-left
        divs = align_left.css(":scope > div")
        if len(divs) >= 2:
            subtype_path = _clean_text(divs[1].text())

    # Аналоги.
    analog_ids: list[str] = []
    minor = maindiv.css_first("div.minor")
    if minor:
        for a in minor.css("a[href*='/problem?id=']"):
            href = _attr(a, "href") or ""
            ma = re.search(r"/problem\?id=(\d+)", href)
            if ma and ma.group(1) != sdamgia_id:
                if ma.group(1) not in analog_ids:
                    analog_ids.append(ma.group(1))

    return RawProblem(
        sdamgia_id=sdamgia_id,
        type_marker=type_marker,
        statement_html=statement_html,
        solution_html=solution_html,
        answer_text=answer_text,
        kes_code=kes_code,
        kes_title=kes_title,
        subtype_path=subtype_path,
        analog_ids=analog_ids,
    )
