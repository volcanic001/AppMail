# Resultados — Subfase 4.1: Pull-to-refresh

Fecha: 2026-08-31 (CST)

## Cambios realizados
- **Extracción de `InboxSuccessContent.kt`**: Se creó el composable interno `InboxSuccessContent` que encapsula exclusivamente:
  - `rememberPullToRefreshState()`.
  - `PullToRefreshBox` con su contenedor e indicador personalizado (`inbox_refresh_indicator`).
  - La preservación estricta de las transformaciones: visibilidad (`isRefreshing || ptrState.distanceFraction > 0f`), clamp en `0f..1.5f`, traslación Y (`24.dp` en refresh / `fraction * 40.dp`), escala (`1f` / `(fraction * 1.2f).coerceIn(0f, 1f)`), alpha (`1f` / `fraction.coerceIn(0f, 1f)`), contenedor de `48.dp`, indicador de `32.dp` y progreso nulo durante el refresco.
- **Integración en `InboxContent.kt`**: En el estado `InboxUiState.Success`, se envolvió `LazyColumn` dentro de `InboxSuccessContent`. La lista, filas, ítems de empty/loader y el efecto de paginación se conservaron literalmente sin extraer.
- **Ampliación de Caracterización Instrumental**:
  - `refreshing_empty_state_renders_indicator_list_and_empty_item`: Valida la coexistencia simultánea de `inbox_list`, `inbox_empty` e `inbox_refresh_indicator`.
  - `pull_to_refresh_gesture_on_empty_state_triggers_refresh_callback`: Valida que el gesto de swipe hacia abajo en la bandeja vacía despacha exactamente una vez el callback `onRefresh()`.

## Verificación

| Verificación | Comando / Acción | Resultado |
|---|---|---|
| Compilación completa | `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks` | exit 0 (BUILD SUCCESSFUL) |
| JVM focales Inbox | `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks` | 28/28 APROBADOS (exit 0) |
| Tests instrumentados Compose (Pase 1) | `./gradlew :app:connectedDebugAndroidTest` | 12/12 APROBADOS en Emulator y Pixel 9 (exit 0) |
| Tests instrumentados Compose (Pase 2) | `./gradlew :app:connectedDebugAndroidTest` | 12/12 APROBADOS en Emulator y Pixel 9 (exit 0) |
| Tests instrumentados Compose (Pase 3) | `./gradlew :app:connectedDebugAndroidTest` | 12/12 APROBADOS en Emulator y Pixel 9 (exit 0) |
| Git diff check | `git diff --check` | Limpio (exit 0) |

## Evidencia visual comparativa (Baseline c2f5639 vs Post)

Se ejecutaron capturas canónicas con reloj congelado y avance de exactamente 16 ms en escenarios independientes montados por separado (`capture_populated` y `capture_empty`):

| Dispositivo | Escenario | Dimensiones | Hash SHA-256 baseline = post | PNG |
|---|---|---:|---|---|
| Medium_Phone_API_36.1 (API 36) | Refreshing Populated | 1080×2400 | `0e1bd85f8f90045b852a18cc583a1f0b07ef8027fd2481cba8f55be56de26203` | idéntico |
| Medium_Phone_API_36.1 (API 36) | Refreshing Empty | 1080×2400 | `8bcd508803714e2a1dc61c9ff3dc6c3c8aaafc77933ee7ae700b249ff982f707` | idéntico |
| Pixel 9 (API 37) | Refreshing Populated | 1080×2424 | `592e71a447ea2927efff0369f68aece387f8c4b6fcbd4b197c3cd2445855c40e` | idéntico |
| Pixel 9 (API 37) | Refreshing Empty | 1080×2424 | `dbc498edaaf5d77648310c830f3b27ff273fee701934a67ae776ee453918579d` | idéntico |

- **Verificación de Diferenciación de Escenarios**:
  - Emulador: `refreshing_empty` (`8bcd5088...`) ≠ `refreshing_populated` (`0e1bd85f...`)
  - Pixel 9: `refreshing_empty` (`dbc498ed...`) ≠ `refreshing_populated` (`592e71a4...`)
- **Inspección Visual**:
  - Escenario poblado muestra las 2 tarjetas de email fixtures y la barra de refresco superior.
  - Escenario vacío muestra la ilustración de bandeja vacía y la barra de refresco superior.

Las capturas PNG y los manifiestos `.tsv` se almacenaron en `docs/verification/inbox-screen-refactor/capturas/4.1/`.

## Integridad de Archivos Ajenos Protegidos

Los hashes SHA-1 de los archivos ajenos coinciden exactamente con el baseline original:
- `ComposeScreen.kt`: `70334824e407daf48f6e1ec445e7114722c6e1eb` (OK)
- `MainNavHost.kt`: `411373faa8cd6f32c98c708830bd494f9aab40b8` (OK)
- `gradle.properties`: `49a749189f38ad45b2bec7aca6a7bf865cc932f5` (OK)

## Criterio de salida
**GO.** El componente `InboxSuccessContent.kt` encapsula fielmente el pull-to-refresh sin regresiones visuales ni de comportamiento, pasando 100% de verificaciones y manteniendo intactos los archivos protegidos.
