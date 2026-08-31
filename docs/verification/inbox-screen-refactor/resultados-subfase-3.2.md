# Resultados — Subfase 3.2: Barra superior

Fecha: 2026-08-31 (CST)

## Cambios realizados
- **Auditoría de Barra Superior**: Se confirmó que `InboxTopBar.kt` conserva fielmente la secuencia de escala `Animatable(1f)` a `MotionTokens.pressScale` (0.97f), rebotando a `1.02f` (iconTap) y estabilizándose en `1f` (con spring dampingRatio = 0.35f, stiffness = 500f). También se despacha de forma inmediata y síncrona el callback `onSearchClick()`.
- **Ampliación de Caracterización**: Se actualizó el caso de prueba `populated_list_triggers_menu_and_search_callbacks` en `app/src/androidTest/java/com/david/mailapp/feature/inbox/InboxContentCharacterizationTest.kt`. La prueba resuelve las descripciones accesibles desde recursos de localización (`R.string.action_menu` y `R.string.action_search`) y valida con contadores explícitos (`menuCalls`, `searchCalls`) el despacho exactamente una vez (`assertEquals(1, ...)`), síncronamente tras cada pulsación (`performClick()`).
- No se modificó la API pública de `InboxScreen`, el ViewModel, ni los archivos ajenos.

## Verificación

| Verificación | Comando / Acción | Resultado |
|---|---|---|
| Compilación completa | `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks` | exit 0 (BUILD SUCCESSFUL) |
| JVM focales Inbox | `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks` | 28/28 APROBADOS (exit 0) |
| Tests instrumentados Compose | `./gradlew :app:connectedDebugAndroidTest` | 6/6 APROBADOS en Pixel 9 y Emulator (exit 0) |
| Git diff check | `git diff --check` | Limpio (exit 0) |

## Evidencia visual de lista poblada

Se montó un escenario de prueba Compose temporal para capturar el estado de éxito con lista poblada en ambos extremos del refactor de la barra superior (línea de base pre-extracción en `f144d0e` vs estado actual extraído en `df54203`).

| Dispositivo | Estado | Dimensiones | Hash SHA-256 de píxeles baseline = post | PNG |
|---|---|---:|---|---|
| Medium_Phone_API_36.1 (API 36) | Populated List | 1080×2400 | `cefbc45b384813651cb295ca9762d9358ed82e4121f8f16ebccc8c8fcc6cac4f` | idéntico |
| Pixel 9 (API 37) | Populated List | 1080×2424 | `210ecbce24fa15fc66120bed985f7377fb08132808ece2b9602deea1f3d67909` | idéntico |

Los artefactos de imagen y los manifiestos `.tsv` correspondientes se almacenaron en `docs/verification/inbox-screen-refactor/capturas/3.2/`.

## Integridad de Archivos Ajenos Protegidos

Los hashes SHA-1 de los archivos ajenos coinciden exactamente con el baseline original:
- `ComposeScreen.kt`: `70334824e407daf48f6e1ec445e7114722c6e1eb` (OK)
- `MainNavHost.kt`: `411373faa8cd6f32c98c708830bd494f9aab40b8` (OK)
- `gradle.properties`: `49a749189f38ad45b2bec7aca6a7bf865cc932f5` (OK)

## Criterio de salida
**GO.** Se ha auditado la barra superior y se ha ampliado la suite Compose para caracterizar síncronamente los callbacks de menú y búsqueda. La equivalencia visual se mantiene al 100% (cero regresiones visuales o de firmas).
