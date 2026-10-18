# 🤖 AGENT Guidelines — TINT Monorepo (raíz)

Este repositorio es un monorepo multi-lenguaje (Go, TypeScript y Python). **Antes de modificar cualquier archivo, lee el `AGENTS.md` del dominio correspondiente** y respeta sus límites.

## Dominios
| Dominio | Ruta | Stack | Guía |
| :--- | :--- | :--- | :--- |
| Frontend | `apps/web` | React 18, TS, Vite, Tailwind | [apps/web/AGENTS.md](apps/web/AGENTS.md) |
| Identidad | `services/identity-api` | Node 22, Express 5, Drizzle/PostgreSQL | [services/identity-api/AGENTS.md](services/identity-api/AGENTS.md) |
| Sincronización | `services/sync-core` | Go 1.22, WebSockets, Redis | [services/sync-core/AGENTS.md](services/sync-core/AGENTS.md) |
| Social & Gamificación | `services/social-core` | Go, Redis | [services/social-core/AGENTS.md](services/social-core/AGENTS.md) |
| Media Worker | `services/media-worker` | Python 3.11, FFmpeg, MinIO | [services/media-worker/AGENTS.md](services/media-worker/AGENTS.md) |
| Analítica | `services/analytics` | Python 3.11, Redis Streams, PostgreSQL, API HTTP de solo lectura | [services/analytics/AGENTS.md](services/analytics/AGENTS.md) |
| Infra / CI | raíz, `.github/`, `scripts/` | Docker Compose, GitHub Actions | este archivo |

## Documentación
La documentación de features vive en [`docs/`](docs/README.md), con diagramas Mermaid:
- [Arquitectura](docs/arquitectura.md): servicios, **contratos entre dominios**, vistas de componentes y conectores y de despliegue, almacenes, puertos y CI/CD.
- [Decisiones de arquitectura (ADRs)](docs/adr/README.md): una decisión por archivo; una decisión nueva va como ADR en el mismo PR.
- [Usuarios, identidad y permisos](docs/usuarios-e-identidad.md): tipos de usuario, permisos, esquema de BD y tokens.
- [Dominio de Reproducción](docs/dominio-reproduccion.md): Canal Sync v2.
- [Contrato de eventos de analítica](docs/contrato-eventos-analitica.md): `analytics:events` y la API de KPIs.

**Actualiza el documento en el mismo PR** en el que cambie un flujo, un contrato, una tabla o una variable de entorno.

## Reglas Globales
1. **Un cambio, un dominio:** un PR no toca varios dominios, salvo contratos compartidos acordados (ver abajo). Si una feature cruza dominios, haz un PR por dominio y deja explícito el orden de merge.
2. **TDD obligatorio:** Red → Green → Refactor. Cobertura mínima **85 %** por servicio; el CI (`.github/workflows/ci-pipeline.yml`) bloquea el merge si no se cumple.
3. **Conventional Commits en español:** `tipo(alcance): descripción`, con el dominio como alcance: `feat(sync-core): …`, `fix(identity-api): …`, `docs(web): …`, `perf(sync-core): …`, `ci: …`, `chore: …`. El cuerpo explica el *porqué* y, si hay mediciones, las incluye.
4. **Ramas:** `main` (producción) y `develop` (integración) están protegidas. Trabaja en `feat/*`, `fix/*` o `chore/*` y abre el PR hacia `develop`.
5. **Infraestructura local:** `docker compose up -d` (PostgreSQL, Redis, MinIO, identity-api, web, media-worker, analytics, social-core y sync-core). **Nunca subas `.env`** ni credenciales; documenta las variables nuevas en el `.env.example` correspondiente.
6. **Verificación:** `make test-all` antes de abrir un PR, más los comandos propios del dominio.
7. **Archivos ajenos:** los `.docx` de planeación en la raíz no se versionan. No los agregues a commits.
8. **Dependencias:** no agregues dependencias sin justificarlas en el commit y en el Stack Técnico del `AGENTS.md` del dominio. Fija versiones exactas en Go y prefiere versiones exactas en npm.

