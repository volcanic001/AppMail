# Inventario de API y consumidores de EmailRepository

## Identificación

- Etapa: 1 — Congelar estado, contratos y cobertura.
- Subfase: 1.2 — Inventario de API y consumidores.
- Captura: 2026-08-08 16:45:32 -0600 (CST).
- Fuente analizada: árbol de trabajo basado en `0ba0f8bbbabb4442a747134f3db64b576837d595`.
- Archivo objetivo: `app/src/main/java/com/david/mailapp/data/repository/EmailRepository.kt`.

Este documento describe la superficie observable actual. No propone divisiones, nombres de componentes futuros ni cambios de lógica.

## Forma de construcción y dependencias

`EmailRepository` es una clase concreta; no implementa una interfaz de repositorio. `AppContainer.emailRepository` crea una única instancia mediante el constructor público:

```text
EmailRepository(
    database: MailDatabase,
    providerFactory: () -> EmailProvider?,
    pdfCacheManager: PdfCacheManager,
    writeGuard: SessionWriteGuard
)
```

Responsabilidad de cada dependencia:

| Dependencia | Uso observable |
|---|---|
| `MailDatabase` | Obtiene `EmailDao`; lecturas Flow, resolución, refresh, acciones y persistencia de cuerpo. |
| `providerFactory` | Resuelve el proveedor vigente en cada acceso para no retener una sesión anterior. |
| `PdfCacheManager` | Consulta, elimina y almacena adjuntos PDF por `emailId + stablePartId`. |
| `SessionWriteGuard` | Captura leases y rechaza lecturas/escrituras pertenecientes a una sesión invalidada. |

Estado mutable interno que forma parte del comportamiento que debe protegerse:

- Un `FolderCommitCoordinator` independiente para Inbox.
- Un `FolderCommitCoordinator` independiente para Trash.
- Un mapa concurrente de resoluciones pendientes, indexado por `(sessionGeneration, emailId)`.
- Acceso dinámico al proveedor; no existe una copia fija del proveedor en el repositorio.

## Superficie pública y consumidores

Se identificaron 20 métodos públicos, el constructor y una constante pública.

| API | Consumidores de producción | Red / proveedor | Room / archivos | Resultado y contrato principal |
|---|---|---|---|---|
| `resolveEmailById(emailId)` | `RepositoryEmailDetailSource` | Cache miss llama `fetchEmailById` | Lectura protegida y `upsertWithMerge` | `Found`, `NotFound` o `Failure`; cache-first y single-flight por sesión/id. |
| `getInbox()` | `RepositoryInboxEmailSource` | No | Flow de carpeta `inbox` | `Flow<List<Email>>` vivo, ordenado por timestamp desde Room. |
| `getTrash()` | `RepositoryTrashEmailSource` | No | Flow de carpeta `trash` | `Flow<List<Email>>` vivo, ordenado por timestamp desde Room. |
| `getEmailById(emailId)` | `RepositoryEmailDetailSource`; Factory de `ComposeViewModel` | No | Flow por id | `Flow<Email?>`; Compose consume el primer valor. |
| `refreshInbox(pageToken)` | `RepositoryInboxEmailSource` | `fetchInbox` | Reemplaza primera página completa o fusiona parcial/paginación | Devuelve `PaginatedResult`; una página incompleta pierde su token remoto. |
| `refreshTrash(pageToken)` | `RepositoryTrashEmailSource` | `fetchTrash` | Misma política para `trash` | Devuelve `PaginatedResult`; coordinador separado de Inbox. |
| `searchEmails(query, pageToken)` | Factory de `SearchViewModel` mediante `SearchEmailSource` | `search` | No persiste | Devuelve resultados efímeros; sin proveedor devuelve resultado vacío. |
| `moveToTrash(emailId)` | `RepositoryInboxEmailSource` | `moveToTrash` primero | Después mueve la fila a `trash` | `EmailActionResult`; reconcilia Inbox y Trash si falla el commit local. |
| `restoreFromTrash(emailId)` | `RepositoryInboxEmailSource`; `RepositoryTrashEmailSource` | `restoreFromTrash` primero | Después mueve la fila a `inbox` | Reconcilia Trash y luego Inbox si falla el commit. |
| `deletePermanently(emailId)` | `RepositoryTrashEmailSource` | `deletePermanently` primero | Después elimina la fila | Reconcilia Trash si falla el commit. |
| `markAsRead(emailId)` | `RepositoryInboxEmailSource`; `RepositoryEmailDetailSource` | `markAsRead` primero | Después actualiza `is_read` | Reconcilia Inbox y Trash si falla el commit. |
| `fetchAndCacheBody(emailId)` | `RepositoryEmailDetailSource` | `fetchBodyWithRefs` | Guarda body crudo/limpio y metadatos PDF | Devuelve `BodyFetchResult?`; limpia HTML no vacío en `Dispatchers.Default`. |
| `downloadInlineImages(emailId, refs)` | `RepositoryEmailDetailSource` | `downloadInlineImages` | No | Devuelve mapa CID → data URI; refs vacías cortocircuitan. |
| `injectInlineImages(html, inlineImages)` | `RepositoryEmailDetailSource` | No | No | Reemplaza tres representaciones CID y devuelve HTML. |
| `downloadPdf(emailId, metadata)` | `RepositoryEmailDetailSource` | `downloadAttachment` después de prevalidar/caché | Valida, limpia caché inválida y almacena atómicamente | `PdfDownloadState`; caché válida evita red. |
| `isPdfCached(emailId, stablePartId)` | Sin consumidor de producción encontrado | No | Consulta y valida caché | `Boolean`; API pública actualmente no utilizada. |
| `checkPdfCache(emailId, stablePartId)` | `RepositoryEmailDetailSource` | No | Consulta y valida caché | `Ready?`; se usa para presentar estado de adjunto. |
| `getValidatedCachedPdf(emailId, stablePartId)` | `RepositoryEmailDetailSource`; `PdfExternalActionHandler`; `EmailDetailPdfEffects` | No | Consulta y valida caché | `File?`; requerido para abrir y guardar mediante SAF. |
| `getUserEmail()` | Factory de `ComposeViewModel`; `AccountSettingsScreen` | `getUserEmail` | No | Dirección o `null`; consulta el proveedor vigente. |
| `sendEmail(to, cc, bcc, subject, body, replyContext)` | Factory de `ComposeViewModel` | `sendEmail` | No | Delega argumentos; sin proveedor lanza `IllegalStateException`. |
| `MAX_PDF_SIZE` | Sin consumidor externo encontrado | No | Limita validación PDF | Constante pública: `26_214_400L` bytes (25 MiB). |

