# STAGE_P4-D.md — Pre-generated AI объяснения + новые тренажёры

> **Финальная итерация Phase 4 в автономном режиме.**
>
> Pre-generated AI-объяснения для всех тренажёров через max20 (Opus 4.7 в сессии Claude Code) + 8 новых тренажёров (3 русский + 5 математика) + поздравления после прохождения тренажёра.
>
> Состав:
> - **Часть А** — Pre-gen объяснения через max20 (Opus 4.7 в сессии Claude Code).
> - **Часть Б** — 3 новых тренажёра по русскому (источник: corpus.db из РешуЕГЭ).
> - **Часть В** — 5 новых тренажёров по математике.
> - **Часть Г** — Унифицированный UI объяснений (4 таба).
> - **Часть Д** — Поздравления после прохождения тренажёра.
> - **Часть Е** — Финальная интеграция (главный, каталог, статистика, бэкап).

---

# ПРАВИЛА АВТОНОМНОСТИ

Stage выполняется без участия пользователя (он ушёл надолго):
- **НЕ задавать вопросы** — все решения принимать самостоятельно.
- При сомнениях — **консервативный** вариант (existing patterns).
- `/compact` между большими частями (А↔Б, Б↔В, В↔Г) если контекст > 75%.
- Финальный отчёт **ОДИН** в конце ВСЕЙ итерации.
- При rate-limit ошибках Anthropic — **остановка работы**, документируй прогресс, продолжим позже.

---

## Что работает (НЕ ломать)

- Phase 1 + 2 + 3 + 4 (Stage A+B+C+C2+C3+D2).
- 12 существующих тренажёров.
- AnswerChecker, LatexCleaner, LetterChoiceRow, SmoothLazyColumn, SwipeBack, BreadcrumbLog, CrashRecoveryDialog.
- AI через OpenRouter/Gemini/Anthropic в runtime.
- Бэкап v1.7.
- Подсветка задач, прогресс-бары двух уровней, typesCovered (P4-D2).

---

# КРИТИЧЕСКИ ВАЖНО — Стратегия pre-gen через кастомного сабагента

Пользователь выбрал **кастомного сабагента** в `.claude/agents/explanation-generator.md`. Стратегия:

## Шаг 0 — Создать сабагента

Создать файл `.claude/agents/explanation-generator.md` со следующим содержимым:

```markdown
---
name: explanation-generator
description: Generates high-quality educational explanations for EGE trainer words (accent, word_blank, paronym, pleonasm, grammar). Each invocation processes a batch of 30-50 words and returns a JSON array. Use for pre-generating explanations cached in the SQLite trainer_explanations table.
tools:
  - bash
---

# Explanation Generator Subagent

You are an expert Russian language and mathematics tutor for EGE (Russian state exam) preparation. You explain rules to 11th-grade students in simple language.

## Your task

You receive a BATCH of 30-50 words/expressions. For each word, return a JSON object with 4 fields.

## Output format

CRITICAL: Output ONLY a valid JSON array. No markdown wrapping. No preamble. No explanations of what you're doing. Just the JSON array.

```json
[
  {
    "word": "торты",
    "kind": "accent",
    "subtype": "noun",
    "explanation": "В слове 'торты' ударение всегда падает на первый слог: тОрты. Запомни: тОрт-тОрты, как 'порт-порты'.",
    "rule": "В существительных, оканчивающихся на согласный, во множественном числе ударение часто сохраняется на основе.",
    "examples": "бАнты, шАрфы, крАны",
    "mnemonic": "Я ел тОрты, как в портУ моряк ел тОрты на бортУ."
  }
]
```

## Field requirements

1. **explanation**: Why exactly this way. 2-3 sentences, CONCRETE to this specific word.
2. **rule**: The EGE rule. Precise textbook formulation, 1-2 sentences.
3. **examples**: 3 similar examples separated by commas (same rule type).
4. **mnemonic**: Memorization aid. ONE sentence — image, rhyme, association, or story.

## Strict rules

- NEVER use markdown, LaTeX, **bold**, *italic*.
- NEVER use special characters: \\, $, {, }, ^{, _{.
- Fractions through slash: 1/2 (not \\frac{1}{2}).
- Greek letters in words: pi, alpha (not π, α).
- Explain SIMPLY, no academic language.
- Mnemonic must be SHORT and VIVID.

## Idempotency

Before processing each word, check if it already exists in `parser/corpus.db`:

```bash
sqlite3 parser/corpus.db "SELECT 1 FROM trainer_explanations WHERE word='$WORD' AND kind='$KIND' AND subtype='$SUBTYPE' LIMIT 1"
```

If result is non-empty, SKIP that word — do not include it in your output. This prevents duplicates when multiple batches run.

## After generating

Save batch to DB:
```bash
echo '$JSON' | python parser/scrapers/save_explanations_batch.py
```

Report: "Batch saved: N new, M skipped (already in DB)."
```

## Шаг 1 — Тест на одном пакете

**ПЕРЕД массовой генерацией** обязательно протестировать:

1. Прочитать первые 5 слов из подготовленного корпуса:
   ```bash
   python parser/scrapers/prepare_explanations_corpus.py > /tmp/all_words.json
   head -c 5000 /tmp/all_words.json | python -c "import sys,json; print(json.dumps(json.loads(sys.stdin.read()[:sys.stdin.read().rfind(']')+1])[:5]))"
   ```

2. Вызвать сабагента **на этих 5 словах**:
   ```
   Task(subagent_type="explanation-generator", 
        description="Test batch of 5 words", 
        prompt="Process these 5 words: [JSON of 5 words]")
   ```

3. Проверить результат:
   - Сабагент вернул валидный JSON массив?
   - 5 записей сохранено в `trainer_explanations` таблицу?
   - Качество объяснений приемлемое (есть rule + examples + mnemonic)?
   - Нет LaTeX/markdown в полях?

