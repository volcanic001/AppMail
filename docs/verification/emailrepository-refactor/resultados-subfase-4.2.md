# Resultados — Subfase 4.2, Descarga PDF

## Identificación

- Plan maestro: Refactor estructural de `EmailRepository` — Etapa 4, Subfase 4.2.
- Fecha: 2026-08-11, CST (`-0600`).
- Objetivo: trasladar `downloadPdf` a `EmailPdfCoordinator` sin alterar validaciones, orden de efectos, semántica de sesión, errores, logs ni cancelación.

## Implementación

- **Modificado** `EmailPdfCoordinator.kt`: recibe `providerFactory` y `writeGuard`, contiene el flujo completo de `downloadPdf` y mantiene privados `isValidPdfFile` y `hasPdfMagic` tras concluir la extracción.
- **Modificado** `EmailRepository.kt`: construye el coordinador con sus cuatro dependencias y conserva la firma pública de `downloadPdf` como delegación directa. Se retiraron de la fachada el flujo movido, su import de `PdfDownloadFailure`, la propiedad privada `provider` y la etiqueta `MailPerfTrace`, que ahora pertenece al coordinador.
- Orden contractual preservado: prevalidación → captura de lease → consulta/limpieza de caché → provider → postvalidación → escritura protegida. `CancellationException` continúa propagándose tanto en red como en escritura.

## Gates

- Compilación Kotlin: `compileDebugKotlin`, correcta.
- JVM: 584/584, sin fallos, errores ni omisiones.
- Instrumentación en `Medium_Phone_API_36.1(AVD) - 16` (`ANDROID_SERIAL=emulator-5554`):
  - `EmailRepositoryPdfContractsTest`: 21 contratos por ejecución.
  - `PdfCancellationContractsTest`: 2 contratos por ejecución.
  - Tres ejecuciones conjuntas: 3×23=69, sin fallos, errores ni omisiones.
- Lint: 0 errores y 65 advertencias conocidas.

## Incidencia ambiental

La instancia inicial de `emulator-5554` quedó desconectada antes de ejecutar el gate. Se cerró únicamente esa instancia, se inició de nuevo `Medium_Phone_API_36.1` sin snapshot y se confirmó el arranque completo. Las tres ejecuciones aceptadas se dirigieron explícitamente a `emulator-5554`; no se usó el Pixel 9 conectado.

## Integridad

- Superficie pública intacta: 20 métodos y `MAX_PDF_SIZE`.
- Las cuatro operaciones PDF delegan en `EmailPdfCoordinator`; `downloadPdf` es una delegación de una línea.
- Los cinco coordinadores extraídos previamente y `EmailProviderGateway` permanecen intactos.
- Los cambios locales protegidos en `ComposeScreen.kt` y `MainNavHost.kt` permanecen fuera del allowlist y del staging.
- `git diff --check` limpio; el commit de cierre contiene exclusivamente los cuatro archivos autorizados.

## Cierre

- Commit: **`refactor(repository): extract pdf download coordination`** (4 archivos, sin push).
- Etapa 4 en curso; la Subfase 4.3 — Resolución y single-flight queda pendiente de planificación técnica cerrada.
