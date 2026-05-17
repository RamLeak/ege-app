"""JSONL writer + state.json checkpoint.

JSONL — append-only, по одной задаче на строку. Если задача с тем же sdamgia_id
уже есть в файле, пропускаем (за дедупликацию отвечает обходчик через
loaded_ids()).

state.json формат:
{
  "subject": "math_profile",
  "started_at": "2026-05-16T12:00:00",
  "last_updated_at": "...",
  "completed_subtypes": [14, 79, ...],         // category_id, по которым закончено
  "current_subtype": {"category_id": 14, "page": 2, "total_pages": 2},
  "processed_count": 1234,
  "errors": [{"category_id": 14, "page": 2, "msg": "..."}]
}
"""
from __future__ import annotations

import json
import os
from dataclasses import asdict, is_dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable, Optional

PARSER_ROOT = Path(__file__).resolve().parent.parent


def _default(o: Any) -> Any:
    if is_dataclass(o):
        return asdict(o)
    raise TypeError(f"not JSON serializable: {type(o)}")


class JsonlWriter:
    """Append-only writer. На init читает существующие sdamgia_id для дедупа."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._seen: set[str] = set()
        if self.path.exists():
            with self.path.open("r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        row = json.loads(line)
                        sid = str(row.get("sdamgia_id") or "")
                        if sid:
                            self._seen.add(sid)
                    except json.JSONDecodeError:
                        continue

    def has(self, sdamgia_id: str) -> bool:
        return sdamgia_id in self._seen

    def write(self, row: dict) -> bool:
        sid = str(row.get("sdamgia_id") or "")
        if sid and sid in self._seen:
            return False
        line = json.dumps(row, ensure_ascii=False, default=_default)
        with self.path.open("a", encoding="utf-8") as f:
            f.write(line + "\n")
        if sid:
            self._seen.add(sid)
        return True

    @property
    def count(self) -> int:
        return len(self._seen)


class StateCheckpoint:
    """Атомарная запись state.json (через tmp + os.replace)."""

    def __init__(self, path: Path, subject: str) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.state: dict[str, Any] = {
            "subject": subject,
            "started_at": datetime.now().astimezone().isoformat(timespec="seconds"),
            "last_updated_at": "",
            "completed_subtypes": [],
            "current_subtype": None,
            "processed_count": 0,
            "errors": [],
        }
        if self.path.exists():
            try:
                with self.path.open("r", encoding="utf-8") as f:
                    loaded = json.load(f)
                # Перетираем стартовое состояние, но сохраняем started_at если оно было.
                started = loaded.get("started_at") or self.state["started_at"]
                self.state.update(loaded)
                self.state["started_at"] = started
            except (json.JSONDecodeError, OSError):
                pass

    def update(self, **kwargs: Any) -> None:
        for k, v in kwargs.items():
            self.state[k] = v
        self.state["last_updated_at"] = datetime.now().astimezone().isoformat(timespec="seconds")

    def mark_subtype_done(self, category_id: int) -> None:
        if category_id not in self.state["completed_subtypes"]:
            self.state["completed_subtypes"].append(category_id)
        self.state["current_subtype"] = None
        self.flush()

    def is_subtype_done(self, category_id: int) -> bool:
        return category_id in (self.state.get("completed_subtypes") or [])

    def add_error(self, **kwargs: Any) -> None:
        self.state.setdefault("errors", []).append({
            **kwargs,
            "at": datetime.now().astimezone().isoformat(timespec="seconds"),
        })

    def inc_processed(self, n: int = 1) -> None:
        self.state["processed_count"] = self.state.get("processed_count", 0) + n

    def flush(self) -> None:
        self.state["last_updated_at"] = datetime.now().astimezone().isoformat(timespec="seconds")
        tmp = self.path.with_suffix(".json.tmp")
        with tmp.open("w", encoding="utf-8") as f:
            json.dump(self.state, f, ensure_ascii=False, indent=2)
        os.replace(tmp, self.path)
