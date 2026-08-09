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
| `EmailResolutionContractsTest` | 26 | Cache-first, errores tipados, merge, sesión, single-flight y cancelación leader/follower. |
| `EmailRepositoryActionContractsTest` | 20 | Cuatro acciones, remote-first, errores, reconciliación, sesión y cancelación. |
| `SafeRefreshContractsTest` | 11 | Replace/merge, páginas parciales, datos enriquecidos y generaciones obsoletas. |
| `PartialPageContractsTest` | 1 | Integración Gmail parcial sin pérdida de caché ni avance de token. |
| `PdfCancellationContractsTest` | 2 | Cancelación de descarga y de commit PDF. |
| `FolderCommitCoordinatorTest` | 3 | Generación vigente, exclusión mutua y registro de nueva generación. |
| `EmailRepositoryReadSyncSearchContractsTest` | 20 | Lecturas Room, refresh, búsqueda remota y concurrencia cruzada. |

Total directo identificado al cerrar la etapa 2: 83 casos. Las suites de integración de Email Detail añaden evidencia del consumidor, pero no cubren las APIs todavía ausentes enumeradas abajo.

## Matriz por API

| API | Éxito | Sin proveedor / sesión | Error ordinario | Cancelación | Persistencia / caché | Concurrencia | Nivel actual | Hueco concreto |
|---|---|---|---|---|---|---|---|---|
| `resolveEmailById` | Sí | Sí | Sí | Sí | Sí | Sí | Alta | Auditar ramas exteriores y frescura del provider; no requiere una suite nueva completa. |
| `getInbox` | Sí | N/A | N/A | Collector cancelable | Sí | Flow vivo | Alta | Cerrado en subfase 2.1: emisión inicial, orden y actualización protegidos. |
| `getTrash` | Sí | N/A | N/A | Collector cancelable | Sí | Flow vivo | Alta | Cerrado en subfase 2.1: emisión inicial, orden y eliminación protegidos. |
| `getEmailById` | Sí | N/A | N/A | Collector cancelable | Sí | Flow vivo | Alta | Cerrado en subfase 2.1: null → insert/update/delete y mapeo enriquecido. |
| `refreshInbox` | Sí | Sí | Sí | Sí | Sí | Misma y distinta carpeta | Alta | Cerrado: generaciones obsoletas, paginación, exclusión mutua e independencia de Trash protegidas. |
| `refreshTrash` | Sí | Sí | Sí | Sí | Sí | Misma y distinta carpeta | Alta | Cerrado: generaciones obsoletas, paginación, exclusión mutua e independencia de Inbox protegidas. |
| `searchEmails` | Sí | Sí | Sí | Sí | Sí | Provider dinámico | Alta | Cerrado en subfase 2.3: delegación exacta, resultado efímero, provider vigente, errores y cancelación protegidos. |
| `moveToTrash` | Sí | Sí | Sí | Representativa | Sí | Reconciliación | Alta | Confirmar matriz final y que las ramas compartidas representan las cuatro acciones. |
| `restoreFromTrash` | Sí | Parcial compartida | Sí | Compartida | Sí | Reconciliación | Alta | Completar únicamente si la auditoría encuentra una rama exclusiva sin protección. |
| `deletePermanently` | Sí | Sí | Sí | Sí | Sí | Reconciliación | Alta | Sin hueco estructural mayor identificado. |
| `markAsRead` | Sí | Parcial compartida | Sí | Compartida | Sí | Reconciliación | Alta | Completar únicamente ramas exclusivas; preservar carpeta original. |
| `fetchAndCacheBody` | No | No | No | No | No | No | Ausente | Falta retorno, limpieza, metadata PDF, commit, sesión y ausencia de escrituras parciales. |
| `downloadInlineImages` | No | No | No | Provider aislado | N/A | No | Ausente | Falta cortocircuito, delegación, parcial, proveedor ausente, error y cancelación en repositorio. |
| `injectInlineImages` | No | N/A | N/A | N/A | N/A | N/A | Ausente | Falta proteger las tres variantes CID, no coincidencias e IDs similares. |
| `downloadPdf` | No | No | No | Sí | Solo ausencia de residuos al cancelar | No | Mínima | Faltan pre/postvalidación, límites, cache hit/inválida, errores tipados, sesión y escritura correcta. |
| `isPdfCached` | No | N/A | No | No | No | No | Ausente | Falta validar archivo válido, inexistente, vacío, truncado y sobredimensionado. |
| `checkPdfCache` | No | N/A | No | No | No | No | Ausente | Falta proteger `Ready?` y tamaño reportado. |
| `getValidatedCachedPdf` | No | N/A | No | No | No | No | Ausente | Falta proteger `File?` válido y rechazo de archivos inválidos. |
| `getUserEmail` | No | No | No | No | N/A | Frescura no probada | Ausente | Falta proveedor dinámico, null, excepción y cancelación. |
| `sendEmail` | Solo fuente falsa | No | Solo capa Compose | Solo capa Compose | N/A | No | Ausente | Falta delegación exacta, `ReplyContext`, proveedor ausente, error y cancelación directa. |
| `MAX_PDF_SIZE` | N/A | N/A | N/A | N/A | No | N/A | Ausente | Falta comprobar el límite exacto y un byte por encima mediante `downloadPdf`. |

