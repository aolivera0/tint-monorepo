"""Sanitización de eventos de telemetría (Privacy by Design, Ley 1581)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Any

STREAM_KEY = "analytics:events"
CONSUMER_GROUP = "analytics-workers"
RETENTION = timedelta(days=90)

# Campos que pueden contener texto libre del usuario y nunca deben persistirse.
FORBIDDEN_FIELDS = frozenset({"text", "message", "content", "body"})
REQUIRED_FIELDS = ("event_id", "type", "room_id", "occurred_at")


class InvalidEvent(ValueError):
    """El evento no cumple el contrato mínimo de telemetría."""


def sanitize_event(raw: dict[str, Any]) -> dict[str, Any]:
    """Devuelve una copia del evento sin texto de chat.

    Para eventos de chat se conserva únicamente `message_length`.
    """
    missing = [f for f in REQUIRED_FIELDS if not raw.get(f)]
    if missing:
        raise InvalidEvent(f"faltan campos obligatorios: {', '.join(missing)}")

    clean = {k: v for k, v in raw.items() if k not in FORBIDDEN_FIELDS}
    if raw["type"] == "chat_message":
        text = next((raw[f] for f in FORBIDDEN_FIELDS if isinstance(raw.get(f), str)), "")
        clean["message_length"] = len(text)
    return clean


def is_expired(occurred_at: datetime, now: datetime | None = None) -> bool:
    """Indica si un evento superó la ventana de retención de 90 días."""
    now = now or datetime.now(UTC)
    return now - occurred_at > RETENTION
