# 📚 Documentación de TINT

| Documento | Para qué leerlo |
| :--- | :--- |
| [Arquitectura del sistema](arquitectura.md) | Visión general: servicios, contratos entre dominios, almacenes, puertos y CI. **Empieza aquí.** |
| ↳ [Vista de componentes y conectores](arquitectura.md#4-vista-de-componentes-y-conectores-cc) | Componentes en ejecución y cada conector con su tipo y protocolo (REST, WebSocket, Pub/Sub, Streams, S3, SQL), más los flujos de reproducción y social. |
| ↳ [Vista de despliegue](arquitectura.md#7-vista-de-despliegue) | Contenedores de `docker compose` (incluidos identity-api y web), volúmenes, puertos, variables, estado de producción y pipeline de CI/CD. |
| [Decisiones de arquitectura (ADRs)](adr/README.md) | Por qué el sistema es como es: una decisión por archivo, con contexto, alternativas y consecuencias. |
| [Usuarios, identidad y permisos](usuarios-e-identidad.md) | Tipos de usuario, matriz de permisos, esquema de PostgreSQL, esquemas de tokens, flujos de login, invitación, retiro y bloqueo. |
| [Dominio de Reproducción](dominio-reproduccion.md) | Canal Sync v2: Reloj Maestro, comandos, drift, presencia, delegación, revocación, subida y biblioteca de vídeos, métricas, benchmarks y pruebas. |
| [Contrato de eventos de analítica](contrato-eventos-analitica.md) | Qué publica cada servicio en `analytics:events` y qué entrega Analítica a cambio (tablas y API de lectura de KPIs). |
| [Bug hunting 2026-10](bug-hunting-2026-10.md) | Hallazgos de la ronda de bug hunting de octubre de 2026 y cómo se corrigieron. |

## README de cada servicio

| Servicio | README | Guía para agentes |
| :--- | :--- | :--- |
| web | [apps/web/README.md](../apps/web/README.md) | [AGENTS](../apps/web/AGENTS.md) |
| identity-api | [services/identity-api/README.md](../services/identity-api/README.md) | [AGENTS](../services/identity-api/AGENTS.md) |
| sync-core | [services/sync-core/README.md](../services/sync-core/README.md) | [AGENTS](../services/sync-core/AGENTS.md) |
| social-core | [services/social-core/README.md](../services/social-core/README.md) | [AGENTS](../services/social-core/AGENTS.md) |
| media-worker | [services/media-worker/README.md](../services/media-worker/README.md) | [AGENTS](../services/media-worker/AGENTS.md) |
| analytics | [services/analytics/README.md](../services/analytics/README.md) | [AGENTS](../services/analytics/AGENTS.md) |

Cada `AGENTS.md` tiene stack, límites, modelo de datos y comandos (ver la tabla en el [AGENTS.md raíz](../AGENTS.md)).

## Convenciones de esta carpeta
- Español y diagramas **Mermaid**, que GitHub renderiza.
- Un documento por feature o tema transversal. Actualízalo en el mismo PR que cambie el flujo o el contrato.
- Una decisión de arquitectura nueva (o que reemplaza a otra) va como ADR en [`adr/`](adr/README.md), con la [plantilla](adr/0000-plantilla.md).
- Antes de hacer commit, verifica que los diagramas compilen (por ejemplo, con `mmdc` de `@mermaid-js/mermaid-cli` o en [mermaid.live](https://mermaid.live)).
- Usa nombres reales de archivos, rutas, claves Redis y tablas, y marca qué está implementado y qué está previsto.
