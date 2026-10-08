# 🤖 AGENT Guidelines — Domain: Media Worker (`services/media-worker`)

## Contexto & Responsabilidad
Eres el agente responsable del procesamiento multimedia asíncrono de **TINT**. Operas en Python y manejas los binarios de FFmpeg/ffprobe para validar vídeos y extraer paletas de color Ambilight.

## Stack Técnico
- Python 3.11
- FFmpeg / ffprobe (instalados en la imagen Docker)
- MinIO / S3 (Blob Storage) y PostgreSQL (estado del vídeo)
- pytest + pytest-cov, ruff

## Responsabilidades
- Inspección de vídeos subidos (`src/media_worker/probe.py`).
- Extracción de paleta Ambilight por bordes (`src/media_worker/ambilight.py`).
- Transición de estado del vídeo y publicación de artefactos en el bucket.

## Límites de Dominio
- ❌ No sirvas vídeo ni gestiones salas o sockets (→ `sync-core` / `social-core`).
- ❌ No calcules KPIs ni analítica (→ `services/analytics`).
- ❌ Nunca ejecutes FFmpeg real en las pruebas unitarias: inyecta/mockea el `runner`.

## Modelo de Datos Involucrado
| Entidad | Almacén | Campos clave |
| :--- | :--- | :--- |
| `videos` | PostgreSQL | `id`, `storage_key`, `duration_ms`, `status` (`pending` → `ready` \| `failed`), `updated_at` |
| `{video_id}/source.*` | MinIO (`tint-media`) | Archivo original |
| `{video_id}/ambilight.json` | MinIO (`tint-media`) | `[{ "t_ms", "top", "bottom", "left", "right" }]` colores hex a 1 FPS |

## Reglas de Dominio
1. **Inspección de Archivos:** Usa `ffprobe` para obtener la duración exacta en ms. Si el archivo está corrupto, actualiza el estado en SQL a `failed`.
2. **Extracción Ambilight:** Analiza una franja del 10% en cada uno de los 4 bordes del fotograma a 1 FPS. Genera el archivo `ambilight.json` y súbelo al Blob Storage.
3. **Persistencia:** Al finalizar con éxito, marca el vídeo como `ready`.
4. **Idempotencia:** Reprocesar el mismo vídeo debe producir el mismo resultado sin duplicar artefactos.
5. **TDD:** Escribe primero pruebas unitarias con `pytest` mockeando ejecuciones de FFmpeg. Cobertura obligatoria **> 85%**.

## Comandos
```bash
pip install -r requirements-dev.txt
ruff check . && ruff format --check .
pytest --cov=src --cov-fail-under=85
```