## Adaptadores y rutas de consumo

| Área | Adaptador o acceso | APIs del repositorio |
|---|---|---|
| Inbox | `RepositoryInboxEmailSource` | `getInbox`, `refreshInbox`, `moveToTrash`, `restoreFromTrash`, `markAsRead`. |
| Trash | `RepositoryTrashEmailSource` | `getTrash`, `refreshTrash`, `deletePermanently`, `restoreFromTrash`. |
| Email Detail | `RepositoryEmailDetailSource` | Observación, resolución, leído, cuerpo, inline y PDF. |
| PDF externo/SAF | Acceso directo desde efectos Android | `getValidatedCachedPdf`. |
| Search | Lambda `SearchEmailSource` en Factory | `searchEmails`. |
| Compose | Adaptador anónimo `ComposeEmailSource` | `getUserEmail`, primer valor de `getEmailById`, `sendEmail`. |
| Settings | Acceso directo mediante `AppContainer` | `getUserEmail`. |

La mayoría de consumidores están aislados mediante interfaces de feature. Las excepciones actuales son Settings y dos efectos PDF que acceden directamente a `AppContainer.emailRepository`; se registran como consumidores, pero no se modificarán durante el baseline.

## Contratos de resultado expuestos

### Resolución

`EmailResolutionResult` diferencia:

- `Found(email)`.
- `NotFound` confirmado.
- `Failure(reason)` con `INVALID_ID`, ausencia/cambio de sesión, fallos remotos tipados y fallos de lectura/escritura local.

### Acciones

`EmailActionResult` diferencia:

- `Success`.
- `Failure(reason, remoteApplied)`, donde `remoteApplied` indica si Gmail ya cambió antes de fallar Room.

### PDF

`PdfDownloadState` es consumido como `Ready` o `Error(PdfDownloadFailure)`. El repositorio también expone consultas nullable/boolean/File para la misma caché; esas diferencias de forma deben preservarse.

## Coordinador interno

`FolderCommitCoordinator` es `internal` y expone dentro del módulo:

- `nextGeneration()` incrementa y devuelve la generación.
- `currentGeneration()` devuelve la generación sin incrementarla.
- `commitIfValid(generation, block)` serializa el commit y ejecuta el bloque solo si la generación sigue vigente.

Aunque no es API pública de aplicación, es un contrato crítico del futuro refactor porque evita que un refresh antiguo sobrescriba resultados recientes.

## Efectos laterales que atraviesan responsabilidades

- Resolución: Room → proveedor → Room, protegido por lease y single-flight.
- Refresh: proveedor → conversión → reemplazo/fusión Room, coordinado por carpeta.
- Acciones: proveedor primero → Room → reconciliación remota/local si el commit falla.
- Cuerpo: proveedor → limpieza HTML → codec PDF → Room.
- PDF: validación → caché → proveedor → validación → caché protegida.
- Envío y búsqueda: proveedor sin persistencia del repositorio.

Estos órdenes son parte del comportamiento actual; el inventario no autoriza invertirlos ni unificarlos.

## Cierre de la subfase 1.2

- Constructor y dependencias identificados: cumplido.
- 20 métodos públicos y constante pública inventariados: cumplido.
- Consumidores directos e indirectos identificados: cumplido.
- Accesos directos a `AppContainer.emailRepository` registrados: cumplido.
- Estado concurrente y coordinador interno registrados: cumplido.
- Efectos sobre proveedor, Room, caché y sesión documentados: cumplido.
