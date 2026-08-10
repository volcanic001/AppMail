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

Total directo identificado después de la subfase 3.4: 110 casos. Las suites de integración de Email Detail añaden evidencia del consumidor, pero no cubren las APIs todavía ausentes enumeradas abajo.

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
| `downloadPdf` | No | No | No | Sí | Solo ausencia de residuos al cancelar | No | Mínima | Faltan pre/postvalidación, límites, cache hit/inválida, errores tipados, sesión y escritura correcta. |
| `isPdfCached` | No | N/A | No | No | No | No | Ausente | Falta validar archivo válido, inexistente, vacío, truncado y sobredimensionado. |
| `checkPdfCache` | No | N/A | No | No | No | No | Ausente | Falta proteger `Ready?` y tamaño reportado. |
| `getValidatedCachedPdf` | No | N/A | No | No | No | No | Ausente | Falta proteger `File?` válido y rechazo de archivos inválidos. |
| `getUserEmail` | No | No | No | No | N/A | Frescura no probada | Ausente | Falta proveedor dinámico, null, excepción y cancelación. |
| `sendEmail` | Solo fuente falsa | No | Solo capa Compose | Solo capa Compose | N/A | No | Ausente | Falta delegación exacta, `ReplyContext`, proveedor ausente, error y cancelación directa. |
| `MAX_PDF_SIZE` | N/A | N/A | N/A | N/A | No | N/A | Ausente | Falta comprobar el límite exacto y un byte por encima mediante `downloadPdf`. |

Resumen de los 20 métodos:

- Cobertura alta: 14 métodos (`resolveEmailById`, `fetchAndCacheBody`, `downloadInlineImages`, `injectInlineImages`, cuatro acciones, tres lecturas, dos refresh y búsqueda).
- Cobertura media: 0 métodos.
- Cobertura mínima: 1 método (`downloadPdf`).
- Cobertura directa ausente: 5 métodos.
- La constante pública PDF también carece de prueba de frontera.

## Matriz transversal

| Contrato transversal | Evidencia actual | Estado | Trabajo posterior requerido |
|---|---|---|---|
| Provider obtenido dinámicamente | Resolución y búsqueda obtienen el provider vigente por llamada | Parcial | Comprobar identidad y envío en etapa 4. |
| Lease antes de trabajo remoto | Resolución, acciones, refresh y cuerpo cubiertos; PDF observado en código | Parcial | Caracterizar PDF durante invalidación. |
| `CancellationException` no se transforma | Fuerte en resolución, acciones, refresh, búsqueda, cuerpo e inline; dos casos PDF | Parcial | Completar PDF y añadir identidad y envío. |
| No hay escrituras después de cambiar sesión | Resolución, rechazo de commit en refresh y cuerpo cubiertos | Parcial | Añadir PDF. |
| Orden proveedor → Room | Acciones y cuerpo cubiertos | Parcial | Preservar como regresión en refresh y resolución. |
| Datos ricos sobreviven refresh/resolución | Cubierto por resolución, refresh y DAO | Alto | Reutilizar como regresión, sin duplicar casos. |
| Inbox y Trash no se invalidan entre sí | Tres contratos cruzados, cada uno repetido tres veces | Alto | Mantener como regresión. |
| Resultado remoto no se persiste en Search | Room verificado antes y después de éxito, error y cancelación | Alto | Mantener como regresión. |
| Caché PDF atómica y sin residuos | `PdfCacheManagerTest` y cancelación parcial | Parcial | Probar el límite del repositorio y cambios de sesión. |
| Ausencia de proveedor | Resolución, acciones, refresh, búsqueda, cuerpo e inline cubiertos | Parcial | Añadir PDF, identidad y envío. |

## Huecos priorizados para las etapas siguientes

### Prioridad crítica

- Escrituras tardías de refresh, cuerpo o PDF después de invalidar sesión.
- Cancelación absorbida o convertida en resultados ordinarios.
- Semántica exacta de caché y validación PDF.

### Prioridad alta

- Contratos de cuerpo, HTML limpio, metadata PDF e imágenes inline (cerrados en la subfase 3.4).
- Delegación exacta y provider vigente en identidad y envío.

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

### Prioridad media

- Método público `isPdfCached`, aunque no tenga consumidor actual.
- Frontera pública `MAX_PDF_SIZE`.

## Clasificación de comportamientos sospechosos

La lectura estática identificó comportamientos que deben caracterizarse antes de juzgarlos:

- `refreshInbox` y `refreshTrash` devuelven el resultado remoto aunque el commit protegido no llegue a ejecutarse.
- `fetchAndCacheBody` devuelve el resultado remoto aunque `writeGuard.commit` sea rechazado (confirmado en la subfase 3.3 como comportamiento heredado y protegido).
- `injectInlineImages` reemplaza en orden de iteración del mapa: un CID corto puede sustituir el prefijo de otro más largo, y el resultado depende del orden (caracterizado en la subfase 3.4 como comportamiento heredado; identificado para el futuro refactor lógico).
- `searchEmails` no usa `SessionWriteGuard`; depende únicamente del proveedor vigente.
- `isPdfCached` es público pero no tiene consumidor de producción.
- `sendEmail` sin proveedor lanza una excepción con mensaje en español, mientras otras APIs devuelven resultados vacíos o tipados.

Estos puntos no se clasifican como bugs en esta etapa. Las pruebas futuras deben fijar primero el comportamiento real. Si alguno demuestra mezcla de sesiones, pérdida de datos o un riesgo de seguridad, el baseline se detendrá sin modificar producción.

## Trazabilidad hacia las próximas etapas

- Etapa 2 cerró lecturas, refresh, búsqueda y coordinación temporal; no quedan huecos asignados a esta etapa.
- Etapa 3 cerrada en la subfase 3.4: resolución, acciones, cuerpo e imágenes inline con cobertura alta.
- Etapa 4 cerrará PDF, identidad y envío.
- Etapa 5 ejecutará la matriz completa y la integración real con Gmail.

## Cierre de la subfase 1.3

- Matriz creada para los 20 métodos y la constante pública: cumplido.
- Cobertura directa separada de cobertura indirecta: cumplido.
- Suites existentes y número de casos inventariados: cumplido.
- Huecos concretos priorizados: cumplido.
- Contratos transversales de sesión, cancelación y concurrencia registrados: cumplido.
- Comportamientos sospechosos documentados sin corregir lógica: cumplido.
