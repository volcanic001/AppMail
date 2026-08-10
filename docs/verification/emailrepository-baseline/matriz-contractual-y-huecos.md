# Matriz contractual y análisis de huecos de EmailRepository

## Identificación y criterio

- Etapa: 1 — Congelar estado, contratos y cobertura.
- Subfase: 1.3 — Matriz contractual y análisis de huecos.
- Captura: 2026-08-08 16:45:32 -0600 (CST).
- Objetivo: distinguir protección directa real de cobertura indirecta o inexistente.

Escala usada:

- **Alta:** pruebas directas del repositorio cubren éxito y los principales contratos de error, sesión o concurrencia.
- **Media:** existe cobertura directa sustancial, pero faltan ejes públicos relevantes.
- **Mínima:** solo hay uno o dos escenarios directos.
- **Ausente:** no existe prueba que invoque directamente esa API en el repositorio.

Las pruebas de ViewModel que sustituyen el repositorio por un `EmailSource` falso no cuentan como caracterización de `EmailRepository`. Las pruebas de DAO, provider o `PdfCacheManager` ayudan como soporte, pero tampoco sustituyen una prueba del límite del repositorio.

## Evidencia directa existente

| Suite | Casos | Protección principal |
|---|---:|---|
| `EmailResolutionContractsTest` | 29 | Cache-first, provider vigente, errores, merge, sesión, single-flight, limpieza y cancelación leader/follower. |
| `EmailRepositoryActionContractsTest` | 25 | Cuatro acciones, remote-first, ausencia, errores, cancelación y reconciliación completa/parcial. |
| `SafeRefreshContractsTest` | 11 | Replace/merge, páginas parciales, datos enriquecidos y generaciones obsoletas. |
| `PartialPageContractsTest` | 1 | Integración Gmail parcial sin pérdida de caché ni avance de token. |
| `PdfCancellationContractsTest` | 2 | Cancelación de descarga y de commit PDF. |
| `FolderCommitCoordinatorTest` | 3 | Generación vigente, exclusión mutua y registro de nueva generación. |
| `EmailRepositoryReadSyncSearchContractsTest` | 20 | Lecturas Room, refresh, búsqueda remota y concurrencia cruzada. |
| `EmailRepositoryContentContractsTest` | 19 | Cuerpo (éxito, ausencias, errores, cancelación, sesión, fallo de commit) e imágenes inline con delegación exacta, tres variantes CID y sensibilidad a prefijos/orden. |
| `EmailRepositoryPdfContractsTest` | 21 | Prevalidación, cache hit, descarga, postvalidación, limpieza de caché inválida, los seis PdfDownloadFailure, tres consultas de caché, sesión, cancelación reforzada y atomicidad. |
| `EmailRepositoryAccountSendContractsTest` | 9 | Identidad (provider dinámico, null, error, cancelación) y envío (delegación exacta, sin provider, login/logout, error, cancelación). |

Total directo identificado después de la subfase 4.4: 140 casos. La cobertura contractual de EmailRepository es completa.

## Matriz por API

