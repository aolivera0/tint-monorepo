package syncevent

import (
	"errors"
	"testing"
	"time"
)

func validEvent() Event {
	now := time.Date(2026, 10, 8, 12, 0, 0, 0, time.UTC)
	return Event{
		EventID:    "6f1c2a8e-8a4e-4d0a-9f5b-1c2d3e4f5a6b",
		Type:       Play,
		PositionMs: 1500,
		OccurredAt: now,
		ReceivedAt: now.Add(40 * time.Millisecond),
	}
}

func TestValidate(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*Event)
		want   error
	}{
		{"valid play", func(*Event) {}, nil},
		{"valid pause", func(e *Event) { e.Type = Pause }, nil},
		{"valid seek", func(e *Event) { e.Type = Seek }, nil},
		{"valid heartbeat", func(e *Event) { e.Type = Heartbeat }, nil},
		{"missing id", func(e *Event) { e.EventID = "" }, ErrMissingEventID},
		{"chat not allowed", func(e *Event) { e.Type = "CHAT" }, ErrUnsupportedType},
		{"missing occurred_at", func(e *Event) { e.OccurredAt = time.Time{} }, ErrMissingTimestamp},
		{"missing received_at", func(e *Event) { e.ReceivedAt = time.Time{} }, ErrMissingTimestamp},
		{"received before occurred", func(e *Event) { e.ReceivedAt = e.OccurredAt.Add(-time.Second) }, ErrReceivedBefore},
		{"negative position", func(e *Event) { e.PositionMs = -1 }, ErrNegativePosition},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := validEvent()
			tt.mutate(&e)
			if err := e.Validate(); !errors.Is(err, tt.want) {
				t.Errorf("Validate() = %v, want %v", err, tt.want)
			}
		})
	}
}

func TestLatency(t *testing.T) {
	if got := validEvent().Latency(); got != 40*time.Millisecond {
		t.Errorf("Latency() = %v, want 40ms", got)
	}
}

func TestStateKey(t *testing.T) {
	if got := StateKey("abc"); got != "room:abc:sync_state" {
		t.Errorf("StateKey() = %q", got)
	}
}
