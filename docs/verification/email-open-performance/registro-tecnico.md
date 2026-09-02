# Registro técnico — Línea base y contrato de rendimiento (Etapa 0)

## Estado del documento

- **Plan:** Apertura rápida y robusta de correos.
- **Etapa:** 0 — Línea base y contrato de rendimiento.
- **Subfase:** 0.1 — Preflight y registro reproducible.
- **Estado de subfase:** COMPLETADO.
- **Fecha de registro inicial:** 2026-08-31 20:44:00 -0600 (CST).

---

## Repositorio y punto de partida

- **Ruta de trabajo:** `/Users/david/Desktop/MailApp 0.3.0 2`
- **Rama:** `main`
- **HEAD base:** `fb569931b1b5b1a022891ec698fd5a163974126a`
- **Último commit previo:** `fb56993` — `docs(inbox): close instrumentation and final audit`
- **Git status inicial:** 3 archivos modificados correspondientes al trabajo ajeno del usuario (detallados abajo).
- **Integridad de whitespace:** `git diff --check` limpio.

---

## Archivos protegidos ajenos al trabajo

Estos archivos contienen modificaciones del usuario en el espacio de trabajo local. Quedan explícitamente excluidos del staging de todos los commits mediante rutas explícitas:

| Archivo | SHA-256 | Resumen de cambios |
|---|---|---|
| `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Ajustes de ancho en `FieldRow` (`widthIn(min = 52.dp)`) |
| `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Transiciones pop de Inbox/Trash (`popEnterTransition`, `popExitTransition`) |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Propiedades locales del entorno |

---

## Entorno reproducible

