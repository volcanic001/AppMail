# Verificación Subfase 5.2: Concurrencia Continua

## Precondiciones
- Subfase 5.1 (Proyecciones parciales y GZIP) implementada y verificada.

## Contratos Implementados
1. **Concurrencia continua saturada**: 
   - Se removió el lote rígido de 6 elementos (`messages.chunked(6)`). 
   - Se procesan todas las peticiones asincrónicamente limitadas globalmente por un `Semaphore(6)`.
2. **Backoff sin bloqueo**:
   - `fetchWithRetry` se modificó para adquirir el permiso de semáforo antes de la solicitud HTTP.
   - El permiso se libera _antes_ de procesar el retardo `delayFn()` en el backoff de reintentos, maximizando la disponibilidad del hilo de red.
3. **Preservación del orden**: 
   - A pesar de que las solicitudes asincrónicas se completan fuera de orden debido al semáforo y las distintas latencias, `PaginatedResult.items` garantiza mantener el mismo orden de los identificadores originales en la lista de Gmail, mapeando el índice inicial previo a la construcción final.
4. **Resiliencia y Cancelación**:
   - La propagación de `CancellationException` asegura la interrupción de todas las tareas (inclusive bloqueadas en `delayFn` o esperando en el semáforo), dejando 0 trabajo residual.

## Resultados de Pruebas
- **`GmailPageHelperConcurrencyTest`**:
  - `nunca hay mas de seis solicitudes activas y el primero bloqueado no frena al resto`: Demostró que el límite de 6 es respetado y no existen pausas de batching rígidas; las respuestas fluyen independientemente.
  - `mensaje en backoff libera permiso inmediatamente para otro`: Validó que un fallo que requiere backoff suspende la tarea pero no acapara el permiso, cediendo su lugar de la ranura de concurrencia inmediatamente.
  - `orden original preservado pese a respuestas desordenadas`: Forzó resoluciones invertidas por latencia (mensajes iniciales respondiendo más lento que los últimos) validando que la preservación de índices lo recupera.
  - `aislamiento de fallos supresion de token e isComplete`: Validó correctitud del conteo final, supresión del token en fallos parciales, y el aislamiento que impide que el error de un correo mate toda la operación de la lista.
  - `cancellacion propaga e interrumpe todo el trabajo residual`: Comprobó la fuga nula de corrutinas cancelando la página madre y validando un conteo estricto del MockEngine limitante.

## Análisis Estático y Suite de Regresión
- Búsqueda `chunked(6)`: 0 ocurrencias encontradas. Reemplazado.
- `./gradlew testDebugUnitTest`: PASADO
- `./gradlew compileDebugKotlin`: PASADO
- `./gradlew compileDebugAndroidTestKotlin`: PASADO
- `./gradlew lintDebug`: PASADO
- `git diff --check`: PASADO (Sin alertas de trailing whitespace).

## Estado Final
Commit generado: `perf(gmail): keep detail concurrency saturated`
