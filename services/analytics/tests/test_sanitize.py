from datetime import UTC, datetime, timedelta

import pytest

from analytics.sanitize import InvalidEvent, is_expired, sanitize_event

BASE = {"event_id": "e1", "room_id": "r1", "occurred_at": "2026-10-08T12:00:00Z"}


def test_chat_message_keeps_only_length():
    event = {**BASE, "type": "chat_message", "user_id": "u1", "text": "hola mundo"}
    clean = sanitize_event(event)
    assert "text" not in clean
    assert clean["message_length"] == 10
    assert clean["user_id"] == "u1"
    assert event["text"] == "hola mundo", "el evento original no debe mutarse"


def test_chat_message_without_text_has_zero_length():
    assert sanitize_event({**BASE, "type": "chat_message"})["message_length"] == 0


def test_non_chat_event_strips_free_text_without_length():
    clean = sanitize_event({**BASE, "type": "reaction", "emoji": "👏", "content": "x"})
    assert clean == {**BASE, "type": "reaction", "emoji": "👏"}


def test_missing_required_fields():
    with pytest.raises(InvalidEvent, match="event_id"):
        sanitize_event({"type": "reaction", "room_id": "r1", "occurred_at": "x"})


def test_is_expired():
    now = datetime(2026, 10, 8, tzinfo=UTC)
    assert is_expired(now - timedelta(days=91), now)
    assert not is_expired(now - timedelta(days=89), now)


def test_is_expired_defaults_to_current_time():
    assert not is_expired(datetime.now(UTC))
