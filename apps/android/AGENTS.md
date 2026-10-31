# 🤖 AGENT Guidelines — Domain: App Android (`apps/android`)

## Contexto & Responsabilidad
Eres el agente especializado en el cliente móvil de **TINT**: una app nativa en Kotlin que hace lo mismo que la web (login con Google, unirse a una sala, reproducción sincronizada, chat, reacciones y trivia) contra el **mismo backend**. Decisión y alternativas en el [ADR 0015](../../docs/adr/0015-app-android-nativa-kotlin.md). Cómo compilarla y probarla: [`README.md`](README.md).

## Stack Técnico
- Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10.01, Material 3), `minSdk` 26, `targetSdk` 35.
- Gradle 8.10.2 (wrapper) + Android Gradle Plugin 8.7.3, JDK 17. Versiones en `gradle/libs.versions.toml`.
- Firebase Auth (BoM 33.5.1) + `androidx.credentials` 1.3.0 + `googleid` 1.1.1 — login con Google contra el mismo proyecto Firebase que la web. Se usa la BoM 33.x porque la 35.x se compila con una versión de Kotlin más nueva que la del proyecto. Plugin `com.google.gms.google-services` 4.5.0.
- OkHttp 4.12.0 — REST y WebSocket (cliente estándar de facto en Android; sin dependencias transitivas relevantes).
- `kotlinx-serialization-json` 1.7.3 — JSON tolerante a campos nuevos (`ignoreUnknownKeys`).
- `kotlinx-coroutines-core` 1.9.0 — llamadas de red suspendibles.
- JUnit 4.13.2 + MockWebServer 4.12.0 — pruebas unitarias del cliente de red.
- Kover 0.9.0 — cobertura (mínimo 85 %).

## Responsabilidades
- Cliente REST de identity-api (`net/ApiClient.kt`): mismas rutas `/api/v1` que la web.
- Canal Sync v2 (`/ws/sync`) y Canal Social (`/ws/social`): se implementan por fases (ver README).
- Reproducción sincronizada con Media3, chat, reacciones, trivia y login con Google.

## Límites de Dominio
- ❌ No implementes lógica de negocio autoritativa (puntajes, reloj maestro, moderación): vive en `services/*`. La app refleja y pre-valida.
- ❌ No cambies contratos desde aquí: un cambio en el API o en un canal exige actualizar **ambos lados**, sus pruebas y [docs/arquitectura.md §5](../../docs/arquitectura.md#5-contratos-compartidos-entre-dominios).
- ❌ No modifiques archivos fuera de `apps/android/` sin coordinación con el dominio dueño.
- ❌ Nunca subas `google-services.json` (solo `google-services.json.example`), keystores ni `local.properties`.
- ❌ Nunca envíes texto de chat a telemetría.

## Contratos que consume
| Contrato | Fuente de verdad |
| :--- | :--- |
| REST `/api/v1` (invitaciones, salas, playback, bans) | [identity-api/AGENTS.md](../../services/identity-api/AGENTS.md#api) |
| Canal Sync v2 (`STATE`, `HEARTBEAT` → `SYNC_CHECK`, `SYNC_CORRECTION`, cierres `4001`/`4003`) | [sync-core/AGENTS.md](../../services/sync-core/AGENTS.md#protocolo-del-canal-sync) |
| Canal Social (`history`, `chat_*`, `reaction_*`, `trivia_*`) | [social-core/AGENTS.md](../../services/social-core/AGENTS.md#protocolo-del-canal-social) |
| Claims JWT HS256 | [usuarios-e-identidad §4](../../docs/usuarios-e-identidad.md#4-esquemas-de-tokens) |

## Estructura
| Ruta (`app/src/main/java/com/tint/app/`) | Rol |
| :--- | :--- |
| `config/Endpoints.kt` | URLs del backend (REST y los dos WebSockets, con `room_id` y `token` en la query). |
| `net/ApiClient.kt` | Cliente REST: `ApiAuth` (`AUTO`/`HOST`/`GUEST`), `ApiError`, 401 de host → borra la sesión y avisa. |
| `net/TokenStore.kt` | `tint_token` (host) y `tint_guest_token` (invitado), mismas claves que la web. |
| `auth/` | `AuthRepository` (Google → Firebase → `tint_token` → `POST /auth/verify`; sin sesión si algo falla), `LoginController` (estado de la pantalla), y las capas finas `CredentialManagerGoogleSource` y `FirebaseAuthGateway`. |
| `join/` | `InviteCode` (extrae el código de un link, ruta o código pelado), `InviteRepository` (`POST /invitations/claim` anónimo con `deviceId`; guarda `tint_guest_token` y **no borra** la sesión de host, a diferencia de la web) y `JoinController` (nick de 2 a 24 caracteres). |
| `identity/DeviceIdProvider.kt` | `tint_device_id` (UUID persistente) para el bloqueo por dispositivo. |
| `store/` | `KeyValueStore` y su implementación sobre `SharedPreferences`. |
| `AppContainer.kt` · `MainActivity.kt` | Cableado y pantalla. Capa fina excluida de la cobertura. |

## Variables de compilación
Se pasan como propiedades de Gradle (`-P`) o en `gradle.properties`; terminan en `BuildConfig`.

| Propiedad | Por defecto | Descripción |
| :--- | :--- | :--- |
| `tintIdentityApiUrl` | `http://localhost:8081/api/v1` | identity-api. |
| `tintSyncWsUrl` | `ws://localhost:8083/ws/sync` | Canal Sync de sync-core. |
| `tintSocialWsUrl` | `ws://localhost:8082/ws/social` | Canal Social de social-core. |
| `tintInviteHost` | `tint.jaucor.online` | Host de los links `https://<host>/invite/<código>` que abren la app (intent-filter). |

Con la tablet por adb, `adb reverse tcp:8081 tcp:8081` (y 8082, 8083) hace que `localhost` llegue al PC. El tráfico sin TLS solo está permitido en el build `debug`.

## Reglas de Dominio
1. **TDD & Cobertura:** prueba que falla primero (Red → Green → Refactor); cobertura mínima **85 %** (`koverVerifyDebug`). Solo se excluyen Compose y el cableado de Android.
2. **Mismos contratos que la web:** ante una duda, `apps/web/src/lib/*` es la referencia de comportamiento (mensajes de error, reglas del cliente).
3. **Tolerancia:** ignora los campos desconocidos y falla cerrado ante credenciales inválidas.
4. **Canales aislados:** un cliente WebSocket para Sync y otro para Social; nunca los mezcles.

## Comandos
```bash
export JAVA_HOME=<JDK 17> ANDROID_HOME=<Android SDK>
./gradlew testDebugUnitTest koverVerifyDebug   # pruebas + umbral de cobertura
./gradlew assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                          # instala en el dispositivo conectado por adb
```
