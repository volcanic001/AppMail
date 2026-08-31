# Resultados — Subfase 4.2: Lista y filas

Fecha: 2026-08-31 (CST)

## Cambios realizados
- **Extracción de `InboxEmailList.kt`**: Se creó el composable interno `InboxEmailList` que aloja exclusivamente:
  - `LazyColumn` con la lista de correos y sus transformaciones `animateItem`.
  - Recordatorio de callbacks por `email.id` para click y eliminación.
  - Cálculo de habilitación de acciones (`actionsEnabled = email.id !in state.activeActionEmailIds`).
  - Renderizado de ítem vacío con clave `"empty"` y loader de página siguiente con clave `"loader"` (`inbox_next_page_loader`).
- **Integración en `InboxContent.kt`**: En el slot de `InboxSuccessContent`, se delegó la presentación de la lista a `InboxEmailList`, conservando el `LaunchedEffect` de paginación literalmente en `InboxContent`.
- **Ampliación de Caracterización Instrumental**:
  - `visual_order_of_emails_matches_input_list`: Verifica que el orden de renderizado visual coincida con la lista recibida, comparando los límites verticales en raíz de `e1` y `e2` (`top(e1) < top(e2)`).
  - `swipe_left_on_email_row_dispatches_move_to_trash`: Valida que el gesto de swipe a la izquierda despache `onMoveToTrash` con el ID correcto.
  - `active_action_email_row_disables_interactions_while_other_row_remains_operable`: Confirma que una fila en `activeActionEmailIds` deshabilite interacciones mientras otra fila permanece operable.
  - `is_loading_next_page_renders_loader_item`: Comprueba que `isLoadingNextPage` muestre el indicador con tag `inbox_next_page_loader`.
  - `highlight_row_clears_internally_after_800ms`: Valida la limpieza interna del resaltado de fila a 800 ms.

## Verificación

| Verificación | Comando / Acción | Resultado |
|---|---|---|
| Compilación completa | `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks` | exit 0 (BUILD SUCCESSFUL) |
| JVM focales Inbox | `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks` | 28/28 APROBADOS (exit 0) |
| Pruebas gestos de fila | `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.inbox.components.EmailListItemGestureTest` | APROBADO en Emulator y Pixel 9 |
| Tests instrumentados Compose (Pase 1) | `./gradlew :app:connectedDebugAndroidTest` | 17/17 APROBADOS en Emulator y Pixel 9 |
| Tests instrumentados Compose (Pase 2) | `./gradlew :app:connectedDebugAndroidTest` | 17/17 APROBADOS en Emulator y Pixel 9 |
| Tests instrumentados Compose (Pase 3) | `./gradlew :app:connectedDebugAndroidTest` | 17/17 APROBADOS en Emulator y Pixel 9 |
| Git diff check | `git diff --check` | Limpio (exit 0) |

## Evidencia visual comparativa (Baseline c2f5639 vs Post)

Se ejecutaron capturas canónicas con reloj congelado en escenarios con divisores habilitados y deshabilitados:

| Dispositivo | Escenario | Dimensiones | Hash SHA-256 baseline = post | PNG |
|---|---|---:|---|---|
| Medium_Phone_API_36.1 (API 36) | Dividers Enabled | 1080×2400 | `cefbc45b384813651cb295ca9762d9358ed82e4121f8f16ebccc8c8fcc6cac4f` | idéntico |
| Medium_Phone_API_36.1 (API 36) | Dividers Disabled | 1080×2400 | `b81de06f7ce267f28d7811bb05994f73236ac73d9f434e5fa477a01136dc7803` | idéntico |
| Pixel 9 (API 37) | Dividers Enabled | 1080×2424 | `210ecbce24fa15fc66120bed985f7377fb08132808ece2b9602deea1f3d67909` | idéntico |
| Pixel 9 (API 37) | Dividers Disabled | 1080×2424 | `9776a544415cf1857d1b07c89a814246695c8e9f08fe8ab88386cf6263e42afd` | idéntico |

- **Diferenciación de Escenarios**:
  - Emulador: `dividers_enabled` (`cefbc45b...`) ≠ `dividers_disabled` (`b81de06f...`)
  - Pixel 9: `dividers_enabled` (`210ecbce...`) ≠ `dividers_disabled` (`9776a544...`)

Las capturas PNG y los manifiestos `.tsv` se almacenaron en `docs/verification/inbox-screen-refactor/capturas/4.2/`.

## Integridad de Archivos Ajenos Protegidos

Los hashes SHA-1 de los archivos ajenos coinciden exactamente con el baseline original:
- `ComposeScreen.kt`: `70334824e407daf48f6e1ec445e7114722c6e1eb` (OK)
- `MainNavHost.kt`: `411373faa8cd6f32c98c708830bd494f9aab40b8` (OK)
- `gradle.properties`: `49a749189f38ad45b2bec7aca6a7bf865cc932f5` (OK)

## Criterio de salida
**GO.** El componente `InboxEmailList.kt` encapsula fielmente la lista y sus filas sin regresiones visuales ni de comportamiento, pasando 100% de verificaciones y manteniendo intactos los archivos protegidos.
