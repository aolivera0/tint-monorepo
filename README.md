<div align="center">

# ✨ TINT — Watch Party Experience
### *Synchronized, Social & Immersive Cinema Platform*

[![CI Pipeline](https://github.com/HanamDavid/tint-monorepo/actions/workflows/ci-pipeline.yml/badge.svg)](https://github.com/HanamDavid/tint-monorepo/actions/workflows/ci-pipeline.yml)
![TINT](https://img.shields.io/badge/TINT-Luxury%20Cinema-66FCF1?style=for-the-badge&logo=react&logoColor=black)
![Go](https://img.shields.io/badge/Golang-1.22-00ADD8?style=for-the-badge&logo=go&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Redis](https://img.shields.io/badge/Redis-Streams%20%26%20PubSub-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Coverage](https://img.shields.io/badge/Coverage-%E2%89%A585%25-brightgreen?style=for-the-badge)

<p align="center">
  <b>TINT</b> redefine la forma de ver contenido compartido en internet. Combina sincronización de vídeo de sub-segundo, iluminación ambiental <b>Ambilight</b> en vivo, mapas de calor sociales y trivias competitivas en una interfaz oscura, sobria y de lujo.
</p>

</div>

---

## 🌌 Características Principales

* ⏱️ **Core Sync (Golang):** Reproducción sincronizada multicanal mediante WebSockets con corrección automática de desfase (*drift*) (< 500 ms).
* 🌈 **Efecto Ambilight en Vivo:** Extracción de paleta de colores fotograma a fotograma proyectada como sombra ambiental difuminada.
* 👏 **Gamificación & Heatmaps:** Reacciones expresivas con *throttling* anti-spam (máx. 5/s), histogramas de emoción en la barra de tiempo y trivias con bonificación por velocidad.
* 📊 **Analítica Privacy-First:** Ingesta desacoplada en Redis Streams con cumplimiento estricto de la Ley 1581 (sin almacenamiento de conversaciones).

---

## 🛠️ Arquitectura Monorepo

| Servicio | Dominio | Tecnologías |
| :--- | :--- | :--- |
| [`apps/web`](apps/web) | UI/UX & Cliente Web | React, TypeScript, Tailwind CSS, Vitest |
| [`services/sync-core`](services/sync-core) | Sincronización & Reloj Maestro | Golang, WebSockets, Redis Pub/Sub |
| [`services/media-worker`](services/media-worker) | Inspección de Vídeo & Ambilight | Python 3.11, FFmpeg, ffprobe, MinIO |
| [`services/social-core`](services/social-core) | Chat, Reacciones & Trivias | Golang, Redis Sorted Sets |
| [`services/analytics`](services/analytics) | Telemetría & KPIs Diferidos | Python, Redis Streams (`XREADGROUP`), SQL |

```text
tint-monorepo/
├── .github/workflows/      # ci-pipeline.yml · cd-deploy.yml
├── apps/
│   └── web/                # Frontend (React + TS + Tailwind)
├── services/
│   ├── sync-core/          # Sincronización y hosts (Go)
│   ├── social-core/        # Gamificación e inmersión (Go)
│   ├── media-worker/       # Worker de media (Python + FFmpeg)
│   └── analytics/          # Analítica & insights (Python + SQL)
├── scripts/                # Utilidades de CI (gate de cobertura Go)
├── docker-compose.yml      # Entorno local (PostgreSQL, Redis, MinIO)
├── Makefile
└── AGENTS.md               # Guía raíz → cada dominio tiene su propio AGENTS.md
```

> [!TIP]
> Cada dominio incluye un `AGENTS.md` con su stack, responsabilidades, límites, modelo de datos y reglas TDD. Léelo antes de contribuir (humano o agente IA).

---

## 🚀 Inicio Rápido en Local

**Requisitos:** Docker, Go 1.22+, Python 3.11+, Node.js 22+.

```bash
# 1. Clonar el repositorio
git clone https://github.com/HanamDavid/tint-monorepo.git
cd tint-monorepo

# 2. Levantar la infraestructura base (PostgreSQL, Redis, MinIO)
cp .env.example .env
docker compose up -d

# 3. Ejecutar la suite global de pruebas (TDD Policy Check)
make test-all
```

| Servicio local | URL |
| :--- | :--- |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| MinIO API / Consola | `localhost:9000` / [`localhost:9001`](http://localhost:9001) |

---

## 🧪 Política TDD y Cobertura

> [!IMPORTANT]
> Todo cambio en la plataforma exige metodología TDD (Red → Green → Refactor) y una cobertura de pruebas automatizadas **≥ 85%**. Los Pull Requests sin pruebas suficientes son rechazados automáticamente por el pipeline de CI.

El workflow [`ci-pipeline.yml`](.github/workflows/ci-pipeline.yml) se ejecuta en cada push y PR hacia `main` y `develop`, en paralelo:

| Job | Comando | Gate |
| :--- | :--- | :--- |
| Go (`sync-core`, `social-core`) | `go test -race -coverprofile=coverage.out ./...` | `scripts/check-go-coverage.sh` |
| Python (`media-worker`, `analytics`) | `pytest --cov=src` | `--cov-fail-under=85` |
| Web (`apps/web`) | `vitest run --coverage` | `thresholds` en `vitest.config.ts` |

El job **`CI Gate`** agrega el resultado de todos los anteriores y es el *status check* requerido por la protección de ramas: si cualquier servicio falla o baja del 85%, el merge queda bloqueado.

---

## 🤝 Contribución

1. Crea una rama desde `develop`: `feat/<dominio>-<descripcion>`.
2. Commits con [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/): `feat(sync-core): ...`, `fix(web): ...`, `ci: ...`.
3. Abre un PR hacia `develop` y completa la plantilla.

## 📄 Licencia

Distribuido bajo licencia [MIT](LICENSE).
