# TINT · App Android

Cliente móvil nativo (Kotlin + Jetpack Compose) de TINT. Usa los mismos endpoints REST y WebSocket que la web; el backend no cambia ([ADR 0015](../../docs/adr/0015-app-android-nativa-kotlin.md)). Guía para agentes y contratos: [`AGENTS.md`](AGENTS.md).

## Estado
| Ticket | Alcance | Estado |
| :--- | :--- | :--- |
| AND-08 | Cliente REST y URLs compartidas con la web | ✅ Esqueleto (`ApiClient`, `Endpoints`) |
| AND-09 | APK instalable | ✅ `./gradlew assembleDebug` |
| AND-01 | Login con Google (Firebase Auth + Credential Manager) | ✅ |
| AND-02 | Unirse por link o código (deep links `tint://invite/<código>` y `https://<host>/invite/<código>`) | ✅ |
| AND-03 | Reproducción sincronizada (Canal Sync v2 + Media3) | ✅ Verificado en una tablet contra el servidor desplegado (ver «Observar la sincronía») |
| AND-04 | Chat y reacciones (Canal Social) | ✅ Verificado en una tablet contra el servidor desplegado, con un host real en el navegador (ver «Chat y reacciones») |
| AND-05 | Trivia (pregunta con cuenta regresiva, respuesta, puntaje y ranking) | ✅ Verificado en una tablet contra el servidor desplegado con un host real (ver «Trivia») |
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

Desde la raíz del monorepo: `make test-android` (las pruebas y el umbral de cobertura; no forma parte de `make test-all`).

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

## Observar la sincronía
En el build `debug` la sala escribe una línea por segundo en logcat: `adb logcat -s TintSync` →
`drift=-47 speed=2.0 advancing=true room=PLAYING@2.0 srvDrift=-96 rtt=59` (`drift` es el desfase del reproductor
frente a la sala, `srvDrift` el que mide sync-core, negativo = atrasado). Medido con una película de 1,5 GB en una
tablet contra el servidor desplegado: a 1× el desfase se mantuvo en −282 ± 4 ms durante más de un minuto y a 2×
en −41 a −62 ms, siempre bajo el umbral de corrección de 500 ms. Dos efectos conocidos:

- **Al unirse** el reproductor tarda unos 8 s en tener datos del punto de la sala (el desfase llega a −6 s) y se
  pone al día con las correcciones `SEEK` del servidor.
- **Cada cambio de velocidad** cuesta ~0,3 s (el audio recrea su pista); el servidor corrige con `RATE` (2,1×) y
  vuelve a −50 ms en ~6 s. Tras una corrección `SEEK` el desfase se queda en ≈ −280 ms; tras una `RATE`, en ≈ −50 ms.

### Chat y reacciones (AND-04)
Probado entre un host (Chromium controlado con Playwright) y la tablet, en los dos sentidos: los mensajes y las
reacciones viajan con el eco del servidor (el propio mensaje vuelve alineado a la derecha porque el `sub` del token
coincide), la reacción lleva la posición del vídeo (`position_ms`), y al reabrir la app aparece el historial. Con el
**modo lento** del host en 10 s, el segundo mensaje no sale mientras corre la cuenta regresiva (el botón muestra `5s`)
y el host solo recibe el primero; el segundo llega cuando termina. Con el **silencio**, el campo dice «El host te
silenció; puedes leer el chat.» y el botón queda deshabilitado; al quitarlo todo vuelve a la normalidad. Los
**spoilers** llegan ocultos y se revelan al tocarlos.

Dos cosas a saber: cada vez que alguien canjea una invitación recibe un `sub` nuevo, así que tus mensajes de una
sesión anterior aparecen como de otra persona; y el borrador del campo se conserva si la pantalla se recrea.

### Trivia (AND-05)
Con el host programando las preguntas desde la web (Playwright) y la tablet respondiendo: la pregunta **aparece en la
tablet cuando el vídeo llega a la marca** (programada ~20 s adelante, apareció a los 20 s); al responder se muestra
«¡Correcto! +1443 puntos» (1000 más el bono por rapidez, con el puntaje que devuelve el `trivia_ack`); al cerrar la
ventana se ve la respuesta correcta, «¡Acertaste! Tus puntos: 1443», el total en la sala y el ranking. Si no se
responde, la pregunta cierra con «Se acabó el tiempo sin responder: 0 puntos» y la respuesta correcta.

La cuenta regresiva parte del reloj local al llegar la pregunta y dura `closes_at_ms − opens_at_ms` (nunca se comparan
relojes); una pregunta repetida al reconectar no reinicia la respuesta ni la cuenta. **Diferencia con la web:** la web
oculta si acertaste hasta el cierre; el ticket AND-05 pide verlo al responder y el servidor ya devuelve el puntaje en el
`trivia_ack`, así que la app lo muestra enseguida.

### Latencia host → tablet (AC1)
Con Playwright controlando la sala del host (Chromium con `--remote-debugging-port`) y la tablet escribiendo una
línea `apply …` al aplicar cada estado, ambos con el reloj del mismo PC: 5 ciclos de play, seek +10 s, velocidad
1,5×, velocidad 1× y pausa (25 comandos). **Los 25 llegaron** a la tablet; desde el clic del host hasta que la tablet
empieza a aplicar el estado, mediana **92 ms**, p95 117 ms, máximo 136 ms (cota superior: incluye el tiempo del
propio clic de Playwright). El criterio de AC1 es 1 s. No mide el cambio audible, que suma decenas de ms de ExoPlayer.

Efecto secundario medido en esa misma prueba: **casi cada comando deja a la tablet > 500 ms atrás un instante**
(25 comandos → 29 correcciones `RATE` del servidor, de −503 a −1109 ms) y tras un seek el desfase se queda en
−360 a −390 ms. Cada cambio de velocidad recrea la pista de audio (~0,3 s), así que una corrección por velocidad
introduce justo el retraso que intenta quitar. Mejora posible, sin hacer: compensar la latencia del seek (saltar a
`esperada + latencia medida`) en vez de corregir por velocidad.

## Probar con un tamaño de teléfono en una tablet
`adb shell wm size 1200x600` deja la pantalla como un teléfono vertical de 600×1200 px cuando la tablet está en horizontal. Para volver al tamaño normal: `adb shell wm size reset && adb shell wm density reset`.
