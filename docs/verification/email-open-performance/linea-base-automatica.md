# Línea Base Automática y Caracterización de Render (Subfase 0.5)

## Estado de la Verificación

- **Fecha:** 2026-08-31 21:05:00 -0600 (CST)
- **Git HEAD:** `aebdc230801683c85ea364af22cab6279e1a9254`
- **Estado general:** APROBADO / VERDE en todas las puertas automáticas.

---

## 1. Suite Completa JVM

- **Comando:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
- **Resultado:** 60 suites de prueba ejecutadas, **600 tests totales**, 0 fallos, 0 errores, 0 omitidos.
- **Suites relevantes verificadas:**
  - `MailOpenPerformanceTraceTest`: 7/7 tests verdes (anonimización de claves, ciclo de vida de sesión, reemplazo por toque duplicado, aborto en error/dispose, secciones asíncronas y comportamiento no-op en release).
  - `EmailDetailViewModelTest` & `EmailDetailContractsTest`: resolución, preparación de cuerpo y manejo de estados UI.
  - `EmailResolutionCoordinatorTest` & `GmailFetchEmailByIdTest`: resolución local/remota y single-flight.
  - `EmailContentCoordinatorTest` & `EmailHtmlCleanerTest`: extracción, decodificación Base64 y sanitización HTML con Jsoup.
  - `GmailPageHelperTest` & `GmailMappingAndThreadingTest`: paginación, concurrencia por lotes y mapeo de cabeceras.

---

## 2. Compilación de Todas las Variantes

- **Comando:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug assembleBenchmark assembleAndroidTest :macrobenchmark:assembleBenchmark`
- **Resultados de artefactos:**
  - `app-debug.apk`: Generado exitosamente.
  - `app-benchmark.apk`: Generado exitosamente (minificado con R8, no depurable, profileable activo).
  - `app-debug-androidTest.apk`: Generado exitosamente.
  - `macrobenchmark-benchmark.apk`: Generado exitosamente.

---

## 3. Análisis Estático (Android Lint)

- **Comando:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew lintDebug`
- **Resultado:** 0 errores bloqueantes (`BUILD SUCCESSFUL`).
- **Reporte generado:** `app/build/reports/lint-results-debug.html`.

---

## 4. Herramienta de Análisis Determinista

- **Comando:** `python3 tools/performance/test_analyze_traces.py`
- **Resultado:** 4/4 tests verdes (cálculo de nearest-rank p50/p95, exclusión de 3 calentamientos, validación de 10 muestras y detección estricta de datos sensibles).

---

## 5. Caracterización del Render Local (EmailBodyWebView)

La suite de referencia `EmailBodyWebViewBaselineTest` (22 contratos observables) congela el comportamiento del renderizado local aislado de la red:
- **HTML simple (F01):** Carga directa con estilos tipográficos y márgenes estándar.
- **Newsletter con tablas (F02):** Manejo de overflow horizontal y layouts responsivos preservados.
- **Imágenes remotas (F03):** Bloqueo de red aplicado vía `WebSettings.blockNetworkImage/blockNetworkLoads`.
- **Datos embebidos (F04 / F05):** Carga de URIs `data:` y navegación segura de enlaces externos vía Chrome Custom Tabs.
- **Ciclo de vida y estado visual:** Pausa y reanudación con preservación exacta de `scrollY` tras confirmación visual `postVisualStateCallback`.

Esta caracterización garantiza que las mediciones de latencia post-HTTP en la app aíslen el render local de las variaciones del transporte Gmail.

---

## 6. Verificación de Integridad y Aislamiento

Hashes SHA-256 de los 3 archivos protegidos verificados:

| Archivo | SHA-256 | Estado |
|---|---|---|
| `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | INTACTO |
| `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | INTACTO |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | INTACTO |