Resumen de los 20 métodos:

- Cobertura alta: 11 métodos (`resolveEmailById`, cuatro acciones, tres lecturas, dos refresh y búsqueda).
- Cobertura media: 0 métodos.
- Cobertura mínima: 1 método (`downloadPdf`).
- Cobertura directa ausente: 8 métodos.
- La constante pública PDF también carece de prueba de frontera.

## Matriz transversal

| Contrato transversal | Evidencia actual | Estado | Trabajo posterior requerido |
|---|---|---|---|
| Provider obtenido dinámicamente | Resolución y búsqueda obtienen el provider vigente por llamada | Parcial | Cambiar provider entre llamadas y comprobar identidad y envío. |
| Lease antes de trabajo remoto | Resolución, acciones y refresh cubiertos; PDF/cuerpo observados en código | Parcial | Caracterizar cuerpo y PDF durante invalidación. |
| `CancellationException` no se transforma | Fuerte en resolución, acciones, refresh y búsqueda; dos casos PDF | Parcial | Añadir cuerpo, inline, identidad y envío. |
| No hay escrituras después de cambiar sesión | Resolución y rechazo de commit en refresh cubiertos | Parcial | Añadir cuerpo y PDF. |
| Orden proveedor → Room | Acciones cubiertas | Parcial | Añadir cuerpo; preservar orden de refresh y resolución ya observado. |
| Datos ricos sobreviven refresh/resolución | Cubierto por resolución, refresh y DAO | Alto | Reutilizar como regresión, sin duplicar casos. |
| Inbox y Trash no se invalidan entre sí | Tres contratos cruzados, cada uno repetido tres veces | Alto | Mantener como regresión. |
| Resultado remoto no se persiste en Search | Room verificado antes y después de éxito, error y cancelación | Alto | Mantener como regresión. |
| Caché PDF atómica y sin residuos | `PdfCacheManagerTest` y cancelación parcial | Parcial | Probar el límite del repositorio y cambios de sesión. |
| Ausencia de proveedor | Resolución, acciones, refresh y búsqueda cubiertos | Parcial | Añadir inline, PDF, identidad y envío. |

## Huecos priorizados para las etapas siguientes

### Prioridad crítica

- Escrituras tardías de refresh, cuerpo o PDF después de invalidar sesión.
- Cancelación absorbida o convertida en resultados ordinarios.
- Semántica exacta de caché y validación PDF.

### Prioridad alta

- Contratos de cuerpo, HTML limpio, metadata PDF e imágenes inline.
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

### Prioridad media

- Variantes exactas de reemplazo CID.
- Método público `isPdfCached`, aunque no tenga consumidor actual.
- Frontera pública `MAX_PDF_SIZE`.

## Clasificación de comportamientos sospechosos

La lectura estática identificó comportamientos que deben caracterizarse antes de juzgarlos:

- `refreshInbox` y `refreshTrash` devuelven el resultado remoto aunque el commit protegido no llegue a ejecutarse.
- `fetchAndCacheBody` devuelve el resultado remoto aunque `writeGuard.commit` sea rechazado.
- `searchEmails` no usa `SessionWriteGuard`; depende únicamente del proveedor vigente.
- `isPdfCached` es público pero no tiene consumidor de producción.
- `sendEmail` sin proveedor lanza una excepción con mensaje en español, mientras otras APIs devuelven resultados vacíos o tipados.

Estos puntos no se clasifican como bugs en esta etapa. Las pruebas futuras deben fijar primero el comportamiento real. Si alguno demuestra mezcla de sesiones, pérdida de datos o un riesgo de seguridad, el baseline se detendrá sin modificar producción.

## Trazabilidad hacia las próximas etapas

- Etapa 2 cerró lecturas, refresh, búsqueda y coordinación temporal; no quedan huecos asignados a esta etapa.
- Etapa 3 auditará resolución/acciones y cerrará cuerpo e imágenes inline.
- Etapa 4 cerrará PDF, identidad y envío.
- Etapa 5 ejecutará la matriz completa y la integración real con Gmail.

## Cierre de la subfase 1.3

- Matriz creada para los 20 métodos y la constante pública: cumplido.
- Cobertura directa separada de cobertura indirecta: cumplido.
- Suites existentes y número de casos inventariados: cumplido.
- Huecos concretos priorizados: cumplido.
- Contratos transversales de sesión, cancelación y concurrencia registrados: cumplido.
- Comportamientos sospechosos documentados sin corregir lógica: cumplido.
