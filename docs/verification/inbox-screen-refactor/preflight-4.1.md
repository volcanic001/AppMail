# Registro de Preflight — Subfase 4.1: Pull-to-refresh

- **HEAD base**: `c2f5639`
- **Commit anterior aprobado**: `c2f5639 refactor(inbox): characterization and consolidation of container and transversal effects`

## Archivos Permitidos (Allowlist)
- `app/src/main/java/com/david/mailapp/feature/inbox/InboxContent.kt`
- `app/src/main/java/com/david/mailapp/feature/inbox/InboxSuccessContent.kt` (nuevo archivo)
- `app/src/androidTest/java/com/david/mailapp/feature/inbox/InboxContentCharacterizationTest.kt`
- `docs/verification/inbox-screen-refactor/preflight-4.1.md` (este archivo)
- `docs/verification/inbox-screen-refactor/resultados-subfase-4.1.md`
- `docs/verification/inbox-screen-refactor/capturas/4.1/` (capturas visuales baseline y post)

## Archivos Ajenos Protegidos (Prohibido modificar/commitear)
- `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt`
- `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt`
- `gradle.properties`

## Contratos Afectados y Extraídos
- Creación de `InboxSuccessContent.kt` para alojar `PullToRefreshBox`, `rememberPullToRefreshState()` y el indicador personalizado con tag `inbox_refresh_indicator`.
- Fórmulas de transformación del indicador idénticas:
  - Visibilidad: `isRefreshing || ptrState.distanceFraction > 0f`
  - Fracción: `coerceIn(0f, 1.5f)`
  - Traslación Y: `24.dp` si refresca, `fraction * 40.dp` si no.
  - Escala: `1f` si refresca, `(fraction * 1.2f).coerceIn(0f, 1f)` si no.
  - Alpha: `1f` si refresca, `fraction.coerceIn(0f, 1f)` si no.
  - `ContainedLoadingIndicator`: contenedor `48.dp`, indicador `32.dp`, progreso `null` en refresh o fracción `0f..1f`.
- `LazyColumn`, filas, empty/loader y paginación se conservan dentro de `InboxContent.kt` encapsulados en el slot `content` de `InboxSuccessContent`.

## Pruebas y Comandos de Verificación
1. Compilación: `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks`
2. Pruebas JVM focales Inbox: `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks`
3. Pruebas instrumentadas Compose: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.inbox.InboxContentCharacterizationTest`
4. Capturas canónicas: Comparación baseline/post para estado de refresh poblado y vacío en emulador y Pixel 9.
5. Análisis estático y hashes: `git diff --check` y comparación de hashes de archivos protegidos.

## Criterios GO/NO-GO
- **GO**:
  - `InboxSuccessContent.kt` extraído mecánicamente y conectado en `InboxContent.kt`.
  - Red Compose ampliada para validar refresh en bandeja vacía y gesto pull-to-refresh.
  - Compilación exitosa, 28/28 tests JVM aprobados, suite instrumentada aprobada en repeticiones exigidas.
  - Capturas visuales baseline y post con coincidencia de dimensiones y hash SHA-256.
  - Hashes de los 3 archivos protegidos idénticos al baseline.
- **NO-GO**:
  - Cualquier extracción anticipada de listas o paginación, fallo de tests o regresión visual.
