# Resultados — Subfase 4.3, sesión, cancelación y atomicidad PDF

## Identificación

- Etapa: 4 — PDF, identidad y envío.
- Subfase: 4.3 — Sesión, cancelación y atomicidad PDF.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite ampliada: `EmailRepositoryPdfContractsTest` (18 → 21 casos).

## Contratos añadidos

| Caso | Contrato protegido |
|---|---|
| `c19_downloadPdf_session_absent_returns_no_provider_file_intact` | `capture()` nulo desde el inicio: `Error(NO_PROVIDER)` sin resolver provider, sin descarga y sin commit. Un archivo inválido preexistente permanece byte por byte intacto y sin temporales. |
| `c20_downloadPdf_cleanup_commit_rejected_returns_no_provider_file_intact` | Lease válido y archivo inválido existente; el primer commit retorna null sin ejecutar el bloque → `Error(NO_PROVIDER)`. No continúa hacia provider ni descarga. Archivo inválido intacto, sin `.tmp`. Eventos limitados a un solo intento de commit (`room.commit`). |
| `c21_downloadPdf_session_change_pending_download_old_rejected_new_persists` | `SessionWriteGuardImpl` real con gen=1; `downloadAttachmentStarted` sincroniza que el lease antiguo ya fue capturado. Invalidar y activar gen=2. La sesión nueva descarga y almacena bytes nuevos en el mismo `stableId` mientras la descarga antigua sigue pendiente. Al liberarla, su commit (gen=1) es rechazado → `Error(NO_PROVIDER)`. La caché conserva exclusivamente los bytes de la sesión nueva, sin sobrescritura ni temporales. Ambos providers reciben exactamente una solicitud con los argumentos esperados. |

## Refuerzo de cancelación

Los dos casos existentes de `PdfCancellationContractsTest` (conteo sin aumentar) recibieron aserciones adicionales:

| Caso | Refuerzo |
|---|---|
| `c3_cancellation_propagates_not_converted_to_error` | verify: 1 llamada a `downloadAttachment`, 0 commits, eventos `["gmail.downloadAttachment"]`. |
| `c3_cancellation_during_commit_propagates` | verify: 1 llamada a `downloadAttachment` antes del intento de commit, 1 intento de commit, eventos `["gmail.downloadAttachment", "room.commit"]`. |

## Cambios en fakes

- `FakeEmailProvider` (solo AndroidTest): señal opcional `downloadAttachmentStarted` completada después de registrar argumentos y antes de esperar el gate. Cancelación, errores y resultados intactos.

## Validación

### Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 10s
```

### Suite ampliada (21 casos)

```text
Starting 21 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 21 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 48s
```

### Corrida conjunta con `PdfCancellationContractsTest` (23 casos)

```text
Starting 23 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 23 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 30s
```

### Repetición del contrato temporal de sesión (C21)

| Corrida | Resultado | Duración Gradle |
|---:|---|---:|
| 1 | 1/1 | 19 s |
| 2 | 1/1 | 18 s |
| 3 | 1/1 | 17 s |

Cero flakiness en el contrato temporal.

### Regresión del consumidor

```text
EmailDetailCancellationTest: Starting 7 tests ... Finished 7 tests ... BUILD SUCCESSFUL in 20s
```

XML: `tests="7" failures="0" errors="0" skipped="0"` (2.762 s).

### `PdfCacheManagerTest` en JVM (22 casos)

```text
./gradlew testDebugUnitTest --tests "com.david.mailapp.data.pdf.PdfCacheManagerTest"
BUILD SUCCESSFUL in 5s
```

## Integridad y cierre

- Cobertura directa: 128 → 131 casos.
- `downloadPdf` pasa de media a alta: lease ausente, limpieza rechazada, cambio real de sesión sin escritura tardía y refuerzo de cancelación quedan protegidos.
- Contrato transversal «Caché PDF atómica y sin residuos» pasa de parcial a alta.
- No se modificaron producción, providers reales, Room ni Gradle.
- `EmailRepository.kt` conserva su hash `abcac202…`; `MainNavHost.kt` conserva `a6840cfc…`.
- La Subfase 4.3 queda cerrada; la Etapa 4 continúa abierta y sin commit.
