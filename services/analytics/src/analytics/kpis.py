"""Cálculo de KPIs consolidados al cerrar una sala."""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class RoomKpis:
    room_id: str
    peak_users: int
    avg_drift_ms: float
    trivia_accuracy_pct: float


def compute_room_kpis(room_id: str, events: Iterable[dict[str, Any]]) -> RoomKpis:
    """Agrega eventos sanitizados de una sala en sus KPIs finales."""
    peak_users = 0
    drifts: list[float] = []
    answers = correct = 0

    for event in events:
        kind = event.get("type")
        if kind == "presence":
            peak_users = max(peak_users, int(event.get("connected_users", 0)))
        elif kind == "drift_correction":
            drifts.append(abs(float(event["drift_ms"])))
        elif kind == "trivia_answer":
            answers += 1
            correct += bool(event.get("correct"))

    return RoomKpis(
        room_id=room_id,
        peak_users=peak_users,
        avg_drift_ms=round(sum(drifts) / len(drifts), 2) if drifts else 0.0,
        trivia_accuracy_pct=round(100 * correct / answers, 2) if answers else 0.0,
    )
