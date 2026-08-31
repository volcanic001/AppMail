# Resultados — Subfase 1.3: baseline técnico

Fecha: 2026-08-30 23:01:14 -0600 (CST)

## Comandos ejecutados

| Comando | Resultado |
|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks --console=plain` | BUILD SUCCESSFUL; 593/593 |
| `./gradlew compileDebugAndroidTestKotlin assembleDebug assembleDebugAndroidTest lintDebug --rerun-tasks --console=plain` | BUILD SUCCESSFUL |
| `git diff --check` | limpio |
| `adb devices -l` | sin dispositivos conectados |

## Suites de Inbox

Las clases JVM existentes de `com.david.mailapp.feature.inbox` suman 28 pruebas:

- `ActionFeedbackTest`: 2
- `InboxContractsTest`: 7
- `InboxRefreshCoordinationTest`: 3
- `InboxViewModelActionTest`: 13
- `InboxViewModelRefreshTokenTest`: 3

Resultado: **28/28**, 0 fallos, 0 errores, 0 omitidas.

## Instrumentación

No se ejecutó `connectedDebugAndroidTest` ni pruebas en Pixel porque el host no tenía ningún dispositivo ADB conectado durante la captura. Debe repetirse en la siguiente puerta con un emulador y un Pixel disponibles.

## Decisión

La evidencia JVM/build/lint es GO. La evidencia instrumentada queda pendiente y bloquea la declaración de baseline completo, pero no bloquea documentar 1.1–1.3 como ejecutadas parcialmente.
