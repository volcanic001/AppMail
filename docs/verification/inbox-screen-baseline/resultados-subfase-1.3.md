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

- Primera corrida en Pixel 9: error transitorio `No compose hierarchies found in the app`, sin aserción funcional.
- Dos corridas posteriores en Pixel 9: **1/1 aprobadas** cada una.
- Corrida en `emulator-5554` (Medium_Phone_API_36.1): **1/1 aprobada**.

La evidencia instrumental focal queda verde en ambos dispositivos. El fallo inicial se conserva como incidencia transitoria de infraestructura y no se atribuye al código.

## Decisión

La evidencia JVM/build/lint e instrumentación focal es GO. La subfase 1.3 queda cerrada para su alcance; la suite instrumentada completa pertenece a las etapas de verificación posteriores.
