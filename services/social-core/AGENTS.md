# 🤖 AGENT Guidelines — Domain: Core Social & Gamificación (`services/social-core`)

## Contexto & Responsabilidad
Gestionas la interactividad de la audiencia en **TINT**: reacciones con control de spam, agregación de Heatmaps, emisión de Ambilight en vivo y motor de Trivias con Leaderboards.

## Stack Técnico
- Go 1.22
- WebSockets (canal social, independiente de `/ws/sync`)
- Redis: Sorted Sets (leaderboards), Hashes/contadores (heatmaps), Pub/Sub
- Imagen Docker `distroless/static` (non-root)

## Responsabilidades
- Chat en vivo, reacciones y su rate limiting autoritativo (`internal/reactions`).
- Heatmaps de emoción por buckets de 5 s.
- Retransmisión de colores Ambilight sincronizados con la posición del vídeo.
- Motor de trivias y puntuación (`internal/trivia`).

## Límites de Dominio
- ❌ No emitas ni modifiques comandos de reproducción (→ `services/sync-core`).
- ❌ No extraigas paletas de color de vídeo (→ `services/media-worker`); solo consumes `ambilight.json`.
- ❌ No persistas el texto del chat en analítica; publica solo metadatos (→ `services/analytics`).

## Modelo de Datos Involucrado
| Clave / Entidad | Tipo | Descripción |
| :--- | :--- | :--- |
| `room:{id}:heatmap` | Redis Hash | `bucket_index → {emoji: count}` (buckets de 5 s) |
| `video:{id}:heatmap:history` | Redis/SQL | Histórico acumulado entre sesiones |
| `room:{id}:trivia:{n}:leaderboard` | Redis Sorted Set | `user_id → score`, TTL 1 h tras cierre |
| `Reaction` | JSON | `user_id`, `emoji`, `position_ms` |

## Reglas de Dominio
1. **Bucketing & Rate Limiting:** Limita las reacciones a 5/s por usuario. Agrupa datos en buckets de 5 segundos.
2. **Heatmaps:** Emite el evento de Heatmap en vivo cada 1 s. Consulta el histórico acumulado si existen ≥ 3 sesiones previas.
3. **Trivias:** Calcula el puntaje bonificando la velocidad (hasta 10 000 ms). Persiste los Leaderboards en Sorted Sets de Redis con TTL de 1 hora post-cierre de sala.
4. **TDD:** Pruebas rigurosas (prueba primero) de algoritmos de puntuación y límites de tasa, con relojes inyectados (nunca `time.Sleep` en tests). Cobertura mínima: **85%**.

## Comandos
```bash
go vet ./...
go test -race -coverprofile=coverage.out ./...
../../scripts/check-go-coverage.sh coverage.out 85
```
