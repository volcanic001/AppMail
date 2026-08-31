# Registro de Preflight — Subfase 3.2: Barra superior

- **HEAD base**: `df54203docs(inbox): add placeholder visual evidence`
- **Commit anterior aprobado**: `df54203 docs(inbox): add placeholder visual evidence`

## Archivos Permitidos (Allowlist)
- `app/src/androidTest/java/com/david/mailapp/feature/inbox/InboxContentCharacterizationTest.kt`
- `docs/verification/inbox-screen-refactor/preflight-3.2.md` (este archivo)
- `docs/verification/inbox-screen-refactor/resultados-subfase-3.2.md`
- `docs/verification/inbox-screen-refactor/capturas/3.2/` (capturas visuales del baseline/post si corresponde)

## Archivos Ajenos Protegidos (Prohibido modificar/commitear)
- `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt`
- `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt`
- `gradle.properties`

## Contratos Afectados
- Click en menú (despacho de callback `onMenuClick`).
- Click en búsqueda (despacho de callback `onSearchClick` inmediatamente, iniciando la animación de escala sin esperarla).

## Movimiento de Responsabilidades
- Ninguno. La barra superior ya está extraída en `InboxTopBar.kt`. Se realiza una auditoría y se agrega el test de caracterización Compose correspondiente.

## Pruebas y Comandos de Verificación
1. Compilación: `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks`
2. Pruebas JVM focales: `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks`
3. Pruebas instrumentadas: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.inbox.InboxContentCharacterizationTest`
4. Análisis estático y hashes: `git diff --check` y verificación de hashes de los archivos protegidos.

## Criterios GO/NO-GO
- **GO**:
  - Auditoría confirma coincidencia literal de la barra superior.
  - El test instrumentado cubre los clicks de menú y búsqueda.
  - La compilación y todas las pruebas JVM (28/28) e instrumentadas pasan sin problemas.
  - Los hashes de los archivos protegidos coinciden al 100%.
- **NO-GO**:
  - Cualquier error de compilación o prueba fallida.
  - Cambios no autorizados fuera de los archivos permitidos.
