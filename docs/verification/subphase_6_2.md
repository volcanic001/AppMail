# Verificación Subfase 6.2: Benchmark físico

## Metadatos
- **Fecha**: 2026-09-02
- **HEAD Inicial**: `b216fe5914c5e041c3687c3a190a46ab8c211006`
- **Estado**: **CERRADA CON EXCEPCIÓN METODOLÓGICA**

## Resumen
La subfase 6.2 exige validar los tiempos físicos y la memoria consumida en la carga de correos para certificar si se cumplen o no los presupuestos arquitectónicos propuestos en la Fase 3.

No obstante, **no se ejecutarán ADB, instrumentación, VM, emulador ni dispositivos físicos en esta subfase**. Esta decisión metodológica está aprobada debido a problemas reales e insalvables de infraestructura local:
1. **Pixel 9**: Continúa bloqueado estructuralmente por un fallo en el punto de montaje de `tracefs`, lo que impide a Perfetto/Macrobenchmark arrancar.
2. **Huawei (Backup)**: No logra completar la suite conjunta de Macrobenchmark y Perfetto bajo entorno continuo.

Por lo anterior, declaramos formalmente la ausencia de evidencia sintética (sin `runs.csv`, sin `summary.json`, sin trazas sanitizadas, sin conteos físicos simulados) y dejamos explícito que **las métricas físicas no fueron medidas**.

## Validación Automática (Infraestructura)
A pesar de la falta de recolección de rendimiento en dispositivo, certificamos la salud de la infraestructura construida ejecutando localmente las rutinas estáticas:

1. **Scripts Físicos**:
   - `python3 tools/performance/test_analyze_traces.py` -> **OK** (5 tests pasados en 0.003s)
   - `bash -n tools/performance/run_physical_benchmark.sh` -> **OK**
   - `bash -n tools/performance/run_pixel9_benchmark.sh` -> **OK**

2. **Macrobenchmark App (`com.david.macrobenchmark.EmailOpenMacrobenchmark`)**:
   - **Tramos**: Confirmadas estáticamente seis secciones `EmailOpen.*` (`Total`, `Resolve`, `BodyFetch`, `HtmlBuild`, `WebViewVisual`, `NetworkFull`) en el código de medición (`MailOpenPerformanceTrace.kt`).
   - **Ganchos Visuales**: `inbox_list` y `email_detail_visual_ready` intactos en los tests.
   - **Escenarios**: Exactamente 3 escenarios inalterados (`plainTextFirstOpen`, `plainTextReopenWarmProcess`, `plainTextReopenColdProcess`).
   - **Mediciones**: Cada escenario conserva intactas las **13 iteraciones** requeridas (3 calentamientos y 10 mediciones).
   - **Parámetros**: Usa estrictamente `CompilationMode.DEFAULT` y `startupMode = null`.
   - **Preflight**: Certificada la presencia de `MacrobenchmarkPerfettoPreflight.kt` a una sola iteración de prueba.

3. **APKs Resultantes (Generados limpiamente, no ejecutados)**:
   - `app-benchmark.apk`
     - Tamaño: 4,969,253 bytes
     - SHA-256: `1cb06c73b11a8b1acbb75577b7e08e9491869ac440d780b5ffcaba6a3bd793a7`
   - `macrobenchmark-benchmark.apk`
     - Tamaño: 40,071,883 bytes
     - SHA-256: `4fe012483fff1dc495a032200ead07079ac0da41a362beebcdbc2a55a267cd2b`

## Matriz De Resultado

| Presupuesto | Resultado permitido en 6.2 |
| --- | --- |
| Texto cacheado, cero FULL | Evidencia funcional automática; latencia física no medida |
| HTML cacheado caliente | No medido físicamente |
| Correo no cacheado, una FULL | Evidencia funcional automática; no medición física |
| HTTP_DONE → texto/HTML legible | No medido físicamente |
| Inbox p95 y memoria pico | No medido físicamente |
| p50/p95 y FrameTiming | No medidos |

## Reapertura Futura
Una ejecución posterior que reabra y elimine la excepción de esta subfase **deberá usar un dispositivo real** que soporte Perfetto, compilación AOT y tracefs. Deberá producir los 3 calentamientos y las 10 muestras completas, publicando `runs.csv`, `summary.json`, `network-counts.csv` y subiendo las trazas válidas sanitizadas. Los valores extrapolados, virtualizados o emulados no serán aceptados.
