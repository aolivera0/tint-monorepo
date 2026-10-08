from analytics.kpis import RoomKpis, compute_room_kpis


def test_compute_room_kpis():
    events = [
        {"type": "presence", "connected_users": 3},
        {"type": "presence", "connected_users": 7},
        {"type": "drift_correction", "drift_ms": 600},
        {"type": "drift_correction", "drift_ms": -800},
        {"type": "trivia_answer", "correct": True},
        {"type": "trivia_answer", "correct": False},
        {"type": "trivia_answer", "correct": True},
        {"type": "reaction"},
    ]
    assert compute_room_kpis("r1", events) == RoomKpis("r1", 7, 700.0, 66.67)


def test_compute_room_kpis_empty_room():
    assert compute_room_kpis("r2", []) == RoomKpis("r2", 0, 0.0, 0.0)