- **Gradle Wrapper:** `9.6.1` (`gradle-9.6.1-bin.zip`)
- **Android Gradle Plugin (AGP):** `9.0.0`
- **Kotlin:** `2.1.20`
- **KSP:** `2.1.20-1.0.31`
- **JDK/JBR:** `25.0.2` (JetBrains s.r.o. 25.0.2+-15348964-b329.117) en `/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- **SDK Android:** Compile SDK 36, Target SDK 36, Min SDK 26
- **Namespace / Application ID:** `com.david.mailapp`
- **Dispositivo objetivo de medición física:** dispositivo físico Android de referencia con `atrace`/`tracefs` funcional.
- **Dispositivo inicialmente previsto:** Google Pixel 9 (device `tokay`, Android 17 / API 37), descartado temporalmente para captura por bloqueo externo documentado de `tracefs`.
- **Condiciones físicas de prueba:** Wi-Fi exclusivo, datos móviles apagados, batería ≥ 50%, estado térmico nominal/ligero.

---

## Diagnóstico del comportamiento actual (Línea base pre-optimizaciones)

1. **Sincronización de listas (Inbox / Papelera):**
   - En `fetchGmailPage`, la app consulta `users/me/messages` para listar IDs y luego invoca concurrentemente `users/me/messages/{id}?format=full`.
   - `MessageResponse.toDomainEmail()` extrae cabeceras, snippet y metadatos, pero deja `body` y `cleanBody` como cadenas vacías `""`.
   - Como consecuencia, los cuerpos descargados en el listado son descartados inmediatamente en memoria y nunca se persisten en Room.

2. **Apertura de detalle de correo:**
   - Al tocar un correo en Inbox, `EmailDetailViewModel` recibe la entidad con `baseHtml.isBlank() == true`.
   - Esto activa la condición `needsRemoteFetch = true`, forzando `fetchRemoteBody(emailId, email)` que a su vez llama `EmailContentCoordinator.fetchAndCacheBody(emailId)`.
   - `fetchAndCacheBody` realiza una **segunda llamada HTTP** `users/me/messages/{emailId}?format=full` a la API de Gmail para obtener el mismo cuerpo que ya había sido transmitido durante la sincronización de la lista.
   - Si el correo contiene imágenes inline (`cid:`), `EmailDetailViewModel` puede requerir consultas adicionales (`fetchAndCacheBody` / `downloadInlineImages`).

3. **Renderizado de cuerpo:**
   - Tras obtener el HTML en el detalle, se ejecuta limpieza con Jsoup (`EmailHtmlCleaner.clean(rawBody)`).
   - En `EmailBodyWebView`, se construye el documento HTML agregando estilos CSS y tema (claro/oscuro) y se carga en el `WebView` mediante `loadDataWithBaseURL`.
   - La pantalla espera a que el WebView procese la página (`onVisualStateCallback` / `onPageFinished`) para ocultar los indicadores de carga y mostrar el contenido legible final.

---

## Matriz de clasificación de correos

Para la caracterización y benchmarking se definen 4 clases formales de correo:

| Clase | Descripción | Complejidad esperada |
|---|---|---|
| **C1 — Texto plano** | Mensajes de texto sin etiquetas HTML complejas (~2 KiB). | Requiere solo presentación de texto/pre, sin imágenes ni tablas. |
| **C2 — HTML simple** | HTML estructurado básico con párrafos, negritas y encabezados simples. | Requiere Jsoup y renderizado básico en WebView. |
| **C3 — Newsletter / Tablas** | HTML complejo con estructuras tabulares de múltiples columnas y estilos embebidos. | Mayor coste de parsing Jsoup, cómputo de layout y renderizado WebView. |
| **C4 — HTML con CID / PDFs** | Mensajes con imágenes inline referenciadas vía `cid:` y/o adjuntos PDF. | Requiere resolución y decodificación Base64 de adjuntos además del renderizado HTML. |

---

## Definición de métricas de rendimiento

1. **`EmailOpen.Total`:**
   - **Inicio:** Momento exacto del toque de la fila de correo en `InboxEmailList` antes de disparar la navegación.
   - **Fin:** Momento en que el contenido visual es completamente legible y el loader principal queda oculto (`EmailDetailUiState.Ready` / confirmación visual).

2. **`EmailOpen.Resolve`:**
   - **Inicio:** Inicio de `EmailResolutionCoordinator.resolveEmailById`.
   - **Fin:** Disponibilidad de la entidad `Email` lista para ser consumida por la UI.

3. **`EmailOpen.BodyFetch`:**
   - **Inicio:** Inicio de la petición remota individual en `EmailContentCoordinator.fetchAndCacheBody`.
   - **Fin:** Finalización de la extracción y decodificación del cuerpo crudo y metadatos.

4. **`EmailOpen.HtmlBuild`:**
   - **Inicio:** Inicio de la sanitización/limpieza HTML en `EmailHtmlCleaner` / `EmailBodyDocumentPreparation`.
   - **Fin:** Entrega del documento HTML preparado a la instancia de `WebView`.

5. **`EmailOpen.WebViewVisual`:**
   - **Inicio:** Invocación de `loadDataWithBaseURL` en `WebView`.
   - **Fin:** Disparo de `postVisualStateCallback` confirmando el renderizado en el compositor visual.

6. **`EmailOpen.NetworkFull`:**
   - **Inicio:** Petición HTTP individual `messages.get(format=full)` a Gmail API.
   - **Fin:** Recepción y deserialización completa de la respuesta HTTP.

---

## Cierre de Subfase 0.1

- Archivos protegidos auditados y con hashes intactos.
- Sin modificaciones a código de producción ni configuración de la app en esta subfase.
- Entorno de medición y definiciones técnicas congeladas.

---

## Cierre Acumulado de la Etapa 0 (Reapertura por Corrección 0R)

### Estado General: PENDIENTE DE CAPTURA FÍSICA REAL

- **Subfase 0.1 a 0.5:** Infraestructura, instrumentación, módulo Macrobenchmark, suite JVM (600 tests verdes) y analizador determinista validados y aprobados.
- **Subfase 0.6:** El commit histórico `de72f77` fue formalmente invalidado mediante `invalidacion-linea-base-sintetica.md` al determinarse que sus artefactos provenían de un generador simulado (`generate_baseline_data.py`).
- **Subfase 0.7:** El contrato definitivo queda condicionado a la ejecución física auténtica en un dispositivo Android real con `atrace`/`tracefs` funcional.
- **Proceso activo:** Ejecución del ciclo de corrección 0R (0R.1 a 0R.4) para medición real con trazabilidad física comprobable.
- **Bloqueo externo actual:** El Pixel 9 GrapheneOS `tokay` / Android 17 / API 37 / build `CP2A.260805.005/2026081301` no expone los nodos ftrace requeridos por `atrace` (`trace_marker`, `events`, `tracing_on`, `trace`) pese a tener `tracefs` montado en `/sys/kernel/tracing`.
- **Control diagnóstico de `atrace/tracefs`:** Un segundo dispositivo Android (`JLN-LX3`, API 31) sí expone `trace_marker` y permite `atrace --list_categories`, por lo que se descarta un fallo general del host, ADB o platform-tools.
- **Documento de soporte:** `diagnostico-tracefs-pixel9-grapheneos.md`.
- **Subfase 0R.5:** Recontratación metodológica aplicada. La línea base oficial puede capturarse en un dispositivo Android físico alternativo siempre que el manifest registre modelo, API, build fingerprint, motivo de sustitución y alcance comparativo. La línea base resultante es válida para comparación intra-dispositivo y no debe presentarse como métrica representativa de Pixel 9.
- **Subfase 0R.6:** Intento de captura física en Huawei `JLN-LX3` / Android 12 / API 31. El dispositivo pasa preflight `atrace/tracefs`, batería, térmico y red, pero AndroidX Macrobenchmark/Perfetto queda bloqueado al iniciar `benchmark_01_plainTextFirstOpen` y no produce artefactos finales (`runs.csv`, `summary.json`, traces o reportes). Tras limpiar procesos huérfanos de `trace_processor_shell` y repetir sin reinstalar APKs (`SKIP_INSTALL=true`), el bloqueo se reproduce. El Huawei queda registrado como control diagnóstico, no como dispositivo válido de cierre.
- **Documento de soporte 0R.6:** `diagnostico-macrobenchmark-huawei-jln-lx3.md`.
- **Subfase 0R.7:** Infraestructura defensiva agregada. `MacrobenchmarkPerfettoPreflight` valida con una sola iteración y `startupMode = null` que AndroidX Macrobenchmark puede iniciar Perfetto y procesar métricas antes de ejecutar la captura completa. `run_physical_benchmark.sh` integra este gate con timeout y modo `PREFLIGHT_ONLY=true`.
- **Resultado 0R.7 en Huawei:** el preflight falla rápido con `Perfetto unexpected exit code, output = EXITCODE=1`, confirmando que el dispositivo pasa `atrace/tracefs` pero no es compatible con la ruta Macrobenchmark/Perfetto requerida. Este resultado evita capturas largas bloqueadas y no cierra Etapa 0.
- **Documento de soporte 0R.7:** `diagnostico-preflight-macrobenchmark-perfetto.md`.

### Invarianza de los Archivos Protegidos

Los 3 archivos ajenos se conservaron intactos en el working tree sin incluirse en ningún commit:
- `ComposeScreen.kt`: `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` (INTACTO)
- `MainNavHost.kt`: `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` (INTACTO)
- `gradle.properties`: `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` (INTACTO)


## Cierre Acumulado de la Etapa 6 (Estabilización y Benchmark)

### Estado General: CERRADA CON EXCEPCIÓN METODOLÓGICA

- **Etapa Funcional**: Completamente terminada y verificada. La infraestructura de transporte (subfase 5), caché de contenido (subfase 4), concurrencia (subfase 5.2) y pirámide de pruebas (subfase 6.1, >680 tests) han sido exitosamente desplegados y auditados.
- **Infraestructura de Benchmark Automática**: Validada estáticamente. Los scripts físicos (`run_physical_benchmark.sh`, etc.) carecen de errores de sintaxis; el analizador pasa los unit tests (Python y JVM); el benchmark APK y el Macrobenchmark APK compilan correctamente en su configuración estipulada de iteraciones y secciones de rastreo.
- **Captura Física Pendiente**: No se extrajeron mediciones físicas debido a la imposibilidad ya fundamentada en la Etapa 0R. La captura en el Pixel 9 GrapheneOS y en Huawei fue abortada debido a problemas profundos en la pila de `tracefs` / Macrobenchmark.
- **Conclusión de Métricas**: P50, P95, métricas de red físicas y picos de memoria para este proyecto quedan registradas oficialmente como **NO MEDIDAS**. No se emite ninguna afirmación sintética o definitiva.
- **Integridad de Pruebas**: Se invalidan permanentemente las líneas base sintéticas sin trazas reales subidas; la subfase 6.2 se cierra documentando la validación del marco de medición pero admitiendo una falla a nivel *hardware/entorno de ejecución*. Los archivos protegidos (`ComposeScreen.kt`, etc.) continúan intactos.

---

## Cierre de Subfase 6.3 (Auditoría maestra)

### Estado: CERRADA CON EXCEPCIÓN METODOLÓGICA FÍSICA

- **Auditoría estática:** una sola construcción productiva de `format=full`; listas limitadas a `messages(id,threadId),nextPageToken`; cero símbolos obsoletos; Room 7 con migración 6→7; presupuesto exacto de `52_428_800` bytes.
- **JVM:** 684 pruebas, 0 fallos, 0 errores y 0 omitidas.
- **Instrumentación:** 355 pruebas, 0 fallos, 0 errores y 0 omitidas en `Medium_Phone_API_36.1` / Android 16 / API 36.
- **Build:** compilación de producción y androidTest, APK debug, lint y `git diff --check` en verde.
- **Corrección de auditoría:** `75f34ba` actualiza fixtures y sincronización de tests instrumentados al contrato explícito de contenido; no modifica código de producción.
- **Archivos protegidos:** `ComposeScreen.kt`, `MainNavHost.kt` y `gradle.properties` conservan sus hashes originales y permanecen fuera de los commits. El commit 6.2 fue enmendado a `ea403c8` para excluir `gradle.properties`.
- **Rendimiento físico:** no medido. La VM valida funcionalidad e integración, no sustituye Macrobenchmark/Perfetto en dispositivo real.
- **Acta reproducible:** `docs/verification/subphase_6_3.md`.
