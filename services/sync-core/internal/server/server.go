// Package server define las rutas HTTP de sync-core.
package server

import (
	"encoding/json"
	"log"
	"net/http"
)

const defaultAddr = ":8080"

// Addr resuelve la dirección de escucha a partir del entorno.
func Addr(getenv func(string) string) string {
	if addr := getenv("SYNC_CORE_ADDR"); addr != "" {
		return addr
	}
	return defaultAddr
}

// Run arranca el servidor HTTP y bloquea hasta que falle.
func Run(getenv func(string) string) error {
	addr := Addr(getenv)
	log.Printf("sync-core escuchando en %s", addr)
	return http.ListenAndServe(addr, NewRouter())
}

// NewRouter construye el router HTTP del servicio.
func NewRouter() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", healthz)
	return mux
}

func healthz(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "ok", "service": "sync-core"})
}
