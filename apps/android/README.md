# TINT · App Android

Cliente móvil nativo (Kotlin + Jetpack Compose) de TINT. Usa los mismos endpoints REST y WebSocket que la web; el backend no cambia ([ADR 0015](../../docs/adr/0015-app-android-nativa-kotlin.md)). Guía para agentes y contratos: [`AGENTS.md`](AGENTS.md).

## Estado
| Ticket | Alcance | Estado |
| :--- | :--- | :--- |
| AND-08 | Cliente REST y URLs compartidas con la web | ✅ Esqueleto (`ApiClient`, `Endpoints`) |
| AND-09 | APK instalable | ✅ `./gradlew assembleDebug` |
| AND-01 | Login con Google (Firebase Auth + Credential Manager) | ✅ |
| AND-02 | Unirse por link o código (deep links `tint://invite/<código>` y `https://<host>/invite/<código>`) | ✅ |
| AND-03 | Reproducción sincronizada | ⏳ |
| AND-04 | Chat y reacciones | ⏳ |
| AND-05 | Trivia | ⏳ |
| AND-06 · AND-07 | Transmisión en vivo y voz | ⏳ Condicionado a que exista su backend |

## Requisitos
- **JDK 17** (Gradle/AGP no soportan JDK 26). Apúntalo con `JAVA_HOME`.
- **Android SDK** con `platforms;android-35` y `build-tools` (`ANDROID_HOME`, o `sdk.dir` en `local.properties`, que no se versiona).
- Un dispositivo o emulador con Android 8.0 (API 26) o superior.

## Configuración de Firebase
El login usa el mismo proyecto Firebase que la web (`tint-login`). Descarga el `google-services.json` de la app Android (`com.tint.app`) desde la consola de Firebase y guárdalo en `apps/android/app/google-services.json` (no se versiona). La app debe tener registrado el SHA-1 del keystore con el que firmas (para debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`). Sin el archivo real, copia `app/google-services.json.example` para poder compilar (el login no funcionará).

## Compilar y probar
```bash
cd apps/android
./gradlew testDebugUnitTest koverVerifyDebug   # pruebas y cobertura (≥ 85 %)
./gradlew assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
```

## Apuntar la app al backend
Las URLs viven en `gradle.properties` y se pueden sobrescribir al compilar:

```bash
./gradlew assembleDebug \
  -PtintIdentityApiUrl=https://mi-host/api/v1 \
  -PtintSyncWsUrl=wss://mi-host/ws/sync \
  -PtintSocialWsUrl=wss://mi-host/ws/social
```

**En un dispositivo físico con el backend local** (`docker compose up -d`), no hace falta cambiar nada: redirige los puertos por adb y `localhost` llegará al PC.

```bash
adb connect <ip>:<puerto>            # depuración inalámbrica (o USB)
adb reverse tcp:8081 tcp:8081        # identity-api
adb reverse tcp:8082 tcp:8082        # social-core
adb reverse tcp:8083 tcp:8083        # sync-core
./gradlew installDebug
```

En un emulador, `localhost` es el propio emulador: usa `10.0.2.2` en las tres propiedades.

**Links de invitación:** la app abre `tint://invite/<código>` y `https://<tintInviteHost>/invite/<código>` (host por defecto `tint.jaucor.online`, propiedad `tintInviteHost`). Sin `assetlinks.json` en ese dominio, Android no los verifica: al tocar un link `https` ofrece elegir entre el navegador y la app. Para probar sin dominio: `adb shell am start -a android.intent.action.VIEW -d "tint://invite/<código>" com.tint.app`.

> El tráfico sin TLS (`http`/`ws`) solo está permitido en el build `debug`.

## Probar con un tamaño de teléfono en una tablet
`adb shell wm size 1200x600` deja la pantalla como un teléfono vertical de 600×1200 px cuando la tablet está en horizontal. Para volver al tamaño normal: `adb shell wm size reset && adb shell wm density reset`.
