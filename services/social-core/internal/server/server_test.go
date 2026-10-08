package server

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHealthz(t *testing.T) {
	rec := httptest.NewRecorder()
	NewRouter().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/healthz", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body["status"] != "ok" || body["service"] != "social-core" {
		t.Errorf("unexpected body: %v", body)
	}
}

func TestHealthzRejectsPost(t *testing.T) {
	rec := httptest.NewRecorder()
	NewRouter().ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/healthz", nil))

	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("status = %d, want 405", rec.Code)
	}
}

func TestAddr(t *testing.T) {
	empty := func(string) string { return "" }
	if got := Addr(empty); got != defaultAddr {
		t.Errorf("Addr() = %q, want %q", got, defaultAddr)
	}
	custom := func(string) string { return ":9999" }
	if got := Addr(custom); got != ":9999" {
		t.Errorf("Addr() = %q, want :9999", got)
	}
}

func TestRunFailsOnInvalidAddr(t *testing.T) {
	invalid := func(string) string { return "invalid-addr" }
	if err := Run(invalid); err == nil {
		t.Fatal("Run() with invalid addr should fail")
	}
}