4. **Если тест прошёл** → переходим к массовой обработке.
5. **Если тест провалился** → исправляем сабагента или системный промпт, повторяем тест.

## Шаг 2 — Защита от дубликатов

Три уровня защиты:

### Уровень 1: SQL UNIQUE constraint

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_explanations_unique 
ON trainer_explanations(word, kind, subtype);
```

При повторной вставке — `INSERT OR REPLACE`, дубликаты невозможны на уровне БД.

### Уровень 2: Проверка перед отправкой пакета

Перед каждым вызовом сабагента — фильтруем слова которые **уже в БД**:

```python
# parser/scrapers/filter_unprocessed.py
import sys, json, sqlite3
from pathlib import Path

words = json.load(sys.stdin)
db_path = Path(__file__).parent.parent / "corpus.db"
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Получаем существующие
existing = set()
for row in cursor.execute("SELECT word, kind, subtype FROM trainer_explanations"):
    existing.add((row[0], row[1], row[2]))

# Фильтруем
unprocessed = [
    w for w in words 
    if (w["word"], w["kind"], w["subtype"]) not in existing
]

print(json.dumps(unprocessed, ensure_ascii=False))
conn.close()
```

Использование:
```bash
cat batch.json | python parser/scrapers/filter_unprocessed.py > batch_filtered.json
```

### Уровень 3: Lock-файл (если используются параллельные subagent'ы)

Создать `parser/.processing.lock` со списком слов которые сейчас обрабатываются:

```python
# parser/scrapers/reserve_batch.py
import sys, json, fcntl, time
from pathlib import Path

LOCK_FILE = Path(__file__).parent.parent / ".processing.lock"
words_to_reserve = json.load(sys.stdin)

# Эксклюзивный lock
with open(LOCK_FILE, 'a+') as f:
    fcntl.flock(f.fileno(), fcntl.LOCK_EX)
    f.seek(0)
    reserved = set()
    for line in f:
        try:
            entry = json.loads(line.strip())
            # Проверка timestamp — освобождаем если старше 10 минут
            if time.time() - entry["timestamp"] < 600:
                reserved.add((entry["word"], entry["kind"], entry["subtype"]))
        except:
            pass
    
    available = []
    for w in words_to_reserve:
        key = (w["word"], w["kind"], w["subtype"])
        if key not in reserved:
            available.append(w)
            f.write(json.dumps({
                "word": w["word"], "kind": w["kind"], "subtype": w["subtype"],
                "timestamp": time.time()
            }) + "\n")
    
    fcntl.flock(f.fileno(), fcntl.LOCK_UN)

print(json.dumps(available, ensure_ascii=False))
```

**Использование:** перед каждым batch вызвать `reserve_batch.py` — он вернёт только слова которые **не зарезервированы другими параллельными процессами**.

## Шаг 3 — Массовая обработка

После успешного теста:

1. Разбить корпус на пакеты по 30-50 слов.
2. Для каждого пакета:
   a. `cat batch.json | python filter_unprocessed.py | python reserve_batch.py > batch_filtered.json`
   b. `Task(subagent_type="explanation-generator", prompt=batch_filtered)`
   c. Сабагент возвращает JSON → проверяем валидность → сохраняем через `save_explanations_batch.py`.
3. После каждых **3-5 пакетов** — `/compact` если контекст > 75%.
4. Отчёт о прогрессе в каждом ответе.

## Приоритизация если не успеваем

Если в одной сессии не успеваешь весь корпус (1400 слов):

1. **Приоритет 1:** Все 4 файла accent_words_*.json (~200 слов).
2. **Приоритет 2:** Файлы word_blank_words_pre_pri.json, _n_nn.json (~150 слов).
3. **Приоритет 3:** Остальные word_blank_words_*.json.
4. **Приоритет 4:** Новые тренажёры (части Б и В).

В отчёте указать сколько обработано и сколько осталось — пользователь продолжит в следующей сессии.

## Fallback стратегия

Если Task tool / subagent НЕ доступен или НЕ работает:

1. **Fallback 1:** Пакетная генерация в основном контексте Claude Code (без сабагента).
2. **Fallback 2:** Записать в Concerns «pre-gen не выполнен, нужна следующая сессия с другим подходом».

Сабагент в `.claude/agents/explanation-generator.md` **остаётся в проекте** — пригодится для Phase 5 SRS-карточек.

---

# ЧАСТЬ А — Pre-gen объяснения через max20

## А1. Системный промпт для генерации

```
Ты — лучший репетитор по русскому языку и математике для подготовки к ЕГЭ. Объясняешь правила школьникам 11 класса простым языком.

Тебе дан ПАКЕТ из 30-50 слов/выражений. Для каждого верни JSON с 4 полями:

1. "explanation": Почему именно так. 2-3 предложения, КОНКРЕТНО про это слово.
2. "rule": Какое правило ЕГЭ. Точная формулировка из учебника, 1-2 предложения.
3. "examples": 3 похожих примера через запятую (тот же тип правила).
4. "mnemonic": Запоминалка. ОДНО предложение — образное, рифма/ассоциация/история.