## Contratos compartidos
Cambiar uno exige actualizar **ambos lados**, sus pruebas y [docs/arquitectura.md](docs/arquitectura.md#5-contratos-compartidos-entre-dominios).

| Contrato | Lados | Fuente de verdad |
| :--- | :--- | :--- |
| Claims JWT HS256 (`sub`, `room_id`, `role`, `nick`, `jti`, `iss`, `aud`, `exp`) | identity-api ↔ sync-core, social-core, analytics | [usuarios-e-identidad §4](docs/usuarios-e-identidad.md#4-esquemas-de-tokens) |
| `JWT_SECRET` / `JWT_ISSUER` / `JWT_AUDIENCE` | identity-api ↔ sync-core, social-core, analytics | `.env.example` raíz |
| Protocolo del Canal Sync v2 (incl. `VOLUME`/`QUALITY`) | sync-core ↔ web | [sync-core/AGENTS.md](services/sync-core/AGENTS.md#protocolo-del-canal-sync) |
| Protocolo del Canal Social (chat con spoilers, reacciones, láser, encuestas, trivias, recap; moderación del chat —modo lento y silencio—, láser, encuestas y programación de trivias con sync-ticket del host) | social-core ↔ web | [social-core/AGENTS.md](services/social-core/AGENTS.md#protocolo-del-canal-social) |
| Comandos de reproducción en `room:{id}:sync` (solo lectura, para el recap) | sync-core → social-core | [arquitectura §5](docs/arquitectura.md#5-contratos-compartidos-entre-dominios) |
| Revocación `token:revoked:{jti}` + `REVOKED` (cierre `4001`; token caducado `4003`) | identity-api → sync-core, social-core, analytics, web | [dominio-reproduccion §6.6](docs/dominio-reproduccion.md#66-retirar-a-un-invitado-f5) |
| Stream `media:uploads` + tabla `videos` (`id`, `storage_key`, `duration_ms`, `status`, `renditions`) + staging `incoming/{id}/original.*` (el worker publica `{id}/source.*` y reescribe `storage_key`; también `{id}/720p.mp4`/`{id}/360p.mp4` y sus alturas en `renditions`) | identity-api → media-worker | [dominio-reproduccion §6.7](docs/dominio-reproduccion.md#67-subida-y-cambio-de-vídeo) |
| Puertos locales: social-core 8082, sync-core 8083, analytics 8084 | infra ↔ web | `docker-compose.yml`, `.env.example` de la web |
| API REST `/api/v1` (incl. bloqueo por dispositivo `room_bans` + `deviceId` en el claim, y biblioteca `GET /videos`) | identity-api → web | [identity-api/AGENTS.md](services/identity-api/AGENTS.md#api) |
| Stream `analytics:events` (sin PII) | identity-api, social-core, sync-core, media-worker → analytics | [docs/contrato-eventos-analitica.md](docs/contrato-eventos-analitica.md) |
| API de lectura de analytics (`/api/v1/rooms/{id}/kpis`, `/reactions/heatmap`, `/timeline`) + `analytics-ticket` (`POST /api/v1/rooms/:id/analytics-ticket`) | identity-api → web → analytics | [analytics/AGENTS.md](services/analytics/AGENTS.md#api-solo-lectura-puerto-analytics_port-por-defecto-8084) |

## Flujo de trabajo recomendado (humanos y agentes)
1. Lee este archivo, el `AGENTS.md` del dominio y el documento de `docs/` afectado.
2. Crea la rama desde `develop` (`feat/<dominio>-<descripcion>`).
3. Escribe la prueba que falla y comprueba que falla por el motivo correcto.
4. Implementa lo mínimo para que pase y luego refactoriza.
5. Corre el gate del dominio (abajo). En Go, siempre con `-race`.
6. Si cambió un flujo o un contrato, actualiza `docs/` y el `AGENTS.md`.
7. Haz commits pequeños con Conventional Commits y abre un PR por dominio hacia `develop` usando la plantilla.

## Gates por dominio
| Dominio | Comandos |
| :--- | :--- |
| Go (`sync-core`, `social-core`) | `gofmt -l .` vacío · `go vet ./...` · `go test -race -coverprofile=coverage.out ./...` · `../../scripts/check-go-coverage.sh coverage.out 85` |
| `identity-api` | `npm ci` · `npm run typecheck` · `npx vitest run --coverage` |
| `apps/web` | `npm ci` · `npm run lint` · `npm run typecheck` · `npx vitest run --coverage` · `npm run build` |
| Python (`media-worker`, `analytics`) | `ruff check . && ruff format --check .` · `pytest --cov=src --cov-fail-under=85` |
| Carga del Canal Sync | `k6 run -e VUS=50 -e DURATION=20s services/sync-core/loadtest/sync.js` (CI); 500 VUs desde otra máquina |

## Lecciones aprendidas (no repetir)
- **Mide con la infraestructura real** antes de afirmar algo sobre rendimiento: con miniredis no se reproducían los conflictos de `WATCH/MULTI` que con Redis real perdían comandos.
- **Orden de mensajes:** el `STATE` inicial debe encolarse antes de cualquier publicación por el bus; los clientes filtran por `version`.
- **Falla cerrado** en todo lo relacionado con autorización (revocación, tokens).
- **No relajes umbrales** para que una prueba pase: documenta el resultado y la causa probable.
- **Nunca confíes en un token sin verificar su firma**, ni como "respaldo": social-core aceptaba ID tokens de Firebase falsificados (bug hunting 2026-10).
- **No compares relojes distintos** (cliente vs. servidor) para rechazar mensajes: un navegador adelantado perdía el chat en silencio.
- **Rechazar antes del upgrade del WebSocket no le dice nada al navegador** (solo ve `1006` y reintenta): para estados terminales, haz upgrade y cierra con un código `4xxx`.
- Los subagentes trabajan en worktrees aislados: hay que integrar sus commits (`cherry-pick`), volver a correr las pruebas en el árbol principal y borrar los worktrees al terminar.
