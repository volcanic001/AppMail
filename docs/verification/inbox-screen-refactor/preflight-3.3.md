# Registro de Preflight — Subfase 3.3: Contenedor y efectos transversales

- **HEAD base**: `d053da1`
- **Commit anterior aprobado**: `d053da1 test(inbox): refine topbar characterization test and update 3.2 documentation`

## Archivos Permitidos (Allowlist)
- `app/src/main/java/com/david/mailapp/feature/inbox/InboxContent.kt` (solo si corrección mecánica resulta imprescindible)
- `app/src/androidTest/java/com/david/mailapp/feature/inbox/InboxContentCharacterizationTest.kt`
- `docs/verification/inbox-screen-refactor/preflight-3.3.md` (este archivo)
- `docs/verification/inbox-screen-refactor/resultados-subfase-3.3.md`

## Archivos Ajenos Protegidos (Prohibido modificar/commitear)
- `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt`
- `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt`
- `gradle.properties`

## Contratos Auditados y Congelados
- `ActionFeedbackEffect`: Ejecutado antes de los demás efectos con clave `feedback?.id`.
- Fallback de highlight: `LaunchedEffect(highlightedEmailId)`, espera de 2500 ms y llamada a `onClearHighlight()`.
- Cálculo de `isRefreshing = (uiState as? Success)?.isRefreshing ?: false` fuera del bloque `Success`.
- Transición tras refresh: `LaunchedEffect(isRefreshing)`, registra inicio si `index == 0 && offset < 50`, espera 100 ms al finalizar para `scrollToItem(0, 0)` y resetea estado.
- `Scaffold` y `SnackbarHost`: Contenedor principal y SnackbarHost en `Alignment.BottomCenter` con `padding(bottom = 24.dp)` común a Loading, Error y Success.

## Pruebas y Comandos de Verificación
1. Compilación: `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks`
2. Pruebas JVM focales Inbox: `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks`
3. Pruebas instrumentadas Compose: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.inbox.InboxContentCharacterizationTest`
4. Análisis estático y hashes: `git diff --check` y comparación de SHA-1 de archivos protegidos.

## Criterios GO/NO-GO
- **GO**:
  - `InboxContent.kt` preserva el orden, claves, delays y literales exactos de los efectos transversales.
  - Se agregan las pruebas para: fallback de highlight a 2500 ms sin fila presente, límites de offset (49 resetea, 50 no), consumo/undo de feedback y presencia de contenedor/SnackbarHost en todos los estados.
  - Compilación exitosa, 28/28 pruebas JVM aprobadas y suite instrumentada aprobada en emulador.
  - Hashes de los 3 archivos protegidos idénticos al baseline.
- **NO-GO**:
  - Cualquier regresión o cambio no autorizado en `PullToRefreshBox`, `LazyColumn`, paginación o archivos ajenos.
