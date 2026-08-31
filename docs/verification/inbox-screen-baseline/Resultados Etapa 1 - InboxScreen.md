# Resultados Etapa 1 — InboxScreen

Fecha: 2026-08-30 23:01:14 -0600 (CST)

## Alcance ejecutado

Se ejecutaron las subfases 1.1 (preflight y protección), 1.2 (inventario de contratos) y 1.3 (baseline técnico) del plan maestro de refactor estructural de InboxScreen.

El código productivo no fue modificado. El único contenido añadido pertenece a la documentación del baseline.

## Punto de partida

- HEAD: `7dd89d3 docs(emailbody): complete structural refactor handoff`.
- Rama: `main`.
- `InboxScreen.kt`: 434 líneas.
- SHA-256: `9584c841659f808e9fe84a75b3cbf549928f7bf7927430f8b3d37456d52802cf`.
- Cambios ajenos protegidos: `ComposeScreen.kt`, `MainNavHost.kt`, `gradle.properties`.

## Contratos congelados

Se documentaron 32 contratos observables: entrada/DI, Scaffold y TopAppBar, estados Loading/Error/Empty/Success, feedback y undo, highlight, pull-to-refresh, reposicionamiento, keys de LazyColumn, animaciones, dividers y paginación.

## Verificación técnica

- Suite JVM completa: **593/593**, BUILD SUCCESSFUL.
- Suite JVM focal de Inbox: **28/28**, 0 fallos, errores u omitidas.
- `compileDebugKotlin`: BUILD SUCCESSFUL.
- `compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- `assembleDebug`: BUILD SUCCESSFUL.
- `assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- `lintDebug`: BUILD SUCCESSFUL, sin errores.
- `git diff --check`: limpio.

## Pendiente de hardware

Durante la captura inicial no había dispositivos. Después se conectaron ambos: `EmailListItemGestureTest` pasó 1/1 en el emulador y 1/1 en dos corridas consecutivas del Pixel 9. Una primera corrida del Pixel presentó un error transitorio de infraestructura (`No compose hierarchies found in the app`), documentado y no atribuido al código.

## Evidencia detallada

- `registro-tecnico.md`: preflight, allowlist, hashes y resultados.
- `contratos-observables.md`: inventario contractual completo.
- `resultados-subfase-1.3.md`: comandos, conteos y decisión de la subfase.

Conclusión: las subfases 1.1, 1.2 y 1.3 quedan terminadas para el alcance técnico definido. La suite instrumentada completa se reserva para la verificación posterior del refactor.
