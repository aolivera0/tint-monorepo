// Package syncevent modela los comandos de reproducción que viajan por /ws/sync.
package syncevent

import (
	"errors"
	"fmt"
	"time"
)

// Type es el tipo de comando de sincronización.
type Type string

const (
	Play      Type = "PLAY"
	Pause     Type = "PAUSE"
	Seek      Type = "SEEK"
	Heartbeat Type = "HEARTBEAT"
)

// Event es el payload transmitido por el canal de sincronización.
type Event struct {
	EventID    string    `json:"event_id"`
	Type       Type      `json:"type"`
	PositionMs int64     `json:"position_ms"`
	OccurredAt time.Time `json:"occurred_at"`
	ReceivedAt time.Time `json:"received_at"`
}

var (
	ErrMissingEventID   = errors.New("event_id es obligatorio")
	ErrUnsupportedType  = errors.New("tipo de evento no soportado en /ws/sync")
	ErrMissingTimestamp = errors.New("occurred_at y received_at son obligatorios")
	ErrNegativePosition = errors.New("position_ms no puede ser negativo")
	ErrReceivedBefore   = errors.New("received_at no puede ser anterior a occurred_at")
)

// Validate verifica que el evento cumpla el contrato del canal de sync.
func (e Event) Validate() error {
	if e.EventID == "" {
		return ErrMissingEventID
	}
	switch e.Type {
	case Play, Pause, Seek, Heartbeat:
	default:
		return fmt.Errorf("%w: %q", ErrUnsupportedType, e.Type)
	}
	if e.OccurredAt.IsZero() || e.ReceivedAt.IsZero() {
		return ErrMissingTimestamp
	}
	if e.ReceivedAt.Before(e.OccurredAt) {
		return ErrReceivedBefore
	}
	if e.PositionMs < 0 {
		return ErrNegativePosition
	}
	return nil
}

// Latency devuelve el tiempo de tránsito del evento.
func (e Event) Latency() time.Duration {
	return e.ReceivedAt.Sub(e.OccurredAt)
}

// StateKey devuelve la clave Redis del estado maestro de una sala.
func StateKey(roomID string) string {
	return "room:" + roomID + ":sync_state"
}
