# Resultados — Subfase 4.1, Consultas y validación PDF

## Identificación
- Plan maestro: Refactor estructural de `EmailRepository` — Etapa 4, Subfase 4.1.
- Fecha: 2026-08-10, CST (`-0600`).
- Objetivo: crear `EmailPdfCoordinator` y extraer `isPdfCached`, `checkPdfCache`, `getValidatedCachedPdf`, `isValidPdfFile`, `hasPdfMagic` y la firma privada `%PDF-`, conservando exactamente validación binaria, consultas de caché y sin red.

## Implementación
- **Nuevo** `EmailPdfCoordinator.kt`: `internal class EmailPdfCoordinator(pdfCacheManager, maxPdfSize)` con las tres consultas de caché (`isPdfCached`, `checkPdfCache`, `getValidatedCachedPdf`), validación binaria (`isValidPdfFile`, `hasPdfMagic`) y constante privada `PDF_MAGIC`. `isValidPdfFile` y `hasPdfMagic` expuestos como `internal` temporalmente para `downloadPdf`.
- **Modificado** `EmailRepository.kt`: `pdfCoordinator` construido con `(pdfCacheManager, MAX_PDF_SIZE)`, tres consultas convertidas a delegaciones de 1 línea, `downloadPdf` califica llamadas a `pdfCoordinator.isValidPdfFile`/`pdfCoordinator.hasPdfMagic`, compañero reducido a `MAX_PDF_SIZE`, import `FileInputStream` retirado.

## Gates
- JVM: 584/584
- Instrumentación en `Medium_Phone_API_36.1(AVD) - 16` (`ANDROID_SERIAL=emulator-5554`):
  - `EmailRepositoryPdfContractsTest` 3×21=63 (0 fallos)
  - `PdfCancellationContractsTest` 2/2
- Lint: 0 errores, 65 advertencias (referencia)

## Incidencia
Las ejecuciones iniciales de instrumentación se realizaron inadvertidamente en Pixel 9 - 17 (dispositivo físico). El plan exige `Medium_Phone_API_36.1` con `ANDROID_SERIAL=emulator-5554` y prohíbe expresamente usar Pixel 9 en esta subfase. Se lanzó el emulator, se re-ejecutaron las pruebas en el dispositivo correcto y se enmendó el commit. No hubo diferencias de comportamiento entre dispositivos.

## Integridad
- 20 métodos + `MAX_PDF_SIZE` intactos. Cinco coordinadores anteriores con fingerprints intactos. Archivos protegidos (`ComposeScreen.kt`, `MainNavHost.kt`) con hashes coincidentes. `EmailPdfCoordinator` con visibilidad `internal`. `git diff --check` limpio. Solo archivos del allowlist modificados.

## Cierre
- Commit **`refactor(repository): extract pdf cache validation`** (enmendado, 4 archivos, sin push). Etapa 4 en curso, Subfase 4.2 pendiente de su plan técnico cerrado.
