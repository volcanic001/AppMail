# Resultados — Subfase 3.3: Contenedor y efectos transversales

Fecha: 2026-08-31 (CST)

## Cambios realizados
- **Auditoría e Inmutabilidad de `InboxContent.kt`**: Se confirmó mediante inspección estática que `InboxContent.kt` conserva literalmente:
  - `ActionFeedbackEffect` como primer efecto transversal, asociando la clave de feedback `feedback?.id`.
  - Fallback de highlight con `LaunchedEffect(highlightedEmailId)`, retardo de 2500 ms y despacho único a `onClearHighlight()`.
  - Cálculo de `isRefreshing = (uiState as? Success)?.isRefreshing ?: false` fuera del bloque condicional `Success`.
  - Transición y reseteo tras refresh con `LaunchedEffect(isRefreshing)`, captura de inicio con `index == 0 && offset < 50`, retardo de 100 ms tras finalizar y llamada condicional a `scrollToItem(0, 0)`.
  - `Scaffold` como contenedor raíz (`inbox_root`) y `SnackbarHost` alineado al fondo (`BottomCenter`, `padding(bottom = 24.dp)`) fuera del bloque `when (uiState)` para coexistir en Loading, Error y Success.
  - No se adelantó ninguna extracción de `PullToRefreshBox`, `LazyColumn` ni paginación (reservadas para la Etapa 4).
- **Ampliación de Caracterización Compose**:
  - `highlight_fallback_clears_after_2500ms_without_row`: Valida con reloj controlado que el fallback se activa exactamente a los 2500 ms cuando no hay fila que lo limpie.
  - `refresh_transition_resets_scroll_only_when_started_below_offset_50`: Valida que con inicio en offset 49 el scroll resetea a (0, 0) tras refresh.
  - `refresh_transition_does_not_reset_scroll_when_started_at_offset_50`: Valida que con inicio en offset 50 el scroll permanece en su posición y no resetea.
  - `action_feedback_shows_snackbar_and_handles_undo_and_consume`: Valida que el snackbar muestra la acción de deshacer, despacha el ID consumido y ejecuta el callback de Undo.

## Verificación

| Verificación | Comando / Acción | Resultado |
|---|---|---|
| Compilación completa | `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks` | exit 0 (BUILD SUCCESSFUL) |
| JVM focales Inbox | `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks` | 28/28 APROBADOS (exit 0) |
| Tests instrumentados Compose | `./gradlew :app:connectedDebugAndroidTest` | 10/10 APROBADOS en Emulator y Pixel 9 (exit 0) |
| Git diff check | `git diff --check` | Limpio (exit 0) |

## Integridad de Archivos Ajenos Protegidos

Los hashes SHA-1 de los archivos ajenos protegidos coinciden exactamente con el baseline:
- `ComposeScreen.kt`: `70334824e407daf48f6e1ec445e7114722c6e1eb` (OK)
- `MainNavHost.kt`: `411373faa8cd6f32c98c708830bd494f9aab40b8` (OK)
- `gradle.properties`: `49a749189f38ad45b2bec7aca6a7bf865cc932f5` (OK)

## Criterio de salida
**GO.** El contenedor `InboxContent` y sus efectos transversales quedan auditados, congelados y completamente cubiertos por la red de caracterización instrumental sin alterar contratos de producción ni archivos ajenos.
