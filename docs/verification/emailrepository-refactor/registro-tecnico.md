# Registro técnico — Refactor estructural conservador de EmailRepository

Registro acumulativo del plan maestro «Refactor estructural conservador de
EmailRepository», en su Etapa 1 — Activación y protección, Subfase 1.1 —
Estado inicial y trazabilidad. Este archivo es el único artefacto permitido
en la Subfase 1.1.

---

## 1. Identificación

| Campo                    | Valor                                                            |
| ------------------------ | ---------------------------------------------------------------- |
| Plan maestro             | Plan maestro revisado — Refactor conservador de EmailRepository  |
| Ruta del plan            | `/Users/david/Documents/Private Notes/Private/Proyecto MailApp/Refactorizacion Estructural EmailRepository.kt/Plan maestro Refactor estructural.md` |
| Etapa                    | 1 — Activación y protección                                      |
| Subfase                  | 1.1 — Estado inicial y trazabilidad                              |
| Fecha del registro       | 2026-08-10                                                       |
| Hora de auditoría final  | `2026-08-10 16:24:37 -0600` (CST, UTC-06:00) — comprobación previa al cierre documental de la Subfase 1.1 |
| Hora del commit de cierre (referencia) | `2026-08-10 13:17:36 -0600` (CST) — momento en que se cerró la baseline; no es la hora de esta auditoría |
| Directorio del proyecto  | `/Users/david/Desktop/MailApp 0.3.0 2`                           |
| Carácter de la subfase   | Exclusivamente documental: sin ejecución de pruebas, build, lint, emuladores ni dispositivo físico |

---

## 2. Estado del repositorio

| Campo                    | Valor                                                            |
| ------------------------ | ---------------------------------------------------------------- |
| Rama                     | `main`                                                           |
| HEAD                     | `aef2d02e96d0b972b6156cc0cde20e18374fa5f8`                       |
| Commit (asunto)          | `docs(repository): close verifiable baseline`                    |
| Relación con origin/main | `0 adelante / 0 atrás` (`rev-list --count` en ambos sentidos = `0`) |
| Estado del árbol         | Un único cambio local previo: `MainNavHost.kt` (6 inserciones, 2 eliminaciones) |

### Estado completo del árbol

```
 M app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt
```

- `git diff --name-only`: solo `MainNavHost.kt`.
- `git diff --stat`: `1 file changed, 6 insertions(+), 2 deletions(-)`.
- `git diff --check`: limpio.

> Aviso ambiental permitido: error conocido del daemon `fsmonitor`; no
> invalida resultados de Git.

---

## 3. Baseline verificable (fuente contractual)

- Commit de cierre de la baseline: `aef2d02` — `docs(repository): close verifiable baseline`
  (HEAD actual).
- Commit de apertura de la baseline: `00d3881` — `docs(repository): establish verifiable baseline scope`.
- Documentos contractuales de referencia (directorio
  `docs/verification/emailrepository-baseline/`):
  - `registro-tecnico.md` — estado inicial y evidencia acumulada.
  - `inventario-api-y-consumidores.md` — constructor, dependencias, 20 métodos
    públicos, constante PDF, coordinador interno, consumidores y efectos laterales.
  - `matriz-contractual-y-huecos.md` — cobertura contractual y huecos priorizados.
  - `resultados-subfase-5.4.md` — auditoría final y puerta de entrada GO.
- Cobertura contractual consolidada: 20/20 métodos; 140 contratos directos de
  `EmailRepository`; JVM 584/584; foco instrumentado 137/137; instrumentación
  completa 284/284; lint 0 errores y 64 advertencias heredadas.

La baseline queda enlazada como fuente contractual: toda divergencia durante el
refactor estructural deberá explicarse como movimiento estructural y no podrá
alterar los comportamientos caracterizados.

---

## 4. Medidas y hash inicial de EmailRepository.kt

| Campo                    | Valor                                                            |
| ------------------------ | ---------------------------------------------------------------- |
| Ruta                     | `app/src/main/java/com/david/mailapp/data/repository/EmailRepository.kt` |
| Líneas                   | 745                                                              |
| Bytes                    | 31,613                                                           |
| SHA-256                  | `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b` |
| Superficie pública       | 20 métodos públicos y una constante pública `MAX_PDF_SIZE`       |

