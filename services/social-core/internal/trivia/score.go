// Package trivia calcula puntajes de trivias con bonificación por velocidad.
package trivia

import (
	"strconv"
	"time"
)

const (
	// BasePoints se otorga por cualquier respuesta correcta.
	BasePoints = 1000
	// MaxSpeedBonus se otorga por una respuesta instantánea.
	MaxSpeedBonus = 1000
	// BonusWindow es el tiempo máximo en el que aplica bonificación.
	BonusWindow = 10_000 * time.Millisecond
	// LeaderboardTTL es la vida del leaderboard tras cerrar la sala.
	LeaderboardTTL = time.Hour
)

// Score devuelve los puntos de una respuesta según si es correcta y su tiempo.
// La bonificación decrece linealmente hasta 0 a los 10 000 ms.
func Score(correct bool, elapsed time.Duration) int {
	if !correct {
		return 0
	}
	if elapsed < 0 {
		elapsed = 0
	}
	if elapsed >= BonusWindow {
		return BasePoints
	}
	remaining := BonusWindow - elapsed
	return BasePoints + int(int64(MaxSpeedBonus)*int64(remaining)/int64(BonusWindow))
}

// LeaderboardKey devuelve la clave del Sorted Set de Redis de una trivia.
func LeaderboardKey(roomID string, triviaID int) string {
	return "room:" + roomID + ":trivia:" + strconv.Itoa(triviaID) + ":leaderboard"
}
