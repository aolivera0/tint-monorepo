# 🤖 AGENT Guidelines — Domain: Core Sync (`services/sync-core`)

## Contexto & Responsabilidad
Eres el agente encargado del corazón de reproducción en tiempo real de **TINT**, desarrollado en **Golang**. Tu única meta es garantizar latencia ultrabaja (<100 ms) y precisión en la sincronización.

## Stack Técnico
- Go 1.22 (solo librería estándar por ahora; `net/http` con patrones de ruta de Go 1.22)
- WebSockets para `/ws/sync`
- Redis (estado maestro + Pub/Sub entre réplicas)
- Imagen Docker `distroless/static` (non-root)

## Responsabilidades
- Reloj maestro por sala y difusión de comandos de reproducción.
- Validación del contrato de eventos (`internal/syncevent`).
- Latidos (`HEARTBEAT`) para medir latencia y detectar clientes caídos.

## Límites de Dominio
- ❌ No proceses chat, reacciones, trivias ni Ambilight (→ `services/social-core`).
- ❌ No persistas telemetría directamente: publica eventos en `analytics:events` (→ `services/analytics`).
- ❌ No introduzcas dependencias externas sin justificar su impacto en latencia.

## Modelo de Datos Involucrado
| Clave / Entidad | Tipo | Descripción |
| :--- | :--- | :--- |
| `room:{id}:sync_state` | Redis Hash | `status` (playing/paused), `position_ms`, `updated_at`, `host_id` |
| `room:{id}:sync` | Redis Pub/Sub | Difusión de `Event` entre réplicas |
| `Event` | JSON | `event_id` (UUID), `type`, `position_ms`, `occurred_at`, `received_at` |

## Reglas de Dominio
1. **Aislamiento:** El canal WebSocket `/ws/sync` es exclusivo para comandos `PLAY`, `PAUSE`, `SEEK` y latidos. No proceses mensajes de chat aquí.
2. **Reloj Maestro:** Mantén el estado maestro de la sala en Redis (`room:{id}:sync_state`).
3. **Formato de Payload:** Todo evento transmitido debe incluir `event_id` (UUID), `occurred_at` y `received_at`.
4. **Concurrencia:** Todo estado compartido protegido (`sync.Mutex`/canales); las pruebas corren con `-race`.
5. **TDD:** Implementa pruebas unitarias e integración en Go con `go test -cover`, escribiendo la prueba primero. La cobertura no puede bajar del **85%**. `main.go` debe ser mínimo; la lógica vive en `internal/`.

## Comandos
```bash
go vet ./...
go test -race -coverprofile=coverage.out ./...
../../scripts/check-go-coverage.sh coverage.out 85
```