Este hash será la referencia para demostrar que `EmailRepository.kt` permanece
sin cambios durante la Etapa 1 y como punto de comparación tras cada extracción.

---

## 5. Constructor público

```kotlin
class EmailRepository(
    private val database: MailDatabase,
    private val providerFactory: () -> EmailProvider?,
    private val pdfCacheManager: PdfCacheManager,
    private val writeGuard: SessionWriteGuard
)
```

Cuatro dependencias inyectadas por AppContainer: la base de datos Room, una
fábrica de proveedor dinámico (leída en cada llamada), el gestor de caché PDF y
el guardián de escritura de sesión.

---

## 6. Superficie pública congelada

Inventario literal, copiado del código actual, de los 20 métodos públicos y la
constante pública. No se reinterpreta ni se añade API.

### Constante pública

```kotlin
companion object {
    /** Límite máximo: 25 MiB. */
    const val MAX_PDF_SIZE = 26_214_400L
}
```

### Métodos públicos

```kotlin
// Resolución (Subfase 4.3 del plan maestro)
suspend fun resolveEmailById(emailId: String): EmailResolutionResult

// Lecturas desde Room (siempre vivas)
fun getInbox(): Flow<List<Email>>
fun getTrash(): Flow<List<Email>>
fun getEmailById(emailId: String): Flow<Email?>

// Refresh por carpeta
suspend fun refreshInbox(pageToken: String? = null): PaginatedResult<Email>
suspend fun refreshTrash(pageToken: String? = null): PaginatedResult<Email>

// Búsqueda remota efímera (no se cachea en Room)
suspend fun searchEmails(query: String, pageToken: String? = null): PaginatedResult<Email>

// Acciones y reconciliación
suspend fun moveToTrash(emailId: String): EmailActionResult
suspend fun restoreFromTrash(emailId: String): EmailActionResult
suspend fun deletePermanently(emailId: String): EmailActionResult
suspend fun markAsRead(emailId: String): EmailActionResult

// Cuerpo y contenido
suspend fun fetchAndCacheBody(emailId: String): BodyFetchResult?
suspend fun downloadInlineImages(emailId: String, refs: List<InlineImageRef>): Map<String, String>
fun injectInlineImages(html: String, inlineImages: Map<String, String>): String

// PDF
suspend fun downloadPdf(emailId: String, metadata: PdfAttachmentMetadata): PdfDownloadState
suspend fun isPdfCached(emailId: String, stablePartId: String): Boolean
suspend fun checkPdfCache(emailId: String, stablePartId: String): PdfDownloadState.Ready?
suspend fun getValidatedCachedPdf(emailId: String, stablePartId: String): File?

// Cuenta y envío
suspend fun getUserEmail(): String?
suspend fun sendEmail(
    to: String, cc: String?, bcc: String?,
    subject: String, body: String,
    replyContext: ReplyContext? = null
)
```

Parámetros, valores predeterminados, tipos de retorno y visibilidad se conservan
exactamente como están en el código actual.

---

## 7. Estado mutable y propiedad actual

| Estado | Declaración actual | Propietario |
| ------ | ------------------ | ----------- |
| DAO | `private val dao = database.emailDao()` | Derivado de `MailDatabase`; usado por todo el repositorio |
| Coordinador de carpeta Inbox | `private val inboxCommitCoordinator = FolderCommitCoordinator()` | Independiente por carpeta |
| Coordinador de carpeta Trash | `private val trashCommitCoordinator = FolderCommitCoordinator()` | Independiente por carpeta |
| Provider dinámico | `private val provider: EmailProvider? get() = providerFactory()` | Leído por llamada para mantenerse fresco tras sign-in/sign-out |
| Mapa single-flight | `private val pendingResolutions = ConcurrentHashMap<Pair<Long, String>, CompletableDeferred<EmailResolutionResult>>()` | Clave `(sessionGeneration, emailId)`; limpieza en completación, cancelación o cambio de sesión |
| Wrapper de lectura protegida | `private data class CachedRead(val entity: EmailEntity?)` | Distingue `commit(null)` (sesión cambiada) de `commit(read = null)` (sin fila) |
| Helpers privados | `resolveInternal`, `mapLookupFailure`, `logResolve`, `commitWithReconcile`, `reconcileFolder` | Lógica interna del repositorio |
| Validación PDF privada | `PDF_MAGIC`, `isValidPdfFile`, `hasPdfMagic` en el `companion object` | Constante y helpers privados de validación |
| Coordinador interno | `internal class FolderCommitCoordinator` (mismo archivo): `Mutex`, `currentGeneration`, `nextGeneration()`, `currentGeneration()`, `commitIfValid()` | Conserva su API interna y comportamiento |