ТРЕБОВАНИЯ:
- НИКОГДА не используй markdown, LaTeX, **жирный**, *курсив*.
- НИКОГДА не пиши спец-символы: \, $, {, }, ^{, _{.
- Дроби через слэш: 1/2 (не \frac{1}{2}).
- Греческие буквы словами: пи, альфа (не π, α).
- Объясняй ПРОСТО, без академического языка.
- Мнемоника КОРОТКАЯ и ОБРАЗНАЯ.

ФОРМАТ: один валидный JSON-массив. БЕЗ markdown оборачивания. БЕЗ преамбулы. Только массив.
```

## А2. Подготовка корпуса

`parser/scrapers/prepare_explanations_corpus.py` — собирает все слова из тренажёров в один JSON:

```python
"""Собирает все слова из тренажёров в один JSON для обработки."""
import json
import glob
import sqlite3
from pathlib import Path

DATA_DIR = Path(__file__).parent.parent / "data"
DB_PATH = Path(__file__).parent.parent / "corpus.db"

conn = sqlite3.connect(DB_PATH)
cursor = conn.cursor()
cursor.execute("""
    CREATE TABLE IF NOT EXISTS trainer_explanations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        word TEXT NOT NULL,
        kind TEXT NOT NULL,
        subtype TEXT NOT NULL,
        UNIQUE(word, kind, subtype)
    )
""")
cursor.execute("SELECT word, kind, subtype FROM trainer_explanations")
already = set((r[0], r[1], r[2]) for r in cursor.fetchall())
conn.close()

corpus = []

# Accent words (4 файла)
for f in glob.glob(str(DATA_DIR / "accent_words_*.json")):
    subtype = Path(f).stem.replace("accent_words_", "")
    with open(f, encoding="utf-8") as fp:
        for w in json.load(fp):
            word = w.get("word", "")
            if (word, "accent", subtype) in already:
                continue
            corpus.append({
                "word": word, "kind": "accent", "subtype": subtype,
                "hint": f"ударный слог: {w.get('stressed_syllable', '')}"
            })

# Word blank (8 файлов)
for f in glob.glob(str(DATA_DIR / "word_blank_words_*.json")):
    subtype = Path(f).stem.replace("word_blank_words_", "")
    with open(f, encoding="utf-8") as fp:
        for w in json.load(fp):
            full = w.get("full", "")
            if (full, "word_blank", subtype) in already:
                continue
            corpus.append({
                "word": full, "kind": "word_blank", "subtype": subtype,
                "hint": f"правильная буква: {w.get('answer', '')}"
            })

print(json.dumps(corpus, ensure_ascii=False, indent=2))
print(f"\nTotal to process: {len(corpus)}", file=__import__('sys').stderr)
```

## А3. Сохранение пакета

`parser/scrapers/save_explanations_batch.py`:

```python
"""Batch save trainer explanations to SQLite. Idempotent."""
import sys, json, sqlite3, time
from pathlib import Path

def main():
    data = json.load(sys.stdin)
    db_path = Path(__file__).parent.parent / "corpus.db"
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS trainer_explanations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word TEXT NOT NULL, kind TEXT NOT NULL, subtype TEXT NOT NULL,
            explanation TEXT, rule TEXT, examples TEXT, mnemonic TEXT,
            generated_at INTEGER, UNIQUE(word, kind, subtype)
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_explanations_lookup ON trainer_explanations(word, kind)")
    
    now = int(time.time())
    inserted = 0
    for item in data:
        try:
            cursor.execute("""
                INSERT OR REPLACE INTO trainer_explanations 
                (word, kind, subtype, explanation, rule, examples, mnemonic, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                item["word"], item["kind"], item["subtype"],
                item.get("explanation", ""), item.get("rule", ""),
                item.get("examples", ""), item.get("mnemonic", ""), now
            ))
            inserted += 1
        except Exception as e:
            print(f"Skipped {item.get('word')}: {e}", file=sys.stderr)
    
    conn.commit()
    cursor.execute("SELECT COUNT(*) FROM trainer_explanations")
    total = cursor.fetchone()[0]
    print(f"Batch saved: {inserted}. Total in DB: {total}")
    conn.close()

if __name__ == "__main__":
    main()
```

## А4. Workflow

1. `python prepare_explanations_corpus.py > /tmp/all_words.json`.
2. Прочитать первые 30-50 слов.
3. В своём ответе сгенерировать JSON-массив с объяснениями.
4. `echo '$JSON' | python save_explanations_batch.py`.
5. /compact после 3-4 пакетов.
6. Следующий пакет.

## А5. Android-интеграция

`data/db/TrainerExplanationDao.kt`:
```kotlin
@Dao
interface TrainerExplanationDao {
    @Query("SELECT * FROM trainer_explanations WHERE word = :word AND kind = :kind LIMIT 1")
    suspend fun get(word: String, kind: String): TrainerExplanationEntity?
}

@Entity(tableName = "trainer_explanations")
data class TrainerExplanationEntity(
    @PrimaryKey val id: Long,
    val word: String, val kind: String, val subtype: String,
    val explanation: String?, val rule: String?,
    val examples: String?, val mnemonic: String?,
    @ColumnInfo(name = "generated_at") val generatedAt: Long
)
```

---

# ЧАСТЬ Б — 3 новых тренажёра по русскому

## Б1. Источник данных — corpus.db

**ВАЖНО:** Берём примеры из **уже существующей corpus.db** (РешуЕГЭ/sdamgia, Phase 1). Не генерируем через Opus.

```sql
-- Все задачи №5 (Паронимы)
SELECT id, statement_html, short_answer FROM problems 
WHERE subject_slug = 'rus' AND type_number = 5;

-- Все задачи №6 (Плеоназмы — только где нужно ИСКЛЮЧИТЬ)
SELECT id, statement_html, short_answer FROM problems 
WHERE subject_slug = 'rus' AND type_number = 6;

-- Все задачи №7 (Грамошибки)
SELECT id, statement_html, short_answer FROM problems 
WHERE subject_slug = 'rus' AND type_number = 7;
```

Через парсер `parser/scrapers/extract_trainer_data_rus.py` извлекаем нужные поля.

## Б2. Тренажёр Паронимов (№5)

### Б2.1 Парсинг

`parser/scrapers/extract_paronyms.py`:

```python
"""
Извлекает паронимы из задач №5 в corpus.db.
Формат задачи №5 sdamgia: текст с одним выделенным словом, нужно заменить на пароним.

Pattern: ищем в statement_html слова в <strong>...</strong> или <b>...</b> 
+ short_answer = правильное слово.

Сохраняем в parser/data/paronyms.json.
"""
import sqlite3, json, re
from pathlib import Path
from bs4 import BeautifulSoup

DB_PATH = Path(__file__).parent.parent / "corpus.db"
OUT_PATH = Path(__file__).parent.parent / "data" / "paronyms.json"

conn = sqlite3.connect(DB_PATH)
cursor = conn.cursor()
cursor.execute("""
    SELECT id, statement_html, short_answer FROM problems 
    WHERE subject_slug = 'rus' AND type_number = 5
""")

paronyms = []
for problem_id, html, correct_answer in cursor.fetchall():
    soup = BeautifulSoup(html, 'html.parser')
    
    # Найти выделенное слово
    bold_words = soup.find_all(['strong', 'b'])
    if not bold_words:
        continue
    
    wrong_word = bold_words[0].get_text().strip()
    correct_word = (correct_answer or "").strip()
    
    if not wrong_word or not correct_word:
        continue
    
    # Извлечь предложение с этим словом
    text = soup.get_text(separator=' ')
    sentences = re.split(r'(?<=[.!?])\s+', text)
    target_sentence = None
    for s in sentences:
        if wrong_word in s:
            target_sentence = s.strip()
            break
    
    if not target_sentence:
        continue
    
    paronyms.append({
        "problem_id": problem_id,
        "sentence": target_sentence,
        "wrong_word": wrong_word,
        "correct_word": correct_word
    })

OUT_PATH.write_text(json.dumps(paronyms, ensure_ascii=False, indent=2), encoding='utf-8')
print(f"Extracted {len(paronyms)} paronym examples")
conn.close()
```

### Б2.2 UI — ParonymTrainerScreen

```kotlin
@Composable
fun ParonymTrainerScreen(...) {
    val state by viewModel.state.collectAsState()
    val current = state.currentItem ?: return
    
    Column(...) {
        Text("Замени выделенное слово на подходящее:")
        
        // Предложение с подсвеченным wrong_word
        SentenceWithHighlight(
            sentence = current.sentence,
            highlightWord = current.wrongWord
        )
        
        // 2 кнопки
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceButton(
                text = current.wrongWord,
                onClick = { viewModel.choose(current.wrongWord) },
                state = ...
            )
            ChoiceButton(
                text = current.correctWord,
                onClick = { viewModel.choose(current.correctWord) },
                state = ...
            )
        }
        
        // AI кнопка после Verdict
        if (state.verdict != null) {
            ExplanationButton(
                word = "${current.wrongWord}/${current.correctWord}",
                kind = "paronym",
                fallbackContext = current.sentence
            )
        }
    }
}
```

## Б3. Тренажёр Плеоназмов (№6) + Грамошибок (№7) — общий компонент

**Ключевое решение:** №6 и №7 имеют **идентичный UX** — тап на проблемное слово в предложении. Делаем **одну общую компоненту**.

### Б3.1 Парсинг плеоназмов

`parser/scrapers/extract_pleonasms.py`:

```python
"""
Извлекает плеоназмы из задач №6.

ВАЖНО: фильтруем — только задачи где нужно ИСКЛЮЧИТЬ слово.
В задачах №6 две формулировки:
1. "Отредактируйте предложение: исключите лишнее слово" ← НАМ НУЖНО
2. "Отредактируйте предложение: замените неверно употреблённое слово" ← НЕ берём

Фильтр через регекс по statement_html.

Pattern: предложение в задаче + лишнее слово = short_answer.
"""
import sqlite3, json, re
from pathlib import Path
from bs4 import BeautifulSoup

DB_PATH = Path(__file__).parent.parent / "corpus.db"
OUT_PATH = Path(__file__).parent.parent / "data" / "pleonasms.json"

conn = sqlite3.connect(DB_PATH)
cursor = conn.cursor()
cursor.execute("""
    SELECT id, statement_html, short_answer FROM problems 
    WHERE subject_slug = 'rus' AND type_number = 6
""")

pleonasms = []
for problem_id, html, correct_answer in cursor.fetchall():
    soup = BeautifulSoup(html, 'html.parser')
    text = soup.get_text(separator=' ')
    
    # ФИЛЬТР: только "исключить", не "заменить"
    if not re.search(r'исключи[тл]', text, re.IGNORECASE):
        continue
    if re.search(r'замен[иь]', text, re.IGNORECASE):
        continue  # двойная проверка
    
    extra_word = (correct_answer or "").strip().lower()
    if not extra_word:
        continue
    
    # Извлечь предложение (после инструкции "Отредактируйте...")
    # Обычно структура: "Инструкция. Само предложение."
    sentences = re.split(r'(?<=[.!?])\s+', text)
    if len(sentences) < 2:
        continue
    
    # Берём предложение которое содержит extra_word
    target_sentence = None
    for s in sentences[1:]:  # skip instruction
        if extra_word in s.lower():
            target_sentence = s.strip()
            break
    
    if not target_sentence:
        continue
    
    pleonasms.append({
        "problem_id": problem_id,
        "sentence": target_sentence,
        "extra_word": extra_word
    })

OUT_PATH.write_text(json.dumps(pleonasms, ensure_ascii=False, indent=2), encoding='utf-8')
print(f"Extracted {len(pleonasms)} pleonasm examples (after filter)")
conn.close()
```

### Б3.2 Парсинг грамошибок

`parser/scrapers/extract_grammar_errors.py`:

```python
"""
Извлекает грамматические ошибки из задач №7.

Pattern: задача №7 содержит таблицу с 5 типами ошибок + 9 предложений.
Берём ОДИН пример из задачи где есть конкретное ошибочное слово.

В short_answer хранится правильный код вида "12345" — соответствие 5 ошибок к 5 предложениям.
Из этого извлекаем одну пару (ошибка-тип, предложение).
"""
import sqlite3, json, re
from pathlib import Path
from bs4 import BeautifulSoup

DB_PATH = Path(__file__).parent.parent / "corpus.db"
OUT_PATH = Path(__file__).parent.parent / "data" / "grammar_errors.json"

conn = sqlite3.connect(DB_PATH)
cursor = conn.cursor()
cursor.execute("""
    SELECT id, statement_html, short_answer FROM problems 
    WHERE subject_slug = 'rus' AND type_number = 7
""")

errors = []
for problem_id, html, correct_answer in cursor.fetchall():
    soup = BeautifulSoup(html, 'html.parser')
    
    # Извлечь нумерованные предложения из задачи
    # sdamgia формат: "1) ...предложение... 2) ...предложение..."
    text = soup.get_text(separator=' ')
    sentence_matches = re.findall(r'(?:^|\s)(\d)\)\s*([^0-9]+?)(?=\s+\d\)|$)', text)
    
    if not sentence_matches:
        continue
    
    # Из этой задачи берём ОДИН пример — первое нумерованное предложение
    # которое содержит грамошибку (попадает в short_answer).
    # short_answer формата "21345" — список из 5 цифр.
    
    if not correct_answer or len(correct_answer) < 1:
        continue
    
    # Берём первое сопоставление
    first_sentence_num = int(correct_answer[0]) if correct_answer[0].isdigit() else None
    if first_sentence_num is None:
        continue
    
    target = None
    for num, sentence in sentence_matches:
        if int(num) == first_sentence_num:
            target = sentence.strip()
            break
    
    if not target:
        continue
    
    # Слова в предложении — нужно определить ошибочное.
    # Для Phase 4 берём краткую запись: целое предложение + помечаем что в нём ошибка.
    # Конкретное "ошибочное слово" определяет Opus 4.7 при предварительном анализе.
    
    errors.append({
        "problem_id": problem_id,
        "sentence": target,
        # error_word будет заполнен через Opus при импорте
        "error_word": None  # placeholder
    })

OUT_PATH.write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding='utf-8')
print(f"Extracted {len(errors)} grammar error examples (need Opus annotation)")
conn.close()
```

После извлечения — пакетная аннотация через Opus 4.7:

```
Для каждого предложения в grammar_errors.json:
1. Прочитать предложение.
2. Определить конкретное слово где грамматическая ошибка.
3. Сохранить error_word в JSON.
```

Это можно сделать в твоих ответах пакетами по 20-30 предложений. Если короче времени — оставить как placeholder и обработать в Phase 5.

### Б3.3 UI — Общая компонента WordTapInSentenceTrainer

```kotlin
@Composable
fun WordTapInSentenceTrainer(
    sentence: String,
    targetWord: String,  // правильное слово для тапа
    instruction: String,  // "Найди лишнее слово" / "Найди ошибочное слово"
    onAnswer: (String) -> Unit,
    state: TrainerState
) {
    Column {
        Text(instruction, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        
        // Предложение со словами-кнопками
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val words = sentence.split(" ")
            words.forEach { word ->
                TappableWord(
                    word = word,
                    targetWord = targetWord,
                    state = state,
                    onTap = { onAnswer(word.lowercase().trim('.', ',', ';', '!', '?')) }
                )
            }
        }
        
        // AI кнопка после Verdict
        if (state.verdict != null) {
            ExplanationButton(...)
        }
    }
}

@Composable
fun TappableWord(
    word: String,
    targetWord: String,
    state: TrainerState,
    onTap: () -> Unit
) {
    val cleanWord = word.lowercase().trim('.', ',', ';', '!', '?')
    val isTarget = cleanWord == targetWord.lowercase()
    
    val backgroundColor = when {
        state.verdict == Verdict.Correct && state.tappedWord == cleanWord && isTarget -> SystemGreen
        state.verdict == Verdict.Wrong && state.tappedWord == cleanWord -> SystemRed
        state.verdict == Verdict.Wrong && isTarget -> SystemGreenTint  // подсветить правильный
        else -> Color.Transparent
    }
    
    val haptic = LocalHapticFeedback.current
    
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(enabled = state.verdict == null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTap()
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(word, style = MaterialTheme.typography.bodyLarge)
    }
}
```

### Б3.4 PleonasmTrainerScreen

```kotlin
@Composable
fun PleonasmTrainerScreen(viewModel: PleonasmTrainerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val current = state.currentItem ?: return
    
    SwipeBackContainer(onBack = ...) {
        WordTapInSentenceTrainer(
            sentence = current.sentence,
            targetWord = current.extraWord,
            instruction = "Найди ЛИШНЕЕ слово",
            onAnswer = { viewModel.choose(it) },
            state = state.trainerState
        )
    }
}
```

### Б3.5 GrammarErrorTrainerScreen

```kotlin
@Composable
fun GrammarErrorTrainerScreen(viewModel: GrammarErrorTrainerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val current = state.currentItem ?: return
    
    SwipeBackContainer(onBack = ...) {
        WordTapInSentenceTrainer(
            sentence = current.sentence,
            targetWord = current.errorWord,
            instruction = "Найди слово где ГРАММАТИЧЕСКАЯ ОШИБКА",
            onAnswer = { viewModel.choose(it) },
            state = state.trainerState
        )
    }
}
```

---

# ЧАСТЬ В — 5 новых тренажёров по математике

## В1. Тригонометрия

### Данные

`parser/data/trig_values.json`:
```json
[
  {"angle_deg": 30, "angle_rad": "пи/6", "sin": "1/2", "cos": "корень(3)/2", "tan": "1/корень(3)", "ctg": "корень(3)"},
  {"angle_deg": 45, "angle_rad": "пи/4", "sin": "корень(2)/2", "cos": "корень(2)/2", "tan": "1", "ctg": "1"},
  {"angle_deg": 60, "angle_rad": "пи/3", "sin": "корень(3)/2", "cos": "1/2", "tan": "корень(3)", "ctg": "1/корень(3)"},
  {"angle_deg": 90, "angle_rad": "пи/2", "sin": "1", "cos": "0", "tan": "не определён", "ctg": "0"},
  {"angle_deg": 0, "angle_rad": "0", "sin": "0", "cos": "1", "tan": "0", "ctg": "не определён"},
  {"angle_deg": 180, "angle_rad": "пи", "sin": "0", "cos": "-1", "tan": "0", "ctg": "не определён"}
]
```

### UI

`TrigonometryTrainerScreen`:
- Случайно выбирается функция (sin/cos/tan/ctg) и угол.
- 4 кнопки с вариантами ответа: правильный + 3 близких неправильных из таблицы.
- Опция в Settings: «На скорость» (таймер 5 секунд).

## В2. Формулы сокращённого умножения

### Данные

`parser/data/short_multiplication_formulas.json`:
```json
[
  {"id": "square_sum", "name": "Квадрат суммы", "formula": "(a + b)² = a² + 2ab + b²"},
  {"id": "square_diff", "name": "Квадрат разности", "formula": "(a - b)² = a² - 2ab + b²"},
  {"id": "diff_squares", "name": "Разность квадратов", "formula": "a² - b² = (a-b)(a+b)"},
  {"id": "cube_sum", "name": "Куб суммы", "formula": "(a + b)³ = a³ + 3a²b + 3ab² + b³"},
  {"id": "cube_diff", "name": "Куб разности", "formula": "(a - b)³ = a³ - 3a²b + 3ab² - b³"},
  {"id": "sum_cubes", "name": "Сумма кубов", "formula": "a³ + b³ = (a+b)(a² - ab + b²)"},
  {"id": "diff_cubes", "name": "Разность кубов", "formula": "a³ - b³ = (a-b)(a² + ab + b²)"}
]
```

### UI

`ShortMultiplicationTrainerScreen`: показывается левая часть формулы → выбрать правую из 4 вариантов.

## В3. Логарифмы и степени

### Данные

`parser/data/log_power_properties.json` — ~10 свойств:
```json
[
  {"left": "log(a·b)", "right": "log(a) + log(b)"},
  {"left": "log(a/b)", "right": "log(a) - log(b)"},
  {"left": "log(a^n)", "right": "n · log(a)"},
  {"left": "a^m · a^n", "right": "a^(m+n)"},
  {"left": "a^m / a^n", "right": "a^(m-n)"},
  {"left": "(a^m)^n", "right": "a^(m·n)"},
  {"left": "a^0", "right": "1"},
  {"left": "a^(-n)", "right": "1/a^n"}
]
```

### UI

Match карточки: левая часть → правая из 4 вариантов.

## В4. Производные стандартных функций

### Данные

`parser/data/derivatives.json`:
```json
[
  {"function": "x^n", "derivative": "n · x^(n-1)"},
  {"function": "sin(x)", "derivative": "cos(x)"},
  {"function": "cos(x)", "derivative": "-sin(x)"},
  {"function": "tg(x)", "derivative": "1/cos²(x)"},
  {"function": "ctg(x)", "derivative": "-1/sin²(x)"},
  {"function": "e^x", "derivative": "e^x"},
  {"function": "ln(x)", "derivative": "1/x"},
  {"function": "log_a(x)", "derivative": "1/(x · ln(a))"},
  {"function": "a^x", "derivative": "a^x · ln(a)"},
  {"function": "c", "derivative": "0"}
]
```

### UI

Карточка: функция → выбрать производную из 4 вариантов.

## В5. Геометрические формулы

### Данные

`parser/data/geometric_formulas.json` — ~25 формул (площади, объёмы, теоремы).

### UI

Карточка: фигура + что найти → выбрать формулу из 4 вариантов.

## В6. Общий компонент MathChoiceTrainer

Для всех 5 матем тренажёров — одна композиция:

```kotlin
@Composable
fun MathChoiceTrainer(
    question: String,
    correctAnswer: String,
    distractors: List<String>,
    timerEnabled: Boolean,
    onAnswer: (String) -> Unit,
    state: TrainerState
) {
    val allOptions = remember(question) { (listOf(correctAnswer) + distractors).shuffled() }
    
    Column {
        // Таймер если "На скорость"
        if (timerEnabled) {
            TimerBar(maxMs = 5000, ...)
        }
        
        Text(question, style = MaterialTheme.typography.headlineMedium)
        
        Spacer(Modifier.height(24.dp))
        
        // 4 кнопки 2x2 grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            allOptions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { option ->
                        MathOptionButton(
                            text = option,
                            isCorrect = option == correctAnswer,
                            state = state,
                            onClick = { onAnswer(option) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
```

---

# ЧАСТЬ Г — Унифицированный UI объяснений

## Г1. ExplanationBottomSheet с 4 табами

```kotlin
@Composable
fun ExplanationBottomSheet(
    word: String,
    kind: String,
    fallbackContext: String,
    onDismiss: () -> Unit,
    viewModel: ExplanationViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var activeTab by remember { mutableStateOf(ExplanationTab.WHY) }
    
    LaunchedEffect(word, kind) { viewModel.load(word, kind, fallbackContext) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 28.sp)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(word, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (state.source == "pre_gen") "Объяснение (Opus 4.7)" 
                        else "Объяснение (онлайн AI)",
                        style = MaterialTheme.typography.bodySmall,
                        color = LabelSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            TabRow(selectedTabIndex = activeTab.ordinal) {
                ExplanationTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> Text("❌ ${state.error}", color = SystemRed)
                else -> {
                    val content = when (activeTab) {
                        ExplanationTab.WHY -> state.explanation
                        ExplanationTab.RULE -> state.rule
                        ExplanationTab.EXAMPLES -> state.examples
                        ExplanationTab.MNEMONIC -> state.mnemonic
                    }
                    Text(
                        content.ifBlank { "Информация отсутствует." },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

enum class ExplanationTab(val title: String) {
    WHY("Почему"),
    RULE("Правило"),
    EXAMPLES("Примеры"),
    MNEMONIC("Запомнить")
}
```

## Г2. ExplanationViewModel — приоритет pre-gen

```kotlin
class ExplanationViewModel(
    private val explanationDao: TrainerExplanationDao,
    private val onlineProvider: AiProviderRegistry,
    private val settingsStore: AiSettingsStore,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {
    
    fun load(word: String, kind: String, fallbackContext: String) {
        viewModelScope.launch {
            _state.value = State(isLoading = true)
            
            // 1. Pre-gen из БД
            val preGen = explanationDao.get(word, kind)
            if (preGen != null) {
                _state.value = State(
                    explanation = preGen.explanation ?: "",
                    rule = preGen.rule ?: "",
                    examples = preGen.examples ?: "",
                    mnemonic = preGen.mnemonic ?: "",
                    source = "pre_gen",
                    isLoading = false
                )
                return@launch
            }
            
            // 2. Fallback на онлайн AI
            val settings = settingsStore.settings.first()
            val apiKey = secureKeyStore.getKey(settings.activeProvider)
            
            if (apiKey.isNullOrBlank()) {
                _state.value = State(
                    error = "Объяснение не найдено. Подключи AI в Настройках для онлайн ответа.",
                    isLoading = false
                )
                return@launch
            }
            
            val provider = onlineProvider.get(settings.activeProvider)
            val response = provider.ask(
                "Объясни почему так. Дай правило, 3 похожих примера, мнемонику. Верни JSON: explanation/rule/examples/mnemonic.",
                fallbackContext,
                settings.modelByProvider[settings.activeProvider] ?: provider.defaultModelId,
                apiKey
            )
            
            when (response) {
                is AiResponse.Success -> {
                    _state.value = parseOnlineResponse(response.text)
                }
                is AiResponse.Error -> {
                    _state.value = State(error = response.message, isLoading = false)
                }
            }
        }
    }
    
    private fun parseOnlineResponse(text: String): State {
        return try {
            val json = JSONObject(text)
            State(
                explanation = json.optString("explanation"),
                rule = json.optString("rule"),
                examples = json.optString("examples"),
                mnemonic = json.optString("mnemonic"),
                source = "online_ai",
                isLoading = false
            )
        } catch (e: Exception) {
            State(explanation = text, source = "online_ai_raw", isLoading = false)
        }
    }
}
```

## Г3. Замена кнопок в тренажёрах

Во всех 18 тренажёрах:
- Старая кнопка "🤖 ИИ" → новая **"📖 Объяснение"**.
- Использует `ExplanationBottomSheet` вместо `AskAiBottomSheet`.
- `AskAiBottomSheet` остаётся для свободных вопросов в задачах каталога.

---

# ЧАСТЬ Д — Поздравления после прохождения тренажёра

## Д1. Определение "прохождения"

Тренажёр считается **пройденным** когда:
- Все слова/карточки тренажёра решены правильно **хотя бы один раз**.
- Или достигнуто 100% выученных слов в `trainerWordsLearned`.

## Д2. UI — CongratulationDialog

`ui/trainer/CongratulationDialog.kt`:

```kotlin
@Composable
fun CongratulationDialog(
    trainerName: String,
    wordsCount: Int,
    onContinue: () -> Unit,
    onClose: () -> Unit
) {
    val confettiState = remember { mutableStateOf(true) }
    
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgElevated),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box {
                // Confetti animation overlay
                if (confettiState.value) {
                    ConfettiAnimation(
                        onComplete = { confettiState.value = false }
                    )
                }
                
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        "Поздравляем!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "Ты прошёл тренажёр",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LabelSecondary,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        trainerName,
                        style = MaterialTheme.typography.titleLarge,
                        color = SystemBlue,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "Освоено: $wordsCount слов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LabelSecondary
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton(
                            text = "Закрыть",
                            onClick = onClose,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = "Ещё раз",
                            onClick = onContinue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfettiAnimation(onComplete: () -> Unit) {
    // Простая Canvas-based анимация конфетти
    // 30-50 разноцветных частиц падают вниз с rotation
    val particles = remember { generateConfettiParticles(40) }
    var time by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        animate(initialValue = 0f, targetValue = 3000f, animationSpec = tween(3000)) { value, _ ->
            time = value
        }
        onComplete()
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val progress = (time + particle.delay) / 3000f
            if (progress > 0 && progress < 1) {
                drawCircle(
                    color = particle.color,
                    radius = 8.dp.toPx(),
                    center = Offset(
                        particle.x * size.width,
                        progress * size.height * 1.2f
                    )
                )
            }
        }
    }
}
```

## Д3. Триггер

В ViewModel'ях тренажёров после каждого правильного ответа проверка:

```kotlin
fun onCorrectAnswer() {
    // ... текущая логика
    
    val newSolvedCount = currentSolvedCount + 1
    val totalCount = totalWordsInTrainer
    
    if (newSolvedCount >= totalCount) {
        _state.value = _state.value.copy(
            showCongratulation = true,
            congratulationTrainerName = trainerName,
            congratulationWordsCount = newSolvedCount
        )
        userStatsStore.markTrainerCompleted(trainerName)
    }
}
```

## Д4. UserStatsStore — markTrainerCompleted

```kotlin
suspend fun markTrainerCompleted(trainerName: String) {
    context.dataStore.edit { prefs ->
        val key = stringSetPreferencesKey("trainers_completed")
        val current = prefs[key] ?: emptySet()
        prefs[key] = current + trainerName
    }
}

suspend fun getTrainersCompletedCount(): Int {
    return context.dataStore.data.map {
        it[stringSetPreferencesKey("trainers_completed")]?.size ?: 0
    }.first()
}
```

---

# ЧАСТЬ Е — Финальная интеграция

## Е1. Главный экран

Добавить в секцию "Быстрый старт":
- **🎯 Случайный тренажёр** — рандомный из всех 18.
- **📚 Все тренажёры** — список всех с прогрессом.

## Е2. Каталог расширяется

```
Русский язык
├── Тренажёры
│   ├── №4 Ударения (4 категории)
│   ├── №5 Паронимы ← NEW
│   ├── №6 Плеоназмы ← NEW
│   ├── №7 Грамматические ошибки ← NEW
│   └── №9-12 Правописание

Математика
├── Тренажёры
│   ├── Тригонометрия ← NEW
│   ├── Сокращённое умножение ← NEW
│   ├── Логарифмы и степени ← NEW
│   ├── Производные ← NEW
│   └── Геометрические формулы ← NEW
```

## Е3. Статистика

В `Stats`:
- **Тренажёров пройдено:** N из 18 (>=10 правильных).
- Тренд для каждого тренажёра.

## Е4. Backup v1.8

```kotlin
data class BackupSnapshot(
    // ... существующие поля
    val trainersCompleted: Set<String> = emptySet(),  // имена пройденных тренажёров
    // НЕ бэкапим trainer_explanations — они в assets
)
```

`SUPPORTED_VERSIONS = ["1.0", ..., "1.8"]`.

---

# Smoke-тесты

## Часть А — Pre-gen
| # | Что |
|---|---|
| 1 | corpus.db содержит trainer_explanations с записями. |
| 2 | Тренажёр №4 → слово → "📖 Объяснение" → 4 таба. |
| 3 | Source "Opus 4.7" виден. |
| 4 | Без интернета — pre-gen работает. |
| 5 | Если pre-gen нет — fallback на онлайн AI. |

## Часть Б — Русский
| # | Что |
|---|---|
| 6 | Паронимы (№5) — 2 кнопки, выбор работает. |
| 7 | parser/data/paronyms.json минимум 50 пар. |
| 8 | Плеоназмы (№6) — тап на слово, фильтрация "исключить" работает. |
| 9 | parser/data/pleonasms.json не содержит задач "замените". |
| 10 | Грамошибки (№7) — тап на ошибочное слово. |
| 11 | parser/data/grammar_errors.json минимум 30 примеров. |
| 12 | №6 и №7 используют общую компоненту WordTapInSentenceTrainer. |

## Часть В — Математика
| # | Что |
|---|---|
| 13 | Тригонометрия — 4 кнопки, проверка. |
| 14 | Опция "На скорость" работает (5сек таймер). |
| 15 | Сокращённое умножение — 7 формул. |
| 16 | Логарифмы — карточки. |
| 17 | Производные — выбор. |
| 18 | Геометрия — фигура + формула. |

## Часть Г — UI
| # | Что |
|---|---|
| 19 | ExplanationBottomSheet — 4 таба. |
| 20 | Source виден в шапке. |
| 21 | Pre-gen → онлайн fallback. |

## Часть Д — Поздравления
| # | Что |
|---|---|
| 22 | Пройди все слова в тренажёре → CongratulationDialog с конфетти. |
| 23 | Кнопки "Ещё раз" и "Закрыть" работают. |
| 24 | Trainer добавлен в trainers_completed. |

## Часть Е — Интеграция
| # | Что |
|---|---|
| 25 | "Случайный тренажёр" работает. |
| 26 | Каталог — 18 тренажёров. |
| 27 | Статистика — "Тренажёров пройдено: N из 18". |
| 28 | Backup v1.8 содержит trainersCompleted. |
| 29 | Импорт v1.7 → trainersCompleted = empty. |

---

# Финальные действия

- `gradlew assembleDebug`.
- НЕ коммитить.
- В отчёте подробно:
  - Сколько слов pre-genned (с разбивкой по тренажёрам).
  - Сколько осталось не обработано (нормально).
  - Список 8 новых тренажёров с количеством элементов.
  - Размер APK.
  - 29 smoke-тестов.

После "работает":
- Один commit Stage P4-D.
- Tag `phase-4-stage-d-done`.
- Tag `phase-4-final-done` (закрытие ВСЕЙ Phase 4 окончательно!).
- Push в GitHub.
- Conventions #69-77:
  - #69: Pre-gen trainer_explanations через max20 Opus 4.7.
  - #70: ExplanationBottomSheet с 4 табами + pre-gen → online fallback.
  - #71: WordTapInSentenceTrainer общая компонента для №6 и №7.
  - #72: Источник новых тренажёров — corpus.db из РешуЕГЭ.
  - #73: Фильтр №6 — только задачи "исключить", не "заменить".
  - #74: MathChoiceTrainer общая компонента для 5 математических тренажёров.
  - #75: CongratulationDialog с Canvas-confetti после прохождения.
  - #76: Trainer completion = все слова решены правильно.
  - #77: BackupSnapshot v1.8 + trainersCompleted set.

---

# Last update

Stage P4-D — финальная итерация Phase 4. После — остаётся только Phase 5 (SRS Spaced Repetition).
