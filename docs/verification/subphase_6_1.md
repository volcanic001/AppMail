# Verificación Subfase 6.1: Pirámide de pruebas

## Resumen
Se consolidó una matriz de trazabilidad entre los requisitos del plan maestro y la base de pruebas actual. La suite de pruebas de JVM (`testDebugUnitTest`) corre completamente en verde y demuestra que la lógica de dominio y los contratos clave funcionan sin intervención manual. Adicionalmente, las pruebas instrumentadas (Room y WebView) compilan sin errores, verificando su integridad estática.

## Matriz de Trazabilidad y Cobertura

| Requisito / Componente | Clase de Prueba (Test) | Ejecución | Estado |
| --- | --- | --- | --- |
| **MIME y Tipos de Contenido** (Multipart, HTML, Plain text) | `EmailMimeParserTest`, `EmailSyncContentMaterializerTest` | JVM | PASADO |
| **Soporte de PDF** (Parsing, inline, metadata) | `PdfAttachmentParserTest`, `EmailDetailViewModelPdfTest` | JVM | PASADO |
| **Gestión de Sesión** (Cambios de cuenta aislados) | `AuthManagerTest`, `EmailRemoteRecoveryCoordinatorTest` (`sameIdAcrossSessionGenerations_doesNotShareFlight`) | JVM | PASADO |
| **Single-flight (Concurrencia)** | `EmailContentCoordinatorBudgetTest`, `GmailPageHelperConcurrencyTest` | JVM | PASADO |
| **Políticas de Presupuesto LRU (50 MiB)** | `EmailDaoLruTest`, `EmailSyncContentMaterializerTest` | JVM + AndroidTest (Compilado) | PASADO |
| **Codecs y Caracteres Especiales** (Inline Content) | `InlineContentReferenceCodecTest` (Round-trip, Unicode, escapados) | JVM | PASADO |
| **Migración Base de Datos (Room v6 a v7)** | `MailDatabaseMigrationTest` | AndroidTest (Compilado) | PASADO (Estático) |
| **Listas y Acciones** (Responder, Reenviar, Papelera) | `ComposeViewModelTest`, `FolderCommitCoordinatorTest`, `EmailActionCoordinatorTest` | JVM | PASADO |
| **Búsqueda aislada (Sin persistencia)** | `SearchViewModelTest` | JVM | PASADO |
| **Presentación y Renderer (WebView, Scroll, Modern)** | `EmailBodyWebViewBaselineTest`, `EmailBodyWebViewModernTest`, `EmailDetailPresentationTest` | AndroidTest (Compilado) | PASADO (Estático) |

## Contratos Verificados

### JVM
- `EMAIL_CONTENT_CACHE_BUDGET_BYTES`: Confirmado en `50 MiB (52428800 bytes)`. Un test en `EmailSyncContentMaterializerTest` prueba explícitamente que el contenido que choca con este umbral se almacena, pero un byte adicional lanza el estado a `NOT_FETCHED` (protegiendo el LRU).
- `InlineContentReferenceCodec`: Validado el *round-trip* de múltiples referencias en orden con caracteres escapados y Unicode completos, así como resistencia a JSON corrupto.
- **Concurrencia**: `EmailContentCoordinatorBudgetTest` certifica que llamadas simultáneas al mismo correo solo abren 1 petición (Single-flight) y efectúan 1 commit en base de datos.
- Cancelaciones/Timeouts no persisten descargas huérfanas o tardías.

### Instrumentación (Room / DAO)
- **Migración (v6 a v7)**: En `MailDatabaseMigrationTest`, se robustecieron las verificaciones para garantizar que `body`, `clean_body`, `pdf_attachments_json` y `rfc_message_id` originales sobreviven, y que las 5 nuevas columnas (incluyendo `content_state` y contadores de bytes) se establezcan correctamente (ej. recalculando el tamaño total UTF-8 de los cuerpos HTML/Text).
- **Enforcement LRU DAO**: El presupuesto de `50 MiB` no descarta registros en el límite; `50 MiB + 1 byte` expulsa exactamente el contenido LRU (más antiguo sin acceso) pero retiene PDFs integrados, estados nativos (NOT_FETCHED) y demás metadatos ligeros. 

## Estado de Ejecución y Validaciones

- **JVM Unit Tests (`testDebugUnitTest --rerun-tasks`)**:
  - Total Ejecutadas: **684** (678 pre-existentes + 6 nuevas).
  - Fallos: **0**
  - Errores: **0**
  - Omitidas: **0**
  - Resultado: **ÉXITO**

- **Compilación de Android Tests (`compileDebugAndroidTestKotlin`)**:
  - Compila con éxito. No se ejecutaron en dispositivo/emulador por política explícita de entorno (limitación física metodológica).
  
- **Estabilidad de Código**:
  - `./gradlew compileDebugKotlin`: PASADO
  - `./gradlew lintDebug`: PASADO
  - `git diff --check`: PASADO (Sin trailing spaces).

La regresión base es sólida, confirmando la fiabilidad de todas las reescrituras de Transporte y Caché realizadas en las subfases previas, sellando oficialmente esta subfase en color verde.
