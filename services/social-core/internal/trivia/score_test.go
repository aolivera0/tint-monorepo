package trivia

import (
	"testing"
	"time"
)

func TestScore(t *testing.T) {
	tests := []struct {
		name    string
		correct bool
		elapsed time.Duration
		want    int
	}{
		{"wrong answer", false, time.Second, 0},
		{"instant", true, 0, 2000},
		{"negative clamps to instant", true, -time.Second, 2000},
		{"halfway", true, 5 * time.Second, 1500},
		{"at window edge", true, BonusWindow, 1000},
		{"after window", true, 20 * time.Second, 1000},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := Score(tt.correct, tt.elapsed); got != tt.want {
				t.Errorf("Score() = %d, want %d", got, tt.want)
			}
		})
	}
}

func TestLeaderboardKey(t *testing.T) {
	if got := LeaderboardKey("r1", 3); got != "room:r1:trivia:3:leaderboard" {
		t.Errorf("LeaderboardKey() = %q", got)
	}
}