---

## 8. Órdenes de efectos que deben preservarse

| Flujo | Orden actual |
| ----- | ------------ |
| Resolución | Room → provider → Room (lectura local protegida con `writeGuard.commit` → cache hit → `fetchEmailById` → persistencia protegida con `upsertWithMerge`) |
| Refresh | provider → conversión → Room (`fetchInbox`/`fetchTrash` → `EmailEntity.fromDomain` → `commitIfValid` → `replaceFolder` en primera página completa / `upsertPreservingBodies` en parcial o paginación) |
| Acciones | provider → Room → reconciliación (remote-first; commit local con `commitWithReconcile`; ante fallo local, `reconcileFolder` en el orden de carpetas declarado) |
| Cuerpo | provider → limpieza → codec → Room (`fetchBodyWithRefs` → `EmailHtmlCleaner.clean` en `Dispatchers.Default` → `PdfAttachmentMetadataCodec.encode` → `updateBodyAndPdfMetadata` atómico; metadata persistida aunque el cuerpo sea vacío) |
| PDF | validación → caché → provider → validación → caché (pre-validación MIME/nombre/id/tamaño → `getCachedFile` + `isValidPdfFile` → `downloadAttachment` → post-validación vacío/tamaño/magia `%PDF-` → `store` bajo lease capturado antes de la red) |

---

## 9. Mapa de destino de los seis componentes internos previstos

| Componente interno | Métodos a extraer | Subfase del plan |
| ------------------ | ----------------- | ---------------- |
| `EmailProviderGateway` | `searchEmails`, `getUserEmail`, `sendEmail` | 2.1 |
| `EmailMailboxCoordinator` | `getInbox`, `getTrash`, `getEmailById`, `refreshInbox`, `refreshTrash` | 2.2, 2.3 |
| `EmailActionCoordinator` | `moveToTrash`, `restoreFromTrash`, `deletePermanently`, `markAsRead`, helpers de commit y reconciliación | 3.1 |
| `EmailContentCoordinator` | `fetchAndCacheBody`, limpieza HTML, codificación/persistencia de metadata PDF, logs del cuerpo; `downloadInlineImages`, `injectInlineImages` | 3.2, 3.3 |
| `EmailPdfCoordinator` | `isPdfCached`, `checkPdfCache`, `getValidatedCachedPdf`, `downloadPdf` | 4.1, 4.2 |
| `EmailResolutionCoordinator` | `resolveEmailById`, resolución interna, mapa single-flight, lectura cache-first protegida, mapeo de errores y logs | 4.3 |

Además se separarán:

- `RepositoryTrace`, para mantener las mismas etiquetas (`MailPerfTrace`,
  `EmailResolve`) y mediciones (`repoNow`).
- `FolderCommitCoordinator`, conservando su API interna y comportamiento
  (generaciones separadas, exclusión mutua, `commitIfValid`).

La fachada mantendrá `MAX_PDF_SIZE` como constante pública y conservará la
construcción, las delegaciones y la documentación pública.

---

## 10. Protección del cambio previo de MainNavHost.kt

El único cambio de producción local es la modificación previa del usuario en
`MainNavHost.kt`. Queda excluido del staging y de cualquier commit del refactor.
Fingerprints protegidos:

