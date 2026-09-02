# Verificación Subfase 5.3: Robustez de red

## Precondiciones
- Subfases 5.1 y 5.2 implementadas con soporte GZIP, campos parciales y concurrencia saturada.

## Contratos Implementados
1. **Política de Reintentos Uniforme**:
   - Limitado estrictamente a 3 intentos (inicial + 2 reintentos).
   - Aplicable exclusivamente a errores transitorios (IO, 408, 429, 500-599).
   - Errores de cliente (400, 401, 403, 404, etc.) son tratados como permanentes evitando tráfico y ciclos inútiles.
   - Compartido estructuralmente tanto por peticiones de lista (`fetchGmailPage`) como en descargas individuales (`fetchEmailById`).
2. **Diagnóstico Anónimo `NetworkDiagnosticEvent`**:
   - `durationMs`: Medición real utilizando `clock()` determinista.
   - `category`: Clasificación rigurosa en `SUCCESS`, `IO`, `TRANSIENT_HTTP`, `PERMANENT_HTTP`, `NOT_FOUND`, `SESSION_EXPIRED`, `INVALID_RESPONSE` y `CANCELLED`.
   - `attempt`: Registro granular emitido por cada solicitud de la pila, incluso en bloqueos de red o errores de serialización.
   - `mailKey`: Llave codificada criptográficamente usando `SHA-256` truncado (heredada de `MailOpenPerformanceTrace`) que prohíbe el almacenamiento de `emailId` en la capa diagnóstica.
3. **Limpieza de Logs Inseguros**:
   - Todos los usos directos de `Log.d` en `GmailProvider` que exponían identificadores internos de subprocesos, CID, Attachment IDs y tokens OAuth/paginación fueron removidos y reemplazados con el nuevo `networkDiagnosticSink`.
4. **Validación OAuth (Refresh Único)**:
   - Se preservó la configuración del plugin Ktor `Auth`. Este plugin intercepta dinámicamente un `401 Unauthorized` de la red y emite internamente un `refreshTokens`.
   - Este mecanismo impide que nuestra lógica externa detecte un 401 a menos que el `refreshTokens` realmente falle. Por tanto, las llamadas no duplican lógicamente intentos 401.

## Resultados de Pruebas
- **`GmailProviderNetworkRobustnessTest`**:
  - `transient errors exhaust at exactly 3 attempts`: Matriz de errores 408, 429, 500, y 599. Verifica 3 intentos de red, generación idéntica de 3 eventos diagnósticos y retorno categorizado como `TEMPORARY_REMOTE`.
  - `io exceptions exhaust at exactly 3 attempts`: Verificó propagación idéntica para problemas de conexión.
  - `permanent errors execute single logical request`: Verificó que 400, 401 (sin Ktor Auth), 403, 404 evaden bucle y lanzan fallos permanentes en 1 solo intento físico de red.
  - `invalid json executes single request and maps correctly`: Verificó intercepción segura de fallos de Ktor Deserialization.
  - `concurrent lookups with 401 trigger one refresh and two physical requests per email`: Construyó explícitamente el escenario `Auth` sobre Ktor con dos correos simultáneos y un Mock Engine. Confirmó 4 solicitudes en la red (2 fallos 401 iniciales + 2 envíos posteriores correctos) disparados bajo 1 único `refreshTokens` ejecutado, resultando en solamente 2 eventos internos diagnosticados como `SUCCESS` desde la óptica de reintentos.
  - `failed refresh results in single SESSION_EXPIRED and no outer retries`: Demostró que un fallo del servidor en renovar el token OAuth se propaga transparentemente como un `SESSION_EXPIRED` (único intento, red nula extra).

## Análisis Estático y Suite de Regresión
- `./gradlew testDebugUnitTest`: PASADO (677 tests, +5 nuevos tests robustos).
- `./gradlew compileDebugKotlin`: PASADO.
- `./gradlew compileDebugAndroidTestKotlin`: PASADO.
- `./gradlew lintDebug`: PASADO.
- `git diff --check`: PASADO (Sin alertas de trailing whitespace).
- Ausencia verificada de llamadas `Log.d` internas en `GmailProvider`.

## Estado Final
Commit generado: `fix(gmail): harden retries and redact network traces`