| API | Éxito | Sin proveedor / sesión | Error ordinario | Cancelación | Persistencia / caché | Concurrencia | Nivel actual | Hueco concreto |
|---|---|---|---|---|---|---|---|---|
| `resolveEmailById` | Sí | Sí | Sí | Sí | Sí | Sí | Alta | Cerrado en subfase 3.1: provider vigente, limpieza terminal y cancelación leader/follower protegidos. |
| `getInbox` | Sí | N/A | N/A | Collector cancelable | Sí | Flow vivo | Alta | Cerrado en subfase 2.1: emisión inicial, orden y actualización protegidos. |
| `getTrash` | Sí | N/A | N/A | Collector cancelable | Sí | Flow vivo | Alta | Cerrado en subfase 2.1: emisión inicial, orden y eliminación protegidos. |
| `getEmailById` | Sí | N/A | N/A | Collector cancelable | Sí | Flow vivo | Alta | Cerrado en subfase 2.1: null → insert/update/delete y mapeo enriquecido. |
| `refreshInbox` | Sí | Sí | Sí | Sí | Sí | Misma y distinta carpeta | Alta | Cerrado: generaciones obsoletas, paginación, exclusión mutua e independencia de Trash protegidas. |
| `refreshTrash` | Sí | Sí | Sí | Sí | Sí | Misma y distinta carpeta | Alta | Cerrado: generaciones obsoletas, paginación, exclusión mutua e independencia de Inbox protegidas. |
| `searchEmails` | Sí | Sí | Sí | Sí | Sí | Provider dinámico | Alta | Cerrado en subfase 2.3: delegación exacta, resultado efímero, provider vigente, errores y cancelación protegidos. |
| `moveToTrash` | Sí | Sí | Sí | Sí | Sí | Completa/parcial | Alta | Cerrado: remote-first, cancelación, orden Inbox → Trash y estado reconciliado protegidos. |
| `restoreFromTrash` | Sí | Sí | Sí | Sí | Sí | Orden Trash → Inbox | Alta | Cerrado: ramas públicas propias y orden de reconciliación protegidos. |
| `deletePermanently` | Sí | Sí | Sí | Sí | Sí | Trash | Alta | Cerrado: ausencia, cancelación y reconciliación de Trash protegidas. |
| `markAsRead` | Sí | Sí | Sí | Sí | Sí | Completa/parcial | Alta | Cerrado: conserva carpeta original y cubre ausencia, cancelación y reconciliación. |
| `fetchAndCacheBody` | Sí | Sí | Sí | Sí | Sí | Sesión | Alta | Cerrado en subfases 3.2 y 3.3: retorno de instancia, limpieza, metadata PDF, cuerpo nulo, lista vacía, ausencias de lease/proveedor/resultado, errores y cancelación, cambio de sesión sin escritura tardía y fallo local de commit. Un commit rechazado todavía devuelve el resultado remoto (comportamiento heredado confirmado). |
| `downloadInlineImages` | Sí | Sí | Sí | Sí | N/A | N/A | Alta | Cerrado en subfase 3.4: cortocircuito sin resolver el provider, delegación exacta de emailId/referencias/orden con retorno de la misma instancia, proveedor ausente, error y cancelación propagados. |
| `injectInlineImages` | Sí | N/A | N/A | N/A | N/A | N/A | Alta | Cerrado en subfase 3.4: mapa vacío devuelve la misma instancia, tres variantes CID sustituidas en todas las apariciones, sin coincidencias ni mayúsculas intactas e IDs similares caracterizados por orden/prefijo. |
| `downloadPdf` | Prevalidación y descarga | Provider y sesión | Sí (convertido) | Sí | Cache hit, limpieza de inválida y persistencia | Sesión | Alta | Cerrado en subfases 4.1–4.3: prevalidación, cache hit, descarga, postvalidación, los seis `PdfDownloadFailure`, `stableId`, limpieza de caché inválida, error de escritura, cancelación reforzada y sesión con cero escrituras tardías. |
| `isPdfCached` | Sí | N/A | N/A | N/A | Sí | N/A | Alta | Cerrado en subfase 4.1: archivo válido, inexistente, vacío, truncado y sobredimensionado. |
| `checkPdfCache` | Sí | N/A | N/A | N/A | Sí | N/A | Alta | Cerrado en subfase 4.1: `Ready(tamaño)` para válido y null para los cuatro casos inválidos. |
| `getValidatedCachedPdf` | Sí | N/A | N/A | N/A | Sí | N/A | Alta | Cerrado en subfase 4.1: `File?` válido con la ruta cacheada correcta y rechazo de inválidos. |
| `getUserEmail` | Sí | Sí | Sí | Sí | N/A | Provider dinámico | Alta | Cerrado en subfase 4.4: provider vigente en cada llamada, null, excepción y cancelación. |
| `sendEmail` | Sí | Sí | Sí | Sí | N/A | Provider dinámico | Alta | Cerrado en subfase 4.4: delegación exacta de los seis argumentos y `ReplyContext`, ausencia de provider con mensaje heredado, login/logout, error y cancelación. |
| `MAX_PDF_SIZE` | N/A | N/A | N/A | N/A | Sí | N/A | Alta | Cerrado en subfase 4.1: frontera exacta (26 214 400) aceptada y un byte superior rechazado mediante `downloadPdf`. |

