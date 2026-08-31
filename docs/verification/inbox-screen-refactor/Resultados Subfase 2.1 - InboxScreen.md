# Resultados Subfase 2.1 — InboxScreen

## Resultado

Se completó el seam interno de caracterización Compose sin cambiar lógica de producto.

- `InboxScreen.kt`: fachada pública de 51 líneas.
- `InboxContent.kt`: estado visual y efectos existentes con callbacks inyectados.
- `InboxPlaceholders.kt`: Empty y shimmer extraídos.
- `InboxContentCharacterizationTest`: 5 pruebas nuevas.

## Pruebas

- JVM Inbox: **28/28**.
- Caracterización Compose: **5/5 en emulador** y **5/5 en Pixel 9**.
- Compilación Kotlin y AndroidTest: correcta.
- No se incluyeron cambios ajenos.

## Contratos preservados

Firma pública, ViewModel/DI, Scaffold, TopAppBar, feedback, highlight, refresh, paginación, keys LazyColumn, callbacks recordados, animaciones, padding y dividers permanecen en los mismos flujos. Los tags añadidos son únicamente seams de prueba no visuales.

## Commit

El commit aislado de esta subfase se registra en el handoff de ejecución; no incluye cambios ajenos.

## Resultados Subfase 2.2 — extracción de barra superior

- Se extrajo `InboxTopBar.kt` como componente `internal`.
- La animación de búsqueda, callbacks, título, iconos, colores y descripciones permanecen sin cambios.
- `InboxContent.kt` conserva el mismo comportamiento y ahora delega únicamente la composición de la barra superior.
- No se modificaron lógica de producto, estado, navegación, DI, recursos ni Gradle.

### Verificación 2.2

| Verificación | Resultado |
|---|---|
| `compileDebugKotlin --rerun-tasks` | BUILD SUCCESSFUL |
| `testDebugUnitTest --tests com.david.mailapp.feature.inbox.* --rerun-tasks` | **28/28**, BUILD SUCCESSFUL |
| `connectedDebugAndroidTest ... InboxContentCharacterizationTest` en emulador | **5/5**, BUILD SUCCESSFUL |
| `connectedDebugAndroidTest ... InboxContentCharacterizationTest` en Pixel 9 | **5/5**, BUILD SUCCESSFUL |

**GO.** La extracción estructural de 2.2 mantiene los contratos observables y deja el contenido listo para la siguiente subfase.
