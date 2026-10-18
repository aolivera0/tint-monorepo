# identity-api

Servicio de **identidad** de TINT (Node.js 22, TypeScript, Express 5, Drizzle ORM sobre PostgreSQL). Es la única puerta HTTP de la web para todo lo que no es tiempo real: quién es el Host, qué salas tiene, quién entra como invitado y qué vídeo se reproduce.

Este README explica **cómo funciona por dentro**. La referencia detallada (tabla completa de rutas, códigos de error y variables) está en [AGENTS.md](AGENTS.md); aquí se enlaza en vez de repetirla.

## Índice

1. [Propósito y responsabilidades](#1-propósito-y-responsabilidades)
2. [Arquitectura interna](#2-arquitectura-interna)
3. [Cómo funciona, paso a paso](#3-cómo-funciona-paso-a-paso)
4. [Estado y datos](#4-estado-y-datos)
5. [Configuración](#5-configuración)
6. [Ejecutar y probar](#6-ejecutar-y-probar)
7. [Observabilidad y errores](#7-observabilidad-y-errores)
8. [Decisiones de arquitectura](#8-decisiones-de-arquitectura)
9. [Enlaces](#9-enlaces)

---

## 1. Propósito y responsabilidades

| Hace | Dónde |
| :--- | :--- |
| Verifica el ID token de Firebase (RS256) del Host y crea o actualiza su fila en `users` con un *upsert* atómico | `middlewares/auth.ts`, `modules/users/users.repo.ts` |
| Crea, lista, renombra y borra (borrado lógico) salas | `routes/v1.ts`, `modules/rooms/rooms.repo.ts` |
| Crea y revoca invitaciones (código `nanoid(12)`) | `modules/invitations/invitations.repo.ts` |
| Emite los **tokens HS256** propios: guest token (invitado), sync-ticket y analytics-ticket (Host) | `modules/auth/auth.service.ts` |
| Registra cada guest token en `guest_sessions` para listarlo y revocarlo | `modules/guests/guestSessions.repo.ts` |
| Retira y **bloquea** invitados: `token:revoked:{jti}` + `PUBLISH REVOKED` | `modules/tokens/revocation.ts`, `modules/bans/roomBans.repo.ts` |
| Firma la subida directa del navegador a MinIO, la confirma y la encola en `media:uploads` | `modules/media/*`, `modules/videos/videos.repo.ts` |
| Biblioteca de vídeos del Host y reuso de un vídeo en otra sala | `routes/v1.ts` (`/videos`, `PUT /rooms/:id/media`) |
| Firma las URLs de reproducción (original, calidades y Ambilight) | `GET /rooms/:id/playback` |
| Publica eventos de analítica sin PII (best-effort) | `modules/analytics/analytics.events.ts` |

**Lo que NO hace:**

- No lleva el estado de reproducción: el Reloj Maestro es de `sync-core`. Aquí solo se **asigna** el vídeo de la sala; el `LOAD` lo manda el cliente por el Canal Sync.
- No procesa vídeo: `ffprobe`, normalización, calidades y Ambilight son de `media-worker`. identity-api solo crea la fila de `videos` y encola el trabajo.
- No calcula KPIs ni sirve analítica: emite el analytics-ticket y la API de lectura es de `analytics`.
- No escribe en Redis más que `token:revoked:{jti}`, el `PUBLISH` de `REVOKED` en `room:{id}:sync` y los `XADD` a `media:uploads` y `analytics:events`.
- No transporta el archivo de vídeo: el navegador sube y descarga **directo de MinIO** con URLs firmadas.

## 2. Arquitectura interna

```mermaid
flowchart LR
    WEB["apps/web"] -- "HTTP /api/v1" --> APP

    subgraph IDAPI["identity-api"]
        APP["app.ts<br/>cors + express.json"]
        ROUTER["routes/v1.ts<br/>todas las rutas"]
        MW["middlewares<br/>auth · guestAuth · requireRoomOwner<br/>activeRoom · validate · error"]
        AUTHM["modules/auth<br/>auth.service · jwks"]
        REPOS["repos Drizzle<br/>users · rooms · invitations<br/>guests · bans · videos"]
        TOK["modules/tokens<br/>revocation"]
        MEDIA["modules/media<br/>signer · store · queue"]
        ANEV["modules/analytics<br/>analytics.events"]
        CFG["config/env.ts<br/>zod"]
        DB["db/client.ts + schema.ts"]
    end

    APP --> ROUTER
    ROUTER --> MW
    ROUTER --> AUTHM
    ROUTER --> REPOS
    ROUTER --> TOK
    ROUTER --> MEDIA
    ROUTER --> ANEV
    MW --> AUTHM
    MW --> REPOS
    MW --> TOK
    REPOS --> DB
    MEDIA --> TOK
    ANEV --> TOK

    DB --> PG[("PostgreSQL")]
    TOK -- "EXISTS · SET EXAT · PUBLISH · XADD" --> RD[("Redis")]
    MEDIA -- "firma local GET/PUT · HEAD · DELETE" --> MI[("MinIO")]
    AUTHM -- "JWKS por kid" --> FB["Firebase / Google"]
```

`modules/tokens/revocation.ts` es también el dueño del **cliente Redis** (`getRedisClient`, `lazyConnect`, `maxRetriesPerRequest: 2`): la cola de medios y los eventos de analítica lo reutilizan. Redis, el firmador y el bucket se pueden sustituir en las pruebas (`setRedisClient`, `setMediaSigner`, `setMediaStore`).

| Módulo | Responsabilidad | Archivo |
| :--- | :--- | :--- |
| Arranque | Carga `.env` (`dotenv`) y escucha en `IDENTITY_API_PORT` | `src/server.ts` |
| App | `cors()`, `express.json()`, monta `/api/v1` y el manejador de errores | `src/app.ts` |
| Configuración | Valida el entorno con zod; `FIREBASE_PROJECT_ID` es obligatoria | `src/config/env.ts` |
| Esquema | Tablas Drizzle (fuente de las migraciones de `migrations/`) | `src/db/schema.ts`, `src/db/client.ts` |
| Rutas | Toda la API REST y la orquestación de cada flujo | `src/routes/v1.ts` |
| Auth del Host | `requireAuthHost`: verifica el ID token de Firebase y hace el *upsert* del usuario | `src/middlewares/auth.ts` |
| Auth del invitado | `requireRoomGuest` y `requireRoomHostOrGuest` (decide por el `alg` del JWT) | `src/middlewares/guestAuth.ts` |
| Dueño de la sala | `requireRoomOwner`: `rooms.host_id === req.user.id` (no mira el estado) | `src/middlewares/requireRoomOwner.ts` |
| Sala activa | `requireActiveRoom`: 404 / 410 y deja la sala en `req.room` | `src/middlewares/activeRoom.ts` |
| Validación | `validate(schema)` con zod: 400 con `details` | `src/middlewares/validate.ts` |
| Errores | Último manejador: nunca filtra detalles internos | `src/middlewares/error.ts` |
| Tokens | `issueGuestToken` y `generateHostTicket` (HS256) | `src/modules/auth/auth.service.ts` |
| JWKS | Claves públicas de Firebase por `kid` (caché, 5 peticiones/min) | `src/modules/auth/jwks.ts` |
| Revocación | `token:revoked:{jti}` y evento `REVOKED`; cliente Redis compartido | `src/modules/tokens/revocation.ts` |
| Medios | Firma GET/PUT (endpoint público), `HEAD`/`DELETE` (endpoint interno), `XADD media:uploads` | `src/modules/media/media.signer.ts`, `media.store.ts`, `media.queue.ts` |
| Analítica | `publishAnalyticsEvent`: `XADD analytics:events MAXLEN ~ 100000` que nunca rechaza | `src/modules/analytics/analytics.events.ts` |
| Repositorios | Acceso a cada tabla | `src/modules/{users,rooms,invitations,guests,bans,videos}/*.repo.ts` |

## 3. Cómo funciona, paso a paso

Todas las rutas cuelgan de `/api/v1`. "Firebase" significa `Authorization: Bearer <ID token de Firebase>`; "dueño" añade `requireRoomOwner`.

### 3.1 Login con Firebase (`POST /auth/verify`)

```mermaid
sequenceDiagram
    autonumber
    actor H as Host
    participant FB as Firebase Auth
    participant API as identity-api
    participant G as JWKS de Google
    participant PG as PostgreSQL
    participant R as Redis

    H->>FB: login con Google (SDK de Firebase)
    FB-->>H: ID token RS256
    H->>API: POST /auth/verify (Bearer ID token)
    API->>API: jwt.decode → kid de la cabecera
    API->>G: getSigningKey(kid) con caché
    API->>API: jwt.verify RS256, iss y aud de FIREBASE_PROJECT_ID
    API->>PG: INSERT users ... ON CONFLICT (provider, provider_sub) DO UPDATE
    API-->>H: { user, isRegistered: true }
    API-)R: XADD analytics:events user_login (best-effort)
```

- Sin cabecera, sin `kid`, firma inválida o sin `sub` → **401**. Si falla la base de datos en el *upsert* → **500** `Internal error`.
- `requireAuthHost` hace este mismo trabajo en **cada** ruta de Host, no solo en `/auth/verify`: email, nombre y avatar se refrescan en cada petición autenticada.
- El evento `user_login` lleva `user_id = users.provider_sub` (uid de Firebase), nunca `users.id` ni el email.

### 3.2 Crear sala e invitación

```mermaid
sequenceDiagram
    autonumber
    actor H as Host
    participant API as identity-api
    participant PG as PostgreSQL
    participant R as Redis

    H->>API: POST /rooms { title } (Firebase)
    API->>PG: INSERT rooms (host_id, title, status active)
    API->>PG: INSERT invitations (code nanoid 12, expires_at = ahora + INVITATION_DEFAULT_TTL_HOURS)
    API-->>H: { room, invitation { code, expiresAt, link } }
    API-)R: XADD analytics:events room_created
    H->>API: POST /rooms/:id/invitations { ttlHours? } (dueño)
    API->>PG: INSERT invitations
    API-->>H: { code, expiresAt, link: /invite/code }
```

- `title` va de 1 a 150 caracteres; `ttlHours` es un entero de 1 a 720 (30 días).
- `GET /rooms` devuelve solo salas no borradas, cada una con su **última invitación vigente** (no revocada ni vencida), calculada en una sola consulta (`listLatestActiveByRooms`).
- `POST /invitations/:code/revoke` cierra la invitación, pero **no** retira a quien ya entró con ella.

### 3.3 Claim de invitación con `deviceId` (y el 403 BANNED)

```mermaid
sequenceDiagram
    autonumber
    actor I as Invitado
    participant API as identity-api
    participant PG as PostgreSQL
    participant R as Redis

    I->>API: POST /invitations/claim { code, nick, deviceId? }
    API->>PG: SELECT invitations WHERE code
    alt no existe
        API-->>I: 404 Not found
    else revocada
        API-->>I: 409 Revoked
    else vencida o sala borrada
        API-->>I: 410 Expired / Deleted
    else deviceId bloqueado en la sala
        API->>PG: SELECT room_bans WHERE room_id, device_id
        API-->>I: 403 { error: "BANNED" }
    else válida
        API->>API: issueGuestToken → sub guest-uuid, role participant, jti, exp 4 h
        API->>PG: INSERT guest_sessions (jti, room_id, sub, nick, expires_at, device_id)
        API-->>I: { token, roomId, nick, roomTitle }
        API-)R: XADD analytics:events invitation_claimed
    end
```

- `deviceId` es un UUID **opcional** que la web genera y guarda en el navegador. Sin `deviceId` no se comprueba ningún bloqueo (clientes viejos).
- La fila de `guest_sessions` se inserta **antes** de responder: un token sin fila no se podría listar ni revocar.
- Cada claim genera un `sub` nuevo (`guest:<uuid>`): el invitado es anónimo y no se vincula con ninguna cuenta.

### 3.4 Sync-ticket y analytics-ticket

Los dos son el **mismo ticket de Host** (`generateHostTicket`): HS256 con `JWT_SECRET`, vida `SYNC_TICKET_TTL_SECONDS` (600 s por defecto) y claims `sub` (`users.id`), `room_id`, `role: host`, `nick`, `jti`, `iss`, `aud`, `exp`, `iat`. Existen porque `sync-core` y `analytics` solo validan JWT simétricos y no conocen los ID tokens de Firebase.

```mermaid
sequenceDiagram
    autonumber
    actor H as Host
    participant API as identity-api
    participant SY as sync-core
    participant AN as analytics

    H->>API: POST /rooms/:id/sync-ticket (dueño + sala activa)
    API-->>H: { token, expiresAt }
    H->>SY: WebSocket /ws/sync con el sync-ticket
    H->>API: POST /rooms/:id/analytics-ticket (dueño, sala activa o borrada)
    API-->>H: { token, expiresAt }
    H->>AN: GET /api/v1/rooms/:id/kpis (Bearer analytics-ticket)
```

- El `nick` del Host es `displayName`; si no hay, la parte local del email; si no, `Host`. **Nunca** el email completo, porque lo ven todos los invitados.
- El sync-ticket exige `requireActiveRoom` (410 si la sala está borrada). El analytics-ticket **no**: así el Host puede ver los KPIs de una sala ya cerrada.
- `social-core` también acepta el sync-ticket para las acciones de moderación del Host (ver [arquitectura §5](../../docs/arquitectura.md#5-contratos-compartidos-entre-dominios)).

### 3.5 Retirar vs. bloquear a un invitado

Las dos acciones cortan la sesión con el mismo mecanismo de revocación, que es un **contrato** con sync-core, social-core y analytics:

1. `SET token:revoked:{jti} 1 EXAT <exp>`: la marca vive exactamente lo que viviría el token.
2. `PUBLISH room:{id}:sync` con `{ type: "REVOKED", room_id, jti, event_id, position_ms: 0, version: 0, occurred_at, received_at }`: sync-core y social-core cierran las conexiones de ese `jti` con el código **4001**.

La diferencia es que **bloquear** además impide que ese navegador vuelva a reclamar una invitación de la sala.

```mermaid
sequenceDiagram
    autonumber
    actor H as Host
    participant API as identity-api
    participant PG as PostgreSQL
    participant R as Redis
    participant SY as sync-core / social-core

    rect rgba(127, 127, 127, 0.1)
    Note over H,SY: Retirar (POST /rooms/:id/guests/:jti/revoke)
    H->>API: POST /rooms/:id/guests/:jti/revoke
    API->>PG: UPDATE guest_sessions SET revoked = true
    API->>R: SET token:revoked:{jti} EXAT exp
    API->>R: PUBLISH room:{id}:sync REVOKED
    R-->>SY: REVOKED → cierre 4001
    API-->>H: { success: true }
    end

    rect rgba(127, 127, 127, 0.1)
    Note over H,SY: Bloquear (POST /rooms/:id/bans)
    H->>API: POST /rooms/:id/bans { jti } o { sub }
    API->>PG: sesiones del invitado (room_id, sub)
    API->>PG: INSERT room_bans por cada device_id (ON CONFLICT DO NOTHING)
    loop cada sesión no vencida
        API->>R: SET token:revoked:{jti} EXAT exp
        API->>R: PUBLISH room:{id}:sync REVOKED
    end
    alt Redis falla
        API-->>H: 503 (el ban ya está guardado, repetir termina de revocar)
    else
        API->>PG: UPDATE guest_sessions SET revoked = true
        API-->>H: { ban: { id, nick, createdAt } }
    end
    end
```

- **Retirar** marca la BD primero. Si Redis falla después, la excepción llega al manejador de errores (**500**) con la sesión ya revocada en BD; repetir la llamada es idempotente y vuelve a escribir la marca y a publicar.
- **Bloquear** guarda el ban **antes** de tocar Redis, así un 503 nunca deja volver a entrar. `{ jti }` lo usa el panel de invitados y `{ sub }` el chat. Se inserta un ban por cada `device_id` distinto de las sesiones del invitado; si ninguna tiene `device_id` (cliente viejo) se guarda uno solo por `sub` (`device_id = NULL`), que corta la sesión pero **no** impide volver a entrar.
- `GET /rooms/:id/bans` y `GET /rooms/:id/guests` nunca devuelven `sub` ni `device_id`. `DELETE /rooms/:id/bans/:banId` desbloquea (los tokens revocados no se reactivan).
- El bloqueo es por **navegador**, no por persona: borrar los datos del navegador, una ventana privada u otro dispositivo lo evaden. Es una barrera contra la reentrada casual ([usuarios-e-identidad §6.6](../../docs/usuarios-e-identidad.md#66-bloquear-a-un-invitado-por-dispositivo)).

Las rutas que aceptan guest token (`requireRoomGuest`) comprueban la revocación en **dos sitios**: `guest_sessions.revoked` y la clave `token:revoked:{jti}`. Si no se puede comprobar (BD o Redis caídos) responden **503**: falla cerrado.

### 3.6 Subida directa a MinIO → `complete` → `XADD media:uploads`

El archivo **nunca pasa por identity-api**: el navegador lo sube a MinIO con una URL PUT firmada (un cálculo SigV4 local, sin red).

```mermaid
sequenceDiagram
    autonumber
    actor H as Host (navegador)
    participant API as identity-api
    participant PG as PostgreSQL
    participant MI as MinIO
    participant R as Redis
    participant MW as media-worker

    H->>API: POST /rooms/:id/media/uploads { filename, contentType, sizeBytes }
    alt sizeBytes > MAX_UPLOAD_BYTES
        API-->>H: 413 File too large
    end
    API->>PG: INSERT videos (id, storage_key incoming/{id}/original.ext, status pending, title)
    API->>API: presignPut(storage_key, contentType, UPLOAD_URL_TTL_SECONDS)
    API-->>H: { videoId, uploadUrl, storageKey, expiresAt, headers }
    H->>MI: PUT uploadUrl (Content-Type firmado)
    H->>API: POST /rooms/:id/media/uploads/:videoId/complete
    alt ya no está en pending
        API-->>H: { mediaId, mediaKey, status } actual (idempotente)
    end
    API->>MI: HEAD storage_key
    alt el objeto no existe
        API-->>H: 409 Upload missing
    else tamaño real > sizeBytes o > MAX_UPLOAD_BYTES
        API->>MI: DELETE storage_key
        API-->>H: 413 (el vídeo sigue en pending)
    end
    API->>PG: UPDATE videos SET status processing WHERE status = pending
    API->>R: XADD media:uploads * video_id {id}
    alt Redis falla
        API->>PG: UPDATE videos SET status pending
        API-->>H: 503
    else
        API->>PG: UPDATE rooms SET media_id, media_key
        API-->>H: { mediaId, mediaKey, status: processing }
        R-->>MW: XREADGROUP media-workers
    end
```

- La extensión de la clave sale del nombre del archivo si es `.mp4`, `.webm`, `.mov`, `.mkv`, `.m4v` u `.ogv`; si no, del `contentType`. El nombre del usuario nunca forma parte de la clave; sí se guarda, sin extensión, como `videos.title`.
- La URL PUT no acota el tamaño: por eso `complete` compara el tamaño real (`HEAD`) con el declarado.
- La transición `pending → processing` es un `UPDATE ... WHERE status = 'pending'`: de dos confirmaciones simultáneas solo una encola.
- Los originales de `incoming/` los borra la regla de ciclo de vida del bucket a los 2 días (`minio-init` en `docker-compose.yml`).

### 3.7 Biblioteca (`GET /videos`) y reuso con `PUT /rooms/:id/media { videoId }`

```mermaid
sequenceDiagram
    autonumber
    actor H as Host
    participant API as identity-api
    participant PG as PostgreSQL
    participant SY as sync-core

    H->>API: GET /videos (Firebase)
    API->>PG: SELECT videos WHERE uploaded_by = host AND status IN (processing, ready) ORDER BY created_at DESC
    API->>API: previewUrl = firma de {id}/360p.mp4 si ready y tiene 360, si no storage_key
    API-->>H: { videos: [{ id, title, durationMs, status, createdAt, previewUrl }] }
    opt renombrar
        H->>API: PATCH /videos/:id { title }
        API-->>H: { video }
    end
    H->>API: PUT /rooms/:id/media { videoId } (dueño + sala activa)
    API->>PG: SELECT videos WHERE id
    alt no existe, no es suyo o no está processing/ready
        API-->>H: 404 Not found
    else
        API->>PG: UPDATE rooms SET media_id = videoId, media_key = storage_key
        API-->>H: { mediaId, mediaKey }
    end
    H->>SY: LOAD { media_id } por el Canal Sync
```

- `PUT /rooms/:id/media` acepta `{ videoId }` **o** `{ mediaKey }` (una clave que ya está en el bucket, con un `mediaId` nuevo), nunca los dos (400).
- Reusar por `videoId` deja `media_id = videos.id`: así el playback sigue resolviendo estado, calidades y Ambilight de ese vídeo.

### 3.8 Playback con calidades y `ambilightUrl`

`GET /rooms/:id/playback` lo pueden llamar el Host dueño (ID token de Firebase) **o** un invitado de la sala (guest token). `requireRoomHostOrGuest` decide mirando el `alg` de la cabecera del JWT: `HS256` → invitado; cualquier otro → Host.

```mermaid
sequenceDiagram
    autonumber
    actor U as Host o invitado
    participant API as identity-api
    participant PG as PostgreSQL
    participant MI as MinIO

    U->>API: GET /rooms/:id/playback
    API->>PG: rooms (requireActiveRoom)
    alt la sala no tiene vídeo
        API-->>U: { mediaId: null, url: null, ..., qualities: [] }
    end
    API->>PG: SELECT videos WHERE id = media_id
    alt status failed
        API-->>U: { mediaId, url: null, status: failed, qualities: [] }
    else
        API->>API: url = firma GET de videos.storage_key o rooms.media_key
        API->>API: si ready, ambilightUrl = firma de {id}/ambilight.json
        API->>API: si ready, firma de {id}/720p.mp4 y {id}/360p.mp4 según renditions
        API-->>U: { mediaId, url, expiresAt, status, ambilightUrl, qualities }
    end
    U->>MI: GET url firmada (vídeo y ambilight.json)
```

- Mientras el vídeo está en `processing` se reproduce el **original** de `incoming/`; al quedar `ready`, media-worker reescribe `storage_key` a `{id}/source.mp4` y el playback sirve esa versión. Por eso la URL se firma sobre `videos.storage_key` y no sobre `rooms.media_key`.
- `qualities` siempre empieza por `{ id: "source", label: "Original" }` (la misma URL que `url`) y, si el vídeo está `ready`, añade `720p` y `360p` solo si están en `videos.renditions`. `renditions` se lee a la defensiva: solo se aceptan las alturas conocidas `[720, 360]`.
- Todas las URLs comparten el TTL `PLAYBACK_URL_TTL_SECONDS` (1 h). Un vídeo asignado por `mediaKey` manual no tiene fila en `videos`: `status: null` y sin Ambilight ni calidades.

### 3.9 Borrado de sala (`DELETE /rooms/:id`)

```mermaid
sequenceDiagram
    autonumber
    actor H as Host
    participant API as identity-api
    participant PG as PostgreSQL
    participant R as Redis

    H->>API: DELETE /rooms/:id (dueño)
    alt la sala ya está borrada (reintento)
        API->>PG: sesiones activas (no revocadas, no vencidas)
        API->>R: SET token:revoked + PUBLISH REVOKED por cada una
        API-->>H: 410 Deleted, o 503 si Redis vuelve a fallar
    else sala activa
        API->>PG: UPDATE rooms SET status deleted
        API->>PG: sesiones activas de la sala
        API->>R: SET token:revoked + PUBLISH REVOKED por cada una
        alt Redis falla
            API-->>H: 503 retry DELETE (las sesiones siguen activas en BD)
        else
            API->>PG: UPDATE guest_sessions SET revoked = true
            API-->>H: { success: true, room }
            API-)R: XADD analytics:events room_closed (reason deleted)
        end
    end
```

- Es un **borrado lógico** (`rooms.status = 'deleted'`): las filas se conservan y las rutas de la sala responden **410**.
- La BD se marca **después** de Redis: si Redis falla, las sesiones siguen activas en BD y el siguiente `DELETE` las encuentra y termina de revocarlas.
- `room_closed` solo se emite en el primer borrado exitoso, nunca en el reintento ni en el 503. Es el evento que dispara la consolidación de KPIs en `analytics`.

## 4. Estado y datos

### 4.1 Tablas (PostgreSQL)

identity-api es el **dueño del esquema**: las migraciones salen de `src/db/schema.ts` con `drizzle-kit` y viven en `migrations/`. media-worker solo actualiza `videos` (y lee `users.provider_sub`).

```mermaid
erDiagram
    users ||--o{ rooms : "host_id"
    users ||--o{ invitations : "created_by"
    users ||--o{ videos : "uploaded_by"
    users ||--o{ room_bans : "created_by"
    rooms ||--o{ invitations : "room_id"
    rooms ||--o{ guest_sessions : "room_id"
    rooms ||--o{ room_bans : "room_id"
    rooms ||--o{ videos : "room_id"

    users {
        uuid id PK
        text provider "firebase"
        text provider_sub "UK con provider"
        text email
        text display_name
        text avatar_url
        timestamptz created_at
        timestamptz updated_at
    }
    rooms {
        uuid id PK
        uuid host_id FK
        varchar title "max 150"
        varchar status "active o deleted"
        uuid media_id "vídeo asignado"
        text media_key "clave en el bucket"
        timestamptz created_at
        timestamptz updated_at
    }
    invitations {
        uuid id PK
        varchar code UK "nanoid 12"
        uuid room_id FK
        uuid created_by FK
        timestamptz expires_at
        boolean revoked
        timestamptz created_at
    }
    guest_sessions {
        uuid jti PK
        uuid room_id FK
        text sub "guest-uuid"
        varchar nick "max 64"
        timestamptz joined_at
        timestamptz expires_at
        boolean revoked
        uuid device_id "nullable, migración 0007"
    }
    room_bans {
        uuid id PK
        uuid room_id FK "UK con device_id"
        uuid device_id "nullable"
        text sub
        varchar nick
        timestamptz created_at
        uuid created_by FK
    }
    videos {
        uuid id PK
        uuid room_id FK
        uuid uploaded_by FK
        text storage_key
        varchar content_type
        bigint size_bytes
        integer duration_ms
        varchar status
        varchar title "nullable, migración 0007"
        jsonb renditions "alturas, default vacío"
        timestamptz created_at
        timestamptz updated_at
    }
```

Borrados: `rooms`, `invitations`, `guest_sessions`, `room_bans` y `videos` caen en cascada al borrar su sala o su Host; `videos.uploaded_by` y `room_bans.created_by` pasan a `NULL` si se borra el usuario.

**Estados de `videos.status`:**

```mermaid
stateDiagram-v2
    direction LR
    [*] --> pending: POST uploads (identity-api)
    pending --> pending: complete con 409 o 413
    pending --> processing: complete y XADD (identity-api)
    processing --> pending: XADD falla, 503 (identity-api)
    processing --> ready: publicado source, ambilight y calidades (media-worker)
    processing --> failed: ffprobe, FFmpeg, extensión u original ausente (media-worker)
    ready --> [*]
    failed --> [*]
```

Solo los vídeos en `processing` o `ready` aparecen en la biblioteca y se pueden reusar. Un vídeo que se queda en `pending` (subida nunca confirmada) no es visible; su original caduca en el bucket a los 2 días.

### 4.2 Redis y MinIO

| Clave | Tipo | Escribe | Lee |
| :--- | :--- | :--- | :--- |
| `token:revoked:{jti}` | String con `EXAT` = `exp` del token | identity-api (retirar, bloquear, borrar sala) | identity-api (`requireRoomGuest`), sync-core, social-core, analytics |
| `room:{id}:sync` | Pub/Sub | identity-api publica solo `REVOKED` (el resto es de sync-core) | sync-core, social-core |
| `media:uploads` | Stream | identity-api: `XADD * video_id <id>` | media-worker (grupo `media-workers`) |
| `analytics:events` | Stream, `MAXLEN ~ 100000` | identity-api: `user_login`, `room_created`, `room_closed`, `invitation_claimed` | analytics (grupo `analytics-workers`) |
| `incoming/{id}/original.*` | Objeto MinIO | Navegador (PUT firmado) | identity-api (`HEAD`/`DELETE`), media-worker |
| `{id}/source.mp4`, `{id}/ambilight.json`, `{id}/{720,360}p.mp4` | Objetos MinIO | media-worker | Navegador (GET firmado por identity-api) |

Detalle de los eventos y sus campos en [AGENTS.md › Eventos de analítica](AGENTS.md#eventos-de-analítica) y en el [contrato](../../docs/contrato-eventos-analitica.md).

## 5. Configuración

identity-api corre en `docker-compose.yml` (`Dockerfile` multi-stage y migraciones en el servicio `identity-migrate`); para desarrollar con recarga, `npm run dev` lee `services/identity-api/.env` (plantilla: [`.env.example`](.env.example)). La tabla completa con valores por defecto está en [AGENTS.md › Variables de Entorno](AGENTS.md#variables-de-entorno).

| Grupo | Variables | Notas |
| :--- | :--- | :--- |
| HTTP | `IDENTITY_API_PORT` (8081) | |
| Datos | `DATABASE_URL`, `REDIS_URL` | Los mismos PostgreSQL y Redis de `docker compose` |
| Firebase | `FIREBASE_PROJECT_ID` | **Obligatoria**: sin ella el proceso no arranca (zod) |
| Tokens HS256 | `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE` | **Contrato**: deben coincidir con sync-core, social-core y analytics (`.env.example` raíz) |
| Vida de tokens | `GUEST_TOKEN_TTL_HOURS` (4), `INVITATION_DEFAULT_TTL_HOURS` (24), `SYNC_TICKET_TTL_SECONDS` (600) | El último aplica también al analytics-ticket |
| MinIO | `MINIO_ENDPOINT`, `MINIO_PUBLIC_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `MINIO_REGION` | Las URLs se firman con el endpoint **público** (el host va dentro de la firma SigV4); `HEAD`/`DELETE` usan el interno |
| Medios | `PLAYBACK_URL_TTL_SECONDS` (3600), `UPLOAD_URL_TTL_SECONDS` (900), `MAX_UPLOAD_BYTES` (2 GiB) | |

Puertos relacionados: web 5173, PostgreSQL 5432, Redis 6379, MinIO 9000 (API) y 9001 (consola). El CORS de MinIO para el navegador se configura en `docker-compose.yml` (`MINIO_CORS_ALLOW_ORIGIN`).

## 6. Ejecutar y probar

```bash
# Infraestructura (desde la raíz del monorepo)
cp .env.example .env
docker compose up -d postgres redis minio minio-init

# identity-api
cd services/identity-api
cp .env.example .env
npm ci
npx drizzle-kit migrate     # aplica migrations/ sobre DATABASE_URL
npm run dev                 # http://localhost:8081/api/v1/healthz
```

**Gate del dominio** (lo mismo que corre el CI):

```bash
npm ci
npm run typecheck
npx vitest run --coverage   # umbral 85 % en líneas, funciones, ramas y sentencias
```

- Las pruebas unitarias (`tests/*.test.ts`, supertest + mocks de repos) **no** usan PostgreSQL, Redis ni MinIO reales: se inyectan dobles con `setRedisClient`, `setMediaSigner` y `setMediaStore`.
- `npm run test:integration` (`INTEGRATION=1`) recorre el flujo completo contra `docker compose` (PostgreSQL, Redis, MinIO, sync-core y social-core); solo se simula la verificación de Firebase. Con `WAIT_WORKER=1` espera además a que media-worker deje el vídeo en `ready`.
- Si cambias `src/db/schema.ts`, genera la migración con `npx drizzle-kit generate` y commitéala junto con `migrations/meta`.

## 7. Observabilidad y errores

**Formato de error.** Siempre `{ "error": "<texto>" }`. Los 400 de zod añaden `details` (`error.flatten()`). Los 500 son siempre `{ "error": "Internal error" }`: `middlewares/error.ts` registra el detalle y solo deja pasar los 4xx que Express marca como expuestos (JSON mal formado, cuerpo demasiado grande).

| Código | Cuándo |
| :--- | :--- |
| 400 | Cuerpo inválido según zod |
| 401 | Sin token, token inválido o revocado |
| 403 | No es el dueño de la sala, guest token de otra sala, o `BANNED` en el claim |
| 404 | No existe (también para no revelar recursos ajenos, p. ej. vídeos de otro Host) |
| 409 | Invitación revocada o `Upload missing` |
| 410 | Sala borrada o invitación vencida |
| 413 | Vídeo por encima de `MAX_UPLOAD_BYTES` o del tamaño declarado |
| 503 | No se pudo comprobar o escribir una revocación, o encolar en `media:uploads` (falla cerrado) |

**Logs.** `console.error` con un prefijo por componente: `[auth]`, `[guestAuth]`, `[activeRoom]`, `[rooms]`, `[uploads]`, `[bans]`, `[analytics]` y `[error]`. Nunca se registran tokens, emails ni los campos de los eventos de analítica (solo su tipo y el mensaje del error).

**Métricas.** El servicio no expone métricas propias; la salud se consulta en `GET /api/v1/healthz` (`{ status: "ok", service: "identity-api" }`). La actividad de negocio (logins, salas, claims, cierres) llega a `analytics` por `analytics:events`.

## 8. Decisiones de arquitectura

- [ADR 0006 · Tickets JWT HS256 emitidos por identity-api](../../docs/adr/0006-tickets-jwt-hs256-emitidos-por-identity-api.md)
- [ADR 0007 · Revocación por clave Redis y cierre 4001](../../docs/adr/0007-revocacion-por-clave-redis-y-cierre-4001.md)
- [ADR 0008 · Subida directa a MinIO con URL firmada](../../docs/adr/0008-subida-directa-a-minio-con-url-firmada.md)
- [ADR 0009 · Procesamiento asíncrono con Redis Streams](../../docs/adr/0009-procesamiento-asincrono-con-redis-streams.md)
- [ADR 0010 · Eventos de analítica sin PII](../../docs/adr/0010-eventos-de-analitica-sin-pii.md)
- [ADR 0011 · Ambilight precalculado por media-worker](../../docs/adr/0011-ambilight-precalculado-por-media-worker.md)
- [ADR 0013 · Bloqueo de invitados por dispositivo](../../docs/adr/0013-bloqueo-de-invitados-por-dispositivo.md)

## 9. Enlaces

- [AGENTS.md](AGENTS.md): reglas del dominio, tabla completa de la API y variables.
- [Usuarios, identidad y permisos](../../docs/usuarios-e-identidad.md): tipos de usuario, matriz de permisos, ER y tokens.
- [Dominio de Reproducción](../../docs/dominio-reproduccion.md): revocación (§6.6), subida y biblioteca (§6.7).
- [Contrato de eventos de analítica](../../docs/contrato-eventos-analitica.md).
- [Arquitectura](../../docs/arquitectura.md): [vista de componentes y conectores (§4)](../../docs/arquitectura.md#vista-de-componentes-y-conectores) y contratos entre dominios (§5).
