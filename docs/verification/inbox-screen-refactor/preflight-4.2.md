# Preflight — Subfase 4.2: Lista y filas

Fecha: 2026-08-31 (CST)

## Estado Inicial del Repositorio
- Commit base: `2e60c01` (refactor(inbox): extract InboxSuccessContent and pull-to-refresh characterization)
- Commit canónico baseline visual: `c2f5639`
- Branch actual: `main`

## Contrato de Extracción
- Crear `InboxEmailList.kt` como composable internal.
- Mover únicamente `LazyColumn` y sus items:
  - `items(state.emails, key = { it.id })`
  - Callbacks `remember(email.id)` para `onClick` y `onDelete`
  - `actionsEnabled = email.id !in state.activeActionEmailIds`
  - `showDivider`, `isHighlighted`, `onClearHighlight` y `animateItem` sin alterar especificaciones
  - Empty item con key `"empty"`
  - Loader item con key `"loader"` y tag `"inbox_next_page_loader"`
- `InboxContent` calcula `PaddingValues(bottom = paddingValues.calculateBottomPadding() + 24.dp)` y lo pasa a `InboxEmailList`.
- `InboxContent` conserva literalmente el `LaunchedEffect` de paginación después de `InboxEmailList`.

## Archivos Ajenos Protegidos (Hash Baseline)
- `ComposeScreen.kt`: `70334824e407daf48f6e1ec445e7114722c6e1eb`
- `MainNavHost.kt`: `411373faa8cd6f32c98c708830bd494f9aab40b8`
- `gradle.properties`: `49a749189f38ad45b2bec7aca6a7bf865cc932f5`
