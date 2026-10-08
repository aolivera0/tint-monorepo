# 🤖 AGENT Guidelines — TINT Monorepo (raíz)

Este repositorio es un monorepo multi-lenguaje. **Antes de modificar cualquier archivo, lee el `AGENTS.md` del dominio correspondiente** y respeta sus límites.

| Dominio | Ruta | Guía |
| :--- | :--- | :--- |
| Frontend | `apps/web` | [apps/web/AGENTS.md](apps/web/AGENTS.md) |
| Sincronización | `services/sync-core` | [services/sync-core/AGENTS.md](services/sync-core/AGENTS.md) |
| Social & Gamificación | `services/social-core` | [services/social-core/AGENTS.md](services/social-core/AGENTS.md) |
| Media Worker | `services/media-worker` | [services/media-worker/AGENTS.md](services/media-worker/AGENTS.md) |
| Analítica | `services/analytics` | [services/analytics/AGENTS.md](services/analytics/AGENTS.md) |

## Reglas Globales
1. **Un cambio, un dominio:** un PR no debe tocar varios dominios salvo contratos compartidos acordados.
2. **TDD obligatorio:** Red → Green → Refactor. Cobertura mínima **85%** por servicio; el CI (`.github/workflows/ci-pipeline.yml`) bloquea el merge si no se cumple.
3. **Conventional Commits:** `tipo(alcance): descripción`, con el dominio como alcance (p. ej. `feat(sync-core): ...`, `ci: ...`, `docs(web): ...`).
4. **Ramas:** `main` (producción) y `develop` (integración) están protegidas; trabaja en `feat/*`, `fix/*`, `chore/*` y abre PR hacia `develop`.
5. **Infraestructura local:** `docker compose up -d` (PostgreSQL, Redis, MinIO). Nunca subas `.env` con credenciales.
6. **Verificación:** `make test-all` antes de abrir un PR.
