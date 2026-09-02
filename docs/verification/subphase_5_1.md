# Verificación Subfase 5.1: Respuestas parciales y compresión

## Precondiciones
- El estado base incluía los cambios confirmados como `feat(emaildetail): load images progressively`.
- Los cambios flotantes correspondientes a la subfase 4.4 fueron encapsulados en un commit independiente (`feat(webview): complete subphase 4.4`) antes de iniciar 5.1.

## Contratos implementados
1. **Respuestas parciales (Proyecciones)**:
   - `messages.list` utiliza `fields=messages(id,threadId),nextPageToken`.
   - `messages.get(format=full)` utiliza `fields=id,threadId,labelIds,snippet,internalDate,payload`.
2. **Compresión GZIP**:
   - Módulo `io.ktor:ktor-client-encoding` integrado con Ktor `3.0.3`.
   - Plugin `ContentEncoding` con soporte exclusivo de descompresión GZIP habilitado en el `HttpClientFactory` para el cliente de Gmail.
   - Modificación en `defaultRequest` para adjuntar explícitamente el token `(gzip)` en el encabezado `User-Agent` (ej. `MailApp-Android/1.0 (gzip)`), satisfaciendo la negociación de compresión requerida.

## Resultados de Pruebas
- **`GmailPartialResponseTest`**:
  - `messages_list requests exact fields projection`: Verifica que la solicitud inyecta el `fields` exacto requerido.
  - `messages_get full requests exact fields projection`: Verifica que una petición completa solicita el `fields` exacto en conjunto con `format=full`.
- **`GmailClientGzipTest`**:
  - `gmail client sends Accept-Encoding and User-Agent gzip and decompresses response`: Prueba la compresión/descompresión real construyendo un payload comprimido con `GZIPOutputStream` en el `MockEngine`, validando la inyección de encabezados de solicitud y afirmando la correcta deserialización JSON transparente del flujo.
  - `gmail client works with uncompressed response`: Confirma la compatibilidad y no degradación si el servidor omite `Content-Encoding`.
- **Regresión y Suite Total**:
  - Se completaron 667 pruebas. Resultado: **PASADO**.
  - `Authorization` continúa sanitizado; no se rompió la lógica OAuth single-flight ni `OAuthTokenManager`.

## Análisis Estático
- `./gradlew testDebugUnitTest`: PASADO
- `./gradlew compileDebugKotlin`: PASADO
- `./gradlew compileDebugAndroidTestKotlin`: PASADO
- `./gradlew lintDebug`: PASADO
- `git diff --check`: PASADO (Sin alertas de trailing whitespace).

## Estado final
Commit generado: `perf(gmail): enable partial responses and gzip`
