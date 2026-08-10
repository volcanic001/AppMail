# Resultados — Subfase 4.2, descarga, validación y resultados PDF

## Identificación

- Etapa: 4 — PDF, identidad y envío.
- Subfase: 4.2 — Descarga, validación y resultados PDF.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite ampliada: `EmailRepositoryPdfContractsTest` (10 → 18 casos).

## Contratos añadidos

| Caso | Contrato protegido |
|---|---|
| `c11_downloadPdf_valid_download_uses_stableId_persists_and_returns_ready` | Descarga válida con `partId` recortado (`stableId`) que prevalece sobre `attachmentId`; provider recibe exactamente `emailId` y `attachmentId`; `Ready(tamaño real)`; un único commit almacena exactamente los bytes recibidos; archivo final válido sin residuos `.tmp`; orden `gmail.downloadAttachment → room.commit`. |
| `c12_downloadPdf_empty_content_returns_empty_content_without_commit` | Provider devuelve `ByteArray(0)` → `Error(EMPTY_CONTENT)` sin commit ni archivos finales/temporales. |
| `c13_downloadPdf_actual_size_too_large_returns_too_large_without_commit` | Provider devuelve `MAX_PDF_SIZE + 1` bytes con firma `%PDF-` en los primeros cinco bytes → `Error(TOO_LARGE)` sin commit ni archivos. |
| `c14_downloadPdf_invalid_signature_returns_invalid_pdf_without_commit` | Bytes no vacíos y dentro del límite, pero sin `%PDF-` → `Error(INVALID_PDF)` sin commit ni archivos. |
| `c15_downloadPdf_no_provider_returns_no_provider_without_download` | Lease válido, caché vacía y `providerFactory` nulo → `Error(NO_PROVIDER)` sin `downloadAttachment`, sin commit y sin archivos. |
| `c16_downloadPdf_remote_error_returns_network_without_propagation` | `downloadAttachment` lanza `IOException` → `Error(NETWORK)` sin propagar la excepción, sin commit ni archivos. |
| `c17_downloadPdf_cache_write_error_returns_cache_write_without_residues` | Descarga válida seguida de `PdfCacheManager` cuya raíz es un archivo (impide crear `pdf_attachments`) → `Error(CACHE_WRITE)` con un intento de commit y sin residuos `.pdf` ni `.tmp`. |
| `c18_downloadPdf_invalid_cache_cleanup_download_and_store_ready` | Archivo con magic inválido bajo el `stableId` → primer commit lo elimina → descarga → segundo commit almacena el PDF válido → `Ready` con bytes antiguos reemplazados, una descarga exacta y ningún temporal. Orden: `room.commit → gmail.downloadAttachment → room.commit`. |

## Cobertura de `PdfDownloadFailure`

Los seis valores del enumerado quedan cubiertos en los contratos del repositorio:

| Valor | Contratos que lo producen |
|---|---|
| `INVALID_PDF` | C1 (MIME), C2 (extensión), C3 (`attachmentId`), C14 (firma inválida) |
| `TOO_LARGE` | C4 (declarado `MAX+1`), C13 (real `MAX+1`) |
| `EMPTY_CONTENT` | C12 |
| `NO_PROVIDER` | C15 |
| `NETWORK` | C16 |
| `CACHE_WRITE` | C17 |

## Cambios en fakes

- `FakeEmailProvider` (solo AndroidTest): registro de pares `emailId`/`attachmentId` en `receivedDownloadAttachmentRequests` y evento `gmail.downloadAttachment` en el log compartido antes del gate o retorno. Resultados, errores y mecanismos de cancelación intactos.
- Log compartido de eventos (`events`) conectado en el setup de `EmailRepositoryPdfContractsTest` a ambos fakes para comprobar el orden de commits.

## Validación

### Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 29s
```

### Suite ampliada (18 casos)

```text
Starting 18 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 18 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 51s
```

### Corrida conjunta con `PdfCancellationContractsTest` (20 casos)

```text
Starting 20 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 20 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 54s
```

XML conjunto: `tests="20" failures="0" errors="0" skipped="0"` (3.426 s).

### `PdfCacheManagerTest` en JVM (22 casos)

```text
./gradlew testDebugUnitTest --tests "com.david.mailapp.data.pdf.PdfCacheManagerTest"
BUILD SUCCESSFUL in 7s
```

## Integridad y cierre

- Cobertura directa: 120 → 128 casos.
- Los seis valores de `PdfDownloadFailure` quedan cubiertos por contratos del repositorio.
- `downloadPdf` permanece en nivel medio hasta completar sesión y atomicidad en 4.3.
- No se adelantaron cancelación, cambio de sesión, rechazo de limpieza ni commit tardío.
- No se modificaron producción, Room, providers reales ni Gradle.
- `EmailRepository.kt` conserva su hash `abcac202…`; `MainNavHost.kt` conserva `a6840cfc…`.
- La Subfase 4.2 queda cerrada; la Etapa 4 continúa abierta y sin commit.
