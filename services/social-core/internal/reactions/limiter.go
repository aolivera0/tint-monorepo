// Package reactions implementa el control anti-spam y el bucketing de reacciones.
package reactions

import (
	"sync"
	"time"
)

const (
	// MaxPerSecond es el máximo de reacciones permitidas por usuario por segundo.
	MaxPerSecond = 5
	// BucketSize es la ventana de agregación de reacciones para el heatmap.
	BucketSize = 5 * time.Second
)

// Limiter aplica una ventana deslizante de 1s por usuario.
type Limiter struct {
	mu      sync.Mutex
	limit   int
	window  time.Duration
	history map[string][]time.Time
}

// NewLimiter crea un limitador con el límite por defecto de TINT.
func NewLimiter() *Limiter {
	return &Limiter{limit: MaxPerSecond, window: time.Second, history: make(map[string][]time.Time)}
}

// Allow indica si el usuario puede emitir una reacción en el instante now.
func (l *Limiter) Allow(userID string, now time.Time) bool {
	l.mu.Lock()
	defer l.mu.Unlock()

	cutoff := now.Add(-l.window)
	recent := l.history[userID][:0]
	for _, t := range l.history[userID] {
		if t.After(cutoff) {
			recent = append(recent, t)
		}
	}
	if len(recent) >= l.limit {
		l.history[userID] = recent
		return false
	}
	l.history[userID] = append(recent, now)
	return true
}

// BucketIndex devuelve el índice del bucket de 5s para una posición del vídeo.
func BucketIndex(position time.Duration) int64 {
	if position < 0 {
		return 0
	}
	return int64(position / BucketSize)
}
