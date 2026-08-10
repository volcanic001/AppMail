# Resultados — Subfase 4.1, prevalidación y consultas de caché PDF

## Identificación

- Etapa: 4 — PDF, identidad y envío.
- Subfase: 4.1 — Prevalidación y consultas de caché PDF.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite nueva: `EmailRepositoryPdfContractsTest`.

## Contratos añadidos

| Caso | Contrato protegido |
|---|---|
| `c1_downloadPdf_wrong_mime_returns_invalid_pdf_without_side_effects` | MIME distinto de `application/pdf` → `Error(INVALID_PDF)` sin resolver provider, sin `downloadAttachment`, sin commit y sin crear archivos. |
| `c2_downloadPdf_missing_pdf_extension_returns_invalid_pdf_without_side_effects` | Nombre sin extensión `.pdf` → `Error(INVALID_PDF)` con las mismas ausencias de efectos. |
| `c3_downloadPdf_blank_attachmentId_returns_invalid_pdf_without_side_effects` | `attachmentId` vacío o compuesto por espacios → `Error(INVALID_PDF)` con las mismas ausencias de efectos. |
| `c4_downloadPdf_declared_size_over_limit_returns_too_large_without_side_effects` | Tamaño declarado `MAX_PDF_SIZE + 1` → `Error(TOO_LARGE)` con las mismas ausencias de efectos. |
| `c5_downloadPdf_exact_max_size_and_cache_hit_ready_without_network` | Tamaño declarado exactamente 26 214 400 bytes y extensión `.PDF` aceptados; con caché válida devuelve `Ready(tamaño)` sin resolver el provider, sin descargar, sin commit y conservando intacto el archivo cacheado por `stableId`. |
| `c6_cached_queries_missing_file_all_reject` | Archivo ausente: `isPdfCached=false`, `checkPdfCache=null` y `getValidatedCachedPdf=null`. |
| `c7_cached_queries_empty_file_all_reject` | Archivo vacío: las tres consultas lo rechazan. |
| `c8_cached_queries_truncated_signature_all_reject` | Firma parcial menor de cinco bytes: las tres consultas lo rechazan. |
| `c9_cached_queries_oversized_file_all_reject` | Archivo de `MAX_PDF_SIZE + 1` bytes (extendido con `RandomAccessFile.setLength`, sin reservar 25 MiB en memoria): las tres consultas lo rechazan. |
| `c10_cached_queries_valid_file_all_accept` | Archivo válido con `%PDF-`: `isPdfCached=true`, `checkPdfCache=Ready(tamaño)` y `getValidatedCachedPdf` devuelve la ruta cacheada correcta. |

## Implementación

- Suite instrumentada nueva con Room en memoria, `PdfCacheManager` real, `FakeEmailProvider` y `FakeSessionWriteGuard`.
- Contador local de resoluciones de `providerFactory` en el test (sin ampliar fakes).
- Archivos inválidos creados dentro del directorio temporal controlado: `PdfCacheManager.store` para vacío y truncado; archivo válido seguido de `RandomAccessFile.setLength` para el sobredimensionado.
- No se ampliaron fakes: los contadores existentes fueron suficientes.

## Validación

### Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 19s
```

### Suite nueva (10 casos)

```text
Starting 10 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 10 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 39s
```

### Corrida conjunta con `PdfCancellationContractsTest` (12 casos)

```text
Starting 12 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 12 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 37s
```

El XML conjunto reportó 12 pruebas, 0 fallos, 0 errores, 0 omitidas y 2.453 s agregados.

### `PdfCacheManagerTest` en JVM (22 casos)

```text
./gradlew testDebugUnitTest --tests "com.david.mailapp.data.pdf.PdfCacheManagerTest"
BUILD SUCCESSFUL in 5s
```

El XML JVM reportó 22 pruebas, 0 fallos, 0 errores, 0 omitidas.

### Corrección posterior de auditoría

La revisión de cierre detectó y corrigió tres debilidades exclusivamente en la suite:

- C1 ahora aísla el MIME inválido manteniendo una extensión `.pdf` válida.
- C5 compara también todos los bytes del archivo antes y después del cache hit.
- C6–C10 comprueban explícitamente que las consultas no resuelven el provider, no descargan y no intentan commit.

Después de las correcciones, `compileDebugAndroidTestKotlin` terminó en `BUILD SUCCESSFUL` en 11 s. La corrida conjunta de `EmailRepositoryPdfContractsTest` y `PdfCancellationContractsTest`, ejecutada con `--rerun-tasks`, volvió a terminar en `BUILD SUCCESSFUL`: 12/12 pruebas, sin fallos, errores ni omitidas.

## Integridad y cierre

- Cobertura directa: 110 → 120 casos.
- Las tres consultas de caché (`isPdfCached`, `checkPdfCache`, `getValidatedCachedPdf`) pasan de ausente a alta.
- `downloadPdf` pasa de mínima a media (prevalidación y cache hit sellados).
- `MAX_PDF_SIZE`: frontera exacta (26 214 400) y un byte superior cubiertos.
- No se adelantaron descarga, postvalidación, sesión ni atomicidad de las subfases 4.2–4.3.
- No se modificaron producción, fakes, Room, providers reales ni Gradle.
- `EmailRepository.kt` conserva su hash `abcac202…`; `MainNavHost.kt` conserva el cambio previo del usuario (hash `a6840cfc…`).
- La Subfase 4.1 queda cerrada; la Etapa 4 permanece en progreso y 4.2 es la siguiente subfase. No se crea commit.