Resumen de los 20 métodos:

- Cobertura alta: 20 métodos (toda la API pública de EmailRepository).
- Cobertura media: 0 métodos.
- Cobertura mínima: 0 métodos.
- Cobertura directa ausente: 0 métodos.
- La constante pública PDF quedó cubierta en su frontera exacta y un byte superior.
- La cobertura contractual de EmailRepository es completa.

## Matriz transversal

| Contrato transversal | Evidencia actual | Estado | Trabajo posterior requerido |
|---|---|---|---|
| Provider obtenido dinámicamente | Resolución, búsqueda, identidad y envío obtienen el provider vigente por llamada | Alto | Mantener como regresión. |
| Lease antes de trabajo remoto | Resolución, acciones, refresh, cuerpo y PDF (C21 captura antes de invalidación) cubiertos | Alto | Mantener como regresión. |
| `CancellationException` no se transforma | Fuerte en las 20 APIs; identidad y envío sellados en 4.4 | Alto | Mantener como regresión. |
| No hay escrituras después de cambiar sesión | Resolución, rechazo de commit en refresh, cuerpo y PDF (C21 sesión nueva sin contaminación) cubiertos | Alto | Mantener como regresión. |
| Orden proveedor → Room | Acciones y cuerpo cubiertos | Parcial | Preservar como regresión en refresh y resolución. |
| Datos ricos sobreviven refresh/resolución | Cubierto por resolución, refresh y DAO | Alto | Reutilizar como regresión, sin duplicar casos. |
| Inbox y Trash no se invalidan entre sí | Tres contratos cruzados, cada uno repetido tres veces | Alto | Mantener como regresión. |
| Resultado remoto no se persiste en Search | Room verificado antes y después de éxito, error y cancelación | Alto | Mantener como regresión. |
| Caché PDF atómica y sin residuos | `PdfCacheManagerTest` (22 JVM), cancelación parcial, prevalidación, consultas de caché, descarga, postvalidación, limpieza de caché inválida, error de escritura, lease ausente, limpieza rechazada y rechazo de escritura tardía | Alto | Mantener como regresión. |
| Ausencia de proveedor | Resolución, acciones, refresh, búsqueda, cuerpo, inline, PDF e identidad y envío cubiertos | Alto | Mantener como regresión. |

## Estado de los huecos priorizados

### Prioridad crítica — cerrada contractualmente

- Escrituras tardías de refresh, cuerpo o PDF después de invalidar sesión: cubiertas en las etapas 2–4.
- Cancelación absorbida o convertida en resultados ordinarios: cubierta en las 20 APIs públicas.
- Semántica exacta de caché y validación PDF: cubierta en las subfases 4.1–4.3.

### Prioridad alta — cerrada contractualmente

- Contratos de cuerpo, HTML limpio, metadata PDF e imágenes inline (cerrados en la subfase 3.4).
- Delegación exacta y provider vigente en identidad y envío (cerrados en la subfase 4.4).

### Hueco cerrado en la subfase 2.1

- Flows públicos de lectura, aislamiento de carpetas y mapeo Room → Domain.

### Huecos cerrados en la subfase 2.2

- Refresh sin lease o provider.
- Delegación de tokens y retorno de páginas completas.
- Errores remotos, cancelación, commit rechazado y excepción local sin mutación de Room.

### Huecos cerrados en la subfase 2.3

- Delegación exacta de query y token, incluida la respuesta parcial completa del provider.
- Resultado remoto efímero sin persistencia en Room.
- Provider vigente resuelto en cada llamada, ausencia de provider, errores y cancelación.

### Huecos cerrados en la subfase 2.4

- Primeras páginas simultáneas de Inbox y Trash conservan ambos commits.
- Una primera página de una carpeta no invalida la paginación activa de la otra.
- Exclusión mutua y generaciones de la misma carpeta verificadas tres veces sin flakiness.

