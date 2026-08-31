# Resultados — Subfase 2.1: seam y caracterización Compose

Fecha: 2026-08-31 (CST)

## Cambios realizados

- `InboxScreen.kt` quedó como fachada pública de 51 líneas.
- Se extrajo `InboxContent` como seam `internal` con estado y callbacks explícitos.
- Se extrajeron `EmptyInbox`, `ShimmerLoading` y `ShimmerRow` a `InboxPlaceholders.kt`.
- Se conservaron firma pública, ViewModel, DI, callbacks, efectos, claves Compose, delays, animaciones y valores de layout.
- Se añadieron únicamente tags no visuales para caracterización: `inbox_root`, `inbox_loading`, `inbox_error`, `inbox_list`, `inbox_empty`, `inbox_refresh_indicator` e `inbox_next_page_loader`.

No se modificaron `InboxViewModel`, `InboxUiState`, `EmailListItem`, navegación, DI, recursos ni Gradle.

## Verificación

| Verificación | Resultado |
|---|---|
| `compileDebugKotlin --rerun-tasks` | BUILD SUCCESSFUL |
| `compileDebugAndroidTestKotlin --rerun-tasks` | BUILD SUCCESSFUL |
| `testDebugUnitTest --tests com.david.mailapp.feature.inbox.* --rerun-tasks` | **28/28**, BUILD SUCCESSFUL |
| `InboxContentCharacterizationTest` en `emulator-5554` | **5/5**, BUILD SUCCESSFUL |
| `InboxContentCharacterizationTest` en Pixel 9 | **5/5**, BUILD SUCCESSFUL |
| `git diff --check` | limpio |

La primera compilación de la prueba detectó imports incompatibles (`assertExists` y `performClick`); se corrigieron únicamente en el test y la compilación posterior fue verde. La primera ejecución de la clase detectó una expectativa textual incorrecta (`Bandeja de entrada`); el recurso vigente es `Bandeja`. Tras corregir la aserción, ambos dispositivos pasaron 5/5.

## Criterio de salida

**GO.** El seam mínimo y la caracterización Compose están listos para la subfase 2.2. La red cubre Loading, Error/Retry, Empty, Success/click y Success/refresh-indicator; la matriz completa de 32 contratos seguirá ampliándose en las subfases de verificación previstas.
