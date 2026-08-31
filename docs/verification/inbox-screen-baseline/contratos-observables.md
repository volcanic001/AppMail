# Contratos observables — InboxScreen

Este inventario congela el comportamiento que el refactor estructural debe conservar. No autoriza cambios de lógica ni de UX.

## Entrada y propiedad

1. La firma pública de `InboxScreen` conserva sus ocho parámetros, orden y valores predeterminados.
2. El consumidor de producción sigue siendo `MainNavHost`.
3. El repositorio se obtiene desde `AppContainer.emailRepository`.
4. El ViewModel se crea con `InboxViewModel.Factory(repository)`.
5. `uiState` se recoge con `collectAsStateWithLifecycle()`.
6. `SnackbarHostState` se recuerda durante la vida de la pantalla.

## Estructura de pantalla

7. La raíz usa `Scaffold` y conserva `modifier.fillMaxSize()`.
8. La barra superior conserva título localizado, menú, búsqueda, colores y descripciones.
9. La animación de búsqueda conserva `0.97f → 1.02f → 1f` y se dispara sin esperar antes de `onSearchClick`.
10. El contenido aplica únicamente el top padding del Scaffold; la lista conserva bottom padding más 24 dp.
11. El Snackbar permanece alineado abajo con padding inferior de 24 dp.

## Estados

12. Loading muestra ocho filas shimmer.
13. Error muestra símbolo, razón localizada y botón Retry conectado a `viewModel.refresh()`.
14. Success vacío conserva un item LazyColumn con key `"empty"` y `EmptyInbox` centrado.
15. Success poblado conserva el orden recibido, key de cada email igual a `email.id` y key `"loader"` para paginación.

## Acciones y feedback

16. Cada fila conserva click con su ID exacto.
17. Swipe izquierdo conserva `moveToTrash(email.id)`.
18. `activeActionEmailIds` deshabilita acciones de la fila.
19. Solo el primer elemento de `pendingFeedbackQueue` se presenta.
20. El feedback se consume mediante `viewModel.consumeFeedback`.
21. Undo conserva `viewModel.undoMoveToTrash` con el ID del correo.
22. Highlight conserva el parámetro, el callback de limpieza y el fallback de 2500 ms.
23. La limpieza interna de `EmailListItem` a 800 ms no se consolida ni se elimina.

## Refresh, lista y paginación

24. Success usa `rememberPullToRefreshState()` y `PullToRefreshBox`.
25. El indicador conserva clamps, translationY, escala, alpha, tamaños y progreso actuales.
26. Al comenzar refresh se captura la posición solo si índice = 0 y offset < 50.
27. Al terminar refresh, una posición capturada espera 100 ms y ejecuta `scrollToItem(0, 0)`.
28. Una posición alejada no se reposiciona.
29. La paginación observa `lastVisible` y `totalItemsCount` mediante `snapshotFlow` y `distinctUntilChanged`.
30. La paginación dispara cuando `lastVisible >= total - 3` y conserva su `LaunchedEffect(listState, state.emails.isEmpty())`.
31. Los modificadores `animateItem` conservan tweens y springs actuales, distintos de los de Trash.
32. `showEmailDividers` se propaga sin reinterpretación.

## Reglas de refactor

- No cambiar claves Compose, delays, valores de animación, callbacks, keys LazyColumn o visibilidad pública.
- No introducir `rememberUpdatedState`, debounce, guards nuevos, cambios de DI o correcciones de comportamiento heredado.
- Todo símbolo extraído debe ser `internal` o `private`.
- La matriz de equivalencia de las etapas posteriores debe referenciar estos 32 contratos.
