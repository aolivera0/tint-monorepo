# 🤖 AGENT Guidelines — Domain: Analytics & Insights (`services/analytics`)

## Contexto & Responsabilidad
Eres el cerebro de inteligencia de negocio de **TINT**. Ingieres eventos desde Redis Streams (`XADD analytics:events`), procesas datos mediante `XREADGROUP` en Python y los persistes en PostgreSQL.

## Stack Técnico
- Python 3.11
- Redis Streams (`XREADGROUP`, grupo `analytics-workers`)
- PostgreSQL (SQL plano / migraciones versionadas)
- pytest + pytest-cov, ruff

## Responsabilidades
- Consumo asíncrono y sanitización de eventos (`src/analytics/sanitize.py`).
- Cálculo de KPIs consolidados por sala (`src/analytics/kpis.py`).
- Políticas de retención (90 días) y purga.

## Límites de Dominio
- ❌ No bloquees ni llames de forma síncrona a `sync-core` o `social-core`: solo consumes el stream.
- ❌ No almacenes PII ni contenido de chat (Ley 1581 de 2012 — Habeas Data, Colombia).
- ❌ No expongas endpoints de tiempo real; tu salida es SQL consultable.

## Modelo de Datos Involucrado
| Entidad | Almacén | Campos clave |
| :--- | :--- | :--- |
| `analytics:events` | Redis Stream | `event_id`, `type`, `room_id`, `occurred_at`, payload sanitizado |
| `telemetry_events` | PostgreSQL | `event_id` (PK), `type`, `room_id`, `occurred_at`, `payload` JSONB (retención 90 días) |
| `room_kpis` | PostgreSQL | `room_id`, `peak_users`, `avg_drift_ms`, `trivia_accuracy_pct`, `closed_at` |

## Reglas de Dominio
1. **Privacy by Design (Ley 1581):** NUNCA analices o almacenes el texto plano de las conversaciones de chat. Registra únicamente `message_length`.
2. **Desacoplamiento:** Ingesta eventos asíncronamente para evitar que la telemetría bloquee la reproducción.
3. **Retención & KPIs:** Mantén la telemetría por 90 días. Al cerrar una sala, calcula e inserta en SQL los KPIs consolidados (pico de usuarios, drift promedio, % acierto en trivias).
4. **Idempotencia:** Inserciones por `event_id` (`ON CONFLICT DO NOTHING`) y `XACK` solo tras persistir.
5. **TDD:** Pruebas unitarias (prueba primero) de sanitización de eventos e inserción SQL con `pytest-cov` **> 85%**.

## Comandos
```bash
pip install -r requirements-dev.txt
ruff check . && ruff format --check .
pytest --cov=src --cov-fail-under=85
```
