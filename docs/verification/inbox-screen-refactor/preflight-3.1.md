# Registro de Preflight — Subfase 3.1: Placeholders

- **HEAD base**: `427e943b185ec157a9775086ee8670ebffaf8eb2`
- **Commit anterior aprobado**: `427e943 revert(inbox): preserve approved inbox stages`

## Archivos Permitidos (Allowlist)
- `app/src/main/java/com/david/mailapp/feature/inbox/InboxPlaceholders.kt`
- `app/src/main/java/com/david/mailapp/feature/inbox/InboxStatePlaceholders.kt` (eliminación)
- `docs/verification/inbox-screen-refactor/preflight-3.1.md` (este archivo)
- `docs/verification/inbox-screen-refactor/resultados-subfase-3.1.md`

## Archivos Ajenos Protegidos (Prohibido modificar/commitear)
- `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt`
- `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt`
- `gradle.properties`

## Contratos Afectados
- `InboxErrorContent`: Presentación estática de error recuperable con reintento.

## Movimiento de Responsabilidades
- Mover de forma literal `InboxErrorContent` desde `InboxStatePlaceholders.kt` a `InboxPlaceholders.kt`.
- Eliminar `InboxStatePlaceholders.kt` tras confirmar que no quedan referencias.

## Pruebas y Comandos de Verificación
1. Compilación: `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks`
2. Pruebas JVM focales de Inbox: `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks`
3. Pruebas instrumentadas Compose focales: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.inbox.InboxContentCharacterizationTest`
4. Análisis estático y diff: `git diff --check` y comparación de hashes de los archivos protegidos.

## Criterios GO/NO-GO
- **GO**:
  - Compilación exitosa de app y tests.
  - 28/28 tests JVM aprobados.
  - 5/5 tests instrumentados Compose aprobados en el dispositivo/emulador conectado.
  - `InboxErrorContent` invocado exactamente igual desde `InboxContent.kt` (mismo reason, onRefresh y tag `inbox_error`).
  - Cero cambios ajenos introducidos en los archivos protegidos.
- **NO-GO**:
  - Cualquier fallo en compilación o ejecución de pruebas.
  - Modificación o inclusión de archivos fuera de la allowlist en el commit final.
