// Command sync-core expone el canal de sincronización de reproducción de TINT.
package main

import (
	"log"
	"os"

	"github.com/HanamDavid/tint-monorepo/services/sync-core/internal/server"
)

func main() {
	log.Fatal(server.Run(os.Getenv))
}
