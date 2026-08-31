# Baseline técnico — InboxScreen

## Estado

- Alcance: subfases 1.1, 1.2 y 1.3 del refactor estructural.
- Fecha de captura: 2026-08-30 23:01:14 -0600 (CST).
- HEAD de referencia: `7dd89d3 docs(emailbody): complete structural refactor handoff`.
- Rama: `main`.
- Archivo principal: `app/src/main/java/com/david/mailapp/feature/inbox/InboxScreen.kt`.
- Tamaño: 434 líneas.
- SHA-256 de `InboxScreen.kt`: `9584c841659f808e9fe84a75b3cbf549928f7bf7927430f8b3d37456d52802cf`.

## Cambios ajenos protegidos

El working tree ya contenía cambios antes de iniciar estas subfases. Se excluyen de la allowlist y no deben entrar en el commit del baseline:

- `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt`
- `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt`
- `gradle.properties`

Hashes registrados en el preflight:

| Archivo | SHA-256 |
|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691e0e7d442a9b1a73c62c1d0a32c53bc3703469f5e69` |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` |

## Allowlist del trabajo

- `docs/verification/inbox-screen-baseline/**`
- `docs/verification/inbox-screen-refactor/**` (reservado para etapas posteriores)
- `app/src/main/java/com/david/mailapp/feature/inbox/**` (solo durante el refactor futuro)
- `app/src/test/java/com/david/mailapp/feature/inbox/**` (solo pruebas futuras)
- `app/src/androidTest/java/com/david/mailapp/feature/inbox/**` (solo pruebas futuras)

No se autoriza `git add .`, cambios en Gradle, recursos, navegación, DI, ViewModel, UiState, `EmailListItem` ni componentes compartidos.

## Resultado de subfase 1.1

Estado: **GO documental**.

- El punto de partida quedó identificado.
- Los cambios ajenos quedaron delimitados.
- La allowlist quedó definida.
- No se modificó código productivo ni de pruebas durante el preflight.

## Resultado de subfase 1.3

Estado: **GO técnico parcial**.

- Baseline JVM focal de Inbox: **28/28**, sin fallos, errores ni omitidas.
- Suite JVM completa: **593/593**, sin fallos, errores ni omitidas.
- `compileDebugKotlin`: BUILD SUCCESSFUL.
- `compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- `assembleDebug`: BUILD SUCCESSFUL.
- `assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- `lintDebug`: BUILD SUCCESSFUL, sin errores; se conservan warnings preexistentes.
- `git diff --check`: sin salida, por tanto limpio.
- APK debug generado: `app/build/outputs/apk/debug/app-debug.apk` (25,642,958 bytes).
- APK androidTest generado: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` (1,518,363 bytes).

La instrumentación en emulador y Pixel no se ejecutó porque `adb devices -l` no devolvió dispositivos conectados. Esto queda como **NO-GO de hardware**, no como fallo del código. La subfase 1.3 no debe declararse completamente cerrada hasta repetir la batería instrumentada con ambos dispositivos disponibles.

### Actualización de instrumentación (2026-08-30)

El Pixel 9 apareció posteriormente como `adb-55080DLAQ002CK-0Wyjbr._adb-tls-connect._tcp`.

- Primera corrida de `EmailListItemGestureTest`: fallo de infraestructura (`No compose hierarchies found in the app`), sin aserción funcional ejecutada.
- Segunda corrida aislada: **1/1 aprobada**.
- Tercera corrida aislada: **1/1 aprobada**.
- El emulador continúa sin estar conectado.

El fallo inicial se conserva como evidencia de inestabilidad transitoria del entorno y no se atribuye al código. El gate de hardware sigue parcial porque falta la corrida equivalente en emulador.

## Warnings observados

Los warnings pertenecen a deprecaciones y anotaciones existentes en distintas áreas del proyecto (Compose/Lifecycle, WebView, Kotlin y tests). No se corrigieron ni atribuyeron al refactor de InboxScreen.
