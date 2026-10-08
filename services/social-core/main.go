// Command social-core expone el canal de interacción social (reacciones, heatmaps y trivias) de TINT.
package main

import (
	"log"
	"os"

	"github.com/HanamDavid/tint-monorepo/services/social-core/internal/server"
)

func main() {
	log.Fatal(server.Run(os.Getenv))
}
