# Registro de Preflight — Subfase 5.2: Limpieza y auditoría estructural

- **HEAD base**: `3eb32d0`
- **Commit anterior aprobado**: `3eb32d0 docs(inbox): audit final facade`

## Archivos permitidos

- `app/src/main/java/com/david/mailapp/feature/inbox/InboxScreen.kt`
- `app/src/main/java/com/david/mailapp/feature/inbox/InboxContent.kt`
- `docs/verification/inbox-screen-baseline/matriz-seguridad-compose.md`
- `docs/verification/inbox-screen-refactor/preflight-5.2.md`
- `docs/verification/inbox-screen-refactor/resultados-subfase-5.2.md`

## Auditoría prevista

- Eliminar solo las anotaciones e imports `ExperimentalMaterial3Api` obsoletos en fachada y contenedor; el opt-in permanece donde se usa la API experimental.
- Confirmar un único propietario para refresh, lista y paginación, y ausencia de helpers duplicados.
- Confirmar visibilidad `internal` o `private` para las extracciones de presentación; no cambiar tipos públicos preexistentes fuera de alcance.
- Actualizar la matriz con la ubicación final de C01–C32.
