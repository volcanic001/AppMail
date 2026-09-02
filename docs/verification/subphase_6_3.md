# Verificación Subfase 6.3: Auditoría maestra

## Metadatos

- **Fecha:** 2026-09-02
- **Commit funcional previo al cierre documental:** `75f34ba`
- **Estado:** **CERRADA CON EXCEPCIÓN METODOLÓGICA FÍSICA**
- **AVD:** `Medium_Phone_API_36.1`, Android 16, API 36, serial `emulator-5554`

## Resultado ejecutivo

Todo lo ejecutable para la auditoría funcional está verde. La suite JVM completó 684 pruebas y la suite instrumentada completó 355 pruebas en el AVD, ambas sin fallos, errores ni omisiones. Compilación, APK debug, lint y validación de whitespace también pasaron.

La excepción metodológica de 6.2 permanece limitada al benchmark físico. La ejecución instrumentada de esta subfase valida integración Android y Room, pero no demuestra p50, p95, memoria, FrameTiming ni rendimiento físico.

## Contratos auditados

- Existe una sola construcción productiva de `format=full`, en `GmailFullMessageRequest`.
- La lista usa `LIST_FIELDS = "messages(id,threadId),nextPageToken"` y no selecciona cuerpos.
- El detalle completo usa `FULL_MESSAGE_FIELDS = "id,threadId,labelIds,snippet,internalDate,payload"`.
- No existen referencias a `fetchBodyWithRefs`, `BodyFetchResult` ni `fetchAndCacheBody` bajo `app/src`.
- El presupuesto de contenido es `52_428_800` bytes; el límite exacto se conserva y un byte adicional activa la política de expulsión.
- Room está en versión 7 y registra `MIGRATION_6_7` junto con `MIGRATION_5_6`.
- La recuperación remota está unificada por `(generación de sesión, emailId)` y el detalle consume el estado explícito `NOT_FETCHED`, `READY` o `EMPTY`.

## Ejecución de pruebas

### JVM, compilación y lint

Comando final:

```text
./gradlew :app:testDebugUnitTest --rerun-tasks :app:compileDebugKotlin \
  :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --console=plain
```

Resultados:

| Verificación | Resultado |
| --- | --- |
| JVM | 684 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Kotlin producción | PASÓ |
| Kotlin androidTest | PASÓ |
| APK debug | PASÓ |
| Lint | PASÓ; 0 errores y 68 advertencias no bloqueantes |
| `git diff --check` | PASÓ |

### Instrumentación

Comando final:

```text
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
```

Resultado XML: **355 pruebas, 0 fallos, 0 errores, 0 omitidas**, tiempo agregado `667.143 s`; Gradle terminó correctamente en `11m 30s`.

La primera corrida completa encontró 29 fallos. Veintisiete provenían de fixtures instrumentados anteriores al contrato explícito de contenido y dos de condiciones UI dependientes de sincronización/estado externo. El commit `75f34ba`:

- diferencia respuestas Gmail completas de entidades ligeras locales;
- declara `READY/HTML` en fixtures que contienen cuerpo pesado;
- alinea CID con coincidencia case-insensitive;
- espera la cancelación de ViewModels antes de cerrar Room;
- compara el cuerpo HTML ya limpiado que realmente entrega el detalle;
- elimina de S09 la dependencia de que Chrome cargue contenido remoto, conservando la validación de Custom Tab, paquete, captura y retorno;
- espera visibilidad real al verificar restauración de scroll.

Después de pruebas dirigidas, la suite completa se repitió y quedó verde.

## Integridad de archivos protegidos

El commit original de 6.2 incluía por error `gradle.properties`. Se enmendó localmente de `e3653d0` a `ea403c8`, dejando en ese commit únicamente sus dos documentos. Los tres cambios preexistentes se restauraron sin stage y mantienen sus hashes originales:

| Archivo | SHA-256 | Estado |
| --- | --- | --- |
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Intacto, fuera del índice |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Intacto, fuera del índice |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Intacto, fuera del índice |

Ninguno forma parte de `75f34ba` ni del commit documental final.

## Compatibilidad y resultado antes/después

| Área | Antes | Después |
| --- | --- | --- |
| Sincronización | Lista de IDs seguida de mensajes completos cuyo cuerpo se descartaba | Respuesta parcial de lista y materialización persistente del contenido completo obtenido |
| Apertura cacheada | Podía repetir `format=full` | Reutiliza Room; cero solicitudes full cuando el contenido está completo |
| Apertura no cacheada | Rutas separadas de resolución/cuerpo | Una recuperación tipada y single-flight |
| Texto plano | WebView para todo contenido | Camino Compose nativo |
| HTML | Limpieza/render repetibles | Limpieza coordinada y WebView moderno |
| Imágenes | Resolución monolítica | Carga progresiva y CID case-insensitive |
| Transporte | Respuestas amplias sin compresión explícita | Campos parciales, gzip, límite de concurrencia y retry sanitizado |
| Persistencia | Room 6 sin estado explícito completo | Room 7 con migración 6→7, estado/tipo, referencias y LRU de 50 MiB |

Se mantienen `minSdk 26`, `targetSdk 36`, las firmas públicas de `EmailDetailScreen`, las rutas y callbacks. Dependencias relevantes verificadas: Room `2.7.0`, Ktor `3.0.3` y AndroidX WebKit `1.16.0`.

## Excepción física vigente

No se generaron métricas físicas en 6.3. Pixel 9 con GrapheneOS continúa bloqueado por la disponibilidad de tracefs requerida por Perfetto; Huawei JLN-LX3 pasa el preflight de tracefs pero no completa Macrobenchmark/Perfetto. Los resultados del AVD no sustituyen esa captura y no autorizan afirmaciones sobre p50, p95, memoria o FrameTiming físicos.
