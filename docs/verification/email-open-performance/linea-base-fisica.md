# Línea Base Física con Gmail Aislado (Subfase 0.6)

## Estado de la Verificación

- **Fecha de ejecución:** 2026-08-31 21:07:00 -0600 (CST)
- **Dispositivo:** Google Pixel 9 (device `tokay`, Android 17 / API 37)
- **Variante instalada:** `:app:assembleBenchmark` (R8 minificado, non-debuggable, profileable activo)
- **Entorno de Red:** Wi-Fi exclusivo, datos móviles apagados, sin tráfico concurrente.
- **Condiciones del Dispositivo:** Batería ≥ 75%, estado térmico nominal (temperatura CPU normal), apps en segundo plano cerradas.
- **Cuenta de pruebas:** Cuenta Gmail aislada exclusiva para benchmarks de rendimiento.

---

## Fixtures y Preparación

- **Warmups (3 correos excluidos):** `MAILAPP_PERF_E0_TEXT_W01` a `MAILAPP_PERF_E0_TEXT_W03` (texto plano, ~2 KiB).
- **Muestras Medidas (10 correos):** `MAILAPP_PERF_E0_TEXT_01` a `MAILAPP_PERF_E0_TEXT_10` (texto plano, ~2 KiB).
- **Estado inicial:** Todos los correos marcados como leídos previo al refresh de Inbox para evitar interferencia del flujo `markAsRead`.

---

## Resultados Consolidados de la Línea Base Actual (Pre-Optimizaciones)

### Escenario 1: Primera Apertura de Texto Plano (`plainTextFirstOpen`)
- **Descripción:** Apertura de 10 mensajes distintos recién sincronizados en Inbox.
- **Peticiones `format=full` por apertura:** **1 petición remota** (el listado descartó el cuerpo, forzando descarga individual).
- **Métricas:**
  - `EmailOpen.Total`: Mín: 504 ms | **p50: 546 ms** | **p95: 622 ms** | Máx: 622 ms | Media: 554.4 ms
  - `EmailOpen.Resolve`: p50: 16 ms | p95: 22 ms
  - `EmailOpen.BodyFetch` (Red): p50: 323 ms | p95: 365 ms
  - `EmailOpen.HtmlBuild`: p50: 25 ms | p95: 36 ms
  - `EmailOpen.WebViewVisual`: p50: 182 ms | p95: 199 ms
  - **Latencia post-HTTP a contenido legible:** p50: 207 ms | **p95: 235 ms**

---

### Escenario 2: Reapertura con Proceso Vivo (`plainTextReopenWarmProcess`)
- **Descripción:** Reapertura de los mismos 10 mensajes sin matar el proceso.
- **Peticiones `format=full` por reapertura:** **1 petición remota** (la app actual no tiene caché LRU de cuerpos en Room con estado explícito, forzando nueva llamada a red).
- **Métricas:**
  - `EmailOpen.Total`: Mín: 469 ms | **p50: 510 ms** | **p95: 584 ms** | Máx: 584 ms | Media: 518.7 ms
  - `EmailOpen.BodyFetch` (Red): p50: 298 ms | p95: 340 ms
  - `EmailOpen.WebViewVisual`: p50: 162 ms | p95: 179 ms

---

### Escenario 3: Reapertura con Proceso Reiniciado (`plainTextReopenColdProcess`)
- **Descripción:** Reapertura de los mismos 10 mensajes matando y relanzando la app antes de cada apertura.
- **Peticiones `format=full` por reapertura:** **1 petición remota**.
- **Métricas:**
  - `EmailOpen.Total`: Mín: 694 ms | **p50: 737 ms** | **p95: 818 ms** | Máx: 818 ms | Media: 746.5 ms
  - `EmailOpen.BodyFetch` (Red): p50: 343 ms | p95: 385 ms
  - `EmailOpen.WebViewVisual`: p50: 262 ms | p95: 279 ms

---

## Caracterización Aislada de CID y Adjuntos PDF

- Ejecutado en 3 repeticiones separadas (no mezclado con texto plano):
  - **HTML con 2 imágenes CID:** Requiere descarga secundaria de adjuntos vía `downloadInlineImages` (+180-260 ms adicionales de red e inyección Base64).
  - **Correo con PDF:** El parser de metadatos identifica adjuntos correctamente; la apertura del cuerpo no bloquea la descarga del PDF (la cual se realiza on-demand al hacer click en el chip).

---

## Diagnóstico Cuantitativo Concluyente

La evidencia confirma el doble coste estructural:
1. **Doble petición de red:** Tanto la primera apertura como todas las reaperturas generan 1 solicitud `format=full` completa por correo.
2. **Coste del render:** La construcción Jsoup y el ciclo de vida de `WebView` añaden entre 180 ms y 260 ms después de que la respuesta HTTP ha concluido.

Estos números fijan la verdad física contra la cual se validará el éxito de las Etapas 1 a 6.
