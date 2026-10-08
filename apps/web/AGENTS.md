# 🤖 AGENT Guidelines — Domain: Frontend Experience (`apps/web`)

## Contexto & Responsabilidad
Eres el agente especializado en la capa de presentación de **TINT**. Tu foco es desarrollar la UI web responsiva, accesible e inmersiva (estética Dark/Luxury) usando React, TypeScript y Tailwind CSS.

## Stack Técnico
- React 18 + TypeScript (modo `strict`) + Vite 5
- Tailwind CSS 3 (paleta `tint.*` en `tailwind.config.js`)
- Vitest + Testing Library (`jsdom`) + cobertura `v8`
- ESLint 9 (`typescript-eslint`, `react-hooks`)

## Responsabilidades
- Reproductor, sala, chat, reacciones, trivias y visualización Ambilight/Heatmap.
- Cliente de los dos canales WebSocket: `/ws/sync` (sync-core) y canal social (social-core).
- Detección y corrección de desfase de reproducción del lado del cliente.

## Límites de Dominio
- ❌ No implementes lógica de negocio autoritativa (puntajes, rate limiting real, estado maestro): eso vive en `services/*`. La UI solo refleja y pre-valida.
- ❌ No modifiques archivos fuera de `apps/web/` sin coordinación con el dominio dueño.
- ❌ Nunca envíes texto de chat a telemetría (ver `services/analytics/AGENTS.md`).

## Modelo de Datos Involucrado
| Entidad | Origen | Campos clave |
| :--- | :--- | :--- |
| `SyncEvent` | sync-core | `event_id`, `type` (`PLAY`/`PAUSE`/`SEEK`/`HEARTBEAT`), `position_ms`, `occurred_at`, `received_at` |
| `Reaction` | social-core | `user_id`, `emoji`, `position_ms` (bucket de 5 s) |
| `AmbilightFrame` | media-worker (`ambilight.json`) | `t_ms`, `top`, `bottom`, `left`, `right` (hex) |
| `TriviaQuestion` / `Leaderboard` | social-core | `trivia_id`, `options`, `score` |

## Reglas de Dominio
1. **Canales Aislados:** Mantén el uso de dos hooks separados: `useSyncSocket` (vídeo/comandos) y `useSocialSocket` (chat, reacciones, ambilight). Nunca mezcles los sockets.
2. **Auto-Alineación:** Implementa la verificación de desfase (`src/lib/drift.ts`). Si `drift > 500ms`, ajusta el vídeo e informa a telemetría.
3. **Throttling UI:** Garantiza que los botones de reacción no permitan más de 5 clics/segundo por usuario (`src/hooks/useThrottle.ts`).
4. **Accesibilidad:** Controles con `aria-label`, foco visible y contraste AA sobre fondo oscuro.
5. **TDD & Cobertura:** Todo componente, hook o utilidad debe contar con pruebas unitarias en Vitest escritas **antes** de la implementación (Red → Green → Refactor). Cobertura mínima **85%** (statements, branches, functions, lines) — configurada en `vitest.config.ts`.

## Comandos
```bash
npm ci
npm run dev             # servidor de desarrollo
npm run lint            # ESLint
npm run typecheck       # tsc
npm run test:coverage   # Vitest + umbral 85%
```