### Huecos cerrados en la subfase 3.1

- Provider vigente, limpieza de vuelos terminales y cancelación conjunta leader/follower en resolución.
- Ramas propias sin lease/provider y cancelación remota de las cuatro acciones.
- Estado final de reconciliaciones completas y parciales, además de orden y `remoteApplied`.

### Huecos cerrados en la subfase 3.2

- Retorno de la misma instancia de `BodyFetchResult` y delegación exacta del `emailId`.
- Persistencia atómica de body crudo, body limpio, metadata PDF, `pdfMetadataScanned` y `hasAttachments` observada como una sola actualización del Flow de Room.
- Orden observable `gmail.fetchBody → room.commit` y preservación de los campos no relacionados del correo.
- Cuerpo nulo normalizado sin transformación observable: fila vacía se mantiene vacía y fila con cuerpo conserva `body`/`cleanBody` preexistentes.
- Lista PDF vacía como resultado autoritativo sobre metadata antigua (`hasAttachments=false`, JSON `[]`).

### Huecos cerrados en la subfase 3.3

- Lease ausente: sin proveedor, sin commit y Room intacto.
- Proveedor ausente y resultado remoto nulo: retorno null sin commit ni cambios locales.
- Excepción ordinaria y cancelación remota: propagación de la misma instancia, sin commit ni escrituras.
- Cambio de sesión durante la descarga con `SessionWriteGuardImpl` real: el commit del lease antiguo es rechazado y la fila de la sesión nueva no se contamina; el resultado remoto antiguo todavía se devuelve (comportamiento heredado confirmado, no corregido).
- Fallo local de commit: propagación de la misma instancia y entidad conservada íntegramente.

### Huecos cerrados en la subfase 3.4

- Referencias vacías en `downloadInlineImages`: mapa vacío sin resolver siquiera el provider.
- Delegación exacta de emailId, referencias y orden con retorno de la misma instancia del mapa, sin filtrar resultados parciales.
- Proveedor ausente, excepción ordinaria y cancelación en `downloadInlineImages` con propagación de la misma instancia.
- `injectInlineImages` con mapa vacío: devuelve la misma instancia del HTML.
- Sustitución de todas las apariciones de `cid:id`, `cid:&lt;id&gt;` y `cid:<id>`.
- Sin coincidencias y diferencias de mayúsculas: el HTML permanece intacto.
- IDs similares: sensibilidad al orden del mapa y reemplazo de prefijos caracterizada como comportamiento heredado.

### Huecos cerrados en la subfase 4.1

- Prevalidación de `downloadPdf`: MIME, extensión, `attachmentId` y límite declarado, sin resolver provider, sin `downloadAttachment`, sin commit y sin crear archivos.
- Cache hit de `downloadPdf`: usa `metadata.stableId`, evita la red y conserva intacto el archivo.
- Frontera de `MAX_PDF_SIZE`: 26 214 400 aceptado y un byte superior rechazado.
- `isPdfCached`, `checkPdfCache` y `getValidatedCachedPdf`: archivo válido aceptado y ausente/vacío/truncado/sobredimensionado rechazado.

### Huecos cerrados en la subfase 4.2

- Descarga válida con `stableId` (partId recortado prevalece sobre attachmentId), persistencia exacta y orden `gmail.downloadAttachment → room.commit`.
- Postvalidación completa: contenido vacío (`EMPTY_CONTENT`), tamaño real excesivo (`TOO_LARGE`) con `ByteArray(MAX+1)` y firma %PDF- válida, y firma inválida (`INVALID_PDF`), sin commit ni archivos.
- Provider ausente con lease válido → `NO_PROVIDER` sin `downloadAttachment`.
- `IOException` remota → `NETWORK` sin propagar la excepción.
- Error de escritura con `PdfCacheManager` bloqueado (raíz es un archivo) → `CACHE_WRITE` con intento de commit y sin residuos.
- Caché inválida: limpieza → descarga → almacenamiento, con orden `room.commit → gmail.downloadAttachment → room.commit`.
- Los seis valores de `PdfDownloadFailure` quedan cubiertos por contratos del repositorio.

