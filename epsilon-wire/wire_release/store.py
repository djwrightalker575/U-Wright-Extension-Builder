from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from threading import Lock
from uuid import UUID
from .models import Mission, utcnow

class MissionStore:
    def __init__(self, path: str = "./wire-release.db"):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()
        self._init()

    def _connect(self):
        c = sqlite3.connect(self.path, timeout=30)
        c.row_factory = sqlite3.Row
        c.execute("PRAGMA busy_timeout=30000")
        return c

    def _init(self):
        with self._connect() as c:
            c.execute("PRAGMA journal_mode=WAL")
            c.execute("""CREATE TABLE IF NOT EXISTS missions (
                mission_id TEXT PRIMARY KEY,
                request_id TEXT NOT NULL,
                state TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )""")
            c.execute("""CREATE TABLE IF NOT EXISTS idempotency (
                key TEXT PRIMARY KEY,
                request_id TEXT NOT NULL,
                result TEXT NOT NULL,
                created_at TEXT NOT NULL
            )""")
            c.execute("""CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mission_id TEXT NOT NULL,
                request_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at TEXT NOT NULL
            )""")

    def put_mission(self, mission: Mission):
        mission.updated_at = utcnow()
        with self._lock, self._connect() as c:
            c.execute(
                "INSERT OR REPLACE INTO missions VALUES (?,?,?,?,?,?)",
                (str(mission.mission_id), mission.request_id, mission.state.value,
                 mission.model_dump_json(), mission.created_at.isoformat(), mission.updated_at.isoformat())
            )
        return mission

    def get_mission(self, mission_id: UUID):
        with self._connect() as c:
            row = c.execute("SELECT payload FROM missions WHERE mission_id=?", (str(mission_id),)).fetchone()
        return Mission.model_validate_json(row["payload"]) if row else None

    def record_event(self, mission_id: UUID, request_id: str, kind: str, payload: dict):
        with self._lock, self._connect() as c:
            c.execute(
                "INSERT INTO events(mission_id,request_id,kind,payload,created_at) VALUES(?,?,?,?,?)",
                (str(mission_id), request_id, kind, json.dumps(payload), utcnow().isoformat())
            )

    def get_idempotent(self, key: str):
        with self._connect() as c:
            row = c.execute("SELECT result FROM idempotency WHERE key=?", (key,)).fetchone()
        return json.loads(row["result"]) if row else None

    def put_idempotent(self, key: str, request_id: str, result: dict):
        with self._lock, self._connect() as c:
            c.execute(
                "INSERT OR IGNORE INTO idempotency VALUES(?,?,?,?)",
                (key, request_id, json.dumps(result), utcnow().isoformat())
            )
