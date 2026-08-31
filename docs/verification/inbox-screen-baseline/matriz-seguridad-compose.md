# Matriz de seguridad Compose — InboxScreen

Subfase 1.4. Documento de diseño; no modifica producción ni pruebas.

## Objetivo

La red de seguridad se implementará en la subfase 2.1 como `InboxContentBaselineTest` bajo `app/src/androidTest/java/com/david/mailapp/feature/inbox/`. La prueba montará un `InboxContent` interno con estado, `LazyListState`, `SnackbarHostState` y callbacks inyectados. `InboxScreen` conservará la creación real de ViewModel y DI.

Se autorizan exclusivamente estos tags no visuales para la prueba: `inbox_root`, `inbox_list`, `inbox_loading`, `inbox_error`, `inbox_empty`, `inbox_refresh_indicator` e `inbox_next_page_loader`.

## Fixtures y escenarios

| ID | Escenario futuro | Evidencia |
|---|---|---|
| S01 | Fachada pública, DI y consumidor | inspección estática y diff |
| S02 | Top bar, menú y búsqueda | Compose UI + captura |
| S03 | Padding y Snackbar | captura Compose |
| S04 | Loading, Error y Empty | Compose UI + captura |
| S05 | Lista, orden, click y swipe | Compose UI |
| S06 | Acción activa, feedback y undo | Compose UI |
| S07 | Highlight de fila y fallback | Compose UI con reloj controlado |
| S08 | Pull-to-refresh e indicador | Compose UI + captura |
| S09 | Reposición según posición inicial | Compose UI con `LazyListState` |
| S10 | Paginación y loader | Compose UI con lista larga |
| S11 | Dividers y animateItem | captura y revisión estática |

## Matriz contrato → prueba

| Contrato | Escenario | Criterio GO / NO-GO |
|---|---|---|
| C01 | S01 | Firma y defaults idénticos; NO-GO ante parámetro, orden o default distinto. |
| C02 | S01 | `MainNavHost` conserva el único consumidor; NO-GO si se toca navegación. |
| C03 | S01 | La fachada sigue obteniendo `AppContainer.emailRepository`; NO-GO ante DI nueva. |
| C04 | S01 | Conserva `InboxViewModel.Factory(repository)`; NO-GO ante Factory distinta. |
| C05 | S01 | Conserva `collectAsStateWithLifecycle`; NO-GO ante colección no lifecycle-aware. |
| C06 | S01 / S06 | `SnackbarHostState` persiste durante la pantalla y muestra feedback. |
| C07 | S02 | Scaffold y raíz ocupan el tamaño disponible. |
| C08 | S02 | Título, iconos, colores y descripciones accesibles permanecen. |
| C09 | S02 | Callback de búsqueda ocurre sin esperar la animación 0.97 → 1.02 → 1. |
| C10 | S03 | Top padding y bottom padding + 24 dp coinciden. |
| C11 | S03 / S06 | Snackbar conserva alineación inferior y padding de 24 dp. |
| C12 | S04 | Loading conserva ocho filas shimmer. |
| C13 | S04 | Error muestra razón localizada y Retry invoca una vez. |
| C14 | S04 | Empty usa lista, key `empty` y queda centrado. |
| C15 | S05 / S10 | Orden y keys `email.id`/`loader` permanecen. |
| C16 | S05 | Click entrega exactamente el ID seleccionado. |
| C17 | S05 | Swipe izquierdo invoca una sola vez `moveToTrash(email.id)`. |
| C18 | S06 | Fila activa rechaza una nueva acción. |
| C19 | S06 | Se visualiza solo la cabeza de `pendingFeedbackQueue`. |
| C20 | S06 | Al cerrar feedback se consume su ID exacto. |
| C21 | S06 | Undo despacha el ID del correo movido. |
| C22 | S07 | Fallback de pantalla limpia highlight a los 2500 ms. |
| C23 | S07 | Fila conserva limpieza interna a 800 ms; no se consolida. |
| C24 | S08 | Empty y lista poblada montan PullToRefreshBox. |
| C25 | S08 | Indicador conserva tamaños y transformación por `distanceFraction`. |
| C26 | S09 | Índice 0 y offset 49 capturan posición; offset 50 no. |
| C27 | S09 | Refresh finalizado espera 100 ms y vuelve a (0, 0) si fue capturado. |
| C28 | S09 | Una posición alejada permanece sin reposición. |
| C29 | S10 | `snapshotFlow` observa último visible/total y conserva distinctUntilChanged. |
| C30 | S10 | Solo dispara al llegar a `total - 3`; no añade debounce ni guards. |
| C31 | S11 | Tweens/springs de `animateItem` coinciden por inspección y captura. |
| C32 | S11 | `showEmailDividers` cambia únicamente la visibilidad del divisor. |

## Ubicación final de contratos

| Contratos | Propietario final |
|---|---|
| C01, C03–C06 | `InboxScreen` |
| C02 | `MainNavHost` (consumidor sin cambios) |
| C07, C11, C22, C26–C28 | `InboxContent` |
| C08–C09 | `InboxTopBar` |
| C10 | `InboxContent` (padding superior) e `InboxEmailList` (padding inferior) |
| C12–C13 | `InboxPlaceholders` |
| C14–C18, C23, C31–C32 | `InboxEmailList` y `EmailListItem` sin cambios |
| C19–C21 | `ActionFeedbackEffect` invocado por `InboxContent` |
| C24–C25 | `InboxSuccessContent` |
| C29–C30 | `InboxPaginationEffect` privado en `InboxEmailList` |

Las entradas de presentación extraídas son `internal` o `private`; `InboxScreen` es la única fachada pública de la UI. Los tipos públicos preexistentes fuera de esta fachada no cambian de visibilidad.

## Capturas canónicas

Las capturas se generarán con fixtures sintéticos, en modo claro y oscuro donde aplique:

1. Loading.
2. Error localizado.
3. Empty.
4. Lista poblada con remitente, asunto, estrella y estados leído/no leído.
5. Lista con divisores desactivados.
6. Pull-to-refresh activo.
7. Loader de página siguiente.
8. Snackbar de papelera con Undo.
9. Highlight de retorno.

La comparación será pre/post en el mismo dispositivo. Se ejecutará la clase focal tres veces en emulador y una vez en Pixel; cualquier variación funcional, captura ausente o test intermitente bloquea la extracción posterior.

## Puerta de salida de etapa 1

La etapa 1 queda GO cuando los 32 contratos tienen escenario y criterio de evidencia, las fixtures no usan datos reales, y la subfase 2.1 puede implementarse sin decidir interfaces ni cobertura adicional.