| Fingerprint | Valor |
| ----------- | ----- |
| SHA-256 del archivo | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` |
| SHA-256 del diff actual | `8d7c88bbf84d56d2008d2d3611962bcbf3c97f4c2522e3d16fe72c2f0a81018e` |
| Stat del diff | `6 inserciones(+)` y `2 eliminaciones(-)` |

Cada subfase verificará estos tres valores antes y después de su ejecución.

---

## 11. Allowlist y archivos prohibidos

### Permitido en esta subfase (1.1)

```
docs/verification/emailrepository-refactor/registro-tecnico.md
```

Único artefacto nuevo. Al terminar, el árbol mostrará solamente el cambio previo
de `MainNavHost.kt` y este registro sin seguimiento.

### Prohibido en la Subfase 1.1

En subfases posteriores `EmailRepository.kt` solo podrá modificarse cuando la
allowlist de su plan técnico cerrado lo autorice expresamente. La Subfase 1.2
es exclusivamente de validación y no autoriza cambios de producción.

- `EmailRepository.kt` o cualquier archivo de producción.
- `MainNavHost.kt`.
- Pruebas JVM o instrumentadas.
- Gradle, manifest, recursos o configuración.
- Documentación de `emailrepository-baseline`.
- Base de datos, DAO, providers, modelos o DI.

En la Subfase 1.1 no se ejecutan tests, build, lint, emuladores ni dispositivo
físico; corresponden a la Subfase 1.2.

---

## 12. Índice de resultados de las 15 subfases

Vacío por diseño: cada subfase añadirá aquí su enlace de evidencias al quedar
aprobada.

| Subfase | Resultados |
| ------- | ---------- |
| 1.1 | (este registro) |
| 1.2 | [resultados-subfase-1.2.md](resultados-subfase-1.2.md) |
| 2.1 | [resultados-subfase-2.1.md](resultados-subfase-2.1.md) |
| 2.2 | [resultados-subfase-2.2.md](resultados-subfase-2.2.md) |
| 2.3 | [resultados-subfase-2.3.md](resultados-subfase-2.3.md) |
| 3.1 | [resultados-subfase-3.1.md](resultados-subfase-3.1.md) |
| 3.2 | [resultados-subfase-3.2.md](resultados-subfase-3.2.md) |
| 3.3 | _pendiente_ |
| 4.1 | _pendiente_ |
| 4.2 | _pendiente_ |
| 4.3 | _pendiente_ |
| 5.1 | _pendiente_ |
| 5.2 | _pendiente_ |
| 5.3 | _pendiente_ |
| 5.4 | _pendiente_ |

---

## 13. Estado de etapas y subfases

| Etapa / Subfase | Descripción | Estado |
| --------------- | ----------- | ------ |
| Etapa 1 | Activación y protección | Aprobada |
| Subfase 1.1 | Estado inicial y trazabilidad | Aprobada |
| Subfase 1.2 | Gate técnico de entrada | Aprobada |
| Etapa 2 | Accesos simples y sincronización | Aprobada |
| Subfase 2.1 | Provider gateway | Aprobada |
| Subfase 2.2 | Lecturas de Mailbox | Aprobada |
| Subfase 2.3 | Refresh y coordinación por carpeta | Aprobada |
| Etapa 3 | Mutaciones y contenido | En curso |
| Subfase 3.1 | Acciones remotas y reconciliación | Aprobada |
| Subfase 3.2 | Cuerpo y metadata | Aprobada |
| Subfase 3.3 | Imágenes inline | Pendiente |
| Etapa 4 | Caché y concurrencia crítica | Pendiente |
| Subfase 4.1 | Consultas y validación PDF | Pendiente |
| Subfase 4.2 | Descarga PDF | Pendiente |
| Subfase 4.3 | Resolución y single-flight | Pendiente |
| Etapa 5 | Cierre integral | Pendiente |
| Subfase 5.1 | JVM, build y lint | Pendiente |
| Subfase 5.2 | Instrumentación y concurrencia | Pendiente |
| Subfase 5.3 | Verificación real en Pixel 9 | Pendiente |
| Subfase 5.4 | Auditoría y cierre | Pendiente |

---

## 14. Gate de aceptación de 1.1 y cierre de la Etapa 1

La Subfase 1.1 quedará aprobada únicamente si:

- Existe un solo artefacto nuevo: `registro-tecnico.md`.
- Los valores registrados coinciden con el repositorio.
- La API pública y el estado concurrente están completos.
- La baseline queda enlazada como fuente contractual.
- `MainNavHost.kt` conserva sus tres fingerprints.
- `git diff --check` está limpio.
- No se modificó ni ejecutó código.
- No se realizó staging ni commit.

Estas condiciones cerraron la Subfase 1.1. La Subfase 1.2 recibió después su
propio plan técnico cerrado y quedó aprobada con JVM 584/584, build correcto,
lint 0/64 e instrumentación focal 137/137; la evidencia está enlazada en el
índice anterior. La Etapa 1 queda aprobada.

Las Subfases 2.1, 2.2 y 2.3 quedaron aprobadas con sus respectivos planes
técnicos cerrados y evidencias de verificación. La Etapa 2 queda aprobada.
