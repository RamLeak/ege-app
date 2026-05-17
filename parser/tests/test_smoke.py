"""Smoke-тесты парсера на закешированных HTML из parser/recon-sdamgia/.

Цель — проверить, что регулярные правки selectors/parse не сломали извлечение
эталонных задач. Сеть НЕ дёргается. Прогонять до и после изменений в
selectors.yaml или pipeline/parse.py.

Запуск:
    python -m unittest parser/tests/test_smoke.py
    python parser/tests/test_smoke.py
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

PARSER_ROOT = Path(__file__).resolve().parent.parent
if str(PARSER_ROOT) not in sys.path:
    sys.path.insert(0, str(PARSER_ROOT))

from pipeline.parse import parse_catalog, parse_test_list
from pipeline.normalize import cleanup_text, detect_answer_format

RECON_DIR = PARSER_ROOT / "recon-sdamgia"

# Эталоны для cat=14 (Линейные, квадратные, кубические уравнения, тип №6).
# 5 задач отдаются на page=1, data-total=2.
EXPECTED_CAT14 = [
    # (sdamgia_id, type_marker, answer, kes_code)
    ("26662", "6", "13", "2.1.2"),
    ("26663", "6", "-5", "2.1.2"),
    ("77368", "6", "-1,5", "1.4.2"),
    ("77369", "6", "-6", "1.4.2"),
    ("77371", "6", "-7", "2.1.1"),
]

# Эталоны для cat=365 русского (Задания ФИПИ под №1, «Средства связи
# предложений в тексте»). 4 задачи, отдаются все на page=1, data-total=1.
EXPECTED_CAT365 = [
    # (sdamgia_id, type_marker, answer)  # kes_code у русского = None
    ("50643", "1", "или|либо"),
    ("50646", "1", "их"),
    ("50663", "1", "своей"),
    ("50675", "1", "если"),
]


class CatalogTests(unittest.TestCase):
    """Smoke-проверка parse_catalog."""

    @classmethod
    def setUpClass(cls) -> None:
        html = (RECON_DIR / "04_math_prob_catalog.html").read_text(
            encoding="utf-8", errors="replace"
        )
        cls.types = parse_catalog(html)

    def test_main_types_count(self) -> None:
        main = [t for t in self.types if not t.is_supplementary]
        self.assertEqual(len(main), 19, "Должно быть ровно 19 основных типов математики")

    def test_supplementary_types_count(self) -> None:
        supp = [t for t in self.types if t.is_supplementary]
        # На момент Stage 0 — 19 supplementary, может незначительно меняться.
        self.assertGreaterEqual(len(supp), 14, "Supplementary должно быть как минимум 14 (Д1..Д14)")

    def test_main_total_problems(self) -> None:
        main = [t for t in self.types if not t.is_supplementary]
        total = sum(t.total_count for t in main)
        # Заявленный объём — ~4863 на дату разведки. Допускаем дрейф ±10%.
        self.assertGreater(total, 4000, f"main total задач должно быть >4000, факт {total}")

    def test_first_main_type(self) -> None:
        main = [t for t in self.types if not t.is_supplementary]
        first = main[0]
        self.assertEqual(first.number, 1)
        self.assertIn("Планиметрия", first.title)
        self.assertEqual(len(first.subtypes), 9, "У типа №1 9 подвидов")
        self.assertEqual(first.subtypes[0].category_id, 79)
        self.assertIn("прямоугольного треугольника", first.subtypes[0].title)

    def test_subtype_count_total(self) -> None:
        total_subs = sum(len(t.subtypes) for t in self.types)
        self.assertGreater(total_subs, 100, f"Всего подвидов должно быть >100, факт {total_subs}")

    def test_theory_T_prefix_stripped(self) -> None:
        """«Т» от иконки theory должна сниматься, но настоящие русские
        заголовки на «Т» («Текстовые задачи», «Треугольники») — оставаться.
        """
        by_num = {(t.number, t.is_supplementary): t for t in self.types}
        # Тип №10 «Текстовые задачи» — настоящая «Т», должна остаться.
        self.assertEqual(by_num[(10, False)].title, "Текстовые задачи")
        # Тип №1 «Планиметрия» имел span.theory с «Т» перед — должно быть очищено.
        self.assertEqual(by_num[(1, False)].title, "Планиметрия")
        # Supplementary Д1 «Чтение графиков и диаграмм» тоже имел theory.
        self.assertEqual(by_num[(1, True)].title, "Чтение графиков и диаграмм")


class TestListTests(unittest.TestCase):
    """Smoke-проверка parse_test_list на cat=14 page=1."""

    @classmethod
    def setUpClass(cls) -> None:
        html = (RECON_DIR / "09_math_test_cat14.html").read_text(
            encoding="utf-8", errors="replace"
        )
        cls.total_pages, cls.data_page, cls.problems = parse_test_list(html)

    def test_pagination_meta(self) -> None:
        self.assertEqual(self.total_pages, 2)
        self.assertEqual(self.data_page, 1)

    def test_problem_count(self) -> None:
        self.assertEqual(len(self.problems), 5,
                         "На cat=14 page=1 должно быть 5 problem_container'ов")

    def test_problems_match_expected(self) -> None:
        for i, expected in enumerate(EXPECTED_CAT14):
            sid, marker, ans, kes = expected
            p = self.problems[i]
            with self.subTest(sdamgia_id=sid):
                self.assertEqual(p.sdamgia_id, sid)
                self.assertEqual(p.type_marker, marker)
                self.assertEqual(p.answer_text, ans)
                self.assertEqual(p.kes_code, kes)

    def test_statement_non_empty(self) -> None:
        for p in self.problems:
            self.assertGreater(len(p.statement_html), 50,
                               f"условие задачи {p.sdamgia_id} слишком короткое")

    def test_solution_present(self) -> None:
        # У всех 5 задач cat=14 должно быть авторское решение.
        for p in self.problems:
            self.assertIsNotNone(p.solution_html, f"нет решения у {p.sdamgia_id}")
            self.assertGreater(len(p.solution_html), 50)

    def test_analogs_extracted(self) -> None:
        # 4 из 5 задач имеют ≥15 аналогов; 5-я (77369) имеет 15.
        # Не привязываемся к точному числу — просто проверяем что аналоги есть.
        for p in self.problems:
            self.assertGreater(len(p.analog_ids), 5,
                               f"задача {p.sdamgia_id} имеет <5 аналогов")
            self.assertNotIn(p.sdamgia_id, p.analog_ids,
                             "сама задача не должна быть в списке аналогов")


class RussianCatalogTests(unittest.TestCase):
    """Smoke-проверка parse_catalog на каталоге русского.

    Структура каталога: 27 активных типов (№1-27) + 30 устаревших после
    разделителя `<h3>Дополнительные задания для подготовки</h3>`.
    Разделитель работает для обоих предметов (math + rus).
    """

    @classmethod
    def setUpClass(cls) -> None:
        html = (RECON_DIR / "07_rus_prob_catalog.html").read_text(
            encoding="utf-8", errors="replace"
        )
        cls.types = parse_catalog(html)

    def test_main_types_count(self) -> None:
        main = [t for t in self.types if not t.is_supplementary]
        self.assertEqual(len(main), 27,
                         "Должно быть 27 активных типов русского №1..№27")

    def test_supplementary_types_count(self) -> None:
        supp = [t for t in self.types if t.is_supplementary]
        # Точное число supplementary может слегка меняться при пересмотрах
        # sdamgia, но не должно быть <25.
        self.assertGreaterEqual(len(supp), 25,
                                f"Устаревших типов должно быть ≥25, факт {len(supp)}")

    def test_separator_works(self) -> None:
        """Проверка, что разделитель отделил «Задания Д A7» в supplementary."""
        # Этот тип в каталоге ИДЁТ ПЕРВЫМ после разделителя, у него
        # number=None (regex «Задания Д\\d+» не ловит «Задания Д A7»).
        supp = [t for t in self.types if t.is_supplementary]
        first_supp = supp[0]
        self.assertTrue("A7" in first_supp.title or first_supp.title.startswith("Задания"),
                        f"Первый supplementary должен содержать «A7», факт {first_supp.title!r}")

    def test_main_total_problems(self) -> None:
        """Объём активных задач русского — порядка 5000."""
        main = [t for t in self.types if not t.is_supplementary]
        total = sum(t.total_count for t in main)
        self.assertGreater(total, 4000, f"main total >4000, факт {total}")
        self.assertLess(total, 8000, f"main total <8000, факт {total}")

    def test_first_main_type(self) -> None:
        main = [t for t in self.types if not t.is_supplementary]
        self.assertEqual(main[0].number, 1)
        self.assertIn("Средства связи", main[0].title)

    def test_last_main_type(self) -> None:
        """Последний main — №27 «Сочинение»."""
        main = [t for t in self.types if not t.is_supplementary]
        self.assertEqual(main[-1].number, 27)
        self.assertIn("Сочинение", main[-1].title)


class RussianTestListTests(unittest.TestCase):
    """Smoke-проверка parse_test_list на cat=365 (4 задачи №1)."""

    @classmethod
    def setUpClass(cls) -> None:
        html = (RECON_DIR / "11_russian_test_cat365.html").read_text(
            encoding="utf-8", errors="replace"
        )
        cls.total_pages, cls.data_page, cls.problems = parse_test_list(html)

    def test_pagination_meta(self) -> None:
        self.assertEqual(self.total_pages, 1)
        self.assertEqual(self.data_page, 1)

    def test_problem_count(self) -> None:
        self.assertEqual(len(self.problems), 4,
                         "На cat=365 page=1 должно быть 4 problem_container'а")

    def test_problems_match_expected(self) -> None:
        for i, expected in enumerate(EXPECTED_CAT365):
            sid, marker, ans = expected
            p = self.problems[i]
            with self.subTest(sdamgia_id=sid):
                self.assertEqual(p.sdamgia_id, sid)
                self.assertEqual(p.type_marker, marker)
                self.assertEqual(p.answer_text, ans)
                # У русского kes_code обычно None — закрепляем как контракт.
                self.assertIsNone(p.kes_code,
                                  f"kes_code должен быть None для русского, факт {p.kes_code}")

    def test_statement_present(self) -> None:
        for p in self.problems:
            self.assertGreater(len(p.statement_html), 50,
                               f"условие задачи {p.sdamgia_id} слишком короткое")

    def test_no_formulas(self) -> None:
        """У русского типа №1 формул нет — лишь текст условия."""
        # statement_html не содержит <img class="tex">
        for p in self.problems:
            self.assertNotIn('class="tex"', p.statement_html)


class RussianRulesTests(unittest.TestCase):
    """Стресс-тест на извлечение правил русского через extract_rules.py.

    Запускать ПОСЛЕ полного прогона parser/scrapers/russian.py и
    parser/scrapers/extract_rules.py. Иначе тесты падают с FileNotFoundError —
    это сигнал «правила ещё не собраны».
    """

    @classmethod
    def setUpClass(cls) -> None:
        import json
        rules_path = PARSER_ROOT / "russian_rules.jsonl"
        if not rules_path.exists():
            raise unittest.SkipTest("russian_rules.jsonl ещё не создан")
        cls.rules = []
        with rules_path.open(encoding="utf-8") as f:
            for line in f:
                cls.rules.append(json.loads(line))

    def test_rules_loaded(self) -> None:
        self.assertGreater(len(self.rules), 10,
                           f"Должно быть >10 уникальных правил, факт {len(self.rules)}")

    def test_rule_type_5_paronyms_present(self) -> None:
        """СТРЕСС-ТЕСТ: у задач №5 «Употребление паронимов» должно быть правило."""
        rule = next((r for r in self.rules if r["rule_title"].startswith("5 ЕГЭ")), None)
        self.assertIsNotNone(rule, "Не нашлось правило с title «5 ЕГЭ. ...»")
        self.assertIn("пароним", rule["rule_title"].lower(),
                      f"title должен упоминать пароним, факт {rule['rule_title']!r}")
        self.assertGreater(len(rule["problem_ids"]), 50,
                           f"К правилу №5 должно быть привязано >50 задач, факт {len(rule['problem_ids'])}")
        # Очистим content от &shy; / U+00AD перед поиском ключевых слов.
        content = rule["content_html"].replace("&shy;", "").replace("­", "")
        self.assertGreater(len(content), 5000,
                           f"content_html правила должен быть >5KB, факт {len(content)}")
        # Plain-text должен содержать «пароним» хотя бы один раз.
        import re
        plain = re.sub(r"<[^>]+>", " ", content).lower()
        self.assertIn("пароним", plain,
                      "В тексте правила должно встречаться слово «пароним»")
        # Должны быть и sources, и difficulties.
        self.assertGreater(sum(rule["sources"].values()), 10,
                           "У правила №5 должно быть >10 sources")
        self.assertGreater(sum(rule["difficulties"].values()), 10,
                           "У правила №5 должно быть >10 difficulties")

    def test_dedup_efficient(self) -> None:
        """Дедупликация должна быть существенной: median ≥ 50 задач на правило."""
        sizes = sorted(len(r["problem_ids"]) for r in self.rules)
        median = sizes[len(sizes) // 2]
        self.assertGreaterEqual(median, 50,
                                f"median задач на правило ≥50, факт {median}")

    def test_no_empty_rules(self) -> None:
        for r in self.rules:
            with self.subTest(hash=r["rule_hash"]):
                self.assertTrue(r["rule_title"].strip())
                self.assertGreater(len(r["content_html"]), 100)
                self.assertGreater(len(r["problem_ids"]), 0)


class NormalizeTests(unittest.TestCase):
    def test_shy_removed(self) -> None:
        s = "Логи­ко-смыс­ло­вые от­но­ше­ния"  # с U+00AD
        out = cleanup_text(s)
        self.assertEqual(out, "Логико-смысловые отношения")

    def test_nbsp_replaced(self) -> None:
        s = "Тип 6\xa0№\xa026662"
        out = cleanup_text(s)
        self.assertEqual(out, "Тип 6 № 26662")

    def test_answer_format_number(self) -> None:
        self.assertEqual(detect_answer_format("13"), "number")
        self.assertEqual(detect_answer_format("-1,5"), "number")
        self.assertEqual(detect_answer_format("-7"), "number")

    def test_answer_format_alternatives(self) -> None:
        self.assertEqual(detect_answer_format("или|либо"), "alternatives")

    def test_answer_format_none(self) -> None:
        self.assertIsNone(detect_answer_format(None))
        self.assertIsNone(detect_answer_format(""))


if __name__ == "__main__":
    unittest.main(verbosity=2)
