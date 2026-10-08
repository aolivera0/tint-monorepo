package reactions

import (
	"testing"
	"time"
)

func TestLimiterAllowsUpToFivePerSecond(t *testing.T) {
	l := NewLimiter()
	base := time.Date(2026, 10, 8, 12, 0, 0, 0, time.UTC)

	for i := 0; i < MaxPerSecond; i++ {
		if !l.Allow("u1", base.Add(time.Duration(i)*100*time.Millisecond)) {
			t.Fatalf("reaction %d should be allowed", i+1)
		}
	}
	if l.Allow("u1", base.Add(500*time.Millisecond)) {
		t.Fatal("6th reaction within 1s should be rejected")
	}
	if !l.Allow("u2", base.Add(500*time.Millisecond)) {
		t.Fatal("limits must be per user")
	}
	if !l.Allow("u1", base.Add(1100*time.Millisecond)) {
		t.Fatal("reaction should be allowed once the window slides")
	}
}

func TestBucketIndex(t *testing.T) {
	tests := []struct {
		pos  time.Duration
		want int64
	}{
		{-time.Second, 0},
		{0, 0},
		{4999 * time.Millisecond, 0},
		{5 * time.Second, 1},
		{27 * time.Second, 5},
	}
	for _, tt := range tests {
		if got := BucketIndex(tt.pos); got != tt.want {
			t.Errorf("BucketIndex(%v) = %d, want %d", tt.pos, got, tt.want)
		}
	}
}