### Huecos cerrados en la subfase 4.3

- Sesión ausente desde el inicio: `capture()` nulo → `NO_PROVIDER` sin provider, descarga ni commit; archivo inválido preexistente intacto.
- Limpieza de caché inválida con commit rechazado: no continúa hacia provider ni descarga; archivo intacto sin temporales.
- Cambio real de sesión con `SessionWriteGuardImpl`: descarga antigua capturada con gen=1, invalidar y activar gen=2, sesión nueva descarga y persiste en el mismo `stableId`, al liberar la antigua su commit es rechazado → `NO_PROVIDER`; caché conserva exclusivamente los bytes de la sesión nueva sin sobrescritura.
- Cancelación reforzada: una llamada exacta al provider, conteos de commit y orden de eventos verificados en ambos casos existentes sin aumentar el conteo.
- Contrato transversal «Caché PDF atómica y sin residuos» alcanza cobertura alta.

### Huecos cerrados en la subfase 4.4

- `getUserEmail` con provider dinámico (tres proveedores sucesivos sin reutilización), resultado nulo, excepción y cancelación.
- `sendEmail` con delegación exacta de los seis argumentos y `ReplyContext`, ausencia de provider con mensaje heredado `No hay proveedor activo`, login/logout dinámico, error y cancelación.
- Todos los contratos de identidad y envío conservan la fila Room y el PDF cacheado previamente intactos, sin commits ni temporales.
- Los 20 métodos públicos de `EmailRepository` alcanzan cobertura alta.
- Contratos transversales «Provider dinámico», «Cancelación no se transforma» y «Ausencia de proveedor» alcanzan cobertura alta.

## Clasificación de comportamientos sospechosos

La lectura estática identificó comportamientos que deben caracterizarse antes de juzgarlos:

- `refreshInbox` y `refreshTrash` devuelven el resultado remoto aunque el commit protegido no llegue a ejecutarse.
- `fetchAndCacheBody` devuelve el resultado remoto aunque `writeGuard.commit` sea rechazado (confirmado en la subfase 3.3 como comportamiento heredado y protegido).
- `injectInlineImages` reemplaza en orden de iteración del mapa: un CID corto puede sustituir el prefijo de otro más largo, y el resultado depende del orden (caracterizado en la subfase 3.4 como comportamiento heredado; identificado para el futuro refactor lógico).
- `searchEmails` no usa `SessionWriteGuard`; depende únicamente del proveedor vigente.
- `isPdfCached` es público pero no tiene consumidor de producción.
- `sendEmail` sin proveedor lanza una excepción con mensaje en español, mientras otras APIs devuelven resultados vacíos o tipados (confirmado en la subfase 4.4 como comportamiento heredado).

Estos puntos no se clasifican como bugs en esta etapa. Las pruebas futuras deben fijar primero el comportamiento real. Si alguno demuestra mezcla de sesiones, pérdida de datos o un riesgo de seguridad, el baseline se detendrá sin modificar producción.

## Trazabilidad hacia las próximas etapas

- Etapa 2 cerró lecturas, refresh, búsqueda y coordinación temporal; no quedan huecos asignados a esta etapa.
- Etapa 3 cerrada en la subfase 3.4: resolución, acciones, cuerpo e imágenes inline con cobertura alta.
- Etapa 4 cerrada en la subfase 4.4: PDF, identidad y envío con cobertura alta.
- Etapa 5 ejecutará la validación integral de toda la matriz y la integración real con Gmail.

## Cierre de la subfase 1.3

- Matriz creada para los 20 métodos y la constante pública: cumplido.
- Cobertura directa separada de cobertura indirecta: cumplido.
- Suites existentes y número de casos inventariados: cumplido.
- Huecos concretos priorizados: cumplido.
- Contratos transversales de sesión, cancelación y concurrencia registrados: cumplido.
- Comportamientos sospechosos documentados sin corregir lógica: cumplido.
